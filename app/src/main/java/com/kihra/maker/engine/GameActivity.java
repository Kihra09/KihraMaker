package com.kihra.maker.engine;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.HandlerThread;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.content.res.ColorStateList;
import com.kihra.maker.R;
import com.kihra.maker.data.PlayLog;
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
        mHaptics = getIntent().getBooleanExtra(EngineLauncher.EXTRA_HAPTICS, true);

        int opacity = Math.round(getIntent().getFloatExtra(EngineLauncher.EXTRA_CONTROLS_OPACITY, 0.6f) * 100f);
        GamepadConfig cfg = new GamepadConfig();
        cfg.opacity = Math.max(10, Math.min(100, opacity));
        cfg.scale = Math.max(50, Math.min(150, getIntent().getIntExtra(EngineLauncher.EXTRA_CONTROLS_SCALE, 100)));
        cfg.diagonalMovement = getIntent().getBooleanExtra(EngineLauncher.EXTRA_DIAGONAL, false);

        mGamepadInvisible = isAndroidTV() || isChromebook();
        mGamepad.init(cfg, mGamepadInvisible);
        mGamepad.setOnKeyDownListener(key -> {
            if (mHaptics) tick();
            SDLActivity.onNativeKeyDown(key);
        });
        mGamepad.setOnKeyUpListener(SDLActivity::onNativeKeyUp);
        if (mLayout != null) {
            mGamepad.attachTo(this, mLayout);
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
            if (getIntent().getBooleanExtra(EngineLauncher.EXTRA_SHOT_BUTTON, true)) {
                addScreenshotButton();
            }
        }
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

    private void addScreenshotButton() {
        ImageButton b = new ImageButton(this);
        b.setImageResource(R.drawable.ic_camera);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        b.setAlpha(0.35f);
        b.setContentDescription("Screenshot");
        int size = Math.round(44 * getResources().getDisplayMetrics().density);
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(size, size);
        lp.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lp.addRule(RelativeLayout.CENTER_HORIZONTAL);
        lp.topMargin = Math.round(8 * getResources().getDisplayMetrics().density);
        b.setLayoutParams(lp);
        b.setOnClickListener(v -> takeScreenshot());
        mLayout.addView(b);
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
        flushPlayTime();
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
        if (mGamepad.processGamepadEvent(evt)) return true;
        return super.dispatchKeyEvent(evt);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent evt) {
        if (mGamepadInvisible) {
            mGamepad.showView();
            mGamepadInvisible = false;
        }
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
