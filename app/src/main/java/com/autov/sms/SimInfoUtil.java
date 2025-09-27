package com.autov.sms;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Locale;

public final class SimInfoUtil {
    private SimInfoUtil() {}

    public static class SimInfo {
        public final String label;            // "Hormuud", "Somtel", etc.
        public final String operatorName;     // raw carrier name
        public final String operatorNumeric;  // MCC+MNC (e.g., "637xx")
        public final String mcc;
        public final String mnc;
        public final String phoneNumber;      // often empty
        public final String iccid;            // likely null on Android 10+

        SimInfo(String label, String operatorName, String operatorNumeric,
                String mcc, String mnc, String phoneNumber, String iccid) {
            this.label = label;
            this.operatorName = operatorName;
            this.operatorNumeric = operatorNumeric;
            this.mcc = mcc;
            this.mnc = mnc;
            this.phoneNumber = phoneNumber;
            this.iccid = iccid;
        }
    }

    public static SimInfo read(Context ctx, int subId) {
        String operatorName = "";
        String operatorNumeric = "";
        String mcc = "";
        String mnc = "";
        String phoneNumber = "";
        String iccid = null;

        boolean hasReadPhoneState   = hasPerm(ctx, Manifest.permission.READ_PHONE_STATE);
        boolean hasReadPhoneNumbers = hasPerm(ctx, Manifest.permission.READ_PHONE_NUMBERS);

        try {
            // --- Subscription-based info (multi-SIM aware) ---
            SubscriptionInfo si = null;
            try {
                SubscriptionManager sm = SubscriptionManager.from(ctx);
                if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    // On newer Android, accessing SubscriptionInfo fields may require READ_PHONE_STATE
                    if (hasReadPhoneState) {
                        si = sm.getActiveSubscriptionInfo(subId);
                    }
                }
            } catch (SecurityException ignored) {}

            if (si != null) {
                CharSequence cn = si.getCarrierName();
                if (cn != null) operatorName = cn.toString();

                // Number from SubscriptionInfo (often empty). May require READ_PHONE_NUMBERS/STATE.
                try {
                    String n = si.getNumber(); // deprecated on 33+, may still return
                    if (n != null && !n.isEmpty()) phoneNumber = n;
                } catch (SecurityException ignored) {}
            }

            // --- TelephonyManager (per-sub) ---
            TelephonyManager tm  = (TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE);
            TelephonyManager tms = (tm != null) ? tm.createForSubscriptionId(subId) : null;

            if (tms != null) {
                // Operator name (raw)
                try {
                    if (operatorName.isEmpty()) {
                        String opName = tms.getSimOperatorName();
                        if (opName != null) operatorName = opName;
                    }
                } catch (SecurityException ignored) {}

                // MCC+MNC (e.g., "637xx")
                try {
                    String opNum = tms.getSimOperator();
                    if (opNum != null) operatorNumeric = opNum;
                } catch (SecurityException ignored) {}

                // Line1 number (very often empty by design)
                if ((phoneNumber == null || phoneNumber.isEmpty()) && (hasReadPhoneNumbers || hasReadPhoneState)) {
                    try {
                        String line = tms.getLine1Number();
                        if (line != null && !line.isEmpty()) phoneNumber = line;
                    } catch (SecurityException ignored) {}
                }

                // ICCID (SIM serial) — typically unavailable on Android 10+ for normal apps
                if (hasIccidAccess() && hasReadPhoneState) {
                    try {
                        iccid = tms.getLine1Number(); // likely null
                    } catch (SecurityException ignored) {}
                }
            }

            // Parse MCC/MNC if we got operatorNumeric (first 3 = MCC, rest = MNC)
            if (operatorNumeric != null && operatorNumeric.length() >= 5) {
                mcc = operatorNumeric.substring(0, 3);
                mnc = operatorNumeric.substring(3);
            }
        } catch (Exception ignored) {}

        String label = mapSomaliOperator(operatorName, operatorNumeric);

        return new SimInfo(label,
                operatorName,
                operatorNumeric,
                mcc,
                mnc,
                nullToEmpty(phoneNumber),
                iccid);
    }

    /** Check a dangerous permission at runtime */
    private static boolean hasPerm(Context ctx, String perm) {
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED;
    }

    /** Realistically false for normal apps on Android 10+. */
    private static boolean hasIccidAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }

    /** Map raw operator to nice Somali labels */
    private static String mapSomaliOperator(String operatorName, String operatorNumeric) {
        String n = (operatorName == null ? "" : operatorName).toLowerCase(Locale.US);

        if (n.contains("hormuud"))  return "Hormuud";
        if (n.contains("somtel"))   return "Somtel";
        if (n.contains("telesom"))  return "Telesom";
        if (n.contains("golis"))    return "Golis";
        if (n.contains("nationlink")) return "NationLink";

        if (operatorNumeric != null && operatorNumeric.startsWith("637")) {
            return capitalizeSafe(operatorName.isEmpty() ? "Somalia Operator" : operatorName);
        }

        return (operatorName == null || operatorName.isEmpty())
                ? "Unknown SIM"
                : capitalizeSafe(operatorName);
    }

    private static String capitalizeSafe(@Nullable String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase(Locale.US) + s.substring(1);
    }

    private static String nullToEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }

    /** Optional: mask a phone number before sending to server */
    public static String maskNumber(@Nullable String number) {
        if (number == null) return "";
        String n = number.replaceAll("\\s+", "");
        if (n.length() <= 4) return n;
        return "****" + n.substring(n.length() - 4);
    }
}
