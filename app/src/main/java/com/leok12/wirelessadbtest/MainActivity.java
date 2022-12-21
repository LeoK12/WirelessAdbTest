package com.leok12.wirelessadbtest;

import androidx.appcompat.app.AppCompatActivity;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.leok12.wirelessadbtest.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Button btnPair = findViewById(R.id.pair);
        btnPair.setOnClickListener(view -> handlePair());

        Button btnStart = findViewById(R.id.start);
        btnStart.setOnClickListener(view -> handleStart());

        Button btnStop = findViewById(R.id.stop);
        btnStop.setOnClickListener(view -> handleStop());
    }

    private boolean isNotificationEnabled(){
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = manager.getNotificationChannel(PairService.NOTIFICATION_CHANNEL_PAIR);
        return manager.areNotificationsEnabled() &&
                (channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    private void handlePair(){
        if (isNotificationEnabled()){
            Intent intent = new Intent(getApplicationContext(), PairService.class);
            intent.setAction(PairService.START_ACTION);
            startForegroundService(intent);
        }

        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        getApplicationContext().startActivity(intent);
    }

    private int getPort(){
        String[] portPropertyKey =
                {"service.adb.tcp.port", "persist.adb.tcp.port", "service.adb.tls.port"};

        for (String s : portPropertyKey) {
            int port = SystemProperties.getInt(s, -1);
            if (port != -1){
                return port;
            }
        }

        return -1;
    }

    private void handleStart(){
        int port = getPort();
        if (port == -1){
            Toast.makeText(this, "port is not found", Toast.LENGTH_LONG).show();
        }

        Intent intent = new Intent(getApplicationContext(), ShellService.class);
        intent.putExtra(ShellService.EXTRA_HOST, AdbMDNS.LOCALHOST);
        intent.putExtra(ShellService.EXTRA_PORT, port);
        intent.setAction(ShellService.START_ACTION);
        startService(intent);
    }

    private void handleStop(){
        Intent intent = new Intent(getApplicationContext(), ShellService.class);
        intent.setAction(ShellService.STOP_ACTION);
        startService(intent);
    }
}