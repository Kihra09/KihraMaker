package com.kihra.maker.engine;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must be set before SDL starts its native thread.
        String path = getIntent().getStringExtra(EngineLauncher.EXTRA_PATH);
        GAME_PATH = path == null ? "" : path;
        Log.i(TAG, "Game path: " + GAME_PATH);

        super.onCreate(savedInstanceState);

        mMainHandler = new Handler(getMainLooper());
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        int opacity = Math.round(getIntent().getFloatExtra(EngineLauncher.EXTRA_CONTROLS_OPACITY, 0.6f) * 100f);
        GamepadConfig cfg = new GamepadConfig();
        cfg.opacity = Math.max(10, Math.min(100, opacity));

        mGamepadInvisible = isAndroidTV() || isChromebook();
        mGamepad.init(cfg, mGamepadInvisible);
        mGamepad.setOnKeyDownListener(SDLActivity::onNativeKeyDown);
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
