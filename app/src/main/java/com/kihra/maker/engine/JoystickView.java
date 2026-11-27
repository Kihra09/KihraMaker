package com.kihra.maker.engine;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.hatkid.mkxpz.gamepad.GamepadButton;

/** Analog-style stick that presses the arrow keys. Drag inside it; the knob follows the finger. */
class JoystickView extends View {
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GamepadButton.OnKeyDownListener down;
    private final GamepadButton.OnKeyUpListener up;
    private final boolean diagonal;
    private float knobX = 0f; // -1..1
    private float knobY = 0f;
    private boolean left, right, upKey, downKey;
    private int alpha = 255;

    JoystickView(Context context, boolean diagonal,
                 GamepadButton.OnKeyDownListener down, GamepadButton.OnKeyUpListener up) {
        super(context);
        this.diagonal = diagonal;
        this.down = down;
        this.up = up;
        basePaint.setStyle(Paint.Style.FILL);
        basePaint.setColor(Color.argb(46, 255, 255, 255));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f * context.getResources().getDisplayMetrics().density);
        ringPaint.setColor(Color.argb(150, 255, 255, 255));
        knobPaint.setStyle(Paint.Style.FILL);
        knobPaint.setColor(Color.argb(120, 255, 255, 255));
    }

    void setControlAlpha(int percent) {
        alpha = Math.round(Math.min(percent, 100) * 2.55f);
        setAlpha(alpha / 255f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - ringPaint.getStrokeWidth();
        canvas.drawCircle(cx, cy, r, basePaint);
        canvas.drawCircle(cx, cy, r, ringPaint);
        float kr = r * 0.42f;
        canvas.drawCircle(cx + knobX * (r - kr), cy + knobY * (r - kr), kr, knobPaint);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float dx = e.getX() - cx;
                float dy = e.getY() - cy;
                float radius = Math.max(1f, Math.min(cx, cy));
                float dist = (float) Math.hypot(dx, dy);
                if (dist > radius) {
                    dx = dx / dist * radius;
                    dy = dy / dist * radius;
                    dist = radius;
                }
                knobX = dx / radius;
                knobY = dy / radius;
                boolean nl = false, nr = false, nu = false, nd = false;
                if (dist > radius * 0.25f) {
                    if (diagonal) {
                        nl = dx < -dist * 0.38f;
                        nr = dx > dist * 0.38f;
                        nu = dy < -dist * 0.38f;
                        nd = dy > dist * 0.38f;
                    } else if (Math.abs(dx) > Math.abs(dy)) {
                        nl = dx < 0;
                        nr = dx > 0;
                    } else {
                        nu = dy < 0;
                        nd = dy > 0;
                    }
                }
                update(nl, nr, nu, nd);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                knobX = 0f;
                knobY = 0f;
                update(false, false, false, false);
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void update(boolean nl, boolean nr, boolean nu, boolean nd) {
        if (nl != left) { send(KeyEvent.KEYCODE_DPAD_LEFT, nl); left = nl; }
        if (nr != right) { send(KeyEvent.KEYCODE_DPAD_RIGHT, nr); right = nr; }
        if (nu != upKey) { send(KeyEvent.KEYCODE_DPAD_UP, nu); upKey = nu; }
        if (nd != downKey) { send(KeyEvent.KEYCODE_DPAD_DOWN, nd); downKey = nd; }
    }

    private void send(int key, boolean pressed) {
        if (pressed) down.onKeyDown(key);
        else up.onKeyUp(key);
    }
}
