package com.autov.sms;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.button.MaterialButton;

public class SendAllActivity extends AppCompatActivity {

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        getWindow().setStatusBarColor(
                androidx.core.content.ContextCompat.getColor(this, R.color.purple_500)
        );

        new androidx.core.view.WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView()
        ).setAppearanceLightStatusBars(false);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_all);

        MaterialToolbar tb = findViewById(R.id.toolbar);
        tb.setTitle("Autov SMS");
        tb.setTitleTextColor(Color.WHITE);

        setSupportActionBar(tb);

        // Default OFF if not set
        boolean sendAll = getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE)
                .getBoolean(Const.PREF_SEND_ALL, false);

        MaterialSwitch sw = findViewById(R.id.switchSendAll);
        sw.setTrackTintList(ColorStateList.valueOf(Color.parseColor("#00B09B")));

        sw.setChecked(sendAll);
        sw.setOnCheckedChangeListener((CompoundButton button, boolean isChecked) -> {
            getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE).edit()
                    .putBoolean(Const.PREF_SEND_ALL, isChecked)
                    .apply();
        });


    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_send_all, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_history) {
            startActivity(new Intent(this, SmsHistoryActivity.class));
            return true;
        } else if (id == R.id.action_dashboard) {
            startActivity(new Intent(this, DashboardActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            // Clear everything and go to MainActivity
            getSharedPreferences(Const.PREF_NAME, MODE_PRIVATE).edit().clear().apply();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
