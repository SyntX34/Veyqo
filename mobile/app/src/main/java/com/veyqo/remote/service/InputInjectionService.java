package com.veyqo.remote.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

/* Injects remote touches and gestures requested by the desktop client */
public class InputInjectionService extends AccessibilityService {

    private static InputInjectionService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        /* No-op */
    }

    @Override
    public void onInterrupt() {
        /* No-op */
    }

    public static InputInjectionService getInstance() {
        return instance;
    }

    public void injectTap(float x, float y) {
        Path swipePath = new Path();
        swipePath.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 50));
        dispatchGesture(builder.build(), null, null);
    }

    public void injectSwipe(float startX, float startY, float endX, float endY, long durationMs) {
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, durationMs));
        dispatchGesture(builder.build(), null, null);
    }
}
