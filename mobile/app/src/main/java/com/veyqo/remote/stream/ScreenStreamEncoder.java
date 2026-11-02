package com.veyqo.remote.stream;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.view.Surface;

import java.io.OutputStream;
import java.nio.ByteBuffer;

/* Hardware-accelerated H.264 screen encoder using MediaProjection and MediaCodec */
public class ScreenStreamEncoder {

    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 60;
    private static final int I_FRAME_INTERVAL = 1;

    private MediaProjection mediaProjection;
    private MediaCodec mediaCodec;
    private VirtualDisplay virtualDisplay;
    private Surface inputSurface;
    private Thread encoderThread;
    private volatile boolean isStreaming = false;

    private int width;
    private int height;
    private int dpi;
    private int bitrate = 4_000_000; // 4 Mbps adaptive default

    public ScreenStreamEncoder(MediaProjection mediaProjection, int width, int height, int dpi) {
        this.mediaProjection = mediaProjection;
        this.width = (width / 16) * 16;
        this.height = (height / 16) * 16;
        this.dpi = dpi;
    }

    public synchronized void start(OutputStream networkOut) throws Exception {
        if (isStreaming) return;
        isStreaming = true;

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

        mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = mediaCodec.createInputSurface();
        mediaCodec.start();

        virtualDisplay = mediaProjection.createVirtualDisplay(
                "VeyqoStream",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
        );

        encoderThread = new Thread(() -> encodeLoop(networkOut));
        encoderThread.start();
    }

    private void encodeLoop(OutputStream out) {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        byte[] lengthHeader = new byte[4];

        try {
            while (isStreaming) {
                int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10_000);
                if (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex);
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                        byte[] packet = new byte[bufferInfo.size];
                        outputBuffer.get(packet);

                        // 4-byte little-endian length prefix followed by H.264 NAL frame
                        lengthHeader[0] = (byte) (bufferInfo.size & 0xFF);
                        lengthHeader[1] = (byte) ((bufferInfo.size >> 8) & 0xFF);
                        lengthHeader[2] = (byte) ((bufferInfo.size >> 16) & 0xFF);
                        lengthHeader[3] = (byte) ((bufferInfo.size >> 24) & 0xFF);

                        synchronized (out) {
                            out.write(lengthHeader);
                            out.write(packet);
                            out.flush();
                        }
                    }
                    mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                }
            }
        } catch (Exception ignored) {
        } finally {
            stop();
        }
    }

    public void adjustBitrate(int newBitrate) {
        if (!isStreaming || mediaCodec == null) return;
        this.bitrate = Math.max(1_000_000, Math.min(newBitrate, 12_000_000));
        Bundle params = new Bundle();
        params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, this.bitrate);
        mediaCodec.setParameters(params);
    }

    public synchronized void stop() {
        isStreaming = false;
        try {
            if (encoderThread != null) encoderThread.interrupt();
            if (virtualDisplay != null) virtualDisplay.release();
            if (inputSurface != null) inputSurface.release();
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            if (mediaProjection != null) mediaProjection.stop();
        } catch (Exception ignored) {
        }
    }
}

