package com.veyqo.remote;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.veyqo.remote.service.RemoteServerService;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView textServerStatus;
    private TextView textIpAddress;
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
        btnToggleServer = findViewById(R.id.btnToggleServer);
        btnScanQr = findViewById(R.id.btnScanQr);
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility);

        updateIpAddress();

        btnToggleServer.setOnClickListener(v -> toggleServer());
        btnScanQr.setOnClickListener(v -> initiateQrScan());
        btnEnableAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
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

