package com.veyqo.remote.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;
import com.veyqo.remote.network.ControlPacket;
import com.veyqo.remote.stream.ScreenStreamEncoder;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/* Background TCP Service supporting both Host listener and Client outgoing connect */
public class RemoteServerService extends Service {

    public static final String ACTION_START = "com.veyqo.action.START_SERVER";
    public static final String ACTION_STOP = "com.veyqo.action.STOP_SERVER";
    public static final String ACTION_CONNECT_OUT = "com.veyqo.action.CONNECT_OUT";
    public static final String EXTRA_HOST = "extra_host";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_RESULT_DATA = "extra_result_data";
    public static final int DEFAULT_PORT = 55443;

    private final IBinder binder = new LocalBinder();
    private ServerSocket serverSocket;
    private Thread serverThread;
    private Thread clientThread;
    private boolean isRunning = false;
    private final Gson gson = new Gson();
    private static MediaProjection activeMediaProjection;
    private ScreenStreamEncoder streamEncoder;

    public class LocalBinder extends Binder {
        public RemoteServerService getService() {
            return RemoteServerService.this;
        }
    }

    public static void setMediaProjection(MediaProjection projection) {
        activeMediaProjection = projection;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP.equals(action)) {
                stopAll();
                stopSelf();
                return START_NOT_STICKY;
            }

            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = null;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
                } else {
                    resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
                }
            } catch (Exception e) {
                try {
                    resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
                } catch (Exception ignored) {
                }
            }

            startForegroundNotification();

            /* On Android 14+, initialize MediaProjection inside active foreground service */
            if (resultCode != 0 && resultData != null) {
                try {
                    android.media.projection.MediaProjectionManager pm =
                            (android.media.projection.MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    if (pm != null) {
                        activeMediaProjection = pm.getMediaProjection(resultCode, resultData);
                    }
                } catch (Exception ignored) {
                }
            }

            if (ACTION_CONNECT_OUT.equals(action)) {
                String host = intent.getStringExtra(EXTRA_HOST);
                int port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT);
                if (host != null && !host.isEmpty()) {
                    connectToDesktop(host, port);
                    return START_STICKY;
                }
            }
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
                serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
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
                serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress("0.0.0.0", DEFAULT_PORT));

                while (isRunning && !serverSocket.isClosed()) {
                    Socket client = serverSocket.accept();
                    client.setTcpNoDelay(true);
                    new Thread(() -> handleConnection(client)).start();
                }
            } catch (Exception ignored) {
            }
        });
        serverThread.start();
    }

    private void connectToDesktop(String host, int port) {
        if (clientThread != null && clientThread.isAlive()) {
            clientThread.interrupt();
        }

        clientThread = new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(host, port), 5000);
                handleConnection(socket);
            } catch (Exception ignored) {
            }
        });
        clientThread.start();
    }

    private void handleConnection(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            if (wm != null) {
                wm.getDefaultDisplay().getRealMetrics(metrics);
            }

            // Start hardware screen encoder if MediaProjection is granted
            if (activeMediaProjection != null) {
                try {
                    streamEncoder = new ScreenStreamEncoder(activeMediaProjection, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi);
                    streamEncoder.start(out);
                } catch (Exception ignored) {
                }
            }

            byte[] lengthBuffer = new byte[4];
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
        } finally {
            if (streamEncoder != null) {
                streamEncoder.stop();
                streamEncoder = null;
            }
        }
    }

    private void processPacket(ControlPacket packet, int screenWidth, int screenHeight) {
        InputInjectionService injectionService = InputInjectionService.getInstance();
        if (injectionService == null || packet == null || screenWidth <= 0 || screenHeight <= 0) return;

        float targetX = packet.x * screenWidth;
        float targetY = packet.y * screenHeight;

        switch (packet.type) {
            case ControlPacket.TYPE_TOUCH_DOWN:
            case ControlPacket.TYPE_TOUCH_UP:
                injectionService.injectTap(targetX, targetY);
                break;

            case ControlPacket.TYPE_SWIPE:
                float endX = packet.endX * screenWidth;
                float endY = packet.endY * screenHeight;
                long duration = packet.durationMs > 0 ? packet.durationMs : 250;
                injectionService.injectSwipe(targetX, targetY, endX, endY, duration);
                break;

            case ControlPacket.TYPE_SCROLL:
                injectionService.injectScroll(targetX, targetY, packet.y != 0 ? packet.y : 0.2f);
                break;

            case ControlPacket.TYPE_TEXT_INJECT:
                if (packet.payload != null) {
                    injectionService.injectText(packet.payload);
                }
                break;

            case ControlPacket.TYPE_KEY_EVENT:
                /* Special keys: Backspace (67), Enter (66), Escape / Back (4) */
                if (packet.keyCode == 67) {
                    injectionService.injectBackspace();
                } else if (packet.keyCode == 4) {
                    injectionService.injectBack();
                }
                break;

            case ControlPacket.TYPE_NAV_BACK:
                injectionService.injectBack();
                break;

            case ControlPacket.TYPE_NAV_HOME:
                injectionService.injectHome();
                break;

            case ControlPacket.TYPE_NAV_RECENTS:
                injectionService.injectRecents();
                break;

            case ControlPacket.TYPE_CLIPBOARD_SYNC:
                if (packet.payload != null) {
                    try {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            android.content.ClipData clip = android.content.ClipData.newPlainText("Veyqo Clipboard", packet.payload);
                            cm.setPrimaryClip(clip);
                        }
                    } catch (Exception ignored) {
                    }
                }
                break;

            case ControlPacket.TYPE_FILE_CHUNK:
                if (packet.fileName != null && packet.fileData != null) {
                    try {
                        byte[] rawBytes = android.util.Base64.decode(packet.fileData, android.util.Base64.DEFAULT);
                        java.io.File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                        if (!downloadDir.exists()) downloadDir.mkdirs();
                        java.io.File targetFile = new java.io.File(downloadDir, packet.fileName);
                        java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile);
                        fos.write(rawBytes);
                        fos.flush();
                        fos.close();
                    } catch (Exception ignored) {
                    }
                }
                break;

            default:
                break;
        }
    }

    private void stopAll() {
        isRunning = false;
        try {
            if (streamEncoder != null) {
                streamEncoder.stop();
                streamEncoder = null;
            }
            if (serverSocket != null) serverSocket.close();
            if (clientThread != null) clientThread.interrupt();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        stopAll();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}
