package com.autov.sms;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OutgoingSmsPoller {
    private static final String TAG = "OutgoingSmsPoller";
    private static ScheduledExecutorService scheduler;
    private static final String PREFS_PROCESSED_MSGS = "processed_msgs_prefs";
    private static final String KEY_PROCESSED_SET = "processed_set";

    public static synchronized void start(Context context) {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> poll(context), 5, 10, TimeUnit.SECONDS);
    }

    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private static void poll(Context context) {
        SmsDatabaseHelper db = SmsDatabaseHelper.getInstance(context);
        List<SmsDatabaseHelper.Config> configs = db.getAllConfigs();
        boolean hasOutgoing = false;
        for (SmsDatabaseHelper.Config c : configs) {
            if (c.isActive && c.configType == 1) {
                hasOutgoing = true;
                break;
            }
        }
        if (!hasOutgoing) return;

        Set<String> processed = new HashSet<>(context.getSharedPreferences(PREFS_PROCESSED_MSGS, Context.MODE_PRIVATE).getStringSet(KEY_PROCESSED_SET, new HashSet<>()));
        boolean globalUpdatedSet = false;

        for (SmsDatabaseHelper.Config c : configs) {
            if (!c.isActive || c.configType != 1) continue;
            
            String endpoint = c.serverType == 1 ? Const.AUTOV_SMS_UPLOAD : c.url;
            if (endpoint == null || endpoint.isEmpty()) continue;

            try {
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                if (c.serverType == 1 && c.token != null) {
                    conn.setRequestProperty("X-Autov-Token", c.token);
                }

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray messages = root.optJSONArray("messages");
                    if (messages == null) continue;

                    for (int i = 0; i < messages.length(); i++) {
                        JSONObject msg = messages.optJSONObject(i);
                        if (msg == null) continue;

                        String msgId = msg.optString("id");
                        if (processed.contains(msgId)) continue;
                        
                        String targetSim = msg.optString("SIM"); // "1" or "2"
                        String to = msg.optString("To");
                        String body = msg.optString("Body");

                        int simReq = "2".equals(targetSim) ? 2 : 1;
                        if (c.simIndex != 0 && c.simIndex != simReq) {
                            continue; // This configuration does not handle this SIM
                        }

                        sendSms(context, to, body, simReq);
                        processed.add(msgId);
                        globalUpdatedSet = true;
                        
                        db.insertSms("To: " + to, body, System.currentTimeMillis(), SmsDatabaseHelper.STATUS_SENT, -1, simReq, Iso.now());
                        
                        // Notify UI
                        android.content.Intent intent = new android.content.Intent(QueueUploader.ACTION_SMS_STATUS_UPDATED);
                        intent.setPackage(context.getPackageName());
                        context.sendBroadcast(intent);
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Poller error for URL " + endpoint, e);
            }
        }

        if (globalUpdatedSet) {
            context.getSharedPreferences(PREFS_PROCESSED_MSGS, Context.MODE_PRIVATE).edit().putStringSet(KEY_PROCESSED_SET, processed).apply();
        }
    }

    private static void sendSms(Context context, String to, String body, int simIndex) {
        try {
            SubscriptionManager localSubscriptionManager = SubscriptionManager.from(context);
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                List<SubscriptionInfo> localList = localSubscriptionManager.getActiveSubscriptionInfoList();
                if (localList != null && localList.size() > 0) {
                    // Try to find matching SIM slot (0-indexed)
                    int subId = -1;
                    for (SubscriptionInfo info : localList) {
                        if (simIndex == 1 && info.getSimSlotIndex() == 0) subId = info.getSubscriptionId();
                        if (simIndex == 2 && info.getSimSlotIndex() == 1) subId = info.getSubscriptionId();
                    }
                    if (subId != -1) {
                        SmsManager.getSmsManagerForSubscriptionId(subId).sendTextMessage(to, null, body, null, null);
                        return;
                    }
                }
            }
            SmsManager.getDefault().sendTextMessage(to, null, body, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS", e);
        }
    }
}
