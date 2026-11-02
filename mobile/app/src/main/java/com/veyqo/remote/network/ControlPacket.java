package com.veyqo.remote.network;

import com.google.gson.annotations.SerializedName;

/* Packet exchanged between Android service and Desktop controller */
public class ControlPacket {
    @SerializedName("type")
    public int type;

    @SerializedName("x")
    public float x;

    @SerializedName("y")
    public float y;

    @SerializedName("keyCode")
    public int keyCode;

    @SerializedName("payload")
    public String payload;

    @SerializedName("timestamp")
    public long timestamp;

    public static final int TYPE_HANDSHAKE = 1;
    public static final int TYPE_TOUCH_DOWN = 2;
    public static final int TYPE_TOUCH_MOVE = 3;
    public static final int TYPE_TOUCH_UP = 4;
    public static final int TYPE_KEY_EVENT = 5;
    public static final int TYPE_TEXT_INJECT = 6;
    public static final int TYPE_HEARTBEAT = 7;
}
