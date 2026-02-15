package com.autov.sms;

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Context; // Added
import android.os.Build;       // Added

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;

import org.json.JSONObject;

import android.view.Menu;
import android.view.MenuItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SmsHistoryActivity extends AppCompatActivity implements SmsAdapter.OnResendClickListener {

    private RecyclerView recyclerView;
    private SmsAdapter adapter;
    private TextView tvDateFilter;
    private android.widget.EditText etSearch;
    private View searchContainer, emptyState;
    private MaterialButton btnClearFilter, btnSearch;
    private View btnCloseSearch;
    private String currentDateFilter = null;
    private boolean hasHistory = false;
    
    // BroadcastReceiver for SMS status updates
    private android.content.BroadcastReceiver smsStatusReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            // Refresh the list when SMS status is updated
            loadData();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#00B09B"));
        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);

        super.onCreate(savedInstanceState);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_sms_history);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (Build.VERSION.SDK_INT >= 33) {
                 registerReceiver(downloadReceiver, new android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
            } else {
                 registerReceiver(downloadReceiver, new android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        } else {
             registerReceiver(downloadReceiver, new android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
        
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SmsAdapter(this);
        recyclerView.setAdapter(adapter);

        tvDateFilter = findViewById(R.id.tvDateFilter);
        btnSearch = findViewById(R.id.btnSearch);
        searchContainer = findViewById(R.id.searchContainer);
        etSearch = findViewById(R.id.etSearch);
        btnCloseSearch = findViewById(R.id.btnCloseSearch);
        btnClearFilter = findViewById(R.id.btnClearFilter);
        emptyState = findViewById(R.id.emptyState);

        btnSearch.setOnClickListener(v -> {
            if (searchContainer.getVisibility() == View.VISIBLE) {
                // If already visible, hide it
                searchContainer.setVisibility(View.GONE);
                etSearch.setText(""); // Clear search when hiding? Optional.
                loadData();
            } else {
                searchContainer.setVisibility(View.VISIBLE);
                etSearch.requestFocus();
                // Show keyboard logic could be added here
            }
        });

        btnCloseSearch.setOnClickListener(v -> {
            etSearch.setText("");
            searchContainer.setVisibility(View.GONE);
            loadData();
        });

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadData();
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        findViewById(R.id.btnPickDate).setOnClickListener(v -> showDatePicker());
        btnClearFilter.setOnClickListener(v -> {
            currentDateFilter = null;
            tvDateFilter.setText("All Dates");
            btnClearFilter.setVisibility(View.GONE);
            loadData();
        });

        // Register broadcast receiver for SMS status updates
        android.content.IntentFilter filter = new android.content.IntentFilter(QueueUploader.ACTION_SMS_STATUS_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(smsStatusReceiver, filter);
        }

        loadData();
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            currentDateFilter = sdf.format(new Date(selection));
            tvDateFilter.setText("Date: " + currentDateFilter);
            btnClearFilter.setVisibility(View.VISIBLE);
            loadData();
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }
    
    // Existing loadData
    private void loadData() {
        List<SmsAdapter.SmsRecord> records = new ArrayList<>();
        String searchText = etSearch != null ? etSearch.getText().toString() : null;
        Cursor cursor = SmsDatabaseHelper.getInstance(this).getAllSmsCursor(currentDateFilter, searchText);
        
        if (cursor != null) {
            while (cursor.moveToNext()) {
                records.add(new SmsAdapter.SmsRecord(
                        cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabaseHelper.COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabaseHelper.COLUMN_FROM)),
                        cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabaseHelper.COLUMN_BODY)),
                        cursor.getLong(cursor.getColumnIndexOrThrow(SmsDatabaseHelper.COLUMN_TIMESTAMP)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabaseHelper.COLUMN_STATUS)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabaseHelper.COLUMN_SIM_ID)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(SmsDatabaseHelper.COLUMN_SIM_INDEX)),
                        cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabaseHelper.COLUMN_ISO_DATE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabaseHelper.COLUMN_RESPONSE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(SmsDatabaseHelper.COLUMN_URL))
                ));
            }
            cursor.close();
        }
        adapter.setItems(records);
        hasHistory = !records.isEmpty();
        invalidateOptionsMenu();
        
        if (!hasHistory) {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
    }

    // Update logic
    private boolean updateAvailable = false;
    private String updateUrl = null;
    private long downloadId = -1;

    // Download Complete Receiver
    private final android.content.BroadcastReceiver downloadReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            long id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (id == downloadId) {
                installApk(id);
            }
        }
    };

    private void checkForUpdate() {
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                // Standard HTTP GET
                java.net.URL url = new java.net.URL(Const.AUTOV_UPDATE_CHECK);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setRequestMethod("GET");
                
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    
                    JSONObject data = new JSONObject(sb.toString());
                    
                    int remoteVer = data.optInt("version_code", -1);
                    String remoteUrl = data.optString("download_url", "");
                    
                    int currentVer = BuildConfig.VERSION_CODE;
                    
                    if (remoteVer > currentVer && !remoteUrl.isEmpty()) {
                        runOnUiThread(() -> {
                            updateAvailable = true;
                            updateUrl = remoteUrl;
                            showUpdateConfirmation(remoteVer, remoteUrl);
                        });
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(SmsHistoryActivity.this, "Already up to date (V" + currentVer + "). Server has V" + remoteVer, Toast.LENGTH_LONG).show();
                        });
                    }
                } else {
                    int code = conn.getResponseCode();
                    runOnUiThread(() -> {
                        Toast.makeText(SmsHistoryActivity.this, "Server Error: " + code + ". Check if update.json exists.", Toast.LENGTH_LONG).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(SmsHistoryActivity.this, "Check failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showUpdateConfirmation(int version, String url) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Update Available")
                .setMessage("A new version (" + version + ") is available. Download and install now?")
                .setPositiveButton("Download", (dialog, which) -> startDownload())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownload() {
        if (updateUrl == null) return;
        try {
            android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(android.net.Uri.parse(updateUrl));
            request.setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI | android.app.DownloadManager.Request.NETWORK_MOBILE);
            request.setTitle("Downloading Update");
            request.setDescription("Downloading latest version...");
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "sms_update.apk");
            request.setMimeType("application/vnd.android.package-archive");

            android.app.DownloadManager manager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                downloadId = manager.enqueue(request);
                Toast.makeText(this, "Downloading...", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void installApk(long id) {
        try {
            android.app.DownloadManager manager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            android.net.Uri downloadUri = manager.getUriForDownloadedFile(id);
            
            if (downloadUri != null) {
                // Check if we have permission to install apps (Android 8+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (!getPackageManager().canRequestPackageInstalls()) {
                        Toast.makeText(this, "Please allow 'Install unknown apps' for this app.", Toast.LENGTH_LONG).show();
                        startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                                .setData(android.net.Uri.parse("package:" + getPackageName())));
                        return;
                    }
                }

                android.content.Intent install = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                install.setDataAndType(downloadUri, "application/vnd.android.package-archive");
                install.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                install.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                startActivity(install);
            } else {
                Toast.makeText(this, "Could not find downloaded file.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Install failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResendClick(SmsAdapter.SmsRecord record) {
        try {
            String deviceId = DeviceIdUtil.get(this);
            SimInfoUtil.SimInfo si = SimInfoUtil.read(this, record.simId);
            String maskedNumber = SimInfoUtil.maskNumber(si.phoneNumber);

            int configuredSimIndex = getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE)
                    .getInt(Const.PREF_SIM_INDEX, 1);

            JSONObject payload = new JSONObject()
                    .put("type", "incoming_resend")
                    .put("device_unique_id", deviceId)
                    .put("from", record.from)
                    .put("body", record.body)
                    .put("sim_id", record.simId)
                    .put("sim_index", configuredSimIndex) // Use configured index
                    .put("line_number", maskedNumber)
                    .put("date", record.isoDate)
                    .put("_db_id", record.id);

            List<SmsDatabaseHelper.Config> configs = SmsDatabaseHelper.getInstance(this).getAllConfigs();
            int matchCount = 0;
            
            // Mark as pending immediately for UI feedback
            SmsDatabaseHelper.getInstance(this).updateStatusResponseAndUrl(record.id, SmsDatabaseHelper.STATUS_PENDING, "Sending...", null);
            loadData(); // Refresh UI to show PENDING state
            
            for (SmsDatabaseHelper.Config config : configs) {
                if (!config.isActive) continue;

                // Match SIM matches...
                
                matchCount++;
                
                // Update the URL in DB immediately so user sees the new URL in details
                String targetUrl = config.url;
                if (config.serverType == 1) {
                    targetUrl = Const.AUTOV_SMS_UPLOAD;
                }
                SmsDatabaseHelper.getInstance(this).updateStatusResponseAndUrl(record.id, SmsDatabaseHelper.STATUS_PENDING, "Sending...", targetUrl);
                loadData(); // Refresh UI again to show new URL
                
                QueueUploader.sendToConfigAsync(this, new JSONObject(payload.toString()), config, "resend-manual");
            }

            if (matchCount > 0) {
                Toast.makeText(this, "Sending...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No active configurations found!", Toast.LENGTH_SHORT).show();
                // Revert status if no configs
                SmsDatabaseHelper.getInstance(this).updateStatusResponseAndUrl(record.id, SmsDatabaseHelper.STATUS_FAILED, "No active config", null);
                loadData();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDetailClick(SmsAdapter.SmsRecord record) {
        String url = record.url;
        String resp = record.response;

        // If URL is missing, it means SMS was NOT forwarded (no configuration matched)
        if (url == null || url.isEmpty()) {
            url = "Not forwarded - No configuration";
            resp = "This SMS was not sent to any server because:\n" +
                   "• No configuration existed, OR\n" +
                   "• All configurations were inactive, OR\n" +
                   "• No configuration matched (wrong SIM or sender not whitelisted)\n\n" +
                   "Create or activate a configuration to forward future SMS.";
        } else {
            // URL exists, show it
            if (resp == null || resp.isEmpty()) {
                resp = "No response data";
            }
        }

        // Format the message
        String msg = "URL:\n" + url + "\n\nResponse:\n" + resp;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Server Details")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sms_history, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem clearItem = menu.findItem(R.id.action_clear_history);
        if (clearItem != null) {
            clearItem.setVisible(hasHistory);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            loadData();
            Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_clear_history) {
            showClearHistoryDialog();
            return true;
        } else if (id == R.id.action_config) {
            startActivity(new android.content.Intent(this, DashboardActivity.class));
            return true;
        } else if (id == R.id.action_update) {
            checkForUpdate();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister broadcast receiver to prevent memory leaks
        try {
            unregisterReceiver(smsStatusReceiver);
            unregisterReceiver(downloadReceiver);
        } catch (Exception e) {
            // Receiver might not be registered
        }
    }

    private void showClearHistoryDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Clear History")
                .setMessage("Are you sure you want to delete all SMS history?")
                .setPositiveButton("Clear All", (dialog, which) -> {
                    SmsDatabaseHelper.getInstance(this).deleteAllSms();
                    loadData(); // Refresh the list
                    Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
