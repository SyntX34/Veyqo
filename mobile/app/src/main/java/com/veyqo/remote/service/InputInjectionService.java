package com.veyqo.remote.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/* Injects remote touches, gestures, text, and navigation keys requested by desktop */
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
        Path tapPath = new Path();
        tapPath.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(tapPath, 0, 50));
        dispatchGesture(builder.build(), null, null);
    }

    public void injectSwipe(float startX, float startY, float endX, float endY, long durationMs) {
        if (durationMs <= 0) durationMs = 300;
        Path swipePath = new Path();
        swipePath.moveTo(startX, startY);
        swipePath.lineTo(endX, endY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, durationMs));
        dispatchGesture(builder.build(), null, null);
    }

    public void injectScroll(float centerX, float centerY, float deltaY) {
        /* Scroll: Positive deltaY scrolls down (finger moves up) */
        float scrollDistance = deltaY * 300f;
        float endY = centerY - scrollDistance;
        injectSwipe(centerX, centerY, centerX, endY, 200);
    }

    public void injectBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public void injectHome() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public void injectRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    public void injectNotifications() {
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    public void injectText(String text) {
        if (text == null || text.isEmpty()) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused == null) {
            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        }

        if (focused != null) {
            Bundle arguments = new Bundle();
            CharSequence existingText = focused.getText();
            String newText = (existingText != null ? existingText.toString() : "") + text;
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText);
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            focused.recycle();
        }
        root.recycle();
    }

    public void injectBackspace() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focused != null) {
            CharSequence existingText = focused.getText();
            if (existingText != null && existingText.length() > 0) {
                String newText = existingText.subSequence(0, existingText.length() - 1).toString();
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText);
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            }
            focused.recycle();
        }
        root.recycle();
    }
}
