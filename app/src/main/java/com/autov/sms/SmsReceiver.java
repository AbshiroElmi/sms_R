package com.autov.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver.PendingResult;
import android.os.Bundle;
import android.telephony.SmsMessage;
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
        QueueUploader.flushQueueIfAnyAsync(context); // opportunistic

        QueueUploader.sendToServerAsync(context, buildPayload(context, intent), "background-new");
        // We also advance baseline and flush in background:
        QueueUploader.flushQueueIfAnyAsync(context);

        // Finish quickly—heavy work is in the executor
        result.finish();
    }

    private JSONObject buildPayload(Context context, Intent intent) {
        try {
            QueueUploader.ensureBaselineNow(context);
            long baseline = QueueUploader.getBaseline(context);

            Bundle bundle = intent.getExtras();
            if (bundle == null) return new JSONObject();

            Object[] pdus = (Object[]) bundle.get("pdus");
            String format = bundle.getString("format");
            int subId = bundle.getInt("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID);

            if (pdus == null || pdus.length == 0) return new JSONObject();

            String from = "";
            StringBuilder body = new StringBuilder();
            long ts = System.currentTimeMillis();

            for (Object pdu : pdus) {
                SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu, format);
                if (msg == null) continue;
                if (from.isEmpty() && msg.getOriginatingAddress() != null) {
                    from = msg.getOriginatingAddress();
                }
                if (msg.getTimestampMillis() > 0) ts = msg.getTimestampMillis();
                if (msg.getMessageBody() != null) body.append(msg.getMessageBody());
            }

            if (ts < baseline) return new JSONObject(); // old SMS; ignore

//            String simName = (subId == 1) ? "SIM 1" : (subId == 2) ? "SIM 2" : "Unknown SIM";

            JSONObject payload = new JSONObject();
            payload.put("type", "incoming_new");
            payload.put("from", from);
            payload.put("body", body.toString());
            payload.put("sim_id",subId );
            payload.put("sim_name", subId);
            payload.put("date", Iso.fromMillis(ts));

//            SimInfoUtil.SimInfo si = SimInfoUtil.read(context, subId);
//
//// If you want to mask phone number before sending:
//            String maskedNumber = SimInfoUtil.maskNumber(si.phoneNumber);
//
//            JSONObject payload = new JSONObject()
//                    .put("type", "incoming_new")
//                    .put("from", from == null ? "" : from)
//                    .put("body", sanitizeBody(body == null ? "" : body.toString()))
//                    .put("sim_id", subId)
//                    .put("sim_name", si.label)                 // "Hormuud", "Somtel", etc.
//                    .put("carrier_name_raw", si.operatorName)  // e.g. "Hormuud Telecom"
//                    .put("operator_numeric", si.operatorNumeric) // e.g. "637xx" when available
//                    .put("mcc", si.mcc)
//                    .put("mnc", si.mnc)
//                    .put("line_number", maskedNumber)          // or si.phoneNumber if you insist
//                    .put("iccid_available", si.iccid != null)  // ICCID likely null on Android 10+
//                    .put("date", Iso.fromMillis(ts));




            // bump baseline
            QueueUploader.maybeAdvanceBaseline(context, ts);

            return payload;
        } catch (Exception e) {
            Log.e(TAG, "buildPayload error", e);
            return new JSONObject();
        }
    }

    // SmsReceiver.java (top-level, inside the class but outside methods)
    private static String sanitizeBody(String s) {
        if (s == null) return "";
        // remove control characters except newline, tab, carriage return
        s = s.replaceAll("[\\p{Cntrl}&&[^\n\r\t]]", "");
        // trim and cap length so queue/webhook isn't flooded
        s = s.trim();
        final int MAX = 4000; // adjust if you want
        return s.length() > MAX ? s.substring(0, MAX) : s;
    }

}
