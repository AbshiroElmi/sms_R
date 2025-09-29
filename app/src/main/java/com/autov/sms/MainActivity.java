package com.autov.sms;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

// ZXing (QR)
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class MainActivity extends AppCompatActivity {

    private RadioGroup rgServers;
    private RadioButton rbAutov, rbSadar, rbOther;
    private LinearLayout formAutov, formOther;
    private EditText etAutovCode, etOtherUrl;
    private Button btnConnect;
    private ImageButton btnScanQr; // ← NEW
    private View progress;

    private final OkHttpClient http = new OkHttpClient.Builder().build();

    private ActivityResultLauncher<String[]> permsLauncher;

    // QR launcher (lifecycle-safe)
    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null && etAutovCode != null) {
                    etAutovCode.setText(result.getContents().trim());
                    Toast.makeText(this, "QR scanned", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        boolean enabled = getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE)
                .getBoolean(Const.PREF_ENABLED, false);
        if (enabled) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        QueueUploader.ensureBaselineNow(this);

        bindViews();
        initPerms();
        restoreUiFromPrefs();
        maybeAskIgnoreBatteryOptimizations();

        // Initial opportunistic flush
        QueueUploader.flushQueueIfAny(this);
    }

    private void bindViews() {
        rgServers   = findViewById(R.id.rgServers);
        rbAutov     = findViewById(R.id.rbAutov);
        rbSadar     = findViewById(R.id.rbSadar);
        rbOther     = findViewById(R.id.rbOther);
        formAutov   = findViewById(R.id.formAutov);
        formOther   = findViewById(R.id.formOther);
        etAutovCode = findViewById(R.id.etAutovCode);
        etOtherUrl  = findViewById(R.id.etOtherUrl);
        btnConnect  = findViewById(R.id.btnConnect);
        progress    = findViewById(R.id.progress);
        btnScanQr   = findViewById(R.id.btnScanQr); // ← NEW (must exist in XML)

        rgServers.setOnCheckedChangeListener((g, id) -> {
            formAutov.setVisibility(id == R.id.rbAutov ? View.VISIBLE : View.GONE);
            formOther.setVisibility(id == R.id.rbOther ? View.VISIBLE : View.GONE);
        });

        btnConnect.setOnClickListener(v -> onConnectClicked());

        if (btnScanQr != null) {
            btnScanQr.setOnClickListener(v -> startQrScan());
        }
    }

    private void initPerms() {
        permsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> { /* optional: inspect grants */ });

        List<String> missing = new ArrayList<>();
        addIfMissing(missing, Manifest.permission.RECEIVE_SMS);
        addIfMissing(missing, Manifest.permission.READ_SMS);
        addIfMissing(missing, Manifest.permission.READ_PHONE_STATE);
        addIfMissing(missing, Manifest.permission.READ_PHONE_NUMBERS);
        addIfMissing(missing, Manifest.permission.CAMERA); // ← NEW for QR
        if (!missing.isEmpty()) {
            permsLauncher.launch(missing.toArray(new String[0]));
        }
    }

    private void addIfMissing(List<String> out, String perm) {
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            out.add(perm);
        }
    }

    private void restoreUiFromPrefs() {
        SharedPreferences sp = getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE);
        int server = sp.getInt(Const.PREF_SERVER, 0);
        if (server == 1) {
            rbAutov.setChecked(true);
            etAutovCode.setText(sp.getString(Const.PREF_AUTOV_TOKEN, ""));
        } else if (server == 3) {
            rbOther.setChecked(true);
            etOtherUrl.setText(sp.getString(Const.PREF_OTHER_URL, ""));
        } else if (server == 2) {
            rbSadar.setChecked(true);
        }
    }

    private void onConnectClicked() {
        int checkedId = rgServers.getCheckedRadioButtonId();

        if (checkedId == R.id.rbAutov) {
            // --- Autov flow (verify token then enable) ---
            String token = etAutovCode.getText().toString().trim();
            if (token.isEmpty()) {
                toast("Enter access code");
                return;
            }
            verifyAutovToken(token); // will call saveServerChoice(1, token, null, true) on success
            return;
        }

        if (checkedId == R.id.rbOther) {
            String url = etOtherUrl.getText().toString().trim();
            if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
                toast("Enter a valid URL (http/https)");
                return;
            }
            url = url.replace(" ", "");
            saveServerChoice(3, null, url, true);
            toast("Connected to Other server");
            QueueUploader.flushQueueIfAnyAsync(this);

            // → Go to dashboard
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }


        if (checkedId == R.id.rbSadar) {
            // Not implemented; keep disabled
            toast("Sadar server currently under maintenance");
            return;
        }

        toast("Select a server first");
    }


    private void verifyAutovToken(String token) {
        showProgress(true);
        Executors.newSingleThreadExecutor().execute(() -> {
            boolean ok = false;
            try {
                String url = Const.verifyUrl(token);
                Request req = new Request.Builder().url(url).get().build();
                try (Response res = http.newCall(req).execute()) {
                    ok = res.isSuccessful(); // 2xx is success; refine if API returns JSON flags
                }
            } catch (IOException ignored) {}

            boolean finalOk = ok;
            runOnUiThread(() -> {
                showProgress(false);
                if (finalOk) {
                    saveServerChoice(1, token, null, true);
                    toast("Verified! Connected to Autov");
                } else {
                    toast("Verification failed");
                }
            });
        });
    }

    private void saveServerChoice(int server, @Nullable String token, @Nullable String otherUrl, boolean enabled) {
        getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE).edit()
                .putInt(Const.PREF_SERVER, server)
                .putBoolean(Const.PREF_ENABLED, enabled)
                .apply();
        if (token != null) {
            getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE).edit()
                    .putString(Const.PREF_AUTOV_TOKEN, token).apply();
        }
        if (otherUrl != null) {
            getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE).edit()
                    .putString(Const.PREF_OTHER_URL, otherUrl).apply();
        }

        // Try to flush queue now that we're configured
        QueueUploader.flushQueueIfAnyAsync(this);
    }

    private void showProgress(boolean show) {
        progress.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
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

    private void startQrScan() {
        ScanOptions opts = new ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan access code QR")
                .setBeepEnabled(true)
                .setOrientationLocked(true);
        qrLauncher.launch(opts);
    }
}
