package com.autov.sms;

import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().setStatusBarColor(android.graphics.Color.parseColor("#00B09B"));
        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);

        super.onCreate(savedInstanceState);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_sms_history);

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
            for (SmsDatabaseHelper.Config config : configs) {
                if (!config.isActive) continue;

                // Match SIM (0=Both)
                // For resend, we don't have the detected SIM slot easily, but we have record.simId.
                // We'll skip complex SIM mapping for now or just send it if config is active.
                
                matchCount++;
                QueueUploader.sendToConfigAsync(this, new JSONObject(payload.toString()), config, "resend-manual");
            }

            if (matchCount > 0) {
                Toast.makeText(this, "Resending to " + matchCount + " configs...", Toast.LENGTH_SHORT).show();
                recyclerView.postDelayed(this::loadData, 2000);
            } else {
                Toast.makeText(this, "No active configurations found!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDetailClick(SmsAdapter.SmsRecord record) {
        String url = record.url;
        String resp = record.response;

        // If URL is missing (old records), show the CURRENT configured URL
        if (url == null || url.isEmpty()) {
            android.content.SharedPreferences sp = getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE);
            int server = sp.getInt(Const.PREF_SERVER, 1);
            if (server == 1) {
                url = Const.AUTOV_SMS_UPLOAD + " ";
            } else if (server == 3) {
                url = sp.getString(Const.PREF_OTHER_URL, "") + " ";
            } else {
                url = "Unknown";
            }
        }
        if (resp == null || resp.isEmpty()) resp = "No response data";

        // Simple formatting
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
        if (id == R.id.action_clear_history) {
            showClearHistoryDialog();
            return true;
        } else if (id == R.id.action_config) {
            startActivity(new android.content.Intent(this, DashboardActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
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
