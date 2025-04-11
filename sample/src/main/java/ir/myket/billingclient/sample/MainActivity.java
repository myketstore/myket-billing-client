package com.example.trivialdrive;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "TrivialDrive";
    private int mTank = 2; // Max is 2 units
    private static final int TANK_MAX = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        loadData();
        updateTankStatus();

        Button driveButton = findViewById(R.id.button_drive);
        Button refillButton = findViewById(R.id.button_refill);

        driveButton.setOnClickListener(v -> {
            if (mTank > 0) {
                mTank--;
                saveData();
                updateTankStatus();
                animateTankChange();
                Toast.makeText(this, "You drove a bit!", Toast.LENGTH_SHORT).show();
            } else {
                complain("Tank is empty! Refill first.");
            }
        });

        refillButton.setOnClickListener(v -> {
            mTank = TANK_MAX;
            saveData();
            updateTankStatus();
            animateTankChange();
            Toast.makeText(this, "Tank refilled!", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateTankStatus() {
        TextView status = findViewById(R.id.tank_status);
        status.setText("Tank Level: " + mTank);
    }

    private void animateTankChange() {
        TextView status = findViewById(R.id.tank_status);
        ObjectAnimator fade = ObjectAnimator.ofFloat(status, "alpha", 0f, 1f);
        fade.setDuration(500);
        fade.setInterpolator(new AccelerateDecelerateInterpolator());
        fade.start();
    }

    void setWaitScreen(boolean set) {
        findViewById(R.id.screen_main).setVisibility(set ? View.GONE : View.VISIBLE);
        findViewById(R.id.screen_wait).setVisibility(set ? View.VISIBLE : View.GONE);
    }

    void complain(String message) {
        Log.e(TAG, "**** TrivialDrive Error: " + message);
        alert("Error: " + message);
    }

    void alert(String message) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setMessage(message);
        bld.setNeutralButton("OK", null);
        Log.d(TAG, "Showing alert dialog: " + message);
        bld.create().show();
    }

    void saveData() {
        SharedPreferences.Editor spe = getPreferences(MODE_PRIVATE).edit();
        spe.putInt("tank", mTank);
        spe.apply();
        Log.d(TAG, "Saved data: tank = " + mTank);
    }

    void loadData() {
        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        mTank = sp.getInt("tank", TANK_MAX);
        Log.d(TAG, "Loaded data: tank = " + mTank);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_reset) {
            mTank = TANK_MAX;
            saveData();
            updateTankStatus();
            animateTankChange();
            Toast.makeText(this, "Tank reset!", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
