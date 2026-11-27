package com.kihra.maker.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.KeyEvent;
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
import java.util.ArrayList;
import java.util.List;

/**
 * On-screen controls editor: move (with a snapping grid), resize, reshape, re-key, add and delete
 * buttons, and swap the D-pad for an analog stick. Layouts are saved per game, with an optional
 * default layout shared by all games.
 *
 * Every control is placed directly under the gamepad root by its centre (fractions of the screen),
 * so it can sit anywhere.
 */
class ControlsEditor {
    private static final class Control {
        final int id;            // layout id for built-in controls, 0 for added buttons
        String name;
        final boolean dpad;
        final boolean extra;
        float cx;                // centre, fraction of root width
        float cy;                // centre, fraction of root height
        float w;                 // size in dp
        float h;
        float defCx, defCy, defW, defH;   // original layout values
        int key = -1;            // -1 = original key
        int shape = 0;           // 0 = round, 1 = rounded square
        boolean hidden = false;  // deleted by the player (can be restored from More)
        boolean special = false; // the menu button: can't be deleted or re-keyed
        View view;

        Control(int id, String name, boolean dpad, boolean extra) {
            this.id = id;
            this.name = name;
            this.dpad = dpad;
            this.extra = extra;
        }
    }

    // ---- Every keyboard key a button can press ----
    private static final List<String> KEY_NAMES = new ArrayList<>();
    private static final List<Integer> KEY_CODES = new ArrayList<>();

    private static void addKey(String name, int code) {
        KEY_NAMES.add(name);
        KEY_CODES.add(code);
    }

    static {
        for (int i = 0; i < 26; i++) addKey(String.valueOf((char) ('A' + i)), KeyEvent.KEYCODE_A + i);
        for (int i = 0; i < 10; i++) addKey(String.valueOf(i), KeyEvent.KEYCODE_0 + i);
        addKey("Enter", KeyEvent.KEYCODE_ENTER);
        addKey("Space", KeyEvent.KEYCODE_SPACE);
        addKey("Esc", KeyEvent.KEYCODE_ESCAPE);
        addKey("Tab", KeyEvent.KEYCODE_TAB);
        addKey("Backspace", KeyEvent.KEYCODE_DEL);
        addKey("Shift", KeyEvent.KEYCODE_SHIFT_LEFT);
        addKey("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT);
        addKey("Alt", KeyEvent.KEYCODE_ALT_LEFT);
        addKey("Up", KeyEvent.KEYCODE_DPAD_UP);
        addKey("Down", KeyEvent.KEYCODE_DPAD_DOWN);
        addKey("Left", KeyEvent.KEYCODE_DPAD_LEFT);
        addKey("Right", KeyEvent.KEYCODE_DPAD_RIGHT);
        addKey("Insert", KeyEvent.KEYCODE_INSERT);
        addKey("Home", KeyEvent.KEYCODE_MOVE_HOME);
        addKey("End", KeyEvent.KEYCODE_MOVE_END);
        addKey("Page Up", KeyEvent.KEYCODE_PAGE_UP);
        addKey("Page Down", KeyEvent.KEYCODE_PAGE_DOWN);
        for (int i = 0; i < 12; i++) addKey("F" + (i + 1), KeyEvent.KEYCODE_F1 + i);
    }

    private static String labelFor(int code) {
        int i = KEY_CODES.indexOf(code);
        if (i >= 0) return KEY_NAMES.get(i);
        return KeyEvent.keyCodeToString(code).replace("KEYCODE_", "");
    }

    private final Activity activity;
    private final ViewGroup overlayParent;
    private final File saveFile;
    private final File defaultFile;
    private boolean useStick;
    private final Runnable onMenu;
    private View dpadView;
    private View stickView;
    private Control menuControl;
    private final boolean diagonal;
    private final GamepadButton.OnKeyDownListener keyDown;
    private final GamepadButton.OnKeyUpListener keyUp;
    private final int opacity;
    private final float density;

    private final List<Control> controls = new ArrayList<>();
    private ViewGroup root;
    private boolean ready = false;
    private boolean editing = false;
    private boolean snap = true;
    private View banner;
    private GridOverlay grid;
    private int extraCounter = 0;

    ControlsEditor(Activity activity, ViewGroup overlayParent, File saveFile, File defaultFile,
                   boolean useStick, boolean diagonal, int opacity,
                   GamepadButton.OnKeyDownListener keyDown, GamepadButton.OnKeyUpListener keyUp,
                   Runnable onMenu) {
        this.onMenu = onMenu;
        this.activity = activity;
        this.overlayParent = overlayParent;
        this.saveFile = saveFile;
        this.defaultFile = defaultFile;
        this.useStick = useStick;
        this.diagonal = diagonal;
        this.opacity = opacity;
        this.keyDown = keyDown;
        this.keyUp = keyUp;
        this.density = activity.getResources().getDisplayMetrics().density;
        controls.add(new Control(R.id.dpad, "D-pad", true, false));
        controls.add(new Control(R.id.button_A, "A", false, false));
        controls.add(new Control(R.id.button_B, "B", false, false));
        controls.add(new Control(R.id.button_C, "C", false, false));
        controls.add(new Control(R.id.button_X, "X", false, false));
        controls.add(new Control(R.id.button_Y, "Y", false, false));
        controls.add(new Control(R.id.button_Z, "Z", false, false));
        controls.add(new Control(R.id.button_L, "L", false, false));
        controls.add(new Control(R.id.button_R, "R", false, false));
        controls.add(new Control(R.id.button_CTRL, "Ctrl", false, false));
        controls.add(new Control(R.id.button_ALT, "Alt", false, false));
        controls.add(new Control(R.id.button_SHIFT, "Shift", false, false));
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
                    captureOriginalAndReparent();
                    load();
                    for (Control c : new ArrayList<>(controls)) apply(c);
                    if (useStick) {
                        useStick = false;
                        setStick(true);
                    }
                    ready = true;
                } catch (Exception ignored) {
                    // Controls simply stay where the layout put them.
                }
            }
        });
    }

    private void captureOriginalAndReparent() {
        if (!(root instanceof RelativeLayout)) throw new IllegalStateException("unexpected root");
        int[] rootLoc = new int[2];
        root.getLocationInWindow(rootLoc);
        float rw = root.getWidth();
        float rh = root.getHeight();
        for (Control c : controls) {
            View v = activity.findViewById(c.id);
            if (v == null) continue;
            int[] loc = new int[2];
            v.getLocationInWindow(loc);
            int w = v.getWidth();
            int h = v.getHeight();
            c.defCx = (loc[0] - rootLoc[0] + w / 2f) / rw;
            c.defCy = (loc[1] - rootLoc[1] + h / 2f) / rh;
            c.defW = w / density;
            c.defH = h / density;
            c.cx = c.defCx;
            c.cy = c.defCy;
            c.w = c.defW;
            c.h = c.defH;
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent != root) {
                parent.removeView(v);
                root.addView(v, new RelativeLayout.LayoutParams(w, h));
            }
            c.view = v;
            if (c.dpad) dpadView = v;
        }
        // The one menu button (screenshot, fast forward, cheats, quick save/load, edit layout).
        menuControl = new Control(0, "Menu", false, false);
        menuControl.special = true;
        android.widget.ImageButton mb = new android.widget.ImageButton(activity);
        mb.setImageResource(R.drawable.ic_menu);
        mb.setBackgroundResource(R.drawable.gamepad_button_circle);
        mb.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        mb.setPadding(Math.round(8 * density), Math.round(8 * density), Math.round(8 * density), Math.round(8 * density));
        mb.setAlpha(0.6f);
        mb.setContentDescription("Menu");
        mb.setOnClickListener(v -> onMenu.run());
        menuControl.view = mb;
        menuControl.defCx = 0.5f;
        menuControl.defCy = 0.07f;
        menuControl.defW = 40;
        menuControl.defH = 40;
        menuControl.cx = 0.5f;
        menuControl.cy = 0.07f;
        menuControl.w = 40;
        menuControl.h = 40;
        root.addView(mb, new RelativeLayout.LayoutParams(Math.round(40 * density), Math.round(40 * density)));
        controls.add(menuControl);
        root.setClipChildren(false);
    }

    private void apply(Control c) {
        if (c.view == null) return;
        int wPx = Math.round(c.w * density);
        int hPx = Math.round(c.h * density);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(wPx, hPx);
        lp.leftMargin = Math.round(c.cx * root.getWidth() - wPx / 2f);
        lp.topMargin = Math.round(c.cy * root.getHeight() - hPx / 2f);
        c.view.setLayoutParams(lp);
        c.view.setTranslationX(0f);
        c.view.setTranslationY(0f);
        // Deleted controls disappear while playing and show faded while editing, so they can be restored.
        c.view.setVisibility(c.hidden && !editing ? View.GONE : View.VISIBLE);
        if (c.hidden && editing) {
            c.view.setAlpha(0.25f);
        } else if (c.view.getAlpha() == 0.25f) {
            c.view.setAlpha(c.special ? 0.6f : (c.view instanceof JoystickView ? Math.min(opacity, 100) / 100f : 1f));
        }
        if (!c.dpad && c.view instanceof GamepadButton) {
            GamepadButton b = (GamepadButton) c.view;
            b.setBackgroundResource(c.shape == 1 ? R.drawable.gamepad_button_square : R.drawable.gamepad_button_circle);
            if (c.key >= 0) {
                b.setKey(c.key);
                b.setForegroundText(labelFor(c.key));
            }
        }
    }

    private void resetToOriginal(Control c) {
        c.cx = c.defCx;
        c.cy = c.defCy;
        c.w = c.defW;
        c.h = c.defH;
        c.shape = 0;
        c.hidden = false;
    }

    // ---- stick / D-pad switch ----

    private void setStick(boolean on) {
        if (on == useStick || dpadView == null) return;
        Control dp = null;
        for (Control c : controls) if (c.dpad) dp = c;
        if (dp == null) return;
        if (on) {
            if (stickView == null) {
                JoystickView stick = new JoystickView(activity, diagonal, keyDown, keyUp);
                stick.setControlAlpha(opacity);
                root.addView(stick, new RelativeLayout.LayoutParams(Math.round(dp.w * density), Math.round(dp.h * density)));
                stickView = stick;
            }
            dpadView.setVisibility(View.GONE);
            dp.view = stickView;
        } else {
            if (stickView != null) stickView.setVisibility(View.GONE);
            dp.view = dpadView;
        }
        useStick = on;
        apply(dp);
        restoreTouch();
    }

    // ---- saving and loading ----

    private String readFile(File f) throws java.io.IOException {
        StringBuilder sb = new StringBuilder();
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void load() {
        File f = saveFile.isFile() ? saveFile : (defaultFile.isFile() ? defaultFile : null);
        if (f != null) loadFrom(f);
    }

    private void loadFrom(File f) {
        try {
            JSONObject o = new JSONObject(readFile(f));
            // Remove added buttons from an earlier layout.
            for (Control c : new ArrayList<>(controls)) {
                if (c.extra) {
                    if (c.view != null) root.removeView(c.view);
                    controls.remove(c);
                }
            }
            for (Control c : new ArrayList<>(controls)) {
                JSONObject j = o.optJSONObject(c.name);
                if (j != null) readControl(c, j);
            }
            Boolean wantStick = o.has("stick") ? o.optBoolean("stick", false) : null;
            JSONObject extras = o.optJSONObject("extras");
            if (extras != null) {
                java.util.Iterator<String> it = extras.keys();
                while (it.hasNext()) {
                    String name = it.next();
                    JSONObject j = extras.getJSONObject(name);
                    Control c = newExtra(name, j.optInt("key", KeyEvent.KEYCODE_Z));
                    readControl(c, j);
                }
            }
            if (wantStick != null) {
                if (ready) setStick(wantStick);
                else useStick = wantStick;
            }
        } catch (Exception ignored) {
        }
    }

    private void readControl(Control c, JSONObject j) {
        c.cx = (float) j.optDouble("cx", c.cx);
        c.cy = (float) j.optDouble("cy", c.cy);
        c.w = (float) j.optDouble("w", c.w);
        c.h = (float) j.optDouble("h", c.h);
        c.key = j.optInt("key", -1);
        c.shape = j.optInt("shape", 0);
        c.hidden = j.optBoolean("hidden", false);
    }

    private JSONObject writeControl(Control c) throws org.json.JSONException {
        return new JSONObject().put("cx", c.cx).put("cy", c.cy).put("w", c.w).put("h", c.h)
                .put("key", c.key).put("shape", c.shape).put("hidden", c.hidden);
    }

    private void saveTo(File f) {
        try {
            JSONObject o = new JSONObject();
            JSONObject extras = new JSONObject();
            for (Control c : controls) {
                if (c.extra) extras.put(c.name, writeControl(c));
                else o.put(c.name, writeControl(c));
            }
            o.put("extras", extras);
            o.put("stick", useStick);
            File dir = f.getParentFile();
            if (dir != null) dir.mkdirs();
            try (java.io.FileWriter w = new java.io.FileWriter(f)) {
                w.write(o.toString());
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        saveTo(saveFile);
    }

    // ---- added buttons ----

    private Control newExtra(String name, int key) {
        Control c = new Control(0, name, false, true);
        GamepadButton b = new GamepadButton(activity);
        b.setBackgroundResource(R.drawable.gamepad_button_circle);
        b.setKey(key);
        b.setForegroundText(labelFor(key));
        b.setOnKeyDownListener(keyDown);
        b.setOnKeyUpListener(keyUp);
        b.setImageAlpha(Math.round(Math.min(opacity, 100) * 2.55f));
        c.key = key;
        c.cx = 0.5f;
        c.cy = 0.5f;
        c.w = 52;
        c.h = 52;
        c.view = b;
        root.addView(b, new RelativeLayout.LayoutParams(Math.round(52 * density), Math.round(52 * density)));
        controls.add(c);
        if (name.startsWith("extra")) {
            try {
                extraCounter = Math.max(extraCounter, Integer.parseInt(name.substring(5)));
            } catch (NumberFormatException ignored) {
            }
        }
        if (editing) c.view.setOnTouchListener(new DragListener(c));
        return c;
    }

    private void addButton() {
        pickKey("Key for the new button", code -> {
            Control c = newExtra("extra" + (++extraCounter), code);
            apply(c);
            save();
        });
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
        for (Control c : controls) {
            if (c.view == null) continue;
            c.view.setOnTouchListener(on ? new DragListener(c) : null);
            if (c.hidden) apply(c);
        }
        if (on) {
            grid = new GridOverlay(activity, 24f * density);
            root.addView(grid, 0, new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            showBanner();
        } else {
            if (banner != null) overlayParent.removeView(banner);
            banner = null;
            if (grid != null) root.removeView(grid);
            grid = null;
            save();
        }
    }

    private Button smallButton(String text, View.OnClickListener l) {
        Button b = new Button(activity);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private void showBanner() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackgroundColor(Color.argb(190, 0, 0, 0));
        int pad = Math.round(8 * density);
        box.setPadding(pad, pad, pad, pad);
        TextView t = new TextView(activity);
        t.setText("Drag to move. Tap a control to resize, re-key or reshape it.");
        t.setTextColor(Color.WHITE);
        box.addView(t);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        final Button snapBtn = smallButton("Snap: on", null);
        snapBtn.setOnClickListener(v -> {
            snap = !snap;
            snapBtn.setText(snap ? "Snap: on" : "Snap: off");
            if (grid != null) grid.setVisibility(snap ? View.VISIBLE : View.INVISIBLE);
        });
        row.addView(smallButton("+ Button", v -> addButton()));
        row.addView(snapBtn);
        row.addView(smallButton("Align all", v -> alignAll()));
        row.addView(smallButton("More", v -> showMore()));
        row.addView(smallButton("Done", v -> setEditing(false)));
        box.addView(row);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lp.topMargin = Math.round(56 * density);
        box.setLayoutParams(lp);
        overlayParent.addView(box);
        banner = box;
    }

    private void showMore() {
        final String[] items = {
                "Save this layout as the default for all games",
                "Use the default layout here",
                "Reset to the original layout",
                useStick ? "Switch to the D-pad" : "Switch to the joystick",
                "Restore deleted buttons",
        };
        new AlertDialog.Builder(activity)
                .setTitle("Layout")
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        saveTo(defaultFile);
                    } else if (which == 1) {
                        if (defaultFile.isFile()) {
                            loadFrom(defaultFile);
                            for (Control c : new ArrayList<>(controls)) apply(c);
                            restoreTouch();
                            save();
                        }
                    } else if (which == 3) {
                        setStick(!useStick);
                        save();
                    } else if (which == 4) {
                        for (Control c : controls) {
                            if (c.hidden) {
                                c.hidden = false;
                                apply(c);
                            }
                        }
                        save();
                    } else {
                        for (Control c : new ArrayList<>(controls)) {
                            if (c.extra) {
                                root.removeView(c.view);
                                controls.remove(c);
                            } else {
                                resetToOriginal(c);
                                c.key = -1;
                                apply(c);
                            }
                        }
                        save();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void restoreTouch() {
        for (Control c : controls) {
            if (c.view != null) c.view.setOnTouchListener(editing ? new DragListener(c) : null);
        }
    }

    // ---- grid snapping ----

    private float cell() {
        return 24f * density;
    }

    private void snapControl(Control c) {
        float cell = cell();
        float px = Math.round(c.cx * root.getWidth() / cell) * cell;
        float py = Math.round(c.cy * root.getHeight() / cell) * cell;
        c.cx = px / root.getWidth();
        c.cy = py / root.getHeight();
    }

    private boolean overlaps(Control a, Control b) {
        float aw = a.w * density, ah = a.h * density, bw = b.w * density, bh = b.h * density;
        float ax = a.cx * root.getWidth(), ay = a.cy * root.getHeight();
        float bx = b.cx * root.getWidth(), by = b.cy * root.getHeight();
        return Math.abs(ax - bx) < (aw + bw) / 2f && Math.abs(ay - by) < (ah + bh) / 2f;
    }

    /** Snaps every control to the grid and moves overlapping ones to the nearest free spot. */
    private void alignAll() {
        float cell = cell();
        List<Control> placed = new ArrayList<>();
        for (Control c : controls) {
            if (c.view == null || c.hidden) continue;
            snapControl(c);
            int guard = 0;
            boolean clash = true;
            while (clash && guard++ < 200) {
                clash = false;
                for (Control p : placed) {
                    if (overlaps(c, p)) {
                        clash = true;
                        float nx = c.cx * root.getWidth() + cell;
                        float ny = c.cy * root.getHeight();
                        if (nx + c.w * density / 2f > root.getWidth()) {
                            nx = cell * 2f;
                            ny += cell;
                        }
                        c.cx = nx / root.getWidth();
                        c.cy = ny / root.getHeight();
                        break;
                    }
                }
            }
            placed.add(c);
            apply(c);
        }
        restoreTouch();
        save();
    }

    private final class DragListener implements View.OnTouchListener {
        private final Control c;
        private float downX;
        private float downY;
        private float startCx;
        private float startCy;
        private boolean moved;

        DragListener(Control c) {
            this.c = c;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            float slop = 8 * density;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startCx = c.cx;
                    startCy = c.cy;
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float mx = e.getRawX() - downX;
                    float my = e.getRawY() - downY;
                    if (Math.abs(mx) > slop || Math.abs(my) > slop) moved = true;
                    if (moved) {
                        c.cx = Math.max(0f, Math.min(1f, startCx + mx / root.getWidth()));
                        c.cy = Math.max(0f, Math.min(1f, startCy + my / root.getHeight()));
                        if (snap) snapControl(c);
                        // Move without re-creating the touch target while dragging.
                        v.setTranslationX(c.cx * root.getWidth() - (((ViewGroup.MarginLayoutParams) v.getLayoutParams()).leftMargin + v.getWidth() / 2f));
                        v.setTranslationY(c.cy * root.getHeight() - (((ViewGroup.MarginLayoutParams) v.getLayoutParams()).topMargin + v.getHeight() / 2f));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                    if (moved) {
                        apply(c);
                        save();
                    } else {
                        showControlDialog(c);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    apply(c);
                    return true;
                default:
                    return true;
            }
        }
    }

    // ---- dialogs ----

    private void showControlDialog(final Control c) {
        final List<String> items = new ArrayList<>();
        items.add("Bigger");
        items.add("Smaller");
        if (!c.dpad && !c.special) {
            items.add("Wider");
            items.add("Narrower");
            items.add("Taller");
            items.add("Shorter");
            items.add("Change key...");
            items.add("Round / rounded square");
        }
        if (c.dpad) items.add(useStick ? "Switch to the D-pad" : "Switch to the joystick");
        if (!c.special) items.add(c.hidden ? "Restore this control" : "Delete this control");
        items.add(c.extra ? "Remove" : "Reset this control");
        String title = c.dpad ? (useStick ? "Joystick" : "D-pad") : c.name + (c.special ? "" : " button");
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String item = items.get(which);
                    switch (item) {
                        case "Bigger": c.w = clamp(c.w * 1.15f); c.h = clamp(c.h * 1.15f); break;
                        case "Smaller": c.w = clamp(c.w / 1.15f); c.h = clamp(c.h / 1.15f); break;
                        case "Wider": c.w = clamp(c.w * 1.2f); break;
                        case "Narrower": c.w = clamp(c.w / 1.2f); break;
                        case "Taller": c.h = clamp(c.h * 1.2f); break;
                        case "Shorter": c.h = clamp(c.h / 1.2f); break;
                        case "Round / rounded square": c.shape = c.shape == 1 ? 0 : 1; break;
                        case "Switch to the D-pad":
                        case "Switch to the joystick":
                            setStick(!useStick);
                            save();
                            return;
                        case "Change key...":
                            pickKey("Key for " + c.name, code -> {
                                c.key = code;
                                apply(c);
                                save();
                            });
                            return;
                        case "Delete this control":
                            c.hidden = true;
                            break;
                        case "Restore this control":
                            c.hidden = false;
                            break;
                        case "Remove":
                            root.removeView(c.view);
                            controls.remove(c);
                            save();
                            return;
                        default:
                            resetToOriginal(c);
                            c.key = -1;
                            break;
                    }
                    apply(c);
                    restoreTouch();
                    save();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private float clamp(float dp) {
        return Math.max(24f, Math.min(260f, dp));
    }

    private interface KeyPicked {
        void picked(int code);
    }

    private void pickKey(String title, final KeyPicked cb) {
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setItems(KEY_NAMES.toArray(new String[0]), (d, which) -> cb.picked(KEY_CODES.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Faint grid shown while editing. */
    private static final class GridOverlay extends View {
        private final Paint paint = new Paint();
        private final float cell;

        GridOverlay(Context context, float cell) {
            super(context);
            this.cell = cell;
            paint.setColor(Color.argb(40, 255, 255, 255));
            paint.setStrokeWidth(1f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            for (float x = 0; x < getWidth(); x += cell) canvas.drawLine(x, 0, x, getHeight(), paint);
            for (float y = 0; y < getHeight(); y += cell) canvas.drawLine(0, y, getWidth(), y, paint);
        }
    }
}
