package com.autov.sms;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class WhitelistUtil {
    private WhitelistUtil() {}

    private static final String KEY_WHITELIST = "whitelist_senders"; // StringSet
    private static final String PREF = Const.PREF_NAME;

    /** Normalize sender for consistent matching */
    public static String normalize(String s) {
        if (s == null) return "";
        // trim + lowercase; you can add more rules if needed
        return s.trim().toLowerCase();
    }

    /** Keep exactly one allowed sender (normalized) */
    public static void setSingleSender(Context ctx, String sender) {
        Set<String> s = new HashSet<>();
        String norm = normalize(sender);
        if (!norm.isEmpty()) s.add(norm);
        prefs(ctx).edit().putStringSet(KEY_WHITELIST, s).apply();
    }

    public static Set<String> getWhitelist(Context ctx) {
        Set<String> def = Collections.emptySet();
        Set<String> raw = prefs(ctx).getStringSet(KEY_WHITELIST, def);
        if (raw == null) return new HashSet<>();
        // ensure everything is normalized
        Set<String> out = new HashSet<>();
        for (String r : raw) out.add(normalize(r));
        return out;
    }

    /** Is this sender allowed? (case/space-insensitive) */
    public static boolean isAllowed(Context ctx, String from) {
        String f = normalize(from);
        Set<String> wl = getWhitelist(ctx);
        if (wl.isEmpty()) return false; // nothing chosen → block
        return wl.contains(f);
    }

    public static void clear(Context ctx) {
        prefs(ctx).edit().remove(KEY_WHITELIST).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
