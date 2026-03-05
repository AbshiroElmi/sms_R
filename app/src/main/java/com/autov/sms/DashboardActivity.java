package com.autov.sms;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends AppCompatActivity implements ConfigAdapter.OnConfigChangeListener {

    private RecyclerView rvConfigs;
    private ConfigAdapter adapter;
    private View emptyState;
    private FloatingActionButton fabAdd;
    private SmsDatabaseHelper dbHelper;
    private ActivityResultLauncher<String[]> permsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {});

    private EditText activeEditText; // For QR scan result
    private SmsDatabaseHelper.Config currentEditing = null;

    private final ActivityResultLauncher<ScanOptions> qrLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null && activeEditText != null) {
                    activeEditText.setText(result.getContents().trim());
                    Toast.makeText(this, "QR scanned", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#00B09B"));
        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        dbHelper = SmsDatabaseHelper.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        rvConfigs = findViewById(R.id.rvConfigs);
        emptyState = findViewById(R.id.emptyState);
        fabAdd = findViewById(R.id.fabAddConfig);
        
        rvConfigs.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ConfigAdapter(this);
        rvConfigs.setAdapter(adapter);

        findViewById(R.id.btnCreateConfig).setOnClickListener(v -> {
            currentEditing = null;
            showStep0Type();
        });
        fabAdd.setOnClickListener(v -> {
            currentEditing = null;
            showStep0Type();
        });

        QueueUploader.ensureBaselineNow(this);
        initPerms();
        maybeAskIgnoreBatteryOptimizations();

        refreshList();
        QueueUploader.flushQueueIfAnyAsync(this);
    }

    private void initPerms() {
        List<String> missing = new ArrayList<>();
        addIfMissing(missing, Manifest.permission.RECEIVE_SMS);
        addIfMissing(missing, Manifest.permission.READ_SMS);
        addIfMissing(missing, Manifest.permission.SEND_SMS);
        addIfMissing(missing, Manifest.permission.READ_PHONE_STATE);
        addIfMissing(missing, Manifest.permission.READ_PHONE_NUMBERS);
        addIfMissing(missing, Manifest.permission.CAMERA);
        if (!missing.isEmpty()) {
            permsLauncher.launch(missing.toArray(new String[0]));
        }
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

    private void refreshList() {
        List<SmsDatabaseHelper.Config> configs = dbHelper.getAllConfigs();
        if (configs.isEmpty()) {
            rvConfigs.setVisibility(View.GONE);
            fabAdd.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            rvConfigs.setVisibility(View.VISIBLE);
            fabAdd.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            adapter.setItems(configs);
        }
    }

    // --- Dialog Flow ---

    private void showStep0Type() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_step0, null);
        RadioGroup rg = view.findViewById(R.id.rgConfigType);
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Configuration Type")
                .setView(view)
                .setPositiveButton("Next", (dialog, which) -> {
                    int configType = rg.getCheckedRadioButtonId() == R.id.rbOutgoing ? 1 : 0;
                    showStep1Title(configType);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showStep1Title(int configType) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_step1, null);
        EditText et = view.findViewById(R.id.etConfigTitle);
        if (currentEditing != null) et.setText(currentEditing.title);
        
        new MaterialAlertDialogBuilder(this)
                .setTitle(currentEditing == null ? "Configuration Name" : "Edit Name")
                .setView(view)
                .setPositiveButton("Next", (dialog, which) -> {
                    String title = et.getText().toString().trim();
                    if (title.isEmpty()) title = "Filter1";
                    showStep2Sim(title, configType);
                })
                .setNegativeButton("Back", (dialog, which) -> {
                    if (currentEditing == null) showStep0Type();
                })
                .show();
    }

    private void showStep2Sim(String title, int configType) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_step2, null);
        RadioGroup rg = view.findViewById(R.id.rgSim);
        if (currentEditing != null) {
            if (currentEditing.simIndex == 1) rg.check(R.id.rbSim1);
            else if (currentEditing.simIndex == 2) rg.check(R.id.rbSim2);
            else rg.check(R.id.rbSimBoth);
        }
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Select SIM Card")
                .setView(view)
                .setPositiveButton("Next", (dialog, which) -> {
                    int id = rg.getCheckedRadioButtonId();
                    int sim = 1;
                    if (id == R.id.rbSim2) sim = 2;
                    else if (id == R.id.rbSimBoth) sim = 0;
                    
                    showStep3Server(title, sim, configType);
                })
                .setNegativeButton("Back", (dialog, which) -> showStep1Title(configType))
                .show();
    }

    private void showStep3Server(String title, int sim, int configType) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_step3, null);
        RadioGroup rg = view.findViewById(R.id.rgServers);
        View formAutov = view.findViewById(R.id.formAutov);
        View formOther = view.findViewById(R.id.formOther);
        EditText etToken = view.findViewById(R.id.etAutovCode);
        EditText etOtherUrl = view.findViewById(R.id.etOtherUrl);

        activeEditText = etToken;
        view.findViewById(R.id.btnScanQr).setOnClickListener(v -> startQrScan());

        rg.setOnCheckedChangeListener((g, id) -> {
            formAutov.setVisibility(id == R.id.rbAutov ? View.VISIBLE : View.GONE);
            formOther.setVisibility(id == R.id.rbOther ? View.VISIBLE : View.GONE);
            if (id == R.id.rbAutov) activeEditText = etToken;
            else if (id == R.id.rbOther) activeEditText = etOtherUrl;
        });

        if (currentEditing != null) {
            if (currentEditing.serverType == 1) {
                rg.check(R.id.rbAutov);
                etToken.setText(currentEditing.token);
            } else if (currentEditing.serverType == 3) {
                rg.check(R.id.rbOther);
                etOtherUrl.setText(currentEditing.url);
            }
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Server Configuration")
                .setView(view)
                .setPositiveButton(configType == 1 ? "Finish" : "Next", (dialog, which) -> {
                    int id = rg.getCheckedRadioButtonId();
                    int type = 1; 
                    String url = "";
                    String token = "";
                    
                    if (id == R.id.rbOther) {
                        type = 3;
                        url = etOtherUrl.getText().toString().trim();
                        if (url.isEmpty()) { Toast.makeText(this, "URL required", Toast.LENGTH_SHORT).show(); return; }
                    } else if (id == R.id.rbAutov) {
                        type = 1;
                        token = etToken.getText().toString().trim();
                        if (token.isEmpty()) { Toast.makeText(this, "Code required", Toast.LENGTH_SHORT).show(); return; }
                        url = Const.AUTOV_SMS_UPLOAD; // Store the actual server URL
                    }
                    
                    if (configType == 1) { // Outgoing flow ends here (skips whitelist)
                        if (currentEditing == null) {
                            dbHelper.addConfig(configType, title, sim, type, url, token, "", true);
                            Toast.makeText(this, "Configuration created", Toast.LENGTH_SHORT).show();
                        } else {
                            dbHelper.updateConfig(currentEditing.id, configType, title, sim, type, url, token, "");
                            Toast.makeText(this, "Configuration updated", Toast.LENGTH_SHORT).show();
                        }
                        refreshList();
                    } else {
                        showStep4Whitelist(title, sim, configType, type, url, token);
                    }
                })
                .setNegativeButton("Back", (dialog, which) -> showStep2Sim(title, configType))
                .show();
    }

    private void showStep4Whitelist(String title, int sim, int configType, int type, String url, String token) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_step4, null);
        EditText et = view.findViewById(R.id.etWhitelist);
        if (currentEditing != null) et.setText(currentEditing.whitelist);
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Allow Senders")
                .setView(view)
                .setPositiveButton("Finish", (dialog, which) -> {
                    String whitelist = et.getText().toString().trim();
                    if (currentEditing == null) {
                        dbHelper.addConfig(configType, title, sim, type, url, token, whitelist, true);
                        Toast.makeText(this, "Configuration created", Toast.LENGTH_SHORT).show();
                    } else {
                        dbHelper.updateConfig(currentEditing.id, configType, title, sim, type, url, token, whitelist);
                        Toast.makeText(this, "Configuration updated", Toast.LENGTH_SHORT).show();
                    }
                    refreshList();
                })
                .setNegativeButton("Back", (dialog, which) -> showStep3Server(title, sim, configType))
                .show();
    }

    // --- Adapter Callbacks ---

    @Override
    public void onToggle(SmsDatabaseHelper.Config config, boolean isActive) {
        dbHelper.updateConfigActive(config.id, isActive);
        Toast.makeText(this, (isActive ? "Enabled " : "Disabled ") + config.title, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEdit(SmsDatabaseHelper.Config config) {
        currentEditing = config;
        showStep1Title(config.configType);
    }

    @Override
    public void onDelete(SmsDatabaseHelper.Config config) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Configuration")
                .setMessage("Are you sure you want to delete '" + config.title + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHelper.deleteConfig(config.id);
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- Menu ---

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
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
