package com.kihra.maker.engine;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import java.io.FileWriter;
import android.graphics.Color;
import android.os.HandlerThread;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.content.res.ColorStateList;
import com.hatkid.mkxpz.gamepad.GamepadButton;
import com.kihra.maker.R;
import com.kihra.maker.data.PlayLog;
import com.kihra.maker.data.SessionLog;
import com.kihra.maker.data.Shots;
import java.io.File;
import java.io.FileOutputStream;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hatkid.mkxpz.gamepad.Gamepad;
import com.hatkid.mkxpz.gamepad.GamepadConfig;

import org.libsdl.app.SDLActivity;

import java.util.Locale;

/**
 * Hosts the native mkxp-z engine (RGSS1/2/3 + Ruby, via SDL2).
 *
 * The native side finds this class through JNI, so the static members below
 * (GAME_PATH and the helper methods) must keep their exact names and signatures.
 * Based on the Android port of mkxp-z by thehatkid (GPL v2+).
 */
public class GameActivity extends SDLActivity {
    private static final String TAG = "KihraMaker[Game]";

    /** Read by native code (main.cpp): the engine switches into this folder. */
    private static String GAME_PATH = "";

    private static Handler mMainHandler;
    private static Vibrator mVibrator;
    private static TextView tvFps;

    private final Gamepad mGamepad = new Gamepad();
    private boolean mGamepadInvisible = false;
    private boolean mStarted = false;
    private String mGameId = "";
    private boolean mHaptics = true;
    private long mSegmentStart = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must be set before SDL starts its native thread.
        String path = getIntent().getStringExtra(EngineLauncher.EXTRA_PATH);
        GAME_PATH = path == null ? "" : path;
        Log.i(TAG, "Game path: " + GAME_PATH);

        super.onCreate(savedInstanceState);

        mMainHandler = new Handler(getMainLooper());
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mGameId = getIntent().getStringExtra(EngineLauncher.EXTRA_GAME_ID);
        if (mGameId == null) mGameId = "";
        SessionLog.gameStarted(getApplicationContext());
        parsePadMap(getIntent().getStringExtra(EngineLauncher.EXTRA_PADMAP));
        mHaptics = getIntent().getBooleanExtra(EngineLauncher.EXTRA_HAPTICS, true);

        int opacity = Math.round(getIntent().getFloatExtra(EngineLauncher.EXTRA_CONTROLS_OPACITY, 0.6f) * 100f);
        GamepadConfig cfg = new GamepadConfig();
        cfg.opacity = Math.max(10, Math.min(100, opacity));
        cfg.scale = Math.max(50, Math.min(150, getIntent().getIntExtra(EngineLauncher.EXTRA_CONTROLS_SCALE, 100)));
        cfg.diagonalMovement = getIntent().getBooleanExtra(EngineLauncher.EXTRA_DIAGONAL, false);

        mGestures = new GestureHandler(mLayout,
                getIntent().getStringExtra(EngineLauncher.EXTRA_GESTURES),
                getResources().getDisplayMetrics().density,
                () -> { if (mHaptics) tick(); },
                SDLActivity::onNativeKeyDown, SDLActivity::onNativeKeyUp);
        mGamepadInvisible = isAndroidTV() || isChromebook();
        mGamepad.init(cfg, mGamepadInvisible);
        final GamepadButton.OnKeyDownListener downL = key -> {
            if (mHaptics) tick();
            SDLActivity.onNativeKeyDown(key);
        };
        final GamepadButton.OnKeyUpListener upL = SDLActivity::onNativeKeyUp;
        mGamepad.setOnKeyDownListener(downL::onKeyDown);
        mGamepad.setOnKeyUpListener(upL::onKeyUp);
        if (mLayout != null) {
            mGamepad.attachTo(this, mLayout);
            File base = getExternalFilesDir(null);
            if (base == null) base = getFilesDir();
            mControls = new ControlsEditor(this, mLayout,
                    new File(base, "controls/" + layoutName() + ".json"),
                    new File(base, "controls/default.json"),
                    getIntent().getBooleanExtra(EngineLauncher.EXTRA_JOYSTICK, false),
                    cfg.diagonalMovement, cfg.opacity, downL, upL, this::showMenu);
            mControls.install();
        }

        tvFps = new TextView(this);
        tvFps.setTextSize(8 * ((float) getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT));
        tvFps.setTextColor(Color.argb(96, 255, 255, 255));
        tvFps.setVisibility(View.GONE);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(16, 16, 0, 0);
        tvFps.setLayoutParams(params);
        if (mLayout != null) {
            mLayout.addView(tvFps);
            setupWallpaper();
            setupRenderScale();
            mAch = new AchievementPopup(this, mLayout, () -> {
                if (mSegmentStart > 0) {
                    long now = System.currentTimeMillis();
                    PlayLog.append(getApplicationContext(), mGameId, mSegmentStart, now);
                    mSegmentStart = now;
                }
            });
            mAch.start();
        }
        showLoadingInfo();
        watchLoading(0);
    }

    /**
     * Internal resolution: the game is drawn into a surface of (screen size x scale) pixels and the
     * system stretches it to the screen. Below 1x it is blurrier and does a little less GPU work;
     * above 1x it is only an oversampled picture. It does not speed up the game's own logic.
     */
    private int mLastW = 0, mLastH = 0;

    private void setupRenderScale() {
        final float scale = Math.max(0.5f, Math.min(2f, getIntent().getFloatExtra(EngineLauncher.EXTRA_RENDER_SCALE, 1f)));
        if (scale == 1f || mSurface == null) return;
        mSurface.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l, h = b - t;
            if (w <= 0 || h <= 0 || (w == mLastW && h == mLastH)) return;
            mLastW = w;
            mLastH = h;
            mSurface.getHolder().setFixedSize(Math.max(64, Math.round(w * scale)), Math.max(64, Math.round(h * scale)));
        });
    }

    // ---- Wallpaper: the game screen is shrunk to the game's aspect ratio and a picture is drawn
    // behind it, so there are no black bars (the engine only draws inside its own surface). ----

    private void setupWallpaper() {
        String wallpaper = getIntent().getStringExtra(EngineLauncher.EXTRA_WALLPAPER);
        final boolean fill = getIntent().getBooleanExtra(EngineLauncher.EXTRA_FILL, false);
        final float aspect = getIntent().getFloatExtra(EngineLauncher.EXTRA_ASPECT, 4f / 3f);
        if (wallpaper == null || wallpaper.isEmpty() || fill || mSurface == null) return;
        Bitmap bg = BitmapFactory.decodeFile(wallpaper);
        if (bg == null) return;
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bg);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mLayout.addView(iv, 0, new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mLayout.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fitSurface(r - l, b - t, aspect));
    }

    private void fitSurface(int width, int height, float aspect) {
        if (width <= 0 || height <= 0 || mSurface == null) return;
        int w;
        int h;
        if ((float) width / height > aspect) {
            h = height;
            w = Math.round(height * aspect);
        } else {
            w = width;
            h = Math.round(width / aspect);
        }
        LayoutParams cur = mSurface.getLayoutParams();
        if (cur != null && cur.width == w && cur.height == h) return;
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(w, h);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        mSurface.setLayoutParams(lp);
    }

    /** Short tap feedback for the on-screen buttons. */
    private void tick() {
        try {
            if (mVibrator == null || !mVibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mVibrator.vibrate(VibrationEffect.createOneShot(12, 80));
            } else {
                mVibrator.vibrate(12);
            }
        } catch (Exception ignored) {
        }
    }

    private ControlsEditor mControls;
    private GestureHandler mGestures;
    private final java.util.HashMap<Integer, Integer> mPadMap = new java.util.HashMap<>();

    private void parsePadMap(String text) {
        if (text == null || text.isEmpty()) return;
        for (String pair : text.split(",")) {
            String[] p = pair.split(":");
            if (p.length != 2) continue;
            try {
                mPadMap.put(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private ImageButton toolButton(int icon, String description, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        b.setAlpha(0.35f);
        b.setContentDescription(description);
        int size = Math.round(44 * getResources().getDisplayMetrics().density);
        b.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        b.setOnClickListener(click);
        return b;
    }

    // ---- Commands for the game (fast forward, cheats): the preload script reads kihra_cmd.txt ----

    private void sendCommand(String line) {
        try (FileWriter w = new FileWriter(new File(GAME_PATH, "kihra_cmd.txt"), true)) {
            w.write(line + "\n");
        } catch (Exception e) {
            Log.w(TAG, "Can't send command", e);
            Toast.makeText(this, "Can't reach the game folder", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean mFast = false;

    /** Switches between normal speed and the fast-forward speed chosen in the game's settings. */
    private void toggleFastForward() {
        float chosen = getIntent().getFloatExtra(EngineLauncher.EXTRA_FF_SPEED, 2f);
        mFast = !mFast;
        float x = mFast ? chosen : 1f;
        sendCommand("speed " + x);
        Toast.makeText(this, mFast ? "Fast forward " + (int) chosen + "x" : "Normal speed", Toast.LENGTH_SHORT).show();
    }

    /** The one menu button: everything that used to be a row of small buttons. */
    private void showMenu() {
        final String[] items = {
                "Screenshot",
                mFast ? "Fast forward: turn off" : "Fast forward: on",
                "Cheats",
                "Quick save",
                "Quick load",
                "Edit layout",
                "Exit game",
        };
        new AlertDialog.Builder(this)
                .setTitle("Menu")
                .setItems(items, (d, i) -> {
                    switch (i) {
                        case 0: takeScreenshot(); break;
                        case 1: toggleFastForward(); break;
                        case 2: showCheats(); break;
                        case 3: quick("qsave"); break;
                        case 4: quick("qload"); break;
                        case 5: if (mControls != null) mControls.toggle(); break;
                        case 6: confirmExit(); break;
                        default: break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("Exit the game?")
                .setMessage("Progress that wasn't saved in the game is lost.")
                .setPositiveButton("Exit", (d, w) -> finish())
                .setNegativeButton("Keep playing", null)
                .show();
    }

    private AchievementPopup mAch;

    /** If the game hasn't drawn its first frame after a while, say so and offer a way out. */
    private android.widget.TextView mLoadInfo;
    private final long mLoadStart = System.currentTimeMillis();

    /** Small live status while the game loads, so a slow start is visibly different from a stuck one. */
    private void showLoadingInfo() {
        if (mLayout == null) return;
        mLoadInfo = new android.widget.TextView(this);
        mLoadInfo.setTextColor(0xFFFFFFFF);
        mLoadInfo.setBackgroundColor(0x99000000);
        mLoadInfo.setTextSize(12f);
        mLoadInfo.setPadding(24, 12, 24, 12);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        mLayout.addView(mLoadInfo, lp);
        tickLoadingInfo();
    }

    private void tickLoadingInfo() {
        mMainHandler.postDelayed(() -> {
            if (mLoadInfo == null || isFinishing()) return;
            String last = "";
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(new File(GAME_PATH, "kihra_boot.txt")))) {
                String line;
                while ((line = r.readLine()) != null) last = line;
            } catch (Exception ignored) {
            }
            if (last.contains("first frame")) {
                ((android.view.ViewGroup) mLoadInfo.getParent()).removeView(mLoadInfo);
                mLoadInfo = null;
                return;
            }
            long sec = (System.currentTimeMillis() - mLoadStart) / 1000;
            int tab = last.indexOf('\t');
            String step = tab >= 0 ? last.substring(tab + 1) : "starting the engine / reading scripts";
            mLoadInfo.setText("Loading " + sec + "s - " + step);
            tickLoadingInfo();
        }, 1000);
    }

    private void watchLoading(final int round) {
        mMainHandler.postDelayed(() -> {
            if (isFinishing()) return;
            String marks = "";
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(new File(GAME_PATH, "kihra_boot.txt")))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
                marks = sb.toString();
            } catch (Exception ignored) {
            }
            if (marks.contains("first frame")) return;
            String where = marks.isEmpty()
                    ? "The engine hasn't started the game's scripts yet (large archives and big games can take a minute)."
                    : "The game's scripts started but it hasn't drawn its first screen yet.";
            new AlertDialog.Builder(this)
                    .setTitle("Still loading...")
                    .setMessage(where + "\n\nYou can keep waiting, or exit and use \"Retry in safe mode\" in the app.")
                    .setPositiveButton("Keep waiting", (d, w) -> watchLoading(round + 1))
                    .setNegativeButton("Exit game", (d, w) -> finish())
                    .setCancelable(false)
                    .show();
        }, round == 0 ? 40_000 : 60_000);
    }

    private void quick(final String cmd) {
        File ack = new File(GAME_PATH, "kihra_ack.txt");
        ack.delete();
        sendCommand(cmd);
        pollAck(ack, 0);
    }

    private void pollAck(final File ack, final int tries) {
        mMainHandler.postDelayed(() -> {
            if (!ack.isFile()) {
                if (tries < 12) pollAck(ack, tries + 1);
                else Toast.makeText(this, "No answer from the game. Is it on a map?", Toast.LENGTH_SHORT).show();
                return;
            }
            String text = "";
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(ack))) {
                text = r.readLine();
            } catch (Exception ignored) {
            }
            ack.delete();
            String[] p = (text == null ? "" : text).split("\t");
            String name = p.length > 0 && p[0].equals("qload") ? "Quick load" : "Quick save";
            String res = p.length > 1 ? p[1] : "failed";
            String msg;
            switch (res) {
                case "ok": msg = name + " done"; break;
                case "notmap": msg = "Quick save only works on the map"; break;
                case "nofile": msg = "No quick save yet"; break;
                case "battle": msg = "Can't quick load during a battle"; break;
                case "unsupported": msg = "Quick save works in RPG Maker VX Ace games only for now"; break;
                default: msg = name + " failed";
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }, 250);
    }

    private interface PromptDone {
        void done(String[] values);
    }

    private void prompt(String title, String[] hints, PromptDone cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText[] fields = new EditText[hints.length];
        for (int i = 0; i < hints.length; i++) {
            fields[i] = new EditText(this);
            fields[i].setHint(hints[i]);
            fields[i].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            box.addView(fields[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("OK", (d, w) -> {
                    String[] out = new String[fields.length];
                    for (int i = 0; i < fields.length; i++) {
                        out[i] = fields[i].getText().toString().trim();
                        if (out[i].isEmpty()) return;
                    }
                    cb.done(out);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String layoutName() {
        String n = getIntent().getStringExtra(EngineLauncher.EXTRA_LAYOUT);
        return n == null || n.isEmpty() ? Shots.INSTANCE.key(mGameId) : n;
    }

    private void showCheats() {
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        new CheatsDialog(this, new File(GAME_PATH),
                new File(base, "controls/" + Shots.INSTANCE.key(mGameId) + ".pins"),
                this::sendCommand).show();
    }

    /** Copies what the game is drawing (without the touch buttons) and saves it as a PNG. */
    private void takeScreenshot() {
        final SurfaceView sv = mSurface;
        if (sv == null || sv.getWidth() <= 0 || sv.getHeight() <= 0) return;
        final Bitmap bmp = Bitmap.createBitmap(sv.getWidth(), sv.getHeight(), Bitmap.Config.ARGB_8888);
        final HandlerThread ht = new HandlerThread("kihra-shot");
        ht.start();
        try {
            PixelCopy.request(sv, bmp, result -> {
                if (result == PixelCopy.SUCCESS) {
                    try {
                        File out = Shots.newFile(getApplicationContext(), mGameId);
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        }
                        runOnUiThread(() -> Toast.makeText(this, "Screenshot saved to the gallery", Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        Log.w(TAG, "Screenshot failed", e);
                    }
                } else {
                    Log.w(TAG, "PixelCopy failed: " + result);
                }
                ht.quitSafely();
            }, new Handler(ht.getLooper()));
        } catch (Exception e) {
            Log.w(TAG, "Screenshot failed", e);
            ht.quitSafely();
        }
    }

    // ---- Play time: one log line every time the game leaves the foreground ----

    @Override
    protected void onResume() {
        super.onResume();
        mSegmentStart = System.currentTimeMillis();
    }

    @Override
    protected void onPause() {
        super.onPause();
        flushPlayTime();
    }

    private void flushPlayTime() {
        if (mSegmentStart > 0) {
            PlayLog.append(getApplicationContext(), mGameId, mSegmentStart, System.currentTimeMillis());
            mSegmentStart = 0L;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // SDLActivity no longer resumes the native thread itself on multi-window devices.
        mStarted = true;
        if (mHasMultiWindow) {
            resumeNativeThread();
        }
    }

    @Override
    protected void onDestroy() {
        if (mAch != null) mAch.stop();
        flushPlayTime();
        SessionLog.gameEnded(getApplicationContext());
        super.onDestroy();
        // Ruby cannot be re-initialised inside a living process. The game runs in its own
        // process (see AndroidManifest), so this only ends the game, not the launcher.
        System.exit(0);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent evt) {
        int code = evt.getKeyCode();
        if (code != KeyEvent.KEYCODE_BACK
                && code != KeyEvent.KEYCODE_VOLUME_UP
                && code != KeyEvent.KEYCODE_VOLUME_DOWN
                && code != KeyEvent.KEYCODE_VOLUME_MUTE
                && code != KeyEvent.KEYCODE_HEADSETHOOK) {
            // A physical key or controller was used: hide the touch pad.
            if (!mGamepadInvisible) {
                mGamepad.hideView();
                mGamepadInvisible = true;
            }
        }
        // Remapped controller buttons: press the chosen keyboard key instead.
        Integer mapped = mPadMap.get(code);
        if (mapped != null && (evt.getSource() & (InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK)) != 0) {
            if (evt.getAction() == KeyEvent.ACTION_DOWN && evt.getRepeatCount() == 0) {
                SDLActivity.onNativeKeyDown(mapped);
            } else if (evt.getAction() == KeyEvent.ACTION_UP) {
                SDLActivity.onNativeKeyUp(mapped);
            }
            return true;
        }
        if (mGamepad.processGamepadEvent(evt)) return true;
        return super.dispatchKeyEvent(evt);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent evt) {
        if (mGamepadInvisible) {
            mGamepad.showView();
            mGamepadInvisible = false;
        }
        if (mGestures != null && !(mControls != null && mControls.isEditing())) mGestures.onTouch(evt);
        return super.dispatchTouchEvent(evt);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent evt) {
        if (mGamepad.processDPadEvent(evt)) return true;
        return super.onGenericMotionEvent(evt);
    }

    @Override
    protected String[] getArguments() {
        return new String[] {};
    }

    // ---- Called from native code (names and signatures must not change) ----

    @SuppressLint("SetTextI18n")
    @SuppressWarnings("unused")
    private static void updateFPSText(int num) {
        mMainHandler.post(() -> tvFps.setText(num + " FPS"));
    }

    @SuppressWarnings("unused")
    private static void setFPSVisibility(boolean visible) {
        mMainHandler.post(() -> tvFps.setVisibility(visible ? View.VISIBLE : View.INVISIBLE));
    }

    @SuppressWarnings("unused")
    private static String getSystemLanguage() {
        return Locale.getDefault().toString();
    }

    @SuppressWarnings("unused")
    private static boolean hasVibrator() {
        return mVibrator.hasVibrator();
    }

    @SuppressWarnings("unused")
    private static void vibrate(int duration) {
        if (duration >= 10000) duration = 10000;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.EFFECT_HEAVY_CLICK));
        } else {
            mVibrator.vibrate(duration);
        }
    }

    @SuppressWarnings("unused")
    private static void vibrateStop() {
        mVibrator.cancel();
    }

    @SuppressWarnings("unused")
    private static boolean inMultiWindow(Activity activity) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode();
    }
}
