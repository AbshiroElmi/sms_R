package com.autov.sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles:
 * - Baseline (send only NEW sms after install/open)
 * - Offline queue stored in SharedPreferences (FIFO, capped)
 * - HTTP POST to webhook with headers
 * - Async helpers so receivers never block the main thread
 *
 * Requires:
 * - Const.java with ENDPOINT, PREF_NAME, PREF_LAST_SYNCED, PREF_QUEUE, PREF_BASELINE_SET, QUEUE_MAX
 * - Iso.java with Iso.now() / Iso.fromMillis(long) helpers (UTC ISO-8601)
 */
public class QueueUploader {
    private static final String TAG = "QueueUploader";

    // Single reusable background executor (no network on main thread)
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();

    // Guard to avoid concurrent flushes
    private static volatile boolean isFlushing = false;

    /* ===========================
     * Public async helpers
     * =========================== */

    /** Run sendToServer() on the background executor */
    public static void sendToServerAsync(Context ctx, JSONObject payload, String source) {
        EXEC.execute(() -> sendToServer(ctx, payload, source));
    }

    /** Run flushQueueIfAny() on the background executor */
    public static void flushQueueIfAnyAsync(Context ctx) {
        EXEC.execute(() -> flushQueueIfAny(ctx));
    }

    /* ===========================
     * Baseline utilities
     * =========================== */

    /** One-time baseline: mark "now" so older SMS are ignored */
    public static void ensureBaselineNow(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE);
        boolean already = sp.getBoolean(Const.PREF_BASELINE_SET, false);
        if (!already) {
            long now = System.currentTimeMillis();
            sp.edit()
                    .putLong(Const.PREF_LAST_SYNCED, now)
                    .putBoolean(Const.PREF_BASELINE_SET, true)
                    .apply();
        }
    }

    public static long getBaseline(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE);
        return sp.getLong(Const.PREF_LAST_SYNCED, 0L);
    }

    public static void maybeAdvanceBaseline(Context ctx, long ts) {
        SharedPreferences sp = ctx.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE);
        long prev = sp.getLong(Const.PREF_LAST_SYNCED, 0L);
        if (ts > prev) sp.edit().putLong(Const.PREF_LAST_SYNCED, ts).apply();
    }

    /* ===========================
     * Queue persistence
     * =========================== */

    private static JSONArray loadQueue(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE);
        String raw = sp.getString(Const.PREF_QUEUE, "[]");
        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private static void saveQueue(Context ctx, JSONArray arr) {
        SharedPreferences sp = ctx.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(Const.PREF_QUEUE, arr.toString()).apply();
    }

    public static void enqueueOffline(Context ctx, JSONObject payload) {
        try {
            JSONArray q = loadQueue(ctx);
            // Cap size: drop oldest
            if (q.length() >= Const.QUEUE_MAX) {
                JSONArray nq = new JSONArray();
                for (int i = 1; i < q.length(); i++) {
                    nq.put(q.opt(i));
                }
                q = nq;
            }
            payload.put("_queuedAt", Iso.now());
            q.put(payload);
            saveQueue(ctx, q);
        } catch (JSONException ignore) {}
    }

    /* ===========================
     * Network: send + flush
     * =========================== */

    public static void sendToServer(Context ctx, JSONObject payload, String source) {
        try {
            Log.d(TAG, "➡️ sending [" + source + "] "
                    + payload.optString("from") + " | " + payload.optString("body"));

            int code = postJson(Const.ENDPOINT, payload, source, null);

            if (code >= 200 && code < 300) {
                Log.d(TAG, "✅ sent [" + source + "] status=" + code);
            } else {
                Log.d(TAG, "❌ server status=" + code + " → queue");
                enqueueOffline(ctx, payload);
            }
        } catch (Exception e) {
            Log.d(TAG, "❌ network error: " + e + " → queue");
            enqueueOffline(ctx, payload);
        }
    }

    public static void flushQueueIfAny(Context ctx) {
        if (isFlushing) return;
        isFlushing = true;
        try {
            JSONArray q = loadQueue(ctx);
            if (q.length() == 0) return;

            JSONArray remain = new JSONArray();
            for (int i = 0; i < q.length(); i++) {
                JSONObject item = q.optJSONObject(i);
                if (item == null) continue;

                int code;
                try {
                    String queuedAt = item.optString("_queuedAt", "");
                    code = postJson(Const.ENDPOINT, item, "flush", queuedAt);
                } catch (Exception e) {
                    code = -1;
                }

                if (code < 200 || code >= 300) {
                    // keep it for future retry
                    remain.put(item);
                }
            }
            saveQueue(ctx, remain);
        } finally {
            isFlushing = false;
        }
    }

    /** Low-level HTTP POST using HttpURLConnection */
    private static int postJson(String endpoint,
                                JSONObject body,
                                String source,
                                String queuedAtHeaderOrNull) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("X-Source", source);
            if (queuedAtHeaderOrNull != null) {
                conn.setRequestProperty("X-Queued-At", queuedAtHeaderOrNull);
            }

            byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = new BufferedOutputStream(conn.getOutputStream());
            os.write(out);
            os.flush();
            os.close();

            int code = conn.getResponseCode();

            // Optional: read response for debugging
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8))) {
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = br.readLine()) != null) sb.append(line);
                Log.d(TAG, "HTTP resp (" + code + "): " + sb);
            } catch (Exception ignore) {}

            return code;
        } finally {
            conn.disconnect();
        }
    }
}
