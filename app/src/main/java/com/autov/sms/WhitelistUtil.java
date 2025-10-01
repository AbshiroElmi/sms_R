package com.autov.sms;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class WhitelistUtil {
    private WhitelistUtil() {}

    private static final String KEY = "whitelist_set";

    // Get current whitelist; empty = ALL allowed
    public static Set<String> getWhitelist(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE);
        String csv = sp.getString(KEY, "");
        Set<String> out = new LinkedHashSet<>();
        if (csv == null || csv.trim().isEmpty()) return out;
        for (String s : csv.split(",")) {
            String v = s.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    // Replace the whole list
    public static void setWhitelist(Context ctx, Set<String> set) {
        String csv = String.join(",", set);
        ctx.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY, csv).apply();
    }

    // Convenience: store a single sender (not used anymore, but kept if needed)
    public static void setSingleSender(Context ctx, String sender) {
        setWhitelist(ctx, new LinkedHashSet<>(Arrays.asList(normalize(sender))));
    }

    // Clear list (ALL allowed)
    public static void clear(Context ctx) {
        ctx.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }

    // Check if an incoming SMS is allowed.
    // Rule: if whitelist empty → allow; else match exact normalized sender
    public static boolean isAllowed(Context ctx, String fromRaw) {
        Set<String> wl = getWhitelist(ctx);
        if (wl.isEmpty()) return true;
        String norm = normalize(fromRaw == null ? "" : fromRaw);
        return wl.contains(norm);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        s = s.trim();
        boolean allDigits = s.matches("\\d+");
        return allDigits ? s : s.toLowerCase(Locale.US);
    }
}
