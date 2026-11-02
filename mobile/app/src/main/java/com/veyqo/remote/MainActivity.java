package com.veyqo.remote;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.veyqo.remote.service.RemoteServerService;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "veyqo_theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    private TextView textServerStatus;
    private TextView textIpAddress;
    private TextView textVersionBanner;
    private TextView textThemeStatus;
    private ImageButton btnToggleTheme;
    private ImageButton btnAutoTheme;
    private Button btnToggleServer;
    private Button btnScanQr;
    private Button btnEnableAccessibility;

    private boolean isServerRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textServerStatus = findViewById(R.id.textServerStatus);
        textIpAddress = findViewById(R.id.textIpAddress);
        textVersionBanner = findViewById(R.id.textVersionBanner);
        textThemeStatus = findViewById(R.id.textThemeStatus);
        btnToggleTheme = findViewById(R.id.btnToggleTheme);
        btnAutoTheme = findViewById(R.id.btnAutoTheme);
        btnToggleServer = findViewById(R.id.btnToggleServer);
        btnScanQr = findViewById(R.id.btnScanQr);
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility);

        initVersionAndThemeBanner();
        updateIpAddress();

        btnToggleServer.setOnClickListener(v -> toggleServer());
        btnScanQr.setOnClickListener(v -> initiateQrScan());
        btnEnableAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnToggleTheme.setOnClickListener(v -> toggleThemeMode());
        btnAutoTheme.setOnClickListener(v -> setFollowSystemTheme());
    }

    private void initVersionAndThemeBanner() {
        String versionName = "1.0.0";
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
        } catch (Exception ignored) {
        }
        textVersionBanner.setText("● Veyqo v" + versionName + " (Official Build)");

        updateThemeBannerText();
    }

    private void updateThemeBannerText() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int mode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
        String modeString = isDark ? "Dark" : "Light";

        /* Update button icon based on current mode */
        btnToggleTheme.setImageResource(isDark ? R.drawable.ic_theme_day : R.drawable.ic_theme_night);

        if (mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            textThemeStatus.setText(modeString + " (OS)");
        } else {
            textThemeStatus.setText(modeString);
        }
    }

    private void toggleThemeMode() {
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        int nextMode = (currentNightMode == Configuration.UI_MODE_NIGHT_YES)
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_YES;

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME_MODE, nextMode)
                .apply();

        AppCompatDelegate.setDefaultNightMode(nextMode);
    }

    private void setFollowSystemTheme() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                .apply();

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private void updateIpAddress() {
        String ip = getDeviceIpAddress();
        textIpAddress.setText("IP: " + ip);
    }

    private void toggleServer() {
        Intent intent = new Intent(this, RemoteServerService.class);
        if (!isServerRunning) {
            intent.setAction(RemoteServerService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            isServerRunning = true;
            textServerStatus.setText("Port " + RemoteServerService.DEFAULT_PORT + ": Active (Listening)");
            btnToggleServer.setText("Stop Remote Server");
        } else {
            intent.setAction(RemoteServerService.ACTION_STOP);
            startService(intent);
            isServerRunning = false;
            textServerStatus.setText("Port " + RemoteServerService.DEFAULT_PORT + ": Offline");
            btnToggleServer.setText("Start Remote Server");
        }
    }

    private void initiateQrScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setPrompt("Align with Veyqo Desktop QR Code");
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                Toast.makeText(this, "Scanned: " + result.getContents(), Toast.LENGTH_LONG).show();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private String getDeviceIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        if (sAddr != null && sAddr.indexOf(':') < 0) {
                            return sAddr;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
}
