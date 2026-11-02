package com.veyqo.remote.stream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/* Resilient screen frame capture and streamer using ImageReader and VirtualDisplay */
public class ScreenStreamEncoder {

    private MediaProjection mediaProjection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private MediaProjection.Callback projectionCallback;
    private volatile boolean isStreaming = false;

    private int width;
    private int height;
    private int dpi;
    private int jpegQuality = 65;
    private long lastFrameTime = 0;
    private static final long MIN_FRAME_INTERVAL_MS = 33; /* Cap at ~30 FPS for smooth LAN responsiveness */

    public ScreenStreamEncoder(MediaProjection mediaProjection, int width, int height, int dpi) {
        this.mediaProjection = mediaProjection;
        /* Scale dimensions down if necessary to ensure smooth streaming performance */
        int targetWidth = width;
        int targetHeight = height;
        if (targetWidth > 720) {
            float ratio = (float) targetHeight / (float) targetWidth;
            targetWidth = 720;
            targetHeight = (int) (720 * ratio);
        }
        this.width = targetWidth;
        this.height = targetHeight;
        this.dpi = dpi;
    }

    public synchronized void start(OutputStream networkOut) {
        if (isStreaming || mediaProjection == null) return;
        isStreaming = true;

        handlerThread = new HandlerThread("VeyqoScreenStreamThread");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());

        /* Register MediaProjection callback first (mandatory on Android 14+) */
        projectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                super.onStop();
                stop();
            }
        };

        try {
            mediaProjection.registerCallback(projectionCallback, new Handler(Looper.getMainLooper()));
        } catch (Exception ignored) {
        }

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

        byte[] lengthHeader = new byte[4];
        ByteArrayOutputStream jpegBuffer = new ByteArrayOutputStream(64 * 1024);

        imageReader.setOnImageAvailableListener(reader -> {
            if (!isStreaming) return;
            long now = System.currentTimeMillis();
            if (now - lastFrameTime < MIN_FRAME_INTERVAL_MS) {
                Image dropImg = reader.acquireLatestImage();
                if (dropImg != null) dropImg.close();
                return;
            }
            lastFrameTime = now;

            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * width;

                    Bitmap bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                    );
                    bitmap.copyPixelsFromBuffer(buffer);

                    Bitmap finalBitmap = bitmap;
                    if (rowPadding != 0) {
                        finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
                        if (finalBitmap != bitmap) {
                            bitmap.recycle();
                        }
                    }

                    jpegBuffer.reset();
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, jpegBuffer);
                    finalBitmap.recycle();

                    byte[] jpegData = jpegBuffer.toByteArray();
                    int size = jpegData.length;

                    lengthHeader[0] = (byte) (size & 0xFF);
                    lengthHeader[1] = (byte) ((size >> 8) & 0xFF);
                    lengthHeader[2] = (byte) ((size >> 16) & 0xFF);
                    lengthHeader[3] = (byte) ((size >> 24) & 0xFF);

                    synchronized (networkOut) {
                        networkOut.write(lengthHeader);
                        networkOut.write(jpegData);
                        networkOut.flush();
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }, backgroundHandler);

        try {
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "VeyqoStream",
                    width,
                    height,
                    dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    backgroundHandler
            );
        } catch (Exception e) {
            stop();
        }
    }

    public void adjustBitrate(int newBitrate) {
        /* In JPEG mode adjust compression quality based on network speed */
        if (newBitrate < 2_000_000) {
            jpegQuality = 45;
        } else if (newBitrate < 5_000_000) {
            jpegQuality = 65;
        } else {
            jpegQuality = 80;
        }
    }

    public synchronized void stop() {
        isStreaming = false;
        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            if (handlerThread != null) {
                handlerThread.quitSafely();
                handlerThread = null;
                backgroundHandler = null;
            }
            if (mediaProjection != null && projectionCallback != null) {
                try {
                    mediaProjection.unregisterCallback(projectionCallback);
                } catch (Exception ignored) {
                }
                projectionCallback = null;
            }
        } catch (Exception ignored) {
        }
    }
}
