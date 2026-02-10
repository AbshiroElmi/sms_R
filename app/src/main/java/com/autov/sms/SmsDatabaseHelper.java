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
    private static final int DATABASE_VERSION = 7;

    public static final String TABLE_SMS = "sms_history";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_FROM = "from_number";
    public static final String COLUMN_BODY = "body";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_STATUS = "status"; // 0: Pending, 1: Sent, 2: Failed
    public static final String COLUMN_SIM_ID = "sim_id";
    public static final String COLUMN_SIM_INDEX = "sim_index"; // 1 or 2 for display
    public static final String COLUMN_ISO_DATE = "iso_date";
    public static final String COLUMN_RESPONSE = "response_data";
    public static final String COLUMN_URL = "req_url";

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_FAILED = 2;

    public static final String TABLE_CONFIG = "configurations";
    public static final String COL_CONFIG_id = "_id";
    public static final String COL_CONFIG_TITLE = "title";
    public static final String COL_CONFIG_SIM = "sim_index"; // 0=Both, 1=Sim1, 2=Sim2
    public static final String COL_CONFIG_SERVER_TYPE = "server_type"; // 1=Autov, 3=Other
    public static final String COL_CONFIG_SERVER_URL = "server_url";
    public static final String COL_CONFIG_TOKEN = "auth_token";
    public static final String COL_CONFIG_WHITELIST = "whitelist";
    public static final String COL_CONFIG_ACTIVE = "is_active"; // 1=true, 0=false

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
                COLUMN_SIM_INDEX + " INTEGER, " +
                COLUMN_ISO_DATE + " TEXT, " +
                COLUMN_RESPONSE + " TEXT, " +
                COLUMN_URL + " TEXT)";
        db.execSQL(createTable);

        String createConfigTable = "CREATE TABLE " + TABLE_CONFIG + " (" +
                COL_CONFIG_id + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_CONFIG_TITLE + " TEXT, " +
                COL_CONFIG_SIM + " INTEGER, " +
                COL_CONFIG_SERVER_TYPE + " INTEGER, " +
                COL_CONFIG_SERVER_URL + " TEXT, " +
                COL_CONFIG_TOKEN + " TEXT, " +
                COL_CONFIG_WHITELIST + " TEXT, " +
                COL_CONFIG_ACTIVE + " INTEGER)";
        db.execSQL(createConfigTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE " + TABLE_SMS + " ADD COLUMN " + COLUMN_RESPONSE + " TEXT");
        if (oldVersion < 3) try { db.execSQL("ALTER TABLE " + TABLE_SMS + " ADD COLUMN " + COLUMN_URL + " TEXT"); } catch(Exception e){}
        if (oldVersion < 6) {
             String createConfigTable = "CREATE TABLE " + TABLE_CONFIG + " (" +
                COL_CONFIG_id + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_CONFIG_TITLE + " TEXT, " +
                COL_CONFIG_SIM + " INTEGER, " +
                COL_CONFIG_SERVER_TYPE + " INTEGER, " +
                COL_CONFIG_SERVER_URL + " TEXT, " +
                COL_CONFIG_TOKEN + " TEXT, " +
                COL_CONFIG_WHITELIST + " TEXT, " +
                COL_CONFIG_ACTIVE + " INTEGER)";
             try { db.execSQL(createConfigTable); } catch(Exception e){}
        }
        if (oldVersion < 7) {
            try { db.execSQL("ALTER TABLE " + TABLE_SMS + " ADD COLUMN " + COLUMN_SIM_INDEX + " INTEGER"); } catch(Exception e){}
        }
    }

    public long insertSms(String from, String body, long timestamp, int status, int simId, int simIndex, String isoDate) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_FROM, from);
        values.put(COLUMN_BODY, body);
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_STATUS, status);
        values.put(COLUMN_SIM_ID, simId);
        values.put(COLUMN_SIM_INDEX, simIndex);
        values.put(COLUMN_ISO_DATE, isoDate);
        return db.insert(TABLE_SMS, null, values);
    }

    public void updateStatusResponseAndUrl(long id, int status, String response, String url) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, status);
        if (response != null) values.put(COLUMN_RESPONSE, response);
        if (url != null) values.put(COLUMN_URL, url);
        db.update(TABLE_SMS, values, COLUMN_ID + "=?", new String[]{String.valueOf(id)});
    }

    public Cursor getAllSmsCursor(String dateFilter, String searchText) {
        SQLiteDatabase db = this.getReadableDatabase();
        StringBuilder selection = new StringBuilder();
        List<String> argsList = new ArrayList<>();
        if (dateFilter != null && !dateFilter.isEmpty()) {
            selection.append(COLUMN_ISO_DATE + " LIKE ?");
            argsList.add(dateFilter + "%");
        }
        if (searchText != null && !searchText.trim().isEmpty()) {
            if (selection.length() > 0) selection.append(" AND ");
            selection.append("(" + COLUMN_FROM + " LIKE ? OR " + COLUMN_BODY + " LIKE ?)");
            String term = "%" + searchText.trim() + "%";
            argsList.add(term);
            argsList.add(term);
        }
        String sel = selection.length() == 0 ? null : selection.toString();
        String[] selArgs = argsList.isEmpty() ? null : argsList.toArray(new String[0]);
        return db.query(TABLE_SMS, null, sel, selArgs, null, null, COLUMN_TIMESTAMP + " DESC");
    }

    public void deleteAllSms() {
        this.getWritableDatabase().delete(TABLE_SMS, null, null);
    }

    // Config Methods
    public long addConfig(String title, int sim, int serverType, String url, String token, String whitelist, boolean isActive) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_CONFIG_TITLE, title);
        cv.put(COL_CONFIG_SIM, sim);
        cv.put(COL_CONFIG_SERVER_TYPE, serverType);
        cv.put(COL_CONFIG_SERVER_URL, url);
        cv.put(COL_CONFIG_TOKEN, token);
        cv.put(COL_CONFIG_WHITELIST, whitelist);
        cv.put(COL_CONFIG_ACTIVE, isActive ? 1 : 0);
        return db.insert(TABLE_CONFIG, null, cv);
    }

    public void updateConfigActive(long id, boolean isActive) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_CONFIG_ACTIVE, isActive ? 1 : 0);
        db.update(TABLE_CONFIG, cv, COL_CONFIG_id + "=?", new String[]{String.valueOf(id)});
    }

    public void updateConfig(long id, String title, int sim, int serverType, String url, String token, String whitelist) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COL_CONFIG_TITLE, title);
        cv.put(COL_CONFIG_SIM, sim);
        cv.put(COL_CONFIG_SERVER_TYPE, serverType);
        cv.put(COL_CONFIG_SERVER_URL, url);
        cv.put(COL_CONFIG_TOKEN, token);
        cv.put(COL_CONFIG_WHITELIST, whitelist);
        db.update(TABLE_CONFIG, cv, COL_CONFIG_id + "=?", new String[]{String.valueOf(id)});
    }

    public void deleteConfig(long id) {
        this.getWritableDatabase().delete(TABLE_CONFIG, COL_CONFIG_id + "=?", new String[]{String.valueOf(id)});
    }

    public List<Config> getAllConfigs() {
        List<Config> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(TABLE_CONFIG, null, null, null, null, null, COL_CONFIG_id + " DESC");
        if (c != null) {
             while(c.moveToNext()) {
                 list.add(new Config(
                     c.getLong(c.getColumnIndexOrThrow(COL_CONFIG_id)),
                     c.getString(c.getColumnIndexOrThrow(COL_CONFIG_TITLE)),
                     c.getInt(c.getColumnIndexOrThrow(COL_CONFIG_SIM)),
                     c.getInt(c.getColumnIndexOrThrow(COL_CONFIG_SERVER_TYPE)),
                     c.getString(c.getColumnIndexOrThrow(COL_CONFIG_SERVER_URL)),
                     c.getString(c.getColumnIndexOrThrow(COL_CONFIG_TOKEN)),
                     c.getString(c.getColumnIndexOrThrow(COL_CONFIG_WHITELIST)),
                     c.getInt(c.getColumnIndexOrThrow(COL_CONFIG_ACTIVE)) == 1
                 ));
             }
             c.close();
        }
        return list;
    }

    public static class Config {
        public long id;
        public String title;
        public int simIndex;
        public int serverType;
        public String url;
        public String token;
        public String whitelist;
        public boolean isActive;
        public Config(long id, String t, int sim, int st, String u, String tok, String wl, boolean act) {
            this.id = id; title=t; simIndex=sim; serverType=st; url=u; token=tok; whitelist=wl; isActive=act;
        }
    }
}
