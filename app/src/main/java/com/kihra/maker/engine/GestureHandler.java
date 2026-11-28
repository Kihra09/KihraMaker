package com.kihra.maker.engine;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import com.hatkid.mkxpz.gamepad.GamepadButton;
import com.hatkid.mkxpz.gamepad.GamepadDPad;

import java.util.HashMap;

/**
 * Touch gestures on the game screen (like PPSSPP): swipe up/down/left/right and tap each press a key.
 * Touches that start on an on-screen button, the stick or the toolbar are ignored.
 */
final class GestureHandler {
    private final ViewGroup root;
    private final float density;
    private final HashMap<String, Integer> keys = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable haptic;
    private final java.util.function.IntConsumer down;
    private final java.util.function.IntConsumer up;

    private boolean tracking = false;
    private float sx, sy;
    private long st;
    private int pointerId = -1;

    GestureHandler(ViewGroup root, String config, float density, Runnable haptic,
                   java.util.function.IntConsumer down, java.util.function.IntConsumer up) {
        this.down = down;
        this.up = up;
        this.root = root;
        this.density = density;
        this.haptic = haptic;
        if (config != null) {
            for (String pair : config.split(",")) {
                String[] p = pair.split(":");
                if (p.length != 2) continue;
                try {
                    keys.put(p[0], Integer.parseInt(p[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    boolean enabled() {
        return !keys.isEmpty();
    }

    /** Observes the event; never consumes it. */
    void onTouch(MotionEvent e) {
        if (keys.isEmpty()) return;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracking = !isOverControl(root, e.getRawX(), e.getRawY());
                if (tracking) {
                    sx = e.getRawX();
                    sy = e.getRawY();
                    st = e.getEventTime();
                    pointerId = e.getPointerId(0);
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                tracking = false; // multi-touch is for the buttons
                break;
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                break;
            case MotionEvent.ACTION_UP:
                if (!tracking) break;
                tracking = false;
                int idx = e.findPointerIndex(pointerId);
                if (idx < 0) break;
                float dx = e.getRawX() - sx;
                float dy = e.getRawY() - sy;
                float min = 40 * density;
                String dir;
                if (Math.abs(dx) < min && Math.abs(dy) < min) {
                    if (e.getEventTime() - st > 450) break; // a long press isn't a tap
                    dir = "tap";
                } else if (Math.abs(dx) > Math.abs(dy)) {
                    dir = dx > 0 ? "right" : "left";
                } else {
                    dir = dy > 0 ? "down" : "up";
                }
                Integer key = keys.get(dir);
                if (key != null) press(key);
                break;
            default:
                break;
        }
    }

    private void press(final int key) {
        if (haptic != null) haptic.run();
        down.accept(key);
        handler.postDelayed(() -> up.accept(key), 70);
    }

    private static boolean isOverControl(View v, float rx, float ry) {
        if (v.getVisibility() != View.VISIBLE) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        boolean inside = rx >= loc[0] && rx <= loc[0] + v.getWidth() && ry >= loc[1] && ry <= loc[1] + v.getHeight();
        if (!inside) return false;
        if (v instanceof GamepadButton || v instanceof GamepadDPad || v instanceof JoystickView
                || v instanceof ImageButton) {
            return true;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (isOverControl(g.getChildAt(i), rx, ry)) return true;
            }
            return false;
        }
        return v.isClickable();
    }
}
