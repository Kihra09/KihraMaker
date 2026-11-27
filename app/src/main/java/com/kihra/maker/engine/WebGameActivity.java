package com.kihra.maker.engine;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.kihra.maker.data.PlayLog;
import com.kihra.maker.data.SessionLog;
import com.kihra.maker.data.Shots;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Plays RPG Maker MV and MZ games (HTML5) in a WebView. The game folder is served through
 * shouldInterceptRequest from a private per-game address, so no network permission is needed and
 * every game keeps its own localStorage (saves).
 */
public class WebGameActivity extends Activity {
    private static final String TAG = "KihraWeb";

    private WebView web;
    private FrameLayout root;
    private File webRoot;
    private String host;
    private String gameId = "";
    private boolean haptics = true;
    private long segmentStart = 0L;
    private Vibrator vibrator;

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        String path = getIntent().getStringExtra(EngineLauncher.EXTRA_PATH);
        gameId = String.valueOf(getIntent().getStringExtra(EngineLauncher.EXTRA_GAME_ID));
        haptics = getIntent().getBooleanExtra(EngineLauncher.EXTRA_HAPTICS, true);
        SessionLog.gameStarted(getApplicationContext());

        File project = new File(path == null ? "" : path);
        File www = new File(project, "www");
        webRoot = new File(www, "index.html").isFile() ? www : project;
        host = "g" + Shots.INSTANCE.key(gameId) + ".kihra.local";

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        web.setBackgroundColor(Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                Log.w(TAG, m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serve(request);
            }
        });

        addControls();
        web.loadUrl("https://" + host + "/index.html");
    }

    // ---- serving the game folder ----

    private static final Map<String, String> MIME = new HashMap<>();
    static {
        String[][] m = {
                {"html", "text/html"}, {"htm", "text/html"}, {"js", "application/javascript"},
                {"mjs", "application/javascript"}, {"css", "text/css"}, {"json", "application/json"},
                {"png", "image/png"}, {"jpg", "image/jpeg"}, {"jpeg", "image/jpeg"}, {"gif", "image/gif"},
                {"webp", "image/webp"}, {"svg", "image/svg+xml"}, {"ico", "image/x-icon"},
                {"ogg", "audio/ogg"}, {"m4a", "audio/mp4"}, {"mp3", "audio/mpeg"}, {"wav", "audio/wav"},
                {"webm", "video/webm"}, {"mp4", "video/mp4"}, {"ttf", "font/ttf"}, {"otf", "font/otf"},
                {"woff", "font/woff"}, {"woff2", "font/woff2"}, {"txt", "text/plain"}, {"xml", "text/xml"},
        };
        for (String[] e : m) MIME.put(e[0], e[1]);
    }

    private WebResourceResponse serve(WebResourceRequest req) {
        Map<String, String> cors = new HashMap<>();
        cors.put("Access-Control-Allow-Origin", "*");
        if (!host.equals(req.getUrl().getHost())) {
            return new WebResourceResponse("text/plain", "utf-8", 404, "Blocked", cors, new ByteArrayInputStream(new byte[0]));
        }
        try {
            String p = req.getUrl().getPath();
            if (p == null || p.equals("/") || p.isEmpty()) p = "/index.html";
            File f = new File(webRoot, Uri_decode(p)).getCanonicalFile();
            if (!f.getPath().startsWith(webRoot.getCanonicalPath()) || !f.isFile()) {
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", cors, new ByteArrayInputStream(new byte[0]));
            }
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
            cors.put("Cache-Control", "no-cache");
            return new WebResourceResponse(mime, null, 200, "OK", cors, new FileInputStream(f));
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 500, "Error", cors, new ByteArrayInputStream(new byte[0]));
        }
    }

    private static String Uri_decode(String s) {
        return android.net.Uri.decode(s);
    }

    // ---- on-screen controls ----

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void key(int code, boolean down) {
        if (web == null) return;
        web.dispatchKeyEvent(new KeyEvent(down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code));
    }

    private void tick() {
        if (!haptics || vibrator == null) return;
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(12, 80));
        else vibrator.vibrate(12);
    }

    private View keyButton(String label, final int code) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextColor(Color.argb(210, 255, 255, 255));
        b.setGravity(Gravity.CENTER);
        b.setTextSize(13);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.argb(46, 255, 255, 255));
        bg.setStroke(dp(2), Color.argb(184, 255, 255, 255));
        b.setBackground(bg);
        b.setOnTouchListener((v, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                tick();
                key(code, true);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                key(code, false);
            }
            return true;
        });
        return b;
    }

    private void addControls() {
        float opacity = Math.max(0.2f, Math.min(1f, getIntent().getFloatExtra(EngineLauncher.EXTRA_CONTROLS_OPACITY, 0.85f)));
        int pct = Math.round(opacity * 100);

        JoystickView stick = new JoystickView(this, getIntent().getBooleanExtra(EngineLauncher.EXTRA_DIAGONAL, false),
                k -> { tick(); key(k, true); }, k -> key(k, false));
        stick.setControlAlpha(pct);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(150), dp(150), Gravity.BOTTOM | Gravity.START);
        sp.leftMargin = dp(24);
        sp.bottomMargin = dp(24);
        root.addView(stick, sp);

        int[][] pos = {{0, 0}, {-70, 0}, {0, 70}}; // OK, Cancel, Dash (right/bottom margins in dp)
        String[] labels = {"OK", "Esc", "Dash"};
        int[] codes = {KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_SHIFT_LEFT};
        int[][] margins = {{24, 80}, {96, 40}, {24, 150}};
        for (int i = 0; i < 3; i++) {
            View b = keyButton(labels[i], codes[i]);
            b.setAlpha(opacity);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.END);
            lp.rightMargin = dp(margins[i][0]);
            lp.bottomMargin = dp(margins[i][1]);
            root.addView(b, lp);
        }

        android.widget.LinearLayout bar = new android.widget.LinearLayout(this);
        if (getIntent().getBooleanExtra(EngineLauncher.EXTRA_SHOT_BUTTON, true)) {
            bar.addView(toolButton(com.kihra.maker.R.drawable.ic_camera, "Screenshot", v -> screenshot()));
        }
        bar.addView(toolButton(android.R.drawable.ic_menu_close_clear_cancel, "Close game", v -> confirmClose()));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bp.topMargin = dp(6);
        root.addView(bar, bp);
    }

    private ImageButton toolButton(int icon, String desc, View.OnClickListener click) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(icon);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setImageTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        b.setAlpha(0.35f);
        b.setContentDescription(desc);
        b.setLayoutParams(new android.widget.LinearLayout.LayoutParams(dp(44), dp(44)));
        b.setOnClickListener(click);
        return b;
    }

    private void confirmClose() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Close the game?")
                .setMessage("Progress that wasn't saved in the game is lost.")
                .setPositiveButton("Close", (d, w) -> finish())
                .setNegativeButton("Keep playing", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        key(KeyEvent.KEYCODE_ESCAPE, true);
        key(KeyEvent.KEYCODE_ESCAPE, false);
    }

    private void screenshot() {
        if (root.getWidth() <= 0) return;
        final Bitmap bmp = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        final HandlerThread ht = new HandlerThread("kihra-shot");
        ht.start();
        try {
            int[] loc = new int[2];
            root.getLocationInWindow(loc);
            android.graphics.Rect r = new android.graphics.Rect(loc[0], loc[1], loc[0] + root.getWidth(), loc[1] + root.getHeight());
            PixelCopy.request(getWindow(), r, bmp, result -> {
                if (result == PixelCopy.SUCCESS) {
                    try {
                        File out = Shots.newFile(getApplicationContext(), gameId);
                        try (FileOutputStream fos = new FileOutputStream(out)) {
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        }
                        runOnUiThread(() -> Toast.makeText(this, "Screenshot saved to the gallery", Toast.LENGTH_SHORT).show());
                    } catch (Exception e) {
                        Log.w(TAG, "Screenshot failed", e);
                    }
                }
                ht.quitSafely();
            }, new Handler(ht.getLooper()));
        } catch (Exception e) {
            Log.w(TAG, "Screenshot failed", e);
            ht.quitSafely();
        }
    }

    // ---- immersive mode and play time ----

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        segmentStart = System.currentTimeMillis();
        if (web != null) web.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
        flush();
    }

    private void flush() {
        if (segmentStart > 0L) {
            PlayLog.append(getApplicationContext(), gameId, segmentStart, System.currentTimeMillis());
            segmentStart = 0L;
        }
    }

    @Override
    protected void onDestroy() {
        flush();
        SessionLog.gameEnded(getApplicationContext());
        if (web != null) {
            ((ViewGroup) web.getParent()).removeView(web);
            web.destroy();
        }
        super.onDestroy();
        System.exit(0);
    }
}
