package com.veyqo.remote.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;
import com.veyqo.remote.network.ControlPacket;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/* Manages background TCP socket listener and incoming remote control packets */
public class RemoteServerService extends Service {

    public static final String ACTION_START = "com.veyqo.action.START_SERVER";
    public static final String ACTION_STOP = "com.veyqo.action.STOP_SERVER";
    public static final int DEFAULT_PORT = 55443;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private boolean isRunning = false;
    private final Gson gson = new Gson();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopServer();
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startForegroundNotification();
            startServer();
        } catch (Exception e) {
            stopSelf();
        }
        return START_STICKY;
    }

    private void startForegroundNotification() {
        String channelId = "veyqo_remote_server_channel";
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Veyqo Remote Server",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows active local remote connection state");
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Veyqo Server Active")
                .setContentText("Listening on port " + DEFAULT_PORT)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int serviceType = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            }
            startForeground(101, notification, serviceType);
        } else {
            startForeground(101, notification);
        }
    }

    private void startServer() {
        if (isRunning) return;
        isRunning = true;

        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(DEFAULT_PORT);
                while (isRunning) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClient(client)).start();
                }
            } catch (Exception ignored) {
            }
        });
        serverThread.start();
    }

    private void handleClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            byte[] lengthBuffer = new byte[4];
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            if (wm != null) {
                wm.getDefaultDisplay().getRealMetrics(metrics);
            }

            while (isRunning && !socket.isClosed()) {
                int read = in.read(lengthBuffer);
                if (read < 4) break;

                int packetLength = ByteBuffer.wrap(lengthBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (packetLength <= 0 || packetLength > 65536) break;

                byte[] buffer = new byte[packetLength];
                int total = 0;
                while (total < packetLength) {
                    int r = in.read(buffer, total, packetLength - total);
                    if (r == -1) break;
                    total += r;
                }

                String json = new String(buffer, StandardCharsets.UTF_8);
                ControlPacket packet = gson.fromJson(json, ControlPacket.class);
                processPacket(packet, metrics.widthPixels, metrics.heightPixels);
            }
        } catch (Exception ignored) {
        }
    }

    private void processPacket(ControlPacket packet, int screenWidth, int screenHeight) {
        InputInjectionService injectionService = InputInjectionService.getInstance();
        if (injectionService == null || packet == null || screenWidth <= 0 || screenHeight <= 0) return;

        float targetX = packet.x * screenWidth;
        float targetY = packet.y * screenHeight;

        if (packet.type == ControlPacket.TYPE_TOUCH_DOWN || packet.type == ControlPacket.TYPE_TOUCH_UP) {
            injectionService.injectTap(targetX, targetY);
        }
    }

    private void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
