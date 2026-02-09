package com.autov.sms;

public final class Const {
    public static final String PREF_NAME = "sms_prefs";
    public static final String PREF_LAST_SYNCED = "last_synced_ms";
    public static final String PREF_QUEUE = "offline_queue_v1";
    public static final String PREF_BASELINE_SET = "baseline_set";
    public static final int    QUEUE_MAX = 1000;
    // Const.java  (add these if not present)
    public static final String PREF_SEND_ALL   = "send_all";       // NEW (default false)
    public static final String PREF_SIM_INDEX  = "sim_index";      // NEW: 1 or 2

    public static final String PREF_SERVER = "server"; // 1=Autov, 3=Other
    public static final String PREF_ENABLED = "enabled";
    public static final String PREF_AUTOV_TOKEN = "autov_token";
    public static final String PREF_OTHER_URL = "other_url";

    public static final String PREF_WHITELIST_SENDER = "whitelist_sender";

//    public static final String AUTOVSERVER = "https://autov.easytouch.cloud";
    public static final String AUTOVSERVER = "http://192.168.100.205:8001";

    // Your default Autov SMS endpoint (if used)
    public static final String AUTOV_SMS_UPLOAD = AUTOVSERVER+"/api/method/autov.api.sms_upload";

    public static String verifyUrl(String token) {
        return AUTOVSERVER+"/api/method/autov.api.verify_token?token=" + token;
    }
}
