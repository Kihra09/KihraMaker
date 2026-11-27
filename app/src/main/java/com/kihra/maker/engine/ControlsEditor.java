package com.kihra.maker.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.hatkid.mkxpz.gamepad.GamepadButton;
import com.kihra.maker.R;

import org.json.JSONObject;

import java.io.File;

/**
 * Lets the player move, resize, reshape and re-key the on-screen controls. Positions are kept per
 * game (files/controls/<game>.json). Every control is moved directly under the gamepad root so it
 * can be dragged anywhere on the screen.
 */
class ControlsEditor {
    private static final class Control {
        final int id;
        final String name;
        final boolean dpad;
        float dx = 0f;      // offset as a fraction of the screen width
        float dy = 0f;      // offset as a fraction of the screen height
        float scale = 1f;
        int key = -1;       // -1 = original key
        int shape = 0;      // 0 = round, 1 = square
        View view;

        Control(int id, String name, boolean dpad) {
            this.id = id;
            this.name = name;
            this.dpad = dpad;
        }
    }

    private static final String[] KEY_NAMES = {
            "Z", "X", "C", "A", "S", "D", "Q", "W", "Enter", "Space", "Esc", "Shift", "Ctrl", "Alt",
            "Tab", "F5", "F6", "F7", "F8", "F9"
    };
    private static final int[] KEY_CODES = {
            android.view.KeyEvent.KEYCODE_Z, android.view.KeyEvent.KEYCODE_X, android.view.KeyEvent.KEYCODE_C,
            android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_D,
            android.view.KeyEvent.KEYCODE_Q, android.view.KeyEvent.KEYCODE_W, android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_SPACE, android.view.KeyEvent.KEYCODE_ESCAPE,
            android.view.KeyEvent.KEYCODE_SHIFT_LEFT, android.view.KeyEvent.KEYCODE_CTRL_LEFT,
            android.view.KeyEvent.KEYCODE_ALT_LEFT, android.view.KeyEvent.KEYCODE_TAB,
            android.view.KeyEvent.KEYCODE_F5, android.view.KeyEvent.KEYCODE_F6, android.view.KeyEvent.KEYCODE_F7,
            android.view.KeyEvent.KEYCODE_F8, android.view.KeyEvent.KEYCODE_F9
    };

    private final Activity activity;
    private final File saveFile;
    private final Control[] controls = {
            new Control(R.id.dpad, "D-pad", true),
            new Control(R.id.button_A, "A", false), new Control(R.id.button_B, "B", false),
            new Control(R.id.button_C, "C", false), new Control(R.id.button_X, "X", false),
            new Control(R.id.button_Y, "Y", false), new Control(R.id.button_Z, "Z", false),
            new Control(R.id.button_L, "L", false), new Control(R.id.button_R, "R", false),
            new Control(R.id.button_CTRL, "Ctrl", false), new Control(R.id.button_ALT, "Alt", false),
            new Control(R.id.button_SHIFT, "Shift", false),
    };
    private ViewGroup root;
    private boolean ready = false;
    private boolean editing = false;
    private View banner;
    private ViewGroup overlayParent;

    ControlsEditor(Activity activity, ViewGroup overlayParent, File saveFile) {
        this.activity = activity;
        this.overlayParent = overlayParent;
        this.saveFile = saveFile;
    }

    /** Call once the gamepad layout is attached. Waits for the first layout pass. */
    void install() {
        root = activity.findViewById(R.id.gamepad_layout);
        if (root == null) return;
        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (root.getWidth() <= 0 || root.getHeight() <= 0) return;
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                try {
                    reparent();
                    load();
                    for (Control c : controls) apply(c);
                    ready = true;
                } catch (Exception ignored) {
                    // Controls simply stay where the layout put them.
                }
            }
        });
    }

    private void reparent() {
        if (!(root instanceof RelativeLayout)) return;
        int[] rootLoc = new int[2];
        root.getLocationInWindow(rootLoc);
        for (Control c : controls) {
            View v = activity.findViewById(c.id);
            c.view = v;
            if (v == null) continue;
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent == root) continue;
            int[] loc = new int[2];
            v.getLocationInWindow(loc);
            int w = v.getWidth();
            int h = v.getHeight();
            parent.removeView(v);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(w, h);
            lp.leftMargin = loc[0] - rootLoc[0];
            lp.topMargin = loc[1] - rootLoc[1];
            root.addView(v, lp);
        }
        root.setClipChildren(false);
    }

    private void apply(Control c) {
        if (c.view == null) return;
        c.view.setTranslationX(c.dx * root.getWidth());
        c.view.setTranslationY(c.dy * root.getHeight());
        c.view.setScaleX(c.scale);
        c.view.setScaleY(c.scale);
        if (!c.dpad && c.view instanceof GamepadButton) {
            GamepadButton b = (GamepadButton) c.view;
            b.setBackgroundResource(c.shape == 1 ? R.drawable.gamepad_button_square : R.drawable.gamepad_button_circle);
            if (c.key >= 0) {
                b.setKey(c.key);
                b.setForegroundText(labelFor(c.key));
            }
        }
    }

    private static String labelFor(int code) {
        for (int i = 0; i < KEY_CODES.length; i++) if (KEY_CODES[i] == code) return KEY_NAMES[i];
        return android.view.KeyEvent.keyCodeToString(code).replace("KEYCODE_", "");
    }

    // ---- saving ----

    private void load() {
        try {
            if (!saveFile.isFile()) return;
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(saveFile))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject o = new JSONObject(sb.toString());
            for (Control c : controls) {
                JSONObject j = o.optJSONObject(c.name);
                if (j == null) continue;
                c.dx = (float) j.optDouble("dx", 0);
                c.dy = (float) j.optDouble("dy", 0);
                c.scale = (float) j.optDouble("scale", 1);
                c.key = j.optInt("key", -1);
                c.shape = j.optInt("shape", 0);
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try {
            JSONObject o = new JSONObject();
            for (Control c : controls) {
                o.put(c.name, new JSONObject()
                        .put("dx", c.dx).put("dy", c.dy).put("scale", c.scale)
                        .put("key", c.key).put("shape", c.shape));
            }
            File dir = saveFile.getParentFile();
            if (dir != null) dir.mkdirs();
            try (java.io.FileWriter w = new java.io.FileWriter(saveFile)) {
                w.write(o.toString());
            }
        } catch (Exception ignored) {
        }
    }

    // ---- editing ----

    boolean isEditing() {
        return editing;
    }

    void toggle() {
        if (!ready) return;
        setEditing(!editing);
    }

    private void setEditing(boolean on) {
        editing = on;
        for (final Control c : controls) {
            if (c.view == null) continue;
            if (on) {
                c.view.setOnTouchListener(new DragListener(c));
            } else {
                c.view.setOnTouchListener(null);
            }
        }
        if (on) {
            showBanner();
        } else {
            if (banner != null) overlayParent.removeView(banner);
            banner = null;
            save();
        }
    }

    private void showBanner() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackgroundColor(Color.argb(190, 0, 0, 0));
        int pad = Math.round(10 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        TextView t = new TextView(activity);
        t.setText("Edit controls: drag to move, tap a control for size, key and shape");
        t.setTextColor(Color.WHITE);
        box.addView(t);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        Button reset = new Button(activity);
        reset.setText("Reset all");
        reset.setOnClickListener(v -> {
            for (Control c : controls) {
                c.dx = 0; c.dy = 0; c.scale = 1f; c.shape = 0;
                if (c.view != null) {
                    c.view.setTranslationX(0); c.view.setTranslationY(0);
                    c.view.setScaleX(1); c.view.setScaleY(1);
                    if (!c.dpad && c.view instanceof GamepadButton) {
                        ((GamepadButton) c.view).setBackgroundResource(R.drawable.gamepad_button_circle);
                    }
                }
            }
            save();
        });
        Button done = new Button(activity);
        done.setText("Done");
        done.setOnClickListener(v -> setEditing(false));
        row.addView(reset);
        row.addView(done);
        box.addView(row);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        box.setLayoutParams(lp);
        overlayParent.addView(box);
        banner = box;
    }

    private final class DragListener implements View.OnTouchListener {
        private final Control c;
        private float downX;
        private float downY;
        private float startDx;
        private float startDy;
        private boolean moved;

        DragListener(Control c) {
            this.c = c;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            float slop = 8 * activity.getResources().getDisplayMetrics().density;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startDx = c.dx;
                    startDy = c.dy;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float mx = e.getRawX() - downX;
                    float my = e.getRawY() - downY;
                    if (Math.abs(mx) > slop || Math.abs(my) > slop) moved = true;
                    if (moved) {
                        c.dx = startDx + mx / root.getWidth();
                        c.dy = startDy + my / root.getHeight();
                        v.setTranslationX(c.dx * root.getWidth());
                        v.setTranslationY(c.dy * root.getHeight());
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (moved) save();
                    else showControlDialog(c);
                    return true;
                default:
                    return true;
            }
        }
    }

    private void showControlDialog(final Control c) {
        final String[] items = c.dpad
                ? new String[]{"Bigger", "Smaller", "Reset this control"}
                : new String[]{"Bigger", "Smaller", "Change key...", "Round / square", "Reset this control"};
        new AlertDialog.Builder(activity)
                .setTitle(c.name + " button")
                .setItems(items, (d, which) -> {
                    String item = items[which];
                    if (item.equals("Bigger")) {
                        c.scale = Math.min(2.5f, c.scale * 1.15f);
                    } else if (item.equals("Smaller")) {
                        c.scale = Math.max(0.5f, c.scale / 1.15f);
                    } else if (item.equals("Round / square")) {
                        c.shape = c.shape == 1 ? 0 : 1;
                    } else if (item.startsWith("Reset")) {
                        c.dx = 0; c.dy = 0; c.scale = 1f; c.shape = 0;
                    } else {
                        pickKey(c);
                        return;
                    }
                    apply(c);
                    save();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void pickKey(final Control c) {
        new AlertDialog.Builder(activity)
                .setTitle("Key for " + c.name)
                .setItems(KEY_NAMES, (d, which) -> {
                    c.key = KEY_CODES[which];
                    apply(c);
                    save();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
