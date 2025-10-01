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

    /** Return the current whitelist. Empty = ALL allowed. */
    public static Set<String> getWhitelist(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE);
        String csv = sp.getString(KEY, "");
        Set<String> out = new LinkedHashSet<>();
        if (csv == null || csv.trim().isEmpty()) return out;
        for (String s : csv.split(",")) {
            String v = normalize(s);
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    /** Replace the whole list (values are normalized before saving). */
    public static void setWhitelist(Context ctx, Set<String> set) {
        Set<String> norm = new LinkedHashSet<>();
        for (String s : set) {
            String v = normalize(s);
            if (!v.isEmpty()) norm.add(v);
        }
        String csv = String.join(",", norm);
        ctx.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY, csv).apply();
    }

    /** True if there is at least one whitelisted sender saved. */
    public static boolean hasAny(Context ctx) {
        return !getWhitelist(ctx).isEmpty();
    }

    /** Add a single sender to the whitelist. */
    public static void add(Context ctx, String sender) {
        Set<String> cur = new LinkedHashSet<>(getWhitelist(ctx));
        String v = normalize(sender);
        if (!v.isEmpty()) {
            cur.add(v);
            setWhitelist(ctx, cur);
        }
    }

    /** Remove a sender from the whitelist. */
    public static void remove(Context ctx, String sender) {
        Set<String> cur = new LinkedHashSet<>(getWhitelist(ctx));
        cur.remove(normalize(sender));
        setWhitelist(ctx, cur);
    }

    /** Convenience: replace with a single sender. */
    public static void setSingleSender(Context ctx, String sender) {
        setWhitelist(ctx, new LinkedHashSet<>(Arrays.asList(normalize(sender))));
    }

    /** Clear list (ALL allowed). */
    public static void clear(Context ctx) {
        ctx.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }

    /**
     * Check if an incoming SMS is allowed.
     * Rule: if whitelist empty → allow; else exact match on normalized sender.
     */
    public static boolean isAllowed(Context ctx, String fromRaw) {
        Set<String> wl = getWhitelist(ctx);
        if (wl.isEmpty()) return true;
        return wl.contains(normalize(fromRaw));
    }

    /** Normalize: numbers keep original; text becomes lower-case & trimmed. */
    private static String normalize(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.isEmpty()) return "";
        boolean allDigits = s.matches("\\d+");
        return allDigits ? s : s.toLowerCase(Locale.US);
    }
}
