package com.autov.sms;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class SmsDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "sms_history.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_SMS = "sms_history";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FROM = "from_number";
    public static final String COLUMN_BODY = "body";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_STATUS = "status"; // 0: Pending, 1: Sent, 2: Failed
    public static final String COLUMN_SIM_ID = "sim_id";
    public static final String COLUMN_ISO_DATE = "iso_date";

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_FAILED = 2;

    private static SmsDatabaseHelper instance;

    public static synchronized SmsDatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new SmsDatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private SmsDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_SMS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_FROM + " TEXT, " +
                COLUMN_BODY + " TEXT, " +
                COLUMN_TIMESTAMP + " INTEGER, " +
                COLUMN_STATUS + " INTEGER, " +
                COLUMN_SIM_ID + " INTEGER, " +
                COLUMN_ISO_DATE + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SMS);
        onCreate(db);
    }

    public long insertSms(String from, String body, long timestamp, int status, int simId, String isoDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_FROM, from);
        values.put(COLUMN_BODY, body);
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_STATUS, status);
        values.put(COLUMN_SIM_ID, simId);
        values.put(COLUMN_ISO_DATE, isoDate);
        return db.insert(TABLE_SMS, null, values);
    }

    public void updateStatus(long id, int status) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, status);
        db.update(TABLE_SMS, values, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
    }

    public Cursor getAllSmsCursor(String dateFilter) {
        SQLiteDatabase db = this.getReadableDatabase();
        String selection = null;
        String[] selectionArgs = null;
        if (dateFilter != null && !dateFilter.isEmpty()) {
            selection = COLUMN_ISO_DATE + " LIKE ?";
            selectionArgs = new String[]{dateFilter + "%"};
        }
        return db.query(TABLE_SMS, null, selection, selectionArgs, null, null, COLUMN_TIMESTAMP + " DESC");
    }
}
