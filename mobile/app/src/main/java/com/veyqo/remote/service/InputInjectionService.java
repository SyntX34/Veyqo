package com.veyqo.remote.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/* Injects remote touches, gestures, text, and navigation keys requested by desktop */
public class InputInjectionService extends AccessibilityService {

    private static final String TAG = "VeyqoInput";
    private static volatile InputInjectionService instance;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.i(TAG, "InputInjectionService connected and active");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.i(TAG, "InputInjectionService destroyed");
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
        try {
            Path tapPath = new Path();
            tapPath.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(tapPath, 0, 50));
            dispatchGesture(builder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.w(TAG, "injectTap cancelled by system at (" + x + "," + y + ")");
                }
            }, null);
        } catch (Exception e) {
            Log.e(TAG, "Error injecting tap: " + e.getMessage());
        }
    }

    public void injectSwipe(float startX, float startY, float endX, float endY, long durationMs) {
        try {
            if (durationMs <= 0) durationMs = 250;
            Path swipePath = new Path();
            swipePath.moveTo(startX, startY);
            swipePath.lineTo(endX, endY);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, durationMs));
            dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            Log.e(TAG, "Error injecting swipe: " + e.getMessage());
        }
    }

    public void injectScroll(float centerX, float centerY, float deltaY) {
        /* Scroll: Positive deltaY scrolls down (finger drags upwards) */
        float scrollDistance = deltaY * 350f;
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
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error injecting text: " + e.getMessage());
        }
    }

    public void injectBackspace() {
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error injecting backspace: " + e.getMessage());
        }
    }

    public void injectSelectAll() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null && focused.isEditable()) {
                CharSequence text = focused.getText();
                int len = text != null ? text.length() : 0;
                Bundle arguments = new Bundle();
                arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
                arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len);
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, arguments);
                focused.recycle();
            }
            root.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error injecting select all: " + e.getMessage());
        }
    }

    public void injectCopy() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null) {
                focused.performAction(AccessibilityNodeInfo.ACTION_COPY);
                focused.recycle();
            }
            root.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error injecting copy: " + e.getMessage());
        }
    }

    public void injectCut() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null && focused.isEditable()) {
                focused.performAction(AccessibilityNodeInfo.ACTION_CUT);
                focused.recycle();
            }
            root.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error injecting cut: " + e.getMessage());
        }
    }

    public void injectPaste() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return;

            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null && focused.isEditable()) {
                focused.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                focused.recycle();
            }
            root.recycle();
        } catch (Exception e) {
            Log.e(TAG, "Error injecting paste: " + e.getMessage());
        }
    }
}
