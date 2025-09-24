package com.autov.sms;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class Iso {
    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    static {
        ISO.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    public static String fromMillis(long ms) {
        return ISO.format(new Date(ms));
    }
    public static String now() {
        return fromMillis(System.currentTimeMillis());
    }
}
