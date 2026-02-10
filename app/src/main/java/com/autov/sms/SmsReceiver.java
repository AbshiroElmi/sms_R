package com.autov.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import org.json.JSONObject;
import java.util.List;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !SMS_RECEIVED.equals(intent.getAction())) return;

        final PendingResult result = goAsync();
        try {
            QueueUploader.flushQueueIfAnyAsync(context);

            JSONObject payloadBase = buildBasePayload(context, intent);
            if (payloadBase != null) {
                processIncomingSms(context, payloadBase);
                QueueUploader.flushQueueIfAnyAsync(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "onReceive error", e);
        } finally {
            result.finish();
        }
    }

    private void processIncomingSms(Context context, JSONObject payloadBase) {
        try {
            long ts = System.currentTimeMillis();
            String isoDate = payloadBase.optString("date", Iso.now());
            String fromNumber = payloadBase.optString("from", "");
            String body = payloadBase.optString("body", "");
            int subId = payloadBase.optInt("sim_id", -1);

            int simIndex = payloadBase.optInt("sim_index", -1);

            // Save to History Database once
            long dbId = SmsDatabaseHelper.getInstance(context).insertSms(
                    fromNumber, body, ts, SmsDatabaseHelper.STATUS_PENDING, subId, simIndex, isoDate
            );
            payloadBase.put("_db_id", dbId);

            // Get all configs and send if matched
            List<SmsDatabaseHelper.Config> configs = SmsDatabaseHelper.getInstance(context).getAllConfigs();
            int matchCount = 0;
            for (SmsDatabaseHelper.Config config : configs) {
                if (!config.isActive) continue;

                // SIM Match
                // simIndex: 0=Both, 1=Sim1, 2=Sim2
                // detected simIndex (1-based slot index)
                int detectedSimIndex = payloadBase.optInt("_detected_sim_index", -1);
                if (config.simIndex != 0 && detectedSimIndex != -1) {
                    if (config.simIndex != detectedSimIndex) continue;
                }

                // If matched SIM, trigger async send
                // QueueUploader will do the whitelist check inside
                matchCount++;
                QueueUploader.sendToConfigAsync(context, new JSONObject(payloadBase.toString()), config, "new-sms");
            }

            if (matchCount == 0) {
                 Log.d(TAG, "No active config matched for SMS from " + fromNumber);
            }

        } catch (Exception e) {
            Log.e(TAG, "processIncomingSms error", e);
        }
    }

    private JSONObject buildBasePayload(Context context, Intent intent) {
        try {
            QueueUploader.ensureBaselineNow(context);
            long baseline = QueueUploader.getBaseline(context);

            Bundle bundle = intent.getExtras();
            if (bundle == null) return null;

            Object[] pdus = (Object[]) bundle.get("pdus");
            String format = bundle.getString("format");
            int subId = bundle.getInt("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID);

            if (pdus == null || pdus.length == 0) return null;

            String fromNumber = "";
            StringBuilder body = new StringBuilder();
            long ts = System.currentTimeMillis();

            for (Object pdu : pdus) {
                SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu, format);
                if (msg == null) continue;
                if (fromNumber.isEmpty() && msg.getOriginatingAddress() != null) fromNumber = msg.getOriginatingAddress();
                if (msg.getTimestampMillis() > 0) ts = msg.getTimestampMillis();
                if (msg.getMessageBody() != null) body.append(msg.getMessageBody());
            }

            if (ts < baseline) return null;

            // SIM info (best-effort)
            SimInfoUtil.SimInfo si = SimInfoUtil.read(context, subId);
            int simSlot = -1;
            int simIndexDetected = -1;
            try {
                SubscriptionManager sm = SubscriptionManager.from(context);
                SubscriptionInfo info = (sm != null) ? sm.getActiveSubscriptionInfo(subId) : null;
                if (info != null) {
                    simSlot = info.getSimSlotIndex();
                    simIndexDetected = (simSlot >= 0) ? simSlot + 1 : -1;
                }
            } catch (SecurityException ignore) {}

            String maskedNumber = SimInfoUtil.maskNumber(si.phoneNumber);
            String deviceId = DeviceIdUtil.get(context);
            
            JSONObject payload = new JSONObject()
                    .put("type", "incoming_new")
                    .put("device_unique_id", deviceId)
                    .put("from", fromNumber)
                    .put("body", sanitizeBody(body.toString()))
                    .put("sim_id", subId)
                    .put("sim_slot", simSlot)
                    .put("sim_index", simIndexDetected) // pass detected one for internal matching
                    .put("_detected_sim_index", simIndexDetected)
                    .put("sim_name", si.label)
                    .put("carrier_name_raw", si.operatorName)
                    .put("operator_numeric", si.operatorNumeric)
                    .put("mcc", si.mcc)
                    .put("mnc", si.mnc)
                    .put("line_number", maskedNumber)
                    .put("iccid_available", si.iccid != null)
                    .put("date", Iso.fromMillis(ts));

            QueueUploader.maybeAdvanceBaseline(context, ts);
            return payload;
        } catch (Exception e) {
            Log.e(TAG, "buildBasePayload error", e);
            return null;
        }
    }

    private static String sanitizeBody(String s) {
        if (s == null) return "";
        s = s.replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "");
        s = s.trim();
        final int MAX = 4000;
        return s.length() > MAX ? s.substring(0, MAX) : s;
    }
}
