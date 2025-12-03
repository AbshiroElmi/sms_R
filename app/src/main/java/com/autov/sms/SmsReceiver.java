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

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !SMS_RECEIVED.equals(intent.getAction())) return;

        final PendingResult result = goAsync();
        try {
            QueueUploader.flushQueueIfAnyAsync(context);

            JSONObject payload = buildPayload(context, intent);

            if (payload != null && payload.has("type")) {
                QueueUploader.sendToServerAsync(context, payload, "background-new");
                QueueUploader.flushQueueIfAnyAsync(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "onReceive error", e);
        } finally {
            result.finish();
        }
    }

    private JSONObject buildPayload(Context context, Intent intent) {
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

                if (fromNumber.isEmpty() && msg.getOriginatingAddress() != null) {
                    fromNumber = msg.getOriginatingAddress();
                }
                if (msg.getTimestampMillis() > 0) ts = msg.getTimestampMillis();
                if (msg.getMessageBody() != null) body.append(msg.getMessageBody());
            }

            if (ts < baseline) return null;

            // ===== Global ON/OFF from SendAllActivity =====
            boolean sendAll = context.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE)
                    .getBoolean(Const.PREF_SEND_ALL, false);
            if (!sendAll) {
                // Switch is OFF → do nothing
                return null;
            }

            // ===== Whitelist behavior =====
            if (!WhitelistUtil.hasAny(context)) {
                // whitelist empty → allow all senders
            } else if (!WhitelistUtil.isAllowed(context, fromNumber)) {
                // whitelist has entries and this sender is not in it → skip
                Log.d(TAG, "Sender '" + fromNumber + "' not in whitelist → skip NEW sms.");
                return null;
            }

            // SIM info (best-effort)
            SimInfoUtil.SimInfo si = SimInfoUtil.read(context, subId);
            int simSlot = -1;
            int simIndexHuman = -1;
            try {
                SubscriptionManager sm = SubscriptionManager.from(context);
                SubscriptionInfo info = (sm != null) ? sm.getActiveSubscriptionInfo(subId) : null;
                if (info != null) {
                    simSlot = info.getSimSlotIndex();
                    simIndexHuman = (simSlot >= 0) ? simSlot + 1 : -1;
                }
            } catch (SecurityException ignore) {}

            String maskedNumber = SimInfoUtil.maskNumber(si.phoneNumber);
            String deviceId = DeviceIdUtil.get(context);
            JSONObject payload = new JSONObject()
                    .put("type", "incoming_new")
                    .put("device_unique_id", deviceId)
                    .put("from", fromNumber == null ? "" : fromNumber)
                    .put("body", sanitizeBody(body == null ? "" : body.toString()))
                    .put("sim_id", subId)
                    .put("sim_slot", simSlot)
                    .put("sim_index", simIndexHuman)
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
            Log.e(TAG, "buildPayload error", e);
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
