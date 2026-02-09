package com.autov.sms;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class DashboardActivity extends AppCompatActivity {

    private ChipGroup chips;
    private EditText etSender;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().setStatusBarColor(
                androidx.core.content.ContextCompat.getColor(this, R.color.purple_500)
        );

// Keep white icons (not light mode icons)
        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setTitle("");

        TextView tv = findViewById(R.id.centerText);
        tv.setText("Allow senders");

        chips = findViewById(R.id.chips);
        etSender = findViewById(R.id.etSender);
        MaterialButton btnAdd = findViewById(R.id.btnAdd);

        // Load saved whitelist
        refreshChips();

        // Add new sender
        btnAdd.setOnClickListener(v -> {
            String raw = etSender.getText() == null ? "" : etSender.getText().toString().trim();
            if (raw.isEmpty()) return;

            // Normalize a little (case-insensitive match)
            String normalized = normalizeSender(raw);

            Set<String> current = new LinkedHashSet<>(WhitelistUtil.getWhitelist(this));
            if (!current.contains(normalized)) {
                current.add(normalized);
                WhitelistUtil.setWhitelist(this, current);
                refreshChips();
            }
            etSender.setText("");
        });
    }

    private String normalizeSender(String s) {
        // Keep numbers as-is; make text case-insensitive by lower-casing
        // (WhitelistUtil will compare exact strings; we store normalized)
        // You can change rule to your need.
        boolean allDigits = s.matches("\\d+");
        return allDigits ? s : s.toLowerCase(Locale.US);
    }

    private void refreshChips() {
        chips.removeAllViews();
        Set<String> list = WhitelistUtil.getWhitelist(this);

        // If empty, show a single “ALL” info chip (not actually saved; just visual hint)
        if (list.isEmpty()) {
            Chip ch = new Chip(this, null, com.google.android.material.R.style.Widget_Material3_Chip_Assist_Elevated);
            ch.setText("ALL senders allowed");
            ch.setChipIconResource(android.R.drawable.ic_menu_info_details);
            ch.setCloseIconVisible(false);
            ch.setEnabled(false);
            chips.addView(ch);
            return;
        }

        for (String entry : list) {
            Chip ch = new Chip(this, null, com.google.android.material.R.style.Widget_Material3_Chip_Assist_Elevated);
            ch.setText(entry);
            ch.setCloseIconVisible(true);
            ch.setOnCloseIconClickListener(v -> {
                Set<String> cur = new LinkedHashSet<>(WhitelistUtil.getWhitelist(this));
                cur.remove(entry);
                WhitelistUtil.setWhitelist(this, cur);
                refreshChips();
            });
            chips.addView(ch);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_dashboard, menu); // contains Logout
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            doLogout();
            return true;
        } else if (item.getItemId() == R.id.action_switch) {
            doSwitch();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void doSwitch() {
        // Only disable "enabled" flag so MainActivity doesn't auto-redirect,
        // but keep all other prefs (token, whitelist, etc.) intact.
        getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE).edit()
                .putBoolean(Const.PREF_ENABLED, false)
                .apply();

        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void doLogout() {
        // Clear prefs + queue and go back to MainActivity
        getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE).edit().clear().apply();

        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
