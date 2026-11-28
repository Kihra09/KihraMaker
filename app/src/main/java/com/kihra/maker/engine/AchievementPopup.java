package com.kihra.maker.engine;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.kihra.maker.data.AchievementWatcher;

import java.util.ArrayDeque;

/**
 * "Achievement unlocked" card in the top-left corner of the game screen, like RetroAchievements.
 * Checks for new achievements every minute while playing (the play time so far is saved first).
 */
final class AchievementPopup {
    private final Activity activity;
    private final ViewGroup root;
    private final Runnable checkpoint;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private boolean showing = false;
    private boolean running = false;
    private LinearLayout card;

    AchievementPopup(Activity activity, ViewGroup root, Runnable checkpoint) {
        this.activity = activity;
        this.root = root;
        this.checkpoint = checkpoint;
    }

    private int dp(float v) {
        return Math.round(v * activity.getResources().getDisplayMetrics().density);
    }

    void start() {
        running = true;
        ui.postDelayed(tick, 20_000);
    }

    void stop() {
        running = false;
        ui.removeCallbacksAndMessages(null);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (checkpoint != null) checkpoint.run();
            new Thread(() -> {
                String[] fresh = AchievementWatcher.check(activity.getApplicationContext());
                if (fresh.length > 0) {
                    ui.post(() -> {
                        for (String t : fresh) queue.add(t);
                        next();
                    });
                }
            }, "kihra-ach").start();
            ui.postDelayed(this, 60_000);
        }
    };

    private void next() {
        if (showing || queue.isEmpty()) return;
        showing = true;
        String title = queue.poll();

        card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(16), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(235, 24, 24, 36));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.argb(200, 255, 200, 80));
        card.setBackground(bg);
        card.setElevation(dp(12));

        TextView head = new TextView(activity);
        head.setText("🏆  Achievement unlocked");
        head.setTextColor(Color.rgb(255, 205, 90));
        head.setTextSize(11);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(head);
        TextView name = new TextView(activity);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(15);
        card.addView(name);

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(12);
        lp.topMargin = dp(12);
        card.setLayoutParams(lp);
        card.setAlpha(0f);
        card.setTranslationX(-dp(40));
        root.addView(card);
        card.animate().alpha(1f).translationX(0).setDuration(280).start();
        ui.postDelayed(() -> {
            final View c = card;
            if (c == null) return;
            c.animate().alpha(0f).translationX(-dp(40)).setDuration(300).withEndAction(() -> {
                root.removeView(c);
                showing = false;
                next();
            }).start();
        }, 4500);
    }
}
