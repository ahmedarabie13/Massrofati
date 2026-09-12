package com.banksms.expensetracker.data.cloud

import android.content.Context
import android.util.Log
import com.banksms.expensetracker.data.file.ManualExpense
import com.banksms.expensetracker.data.local.AiAppDatabase
import com.banksms.expensetracker.data.local.AppDatabase
import com.banksms.expensetracker.data.local.PersistentDatabase
import com.banksms.expensetracker.data.model.BankSender
import com.banksms.expensetracker.data.model.MessageTemplate
import com.banksms.expensetracker.data.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * One-time upload of the legacy on-device databases (Room query DBs +
 * masari_persistent.db) into Firestore.
 *
 * Versioned: v1 used one shared "sms_" namespace, which let the AI upload
 * overwrite same-message regex rows. v2 uses pipeline-scoped doc IDs and
 * repairs v1 by deleting every "sms_" doc and re-uploading. The
 * server-side version in meta/settings is the truth, so reinstalls never
 * re-upload. Same-phone re-runs are naturally idempotent (stable docIds);
 * rows whose natural key already exists in the cloud are skipped, so a
 * second device never duplicates shared SMS history.
 *
 * After every account's migration is confirmed, the whole data/local layer
 * and this file go away.
 */
object LegacyLocalMigration {
    private const val TAG = "LegacyMigration"
    private const val CURRENT_VERSION = 2

    suspend fun uploadIfNeeded(context: Context, store: FirestoreStore) =
        withContext(Dispatchers.IO) {
            val done = store.migrationVersion()
            if (done >= CURRENT_VERSION) {
                Log.d(TAG, "migration v$done already applied, skipping")
                return@withContext
            }
            try {
                if (done < 2) {
                    repairV1Namespace(store)
                }
                uploadAll(context, store)
                store.setMigrationVersion(CURRENT_VERSION)
            } catch (e: Exception) {
                // Never block sign-in on migration: snapshots will merge later
                // and the version stays low so the next launch retries.
                Log.w(TAG, "migration failed, will retry next launch", e)
            }
        }

    /**
     * v1 wrote both pipelines into shared "sms_<messageId>" docs (AI rows
     * overwrote regex rows). v2 gives each pipeline its own namespace, so
     * every v1 doc is stale: delete them all before re-uploading.
     */
    private suspend fun repairV1Namespace(store: FirestoreStore) {
        val staleIds = store.fetchAllTransactionsOnce()
            .map { it.tx.docId }
            .filter { it.startsWith("sms_") }
        if (staleIds.isNotEmpty()) {
            Log.d(TAG, "repair: deleting ${staleIds.size} v1 sms_* docs")
            store.deleteTransactions(staleIds)
        }
    }

    private suspend fun uploadAll(context: Context, store: FirestoreStore) {
        val appContext = context.applicationContext
        val db = AppDatabase.getInstance(appContext)
        val aiDb = AiAppDatabase.getInstance(appContext)
        val pDb = runCatching {
            PersistentDatabase.getInstance(appContext)
        }.getOrNull()

        val regexRows = runCatching {
            val flow = db.transactionDao().getAllTransactionsFlow()
            runCatching { flow.first() }.getOrNull().orEmpty()
        }.getOrNull().orEmpty().map { it.toDomain() }

        val aiRows = runCatching { aiDb.aiTransactionDao().getAllSync() }
            .getOrNull().orEmpty().map { it.toDomain() }

        val verdicts = runCatching { aiDb.aiScannedDao().getAll() }
            .getOrNull().orEmpty().map {
                ScanVerdict(
                    messageId = it.messageId,
                    sender = it.sender,
                    timestamp = it.timestamp,
                    body = it.rawBody,
                    isTransaction = it.isTransaction
                )
            }

        val manuals: List<ManualExpense> = pDb?.let {
            runCatching { it.manualExpenseDao().getAll().map { e -> e.toDomain() } }
                .getOrNull()
        }.orEmpty()

        val skipped = pDb?.let {
            runCatching { it.skippedTransactionDao().getAll().map { e -> e.toDomain() } }
                .getOrNull()
        }.orEmpty().orEmpty()

        val templates: List<MessageTemplate> = pDb?.let {
            runCatching { it.messageTemplateDao().getAll().map { e -> e.toDomain() } }
                .getOrNull()
        }.orEmpty().ifEmpty { MessageTemplate.defaultTemplates }

        val banks: List<BankSender> = pDb?.let {
            runCatching { it.monitoredBankDao().getAll().map { e -> e.toDomain() } }
                .getOrNull()
        }.orEmpty().ifEmpty {
            runCatching { db.bankSenderDao().getAllSendersSync().map { it.toDomain() } }
                .getOrNull().orEmpty()
        }.ifEmpty { BankSender.defaultSenders }

        // Natural-key dedup against what's already in the cloud.
        val cloudKeys = store.fetchAllTransactionsOnce()
            .map { naturalKey(it.tx) }.toSet()

        fun stampRegex(tx: Transaction): Transaction {
            val docId = CloudDocs.regexDocId(tx.messageId)
            return tx.copy(docId = docId, id = CloudDocs.stableId(docId))
        }

        fun stampAi(tx: Transaction): Transaction {
            val docId = CloudDocs.aiDocId(tx.messageId)
            return tx.copy(docId = docId, id = CloudDocs.stableId(docId))
        }

        val freshRegex = regexRows
            .filter { !it.isManual }
            .map(::stampRegex)
            .filter { naturalKey(it) !in cloudKeys }
        val freshAi = aiRows
            .filter { !it.isManual }
            .map(::stampAi)
            .filter { naturalKey(it) !in cloudKeys }
        // Manual mirrors: keyed by manualId, set() is idempotent.
        val manualMirrors = manuals.map {
            val tx = it.toTransaction()
            val docId = CloudDocs.manualDocId(it.id)
            tx.copy(docId = docId, id = CloudDocs.stableId(docId))
        }

        if (freshRegex.isNotEmpty()) store.setTransactions(freshRegex, TxOrigin.REGEX)
        if (freshAi.isNotEmpty()) store.setTransactions(freshAi, TxOrigin.AI)
        if (manualMirrors.isNotEmpty()) {
            store.setTransactions(manualMirrors, TxOrigin.MANUAL)
        }
        if (manuals.isNotEmpty()) store.setManuals(manuals)
        if (verdicts.isNotEmpty()) store.setVerdicts(verdicts)
        for (s in skipped) {
            runCatching { store.addSkipped(s) }
        }
        store.setTemplates(templates)
        store.setBanks(banks)

        Log.d(
            TAG,
            "uploaded regex=${freshRegex.size} ai=${freshAi.size} " +
                "manuals=${manuals.size} skipped=${skipped.size}"
        )
    }

    /** Same natural key the live dedup uses (sender/amount/type/body/minute). */
    private fun naturalKey(tx: Transaction): String =
        "${tx.sender}|${tx.amount}|${tx.type.name}|${tx.rawBody}|${tx.timestamp / 60000}"
}
