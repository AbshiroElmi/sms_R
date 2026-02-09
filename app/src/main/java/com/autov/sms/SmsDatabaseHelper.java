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
    private static final int DATABASE_VERSION = 5;

    public static final String TABLE_SMS = "sms_history";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FROM = "from_number";
    public static final String COLUMN_BODY = "body";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_STATUS = "status"; // 0: Pending, 1: Sent, 2: Failed
    public static final String COLUMN_SIM_ID = "sim_id";
    public static final String COLUMN_ISO_DATE = "iso_date";
    public static final String COLUMN_RESPONSE = "response_data";
    public static final String COLUMN_URL = "req_url";

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
                COLUMN_ISO_DATE + " TEXT, " +
                COLUMN_RESPONSE + " TEXT, " +
                COLUMN_URL + " TEXT)";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
             try {
                db.execSQL("ALTER TABLE " + TABLE_SMS + " ADD COLUMN " + COLUMN_RESPONSE + " TEXT");
            } catch (Exception e) {
                // Column might already exist or other error, ignore in this simple migration
            }
        }
        if (oldVersion < 3) {
            try {
                // This might have been skipped if DB v3 ran before migration code existed
                db.execSQL("ALTER TABLE " + TABLE_SMS + " ADD COLUMN " + COLUMN_URL + " TEXT");
            } catch (Exception e) {
                // Ignore
            }
        }
        if (oldVersion < 4) {
             try {
                // Ensure URL column exists (redundant but safe)
                db.execSQL("ALTER TABLE " + TABLE_SMS + " ADD COLUMN " + COLUMN_URL + " TEXT");
            } catch (Exception e) {
                // Ignore if already exists
            }
        }
        if (oldVersion < 5) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_SMS + " ADD COLUMN " + COLUMN_URL + " TEXT");
            } catch (Exception e) {
                // Ignore
            }
        }
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
        updateStatusAndResponse(id, status, null);
    }

    public void updateStatusAndResponse(long id, int status, String response) {
        updateStatusResponseAndUrl(id, status, response, null);
    }

    public void updateStatusResponseAndUrl(long id, int status, String response, String url) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, status);
        if (response != null) {
            values.put(COLUMN_RESPONSE, response);
        }
        if (url != null) {
            values.put(COLUMN_URL, url);
        }
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
    public void deleteAllSms() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_SMS, null, null);
    }
}
