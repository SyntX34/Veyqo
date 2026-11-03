package com.veyqo.remote;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.veyqo.remote.service.RemoteServerService;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "veyqo_theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String GITHUB_REPO_URL = "https://github.com/SyntX34/Veyqo";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int SCREEN_CAPTURE_REQUEST_CODE = 1002;

    private DrawerLayout drawerLayout;
    private TextView textServerStatus;
    private TextView textIpAddress;
    private TextView textVersionBanner;
    private TextView textThemeStatus;
    private ImageButton btnToggleTheme;
    private ImageButton btnOpenSettings;
    private Button btnToggleServer;
    private Button btnScanQr;
    private Button btnEnableAccessibility;
    private Button btnGithubRepo;
    private Button btnAboutApp;

    private boolean isServerRunning = false;
    private String appVersion = "1.0.0";
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private Intent pendingScreenIntentData;
    private String pendingConnectHost;
    private int pendingConnectPort = RemoteServerService.DEFAULT_PORT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                throwable.printStackTrace(new java.io.PrintWriter(sw));
                String stackTrace = sw.toString();
                android.util.Log.e("VeyqoCrash", stackTrace);

                java.io.File logFile = new java.io.File(getExternalFilesDir(null), "crash.log");
                java.io.FileWriter writer = new java.io.FileWriter(logFile, false);
                writer.write("Veyqo v" + appVersion + " Crash Log\n");
                writer.write("Device: " + Build.MANUFACTURER + " " + Build.MODEL + " (SDK " + Build.VERSION.SDK_INT + ")\n\n");
                writer.write(stackTrace);
                writer.close();
            } catch (Exception ignored) {
            }
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        });

        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawerLayout);
        textServerStatus = findViewById(R.id.textServerStatus);
        textIpAddress = findViewById(R.id.textIpAddress);
        textVersionBanner = findViewById(R.id.textVersionBanner);
        textThemeStatus = findViewById(R.id.textThemeStatus);
        btnToggleTheme = findViewById(R.id.btnToggleTheme);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);
        btnToggleServer = findViewById(R.id.btnToggleServer);
        btnScanQr = findViewById(R.id.btnScanQr);
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility);
        btnGithubRepo = findViewById(R.id.btnGithubRepo);
        btnAboutApp = findViewById(R.id.btnAboutApp);

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        initVersionAndThemeBanner();
        updateIpAddress();
        checkAndRequestAppPermissions();

        btnToggleServer.setOnClickListener(v -> toggleServer());
        btnScanQr.setOnClickListener(v -> initiateQrScanWithPermissionCheck());
        btnEnableAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnToggleTheme.setOnClickListener(v -> toggleThemeMode());

        btnOpenSettings.setOnClickListener(v -> {
            if (drawerLayout != null) {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END);
                } else {
                    drawerLayout.openDrawer(GravityCompat.END);
                }
            }
        });

        btnGithubRepo.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL));
            startActivity(browserIntent);
        });

        btnAboutApp.setOnClickListener(v -> showAboutDialog());
        checkAndShowLastCrashLog();
        updateAccessibilityButtonState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccessibilityButtonState();
    }

    private void updateAccessibilityButtonState() {
        boolean isEnabled = InputInjectionService.getInstance() != null;
        if (isEnabled) {
            btnEnableAccessibility.setText("✓ Remote Control Enabled");
            btnEnableAccessibility.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#10B981")));
        } else {
            btnEnableAccessibility.setText("Grant Input Permission (Required for Control)");
            btnEnableAccessibility.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E11D48")));
        }
    }

    private void checkAndShowLastCrashLog() {
        try {
            java.io.File logFile = new java.io.File(getExternalFilesDir(null), "crash.log");
            if (logFile.exists() && logFile.length() > 0) {
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(logFile));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();

                new AlertDialog.Builder(this)
                        .setTitle("Previous Crash Diagnostic")
                        .setMessage(sb.toString())
                        .setPositiveButton("Dismiss", (d, w) -> logFile.delete())
                        .setNeutralButton("Copy", (d, w) -> {
                            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Veyqo Crash Log", sb.toString()));
                                Toast.makeText(this, "Copied crash log to clipboard", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            }
        } catch (Exception ignored) {
        }
    }

    private void checkAndRequestAppPermissions() {
        List<String> neededPermissions = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.CAMERA);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!neededPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, neededPermissions.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    private void initiateQrScanWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
            return;
        }
        initiateQrScan();
    }

    private void initVersionAndThemeBanner() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            appVersion = pInfo.versionName;
        } catch (Exception ignored) {
        }
        textVersionBanner.setText("● Veyqo v" + appVersion + " (Official Build)");
        updateThemeBannerText();
    }

    private void updateThemeBannerText() {
        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES;

        /* When dark theme is active, show the sun icon to switch to day/light; when light, show moon to switch to dark */
        btnToggleTheme.setImageResource(isDark ? R.drawable.ic_theme_day : R.drawable.ic_theme_night);
        textThemeStatus.setText(isDark ? "Dark" : "Light");
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

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("About Veyqo")
                .setMessage("Veyqo v" + appVersion + "\n\n"
                        + "High-performance local-first peer-to-peer remote device bridge for Windows, Linux, and Android.\n\n"
                        + "Author: Samir Neupane (SyntX34)\n"
                        + "License: MIT License\n"
                        + "Repository: " + GITHUB_REPO_URL)
                .setPositiveButton("OK", null)
                .setNeutralButton("Visit GitHub", (dialog, which) -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_REPO_URL));
                    startActivity(browserIntent);
                })
                .show();
    }

    private void updateIpAddress() {
        String ip = getDeviceIpAddress();
        textIpAddress.setText("IP: " + ip);
    }

    private void toggleServer() {
        if (!isServerRunning) {
            if (projectionManager != null) {
                startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE);
            } else {
                startServerService(0, null);
            }
        } else {
            stopServerService();
        }
    }

    private void startServerService(int resultCode, Intent resultData) {
        Intent intent = new Intent(this, RemoteServerService.class);
        intent.setAction(RemoteServerService.ACTION_START);
        if (resultCode != 0 && resultData != null) {
            intent.putExtra(RemoteServerService.EXTRA_RESULT_CODE, resultCode);
            intent.putExtra(RemoteServerService.EXTRA_RESULT_DATA, resultData);
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            isServerRunning = true;
            textServerStatus.setText("Port " + RemoteServerService.DEFAULT_PORT + ": Active (Listening)");
            btnToggleServer.setText("Stop Remote Server");
        } catch (Exception e) {
            Toast.makeText(this, "Failed to start server: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopServerService() {
        Intent intent = new Intent(this, RemoteServerService.class);
        intent.setAction(RemoteServerService.ACTION_STOP);
        try {
            startService(intent);
        } catch (Exception ignored) {
        }
        isServerRunning = false;
        textServerStatus.setText("Port " + RemoteServerService.DEFAULT_PORT + ": Offline");
        btnToggleServer.setText("Start Remote Server");
    }

    private void initiateQrScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setCaptureActivity(CustomScanActivity.class);
        integrator.setPrompt("Align QR Code inside viewfinder");
        integrator.setOrientationLocked(true);
        integrator.setBeepEnabled(true);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                if (pendingConnectHost != null) {
                    dispatchOutgoingConnect(pendingConnectHost, pendingConnectPort, resultCode, data);
                    pendingConnectHost = null;
                } else {
                    startServerService(resultCode, data);
                }
            } else {
                Toast.makeText(this, "Screen capture permission required", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                handleScannedPayload(result.getContents());
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void handleScannedPayload(String payload) {
        // Parse: veyqo://connect?host=192.168.2.100&port=55443&token=...
        try {
            Uri uri = Uri.parse(payload);
            String host = uri.getQueryParameter("host");
            String portStr = uri.getQueryParameter("port");
            int port = (portStr != null) ? Integer.parseInt(portStr) : RemoteServerService.DEFAULT_PORT;

            if (host != null && !host.isEmpty()) {
                showApprovalDialog(host, port);
                return;
            }
        } catch (Exception ignored) {
        }

        Toast.makeText(this, "Scanned: " + payload, Toast.LENGTH_LONG).show();
    }

    private void showApprovalDialog(String host, int port) {
        new AlertDialog.Builder(this)
                .setTitle("Pairing Request")
                .setMessage("Allow desktop controller at " + host + ":" + port + " to view and control this device?")
                .setPositiveButton("Authorize & Stream", (dialog, which) -> {
                    pendingConnectHost = host;
                    pendingConnectPort = port;
                    if (projectionManager != null) {
                        startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE);
                    } else {
                        dispatchOutgoingConnect(host, port, 0, null);
                    }
                })
                .setNegativeButton("Deny", null)
                .setCancelable(false)
                .show();
    }

    private void dispatchOutgoingConnect(String host, int port, int resultCode, Intent resultData) {
        Intent intent = new Intent(this, RemoteServerService.class);
        intent.setAction(RemoteServerService.ACTION_CONNECT_OUT);
        intent.putExtra(RemoteServerService.EXTRA_HOST, host);
        intent.putExtra(RemoteServerService.EXTRA_PORT, port);
        if (resultCode != 0 && resultData != null) {
            intent.putExtra(RemoteServerService.EXTRA_RESULT_CODE, resultCode);
            intent.putExtra(RemoteServerService.EXTRA_RESULT_DATA, resultData);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }

        Toast.makeText(this, "Connecting to PC (" + host + ":" + port + ")...", Toast.LENGTH_LONG).show();
        textServerStatus.setText("Connected to PC: " + host + ":" + port);
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
