package com.autov.sms;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;

public class DashboardActivity extends AppCompatActivity {

    private static final String[] SENDERS = new String[]{"192", "Notice", "Maamuus"};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Center text already in XML (TextView)
        TextView tv = findViewById(R.id.centerText);
        tv.setText("Sending NEW SMS");

        // Spinner in toolbar (top-right)
        Spinner spinner = findViewById(R.id.senderSpinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, SENDERS);
        spinner.setAdapter(adapter);

        // Initialize selection from prefs (pick first match if present)
        // (We store single selection; if none, no sender is allowed)
        // This snippet sets spinner to the first whitelisted value if found:
        int idx = -1;
        for (int i = 0; i < SENDERS.length; i++) {
            if (WhitelistUtil.getWhitelist(this).contains(SENDERS[i])) {
                idx = i; break;
            }
        }
        if (idx >= 0) spinner.setSelection(idx);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(AdapterView<?> parent, android.view.View view, int position, long id) {
                // Avoid double fire on first attach if you want; harmless if kept
                String chosen = SENDERS[position];
                WhitelistUtil.setSingleSender(DashboardActivity.this, chosen);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {
                // Clear whitelist if nothing selected (not typical with Spinner)
                WhitelistUtil.clear(DashboardActivity.this);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            doLogout();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doLogout() {
        // Clear prefs + queue and go back to MainActivity
        getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE).edit().clear().apply();
        // Also clear queue explicitly (optional; already cleared by clear())
        // WhitelistUtil.clear(this); // redundant after clear()

        // Navigate back to setup
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
