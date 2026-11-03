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

    @SerializedName("endX")
    public float endX;

    @SerializedName("endY")
    public float endY;

    @SerializedName("durationMs")
    public long durationMs;

    @SerializedName("keyCode")
    public int keyCode;

    @SerializedName("payload")
    public String payload;

    @SerializedName("fileName")
    public String fileName;

    @SerializedName("fileData")
    public String fileData;

    @SerializedName("timestamp")
    public long timestamp;

    public static final int TYPE_HANDSHAKE = 1;
    public static final int TYPE_TOUCH_DOWN = 2;
    public static final int TYPE_TOUCH_MOVE = 3;
    public static final int TYPE_TOUCH_UP = 4;
    public static final int TYPE_KEY_EVENT = 5;
    public static final int TYPE_TEXT_INJECT = 6;
    public static final int TYPE_HEARTBEAT = 7;
    public static final int TYPE_SWIPE = 8;
    public static final int TYPE_SCROLL = 9;
    public static final int TYPE_NAV_BACK = 10;
    public static final int TYPE_NAV_HOME = 11;
    public static final int TYPE_NAV_RECENTS = 12;
    public static final int TYPE_CLIPBOARD_SYNC = 13;
    public static final int TYPE_FILE_CHUNK = 14;
    public static final int TYPE_SELECT_ALL = 17;
    public static final int TYPE_COPY = 18;
    public static final int TYPE_CUT = 19;
    public static final int TYPE_PASTE = 20;
}
