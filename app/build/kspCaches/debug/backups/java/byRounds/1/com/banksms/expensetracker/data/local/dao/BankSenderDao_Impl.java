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
import com.banksms.expensetracker.data.local.entity.BankSenderEntity;
import java.lang.Class;
import java.lang.Exception;
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
public final class BankSenderDao_Impl implements BankSenderDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<BankSenderEntity> __insertionAdapterOfBankSenderEntity;

  private final EntityDeletionOrUpdateAdapter<BankSenderEntity> __deletionAdapterOfBankSenderEntity;

  private final EntityDeletionOrUpdateAdapter<BankSenderEntity> __updateAdapterOfBankSenderEntity;

  private final SharedSQLiteStatement __preparedStmtOfSetMonitored;

  public BankSenderDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfBankSenderEntity = new EntityInsertionAdapter<BankSenderEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR IGNORE INTO `bank_senders` (`senderId`,`displayName`,`isMonitored`,`customRegex`) VALUES (?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BankSenderEntity entity) {
        statement.bindString(1, entity.getSenderId());
        statement.bindString(2, entity.getDisplayName());
        final int _tmp = entity.isMonitored() ? 1 : 0;
        statement.bindLong(3, _tmp);
        if (entity.getCustomRegex() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getCustomRegex());
        }
      }
    };
    this.__deletionAdapterOfBankSenderEntity = new EntityDeletionOrUpdateAdapter<BankSenderEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `bank_senders` WHERE `senderId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BankSenderEntity entity) {
        statement.bindString(1, entity.getSenderId());
      }
    };
    this.__updateAdapterOfBankSenderEntity = new EntityDeletionOrUpdateAdapter<BankSenderEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `bank_senders` SET `senderId` = ?,`displayName` = ?,`isMonitored` = ?,`customRegex` = ? WHERE `senderId` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final BankSenderEntity entity) {
        statement.bindString(1, entity.getSenderId());
        statement.bindString(2, entity.getDisplayName());
        final int _tmp = entity.isMonitored() ? 1 : 0;
        statement.bindLong(3, _tmp);
        if (entity.getCustomRegex() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getCustomRegex());
        }
        statement.bindString(5, entity.getSenderId());
      }
    };
    this.__preparedStmtOfSetMonitored = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE bank_senders SET isMonitored = ? WHERE senderId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final BankSenderEntity sender,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfBankSenderEntity.insertAndReturnId(sender);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insertAll(final List<BankSenderEntity> senders,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfBankSenderEntity.insert(senders);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final BankSenderEntity sender,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfBankSenderEntity.handle(sender);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final BankSenderEntity sender,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfBankSenderEntity.handle(sender);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object setMonitored(final String senderId, final boolean isMonitored,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetMonitored.acquire();
        int _argIndex = 1;
        final int _tmp = isMonitored ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindString(_argIndex, senderId);
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
          __preparedStmtOfSetMonitored.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<BankSenderEntity>> getAllSendersFlow() {
    final String _sql = "SELECT * FROM bank_senders ORDER BY displayName ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"bank_senders"}, new Callable<List<BankSenderEntity>>() {
      @Override
      @NonNull
      public List<BankSenderEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfIsMonitored = CursorUtil.getColumnIndexOrThrow(_cursor, "isMonitored");
          final int _cursorIndexOfCustomRegex = CursorUtil.getColumnIndexOrThrow(_cursor, "customRegex");
          final List<BankSenderEntity> _result = new ArrayList<BankSenderEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BankSenderEntity _item;
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final boolean _tmpIsMonitored;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMonitored);
            _tmpIsMonitored = _tmp != 0;
            final String _tmpCustomRegex;
            if (_cursor.isNull(_cursorIndexOfCustomRegex)) {
              _tmpCustomRegex = null;
            } else {
              _tmpCustomRegex = _cursor.getString(_cursorIndexOfCustomRegex);
            }
            _item = new BankSenderEntity(_tmpSenderId,_tmpDisplayName,_tmpIsMonitored,_tmpCustomRegex);
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
  public Flow<List<BankSenderEntity>> getMonitoredSendersFlow() {
    final String _sql = "SELECT * FROM bank_senders WHERE isMonitored = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"bank_senders"}, new Callable<List<BankSenderEntity>>() {
      @Override
      @NonNull
      public List<BankSenderEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfIsMonitored = CursorUtil.getColumnIndexOrThrow(_cursor, "isMonitored");
          final int _cursorIndexOfCustomRegex = CursorUtil.getColumnIndexOrThrow(_cursor, "customRegex");
          final List<BankSenderEntity> _result = new ArrayList<BankSenderEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BankSenderEntity _item;
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final boolean _tmpIsMonitored;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMonitored);
            _tmpIsMonitored = _tmp != 0;
            final String _tmpCustomRegex;
            if (_cursor.isNull(_cursorIndexOfCustomRegex)) {
              _tmpCustomRegex = null;
            } else {
              _tmpCustomRegex = _cursor.getString(_cursorIndexOfCustomRegex);
            }
            _item = new BankSenderEntity(_tmpSenderId,_tmpDisplayName,_tmpIsMonitored,_tmpCustomRegex);
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
  public Object getMonitoredSendersSync(
      final Continuation<? super List<BankSenderEntity>> $completion) {
    final String _sql = "SELECT * FROM bank_senders WHERE isMonitored = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<BankSenderEntity>>() {
      @Override
      @NonNull
      public List<BankSenderEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfIsMonitored = CursorUtil.getColumnIndexOrThrow(_cursor, "isMonitored");
          final int _cursorIndexOfCustomRegex = CursorUtil.getColumnIndexOrThrow(_cursor, "customRegex");
          final List<BankSenderEntity> _result = new ArrayList<BankSenderEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BankSenderEntity _item;
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final boolean _tmpIsMonitored;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMonitored);
            _tmpIsMonitored = _tmp != 0;
            final String _tmpCustomRegex;
            if (_cursor.isNull(_cursorIndexOfCustomRegex)) {
              _tmpCustomRegex = null;
            } else {
              _tmpCustomRegex = _cursor.getString(_cursorIndexOfCustomRegex);
            }
            _item = new BankSenderEntity(_tmpSenderId,_tmpDisplayName,_tmpIsMonitored,_tmpCustomRegex);
            _result.add(_item);
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
  public Flow<List<BankSenderWithStats>> getSendersWithStatsFlow() {
    final String _sql = "\n"
            + "        SELECT \n"
            + "            b.senderId,\n"
            + "            b.displayName,\n"
            + "            b.isMonitored,\n"
            + "            b.customRegex,\n"
            + "            COUNT(t.id) AS totalTransactionsCount,\n"
            + "            MAX(t.timestamp) AS lastTransactionTime\n"
            + "        FROM bank_senders b\n"
            + "        LEFT JOIN transactions t ON b.senderId = t.sender\n"
            + "        GROUP BY b.senderId\n"
            + "        ORDER BY b.isMonitored DESC, totalTransactionsCount DESC, b.displayName ASC\n"
            + "    ";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"bank_senders",
        "transactions"}, new Callable<List<BankSenderWithStats>>() {
      @Override
      @NonNull
      public List<BankSenderWithStats> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSenderId = 0;
          final int _cursorIndexOfDisplayName = 1;
          final int _cursorIndexOfIsMonitored = 2;
          final int _cursorIndexOfCustomRegex = 3;
          final int _cursorIndexOfTotalTransactionsCount = 4;
          final int _cursorIndexOfLastTransactionTime = 5;
          final List<BankSenderWithStats> _result = new ArrayList<BankSenderWithStats>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final BankSenderWithStats _item;
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final boolean _tmpIsMonitored;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMonitored);
            _tmpIsMonitored = _tmp != 0;
            final String _tmpCustomRegex;
            if (_cursor.isNull(_cursorIndexOfCustomRegex)) {
              _tmpCustomRegex = null;
            } else {
              _tmpCustomRegex = _cursor.getString(_cursorIndexOfCustomRegex);
            }
            final int _tmpTotalTransactionsCount;
            _tmpTotalTransactionsCount = _cursor.getInt(_cursorIndexOfTotalTransactionsCount);
            final Long _tmpLastTransactionTime;
            if (_cursor.isNull(_cursorIndexOfLastTransactionTime)) {
              _tmpLastTransactionTime = null;
            } else {
              _tmpLastTransactionTime = _cursor.getLong(_cursorIndexOfLastTransactionTime);
            }
            _item = new BankSenderWithStats(_tmpSenderId,_tmpDisplayName,_tmpIsMonitored,_tmpCustomRegex,_tmpTotalTransactionsCount,_tmpLastTransactionTime);
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
  public Object getBySenderId(final String senderId,
      final Continuation<? super BankSenderEntity> $completion) {
    final String _sql = "SELECT * FROM bank_senders WHERE senderId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, senderId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<BankSenderEntity>() {
      @Override
      @Nullable
      public BankSenderEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfSenderId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfIsMonitored = CursorUtil.getColumnIndexOrThrow(_cursor, "isMonitored");
          final int _cursorIndexOfCustomRegex = CursorUtil.getColumnIndexOrThrow(_cursor, "customRegex");
          final BankSenderEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpSenderId;
            _tmpSenderId = _cursor.getString(_cursorIndexOfSenderId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final boolean _tmpIsMonitored;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsMonitored);
            _tmpIsMonitored = _tmp != 0;
            final String _tmpCustomRegex;
            if (_cursor.isNull(_cursorIndexOfCustomRegex)) {
              _tmpCustomRegex = null;
            } else {
              _tmpCustomRegex = _cursor.getString(_cursorIndexOfCustomRegex);
            }
            _result = new BankSenderEntity(_tmpSenderId,_tmpDisplayName,_tmpIsMonitored,_tmpCustomRegex);
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
