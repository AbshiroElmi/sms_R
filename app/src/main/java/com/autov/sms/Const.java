package com.autov.sms;

public final class Const {
    public static final String PREF_NAME = "sms_prefs";
    public static final String PREF_LAST_SYNCED = "last_synced_ms";
    public static final String PREF_QUEUE = "offline_queue_v1";
    public static final String PREF_BASELINE_SET = "baseline_set";
    public static final int    QUEUE_MAX = 1000;

    public static final String PREF_SERVER = "server"; // 1=Autov, 3=Other
    public static final String PREF_ENABLED = "enabled";
    public static final String PREF_AUTOV_TOKEN = "autov_token";
    public static final String PREF_OTHER_URL = "other_url";

    public static final String PREF_WHITELIST_SENDER = "whitelist_sender";

    // Your default Autov SMS endpoint (if used)
    public static final String AUTOV_SMS_UPLOAD = "https://autov.easytouch.cloud/api/method/autov.api.sms_upload";

    public static String verifyUrl(String token) {
        return "https://autov.easytouch.cloud/api/method/autov.api.verfy_token?token=" + token;
    }
}
