package com.autov.sms;

import android.content.Context;
import android.provider.Settings;

public final class DeviceIdUtil {
    private DeviceIdUtil() {}

    /** Best-effort stable ID (changes only on factory reset) */
    public static String get(Context ctx) {
        String id = Settings.Secure.getString(
                ctx.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
        return id == null ? "" : id;
    }
}
