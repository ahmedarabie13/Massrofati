package com.banksms.expensetracker.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.banksms.expensetracker.data.local.entity.TransactionEntity;
import java.lang.Class;
import java.lang.Double;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TransactionDao_Impl implements TransactionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TransactionEntity> __insertionAdapterOfTransactionEntity;

  private final EntityDeletionOrUpdateAdapter<TransactionEntity> __deletionAdapterOfTransactionEntity;

  private final EntityDeletionOrUpdateAdapter<TransactionEntity> __updateAdapterOfTransactionEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  public TransactionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTransactionEntity = new EntityInsertionAdapter<TransactionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `transactions` (`id`,`messageId`,`sender`,`type`,`amount`,`currency`,`merchant`,`accountOrCard`,`availableBalance`,`category`,`timestamp`,`rawBody`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TransactionEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getMessageId());
        statement.bindString(3, entity.getSender());
        statement.bindString(4, entity.getType());
        statement.bindDouble(5, entity.getAmount());
        statement.bindString(6, entity.getCurrency());
        if (entity.getMerchant() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getMerchant());
        }
        if (entity.getAccountOrCard() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getAccountOrCard());
        }
        if (entity.getAvailableBalance() == null) {
          statement.bindNull(9);
        } else {
          statement.bindDouble(9, entity.getAvailableBalance());
        }
        statement.bindString(10, entity.getCategory());
        statement.bindLong(11, entity.getTimestamp());
        statement.bindString(12, entity.getRawBody());
      }
    };
    this.__deletionAdapterOfTransactionEntity = new EntityDeletionOrUpdateAdapter<TransactionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `transactions` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TransactionEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
    this.__updateAdapterOfTransactionEntity = new EntityDeletionOrUpdateAdapter<TransactionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `transactions` SET `id` = ?,`messageId` = ?,`sender` = ?,`type` = ?,`amount` = ?,`currency` = ?,`merchant` = ?,`accountOrCard` = ?,`availableBalance` = ?,`category` = ?,`timestamp` = ?,`rawBody` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TransactionEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getMessageId());
        statement.bindString(3, entity.getSender());
        statement.bindString(4, entity.getType());
        statement.bindDouble(5, entity.getAmount());
        statement.bindString(6, entity.getCurrency());
        if (entity.getMerchant() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getMerchant());
        }
        if (entity.getAccountOrCard() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getAccountOrCard());
        }
        if (entity.getAvailableBalance() == null) {
          statement.bindNull(9);
        } else {
          statement.bindDouble(9, entity.getAvailableBalance());
        }
        statement.bindString(10, entity.getCategory());
        statement.bindLong(11, entity.getTimestamp());
        statement.bindString(12, entity.getRawBody());
        statement.bindLong(13, entity.getId());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM transactions WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM transactions";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final TransactionEntity transaction,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfTransactionEntity.insertAndReturnId(transaction);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<TransactionEntity> transactions,
      final Continuation<? super List<Long>> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<List<Long>>() {
      @Override
      @NonNull
      public List<Long> call() throws Exception {
        __db.beginTransaction();
        try {
          final List<Long> _result = __insertionAdapterOfTransactionEntity.insertAndReturnIdsList(transactions);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final TransactionEntity transaction,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfTransactionEntity.handle(transaction);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final TransactionEntity transaction,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfTransactionEntity.handle(transaction);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getById(final long id, final Continuation<? super TransactionEntity> $completion) {
    final String _sql = "SELECT * FROM transactions WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TransactionEntity>() {
      @Override
      @Nullable
      public TransactionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
          final int _cursorIndexOfSender = CursorUtil.getColumnIndexOrThrow(_cursor, "sender");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCurrency = CursorUtil.getColumnIndexOrThrow(_cursor, "currency");
          final int _cursorIndexOfMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "merchant");
          final int _cursorIndexOfAccountOrCard = CursorUtil.getColumnIndexOrThrow(_cursor, "accountOrCard");
          final int _cursorIndexOfAvailableBalance = CursorUtil.getColumnIndexOrThrow(_cursor, "availableBalance");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfRawBody = CursorUtil.getColumnIndexOrThrow(_cursor, "rawBody");
          final TransactionEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMessageId;
            _tmpMessageId = _cursor.getLong(_cursorIndexOfMessageId);
            final String _tmpSender;
            _tmpSender = _cursor.getString(_cursorIndexOfSender);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpCurrency;
            _tmpCurrency = _cursor.getString(_cursorIndexOfCurrency);
            final String _tmpMerchant;
            if (_cursor.isNull(_cursorIndexOfMerchant)) {
              _tmpMerchant = null;
            } else {
              _tmpMerchant = _cursor.getString(_cursorIndexOfMerchant);
            }
            final String _tmpAccountOrCard;
            if (_cursor.isNull(_cursorIndexOfAccountOrCard)) {
              _tmpAccountOrCard = null;
            } else {
              _tmpAccountOrCard = _cursor.getString(_cursorIndexOfAccountOrCard);
            }
            final Double _tmpAvailableBalance;
            if (_cursor.isNull(_cursorIndexOfAvailableBalance)) {
              _tmpAvailableBalance = null;
            } else {
              _tmpAvailableBalance = _cursor.getDouble(_cursorIndexOfAvailableBalance);
            }
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpRawBody;
            _tmpRawBody = _cursor.getString(_cursorIndexOfRawBody);
            _result = new TransactionEntity(_tmpId,_tmpMessageId,_tmpSender,_tmpType,_tmpAmount,_tmpCurrency,_tmpMerchant,_tmpAccountOrCard,_tmpAvailableBalance,_tmpCategory,_tmpTimestamp,_tmpRawBody);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TransactionEntity>> getAllTransactionsFlow() {
    final String _sql = "SELECT * FROM transactions ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<List<TransactionEntity>>() {
      @Override
      @NonNull
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
          final int _cursorIndexOfSender = CursorUtil.getColumnIndexOrThrow(_cursor, "sender");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCurrency = CursorUtil.getColumnIndexOrThrow(_cursor, "currency");
          final int _cursorIndexOfMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "merchant");
          final int _cursorIndexOfAccountOrCard = CursorUtil.getColumnIndexOrThrow(_cursor, "accountOrCard");
          final int _cursorIndexOfAvailableBalance = CursorUtil.getColumnIndexOrThrow(_cursor, "availableBalance");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfRawBody = CursorUtil.getColumnIndexOrThrow(_cursor, "rawBody");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMessageId;
            _tmpMessageId = _cursor.getLong(_cursorIndexOfMessageId);
            final String _tmpSender;
            _tmpSender = _cursor.getString(_cursorIndexOfSender);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpCurrency;
            _tmpCurrency = _cursor.getString(_cursorIndexOfCurrency);
            final String _tmpMerchant;
            if (_cursor.isNull(_cursorIndexOfMerchant)) {
              _tmpMerchant = null;
            } else {
              _tmpMerchant = _cursor.getString(_cursorIndexOfMerchant);
            }
            final String _tmpAccountOrCard;
            if (_cursor.isNull(_cursorIndexOfAccountOrCard)) {
              _tmpAccountOrCard = null;
            } else {
              _tmpAccountOrCard = _cursor.getString(_cursorIndexOfAccountOrCard);
            }
            final Double _tmpAvailableBalance;
            if (_cursor.isNull(_cursorIndexOfAvailableBalance)) {
              _tmpAvailableBalance = null;
            } else {
              _tmpAvailableBalance = _cursor.getDouble(_cursorIndexOfAvailableBalance);
            }
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpRawBody;
            _tmpRawBody = _cursor.getString(_cursorIndexOfRawBody);
            _item = new TransactionEntity(_tmpId,_tmpMessageId,_tmpSender,_tmpType,_tmpAmount,_tmpCurrency,_tmpMerchant,_tmpAccountOrCard,_tmpAvailableBalance,_tmpCategory,_tmpTimestamp,_tmpRawBody);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<TransactionEntity>> getTransactionsInRangeFlow(final long startTime,
      final long endTime) {
    final String _sql = "\n"
            + "        SELECT * FROM transactions \n"
            + "        WHERE timestamp >= ? AND timestamp <= ?\n"
            + "        ORDER BY timestamp DESC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startTime);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endTime);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<List<TransactionEntity>>() {
      @Override
      @NonNull
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
          final int _cursorIndexOfSender = CursorUtil.getColumnIndexOrThrow(_cursor, "sender");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCurrency = CursorUtil.getColumnIndexOrThrow(_cursor, "currency");
          final int _cursorIndexOfMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "merchant");
          final int _cursorIndexOfAccountOrCard = CursorUtil.getColumnIndexOrThrow(_cursor, "accountOrCard");
          final int _cursorIndexOfAvailableBalance = CursorUtil.getColumnIndexOrThrow(_cursor, "availableBalance");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfRawBody = CursorUtil.getColumnIndexOrThrow(_cursor, "rawBody");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMessageId;
            _tmpMessageId = _cursor.getLong(_cursorIndexOfMessageId);
            final String _tmpSender;
            _tmpSender = _cursor.getString(_cursorIndexOfSender);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpCurrency;
            _tmpCurrency = _cursor.getString(_cursorIndexOfCurrency);
            final String _tmpMerchant;
            if (_cursor.isNull(_cursorIndexOfMerchant)) {
              _tmpMerchant = null;
            } else {
              _tmpMerchant = _cursor.getString(_cursorIndexOfMerchant);
            }
            final String _tmpAccountOrCard;
            if (_cursor.isNull(_cursorIndexOfAccountOrCard)) {
              _tmpAccountOrCard = null;
            } else {
              _tmpAccountOrCard = _cursor.getString(_cursorIndexOfAccountOrCard);
            }
            final Double _tmpAvailableBalance;
            if (_cursor.isNull(_cursorIndexOfAvailableBalance)) {
              _tmpAvailableBalance = null;
            } else {
              _tmpAvailableBalance = _cursor.getDouble(_cursorIndexOfAvailableBalance);
            }
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpRawBody;
            _tmpRawBody = _cursor.getString(_cursorIndexOfRawBody);
            _item = new TransactionEntity(_tmpId,_tmpMessageId,_tmpSender,_tmpType,_tmpAmount,_tmpCurrency,_tmpMerchant,_tmpAccountOrCard,_tmpAvailableBalance,_tmpCategory,_tmpTimestamp,_tmpRawBody);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<TransactionEntity>> getFilteredTransactionsFlow(final long startTime,
      final long endTime, final String type, final String sender, final String category,
      final String searchQuery) {
    final String _sql = "\n"
            + "        SELECT * FROM transactions \n"
            + "        WHERE timestamp >= ? AND timestamp <= ?\n"
            + "        AND (? IS NULL OR type = ?)\n"
            + "        AND (? IS NULL OR sender = ?)\n"
            + "        AND (? IS NULL OR category = ?)\n"
            + "        AND (? IS NULL OR merchant LIKE '%' || ? || '%' OR rawBody LIKE '%' || ? || '%')\n"
            + "        ORDER BY timestamp DESC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 11);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startTime);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endTime);
    _argIndex = 3;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    _argIndex = 4;
    if (type == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, type);
    }
    _argIndex = 5;
    if (sender == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, sender);
    }
    _argIndex = 6;
    if (sender == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, sender);
    }
    _argIndex = 7;
    if (category == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, category);
    }
    _argIndex = 8;
    if (category == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, category);
    }
    _argIndex = 9;
    if (searchQuery == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, searchQuery);
    }
    _argIndex = 10;
    if (searchQuery == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, searchQuery);
    }
    _argIndex = 11;
    if (searchQuery == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, searchQuery);
    }
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<List<TransactionEntity>>() {
      @Override
      @NonNull
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfMessageId = CursorUtil.getColumnIndexOrThrow(_cursor, "messageId");
          final int _cursorIndexOfSender = CursorUtil.getColumnIndexOrThrow(_cursor, "sender");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfCurrency = CursorUtil.getColumnIndexOrThrow(_cursor, "currency");
          final int _cursorIndexOfMerchant = CursorUtil.getColumnIndexOrThrow(_cursor, "merchant");
          final int _cursorIndexOfAccountOrCard = CursorUtil.getColumnIndexOrThrow(_cursor, "accountOrCard");
          final int _cursorIndexOfAvailableBalance = CursorUtil.getColumnIndexOrThrow(_cursor, "availableBalance");
          final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfRawBody = CursorUtil.getColumnIndexOrThrow(_cursor, "rawBody");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransactionEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpMessageId;
            _tmpMessageId = _cursor.getLong(_cursorIndexOfMessageId);
            final String _tmpSender;
            _tmpSender = _cursor.getString(_cursorIndexOfSender);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final double _tmpAmount;
            _tmpAmount = _cursor.getDouble(_cursorIndexOfAmount);
            final String _tmpCurrency;
            _tmpCurrency = _cursor.getString(_cursorIndexOfCurrency);
            final String _tmpMerchant;
            if (_cursor.isNull(_cursorIndexOfMerchant)) {
              _tmpMerchant = null;
            } else {
              _tmpMerchant = _cursor.getString(_cursorIndexOfMerchant);
            }
            final String _tmpAccountOrCard;
            if (_cursor.isNull(_cursorIndexOfAccountOrCard)) {
              _tmpAccountOrCard = null;
            } else {
              _tmpAccountOrCard = _cursor.getString(_cursorIndexOfAccountOrCard);
            }
            final Double _tmpAvailableBalance;
            if (_cursor.isNull(_cursorIndexOfAvailableBalance)) {
              _tmpAvailableBalance = null;
            } else {
              _tmpAvailableBalance = _cursor.getDouble(_cursorIndexOfAvailableBalance);
            }
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpRawBody;
            _tmpRawBody = _cursor.getString(_cursorIndexOfRawBody);
            _item = new TransactionEntity(_tmpId,_tmpMessageId,_tmpSender,_tmpType,_tmpAmount,_tmpCurrency,_tmpMerchant,_tmpAccountOrCard,_tmpAvailableBalance,_tmpCategory,_tmpTimestamp,_tmpRawBody);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Double> getTotalExpenseFlow(final long startTime, final long endTime) {
    final String _sql = "\n"
            + "        SELECT COALESCE(SUM(amount), 0.0) FROM transactions \n"
            + "        WHERE type = 'EXPENSE' AND timestamp >= ? AND timestamp <= ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startTime);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endTime);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<Double>() {
      @Override
      @NonNull
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final double _tmp;
            _tmp = _cursor.getDouble(0);
            _result = _tmp;
          } else {
            _result = 0.0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<Double> getTotalIncomeFlow(final long startTime, final long endTime) {
    final String _sql = "\n"
            + "        SELECT COALESCE(SUM(amount), 0.0) FROM transactions \n"
            + "        WHERE type = 'INCOME' AND timestamp >= ? AND timestamp <= ?\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startTime);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endTime);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<Double>() {
      @Override
      @NonNull
      public Double call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Double _result;
          if (_cursor.moveToFirst()) {
            final double _tmp;
            _tmp = _cursor.getDouble(0);
            _result = _tmp;
          } else {
            _result = 0.0;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<BankExpenseDbSummary>> getBankExpenseSummaryFlow(final long startTime,
      final long endTime) {
    final String _sql = "\n"
            + "        SELECT \n"
            + "            sender,\n"
            + "            SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS totalExpense,\n"
            + "            SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) AS totalIncome,\n"
            + "            COUNT(*) AS transactionCount\n"
            + "        FROM transactions\n"
            + "        WHERE timestamp >= ? AND timestamp <= ?\n"
            + "        GROUP BY sender\n"
            + "        ORDER BY totalExpense DESC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startTime);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endTime);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<List<BankExpenseDbSummary>>() {
      @Override
      @NonNull
      public List<BankExpenseDbSummary> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSender = 0;
          final int _cursorIndexOfTotalExpense = 1;
          final int _cursorIndexOfTotalIncome = 2;
          final int _cursorIndexOfTransactionCount = 3;
          final List<BankExpenseDbSummary> _result = new ArrayList<BankExpenseDbSummary>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BankExpenseDbSummary _item;
            final String _tmpSender;
            _tmpSender = _cursor.getString(_cursorIndexOfSender);
            final Double _tmpTotalExpense;
            if (_cursor.isNull(_cursorIndexOfTotalExpense)) {
              _tmpTotalExpense = null;
            } else {
              _tmpTotalExpense = _cursor.getDouble(_cursorIndexOfTotalExpense);
            }
            final Double _tmpTotalIncome;
            if (_cursor.isNull(_cursorIndexOfTotalIncome)) {
              _tmpTotalIncome = null;
            } else {
              _tmpTotalIncome = _cursor.getDouble(_cursorIndexOfTotalIncome);
            }
            final int _tmpTransactionCount;
            _tmpTransactionCount = _cursor.getInt(_cursorIndexOfTransactionCount);
            _item = new BankExpenseDbSummary(_tmpSender,_tmpTotalExpense,_tmpTotalIncome,_tmpTransactionCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<CategoryDbSummary>> getCategoryExpenseSummaryFlow(final long startTime,
      final long endTime) {
    final String _sql = "\n"
            + "        SELECT \n"
            + "            category,\n"
            + "            SUM(amount) AS totalAmount,\n"
            + "            COUNT(*) AS count\n"
            + "        FROM transactions\n"
            + "        WHERE type = 'EXPENSE' AND timestamp >= ? AND timestamp <= ?\n"
            + "        GROUP BY category\n"
            + "        ORDER BY totalAmount DESC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, startTime);
    _argIndex = 2;
    _statement.bindLong(_argIndex, endTime);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<List<CategoryDbSummary>>() {
      @Override
      @NonNull
      public List<CategoryDbSummary> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfCategory = 0;
          final int _cursorIndexOfTotalAmount = 1;
          final int _cursorIndexOfCount = 2;
          final List<CategoryDbSummary> _result = new ArrayList<CategoryDbSummary>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CategoryDbSummary _item;
            final String _tmpCategory;
            _tmpCategory = _cursor.getString(_cursorIndexOfCategory);
            final Double _tmpTotalAmount;
            if (_cursor.isNull(_cursorIndexOfTotalAmount)) {
              _tmpTotalAmount = null;
            } else {
              _tmpTotalAmount = _cursor.getDouble(_cursorIndexOfTotalAmount);
            }
            final int _tmpCount;
            _tmpCount = _cursor.getInt(_cursorIndexOfCount);
            _item = new CategoryDbSummary(_tmpCategory,_tmpTotalAmount,_tmpCount);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Flow<List<MonthlyDbTrend>> getMonthlyTrendsFlow() {
    final String _sql = "\n"
            + "        SELECT \n"
            + "            strftime('%Y-%m', datetime(timestamp / 1000, 'unixepoch')) AS yearMonth,\n"
            + "            SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) AS totalExpense,\n"
            + "            SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) AS totalIncome\n"
            + "        FROM transactions\n"
            + "        GROUP BY yearMonth\n"
            + "        ORDER BY yearMonth ASC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<List<MonthlyDbTrend>>() {
      @Override
      @NonNull
      public List<MonthlyDbTrend> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfYearMonth = 0;
          final int _cursorIndexOfTotalExpense = 1;
          final int _cursorIndexOfTotalIncome = 2;
          final List<MonthlyDbTrend> _result = new ArrayList<MonthlyDbTrend>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MonthlyDbTrend _item;
            final String _tmpYearMonth;
            _tmpYearMonth = _cursor.getString(_cursorIndexOfYearMonth);
            final Double _tmpTotalExpense;
            if (_cursor.isNull(_cursorIndexOfTotalExpense)) {
              _tmpTotalExpense = null;
            } else {
              _tmpTotalExpense = _cursor.getDouble(_cursorIndexOfTotalExpense);
            }
            final Double _tmpTotalIncome;
            if (_cursor.isNull(_cursorIndexOfTotalIncome)) {
              _tmpTotalIncome = null;
            } else {
              _tmpTotalIncome = _cursor.getDouble(_cursorIndexOfTotalIncome);
            }
            _item = new MonthlyDbTrend(_tmpYearMonth,_tmpTotalExpense,_tmpTotalIncome);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object countTransactions(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM transactions";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object getLatestTimestampForSender(final String sender,
      final Continuation<? super Long> $completion) {
    final String _sql = "SELECT MAX(timestamp) FROM transactions WHERE sender = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, sender);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Long>() {
      @Override
      @Nullable
      public Long call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Long _result;
          if (_cursor.moveToFirst()) {
            final Long _tmp;
            if (_cursor.isNull(0)) {
              _tmp = null;
            } else {
              _tmp = _cursor.getLong(0);
            }
            _result = _tmp;
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
