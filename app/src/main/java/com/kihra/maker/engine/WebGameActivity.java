package com.kihra.maker.engine;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.hatkid.mkxpz.gamepad.Gamepad;
import com.hatkid.mkxpz.gamepad.GamepadButton;
import com.hatkid.mkxpz.gamepad.GamepadConfig;
import com.kihra.maker.data.PlayLog;
import com.kihra.maker.data.SessionLog;
import com.kihra.maker.data.Shots;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Plays RPG Maker MV and MZ games (HTML5) in a WebView. The game folder is served through
 * shouldInterceptRequest from a private per-game address, so no network permission is needed and
 * every game keeps its own localStorage (saves). The on-screen controls are the same ones the
 * RGSS engine uses (same layout editor, keys, gestures, menu button).
 */
public class WebGameActivity extends Activity {
    private static final String TAG = "KihraWeb";

    private WebView web;
    private RelativeLayout root;
    private File webRoot;
    private String host;
    private String gameId = "";
    private boolean haptics = true;
    private long segmentStart = 0L;
    private Vibrator vibrator;
    private ControlsEditor controls;
    private GestureHandler gestures;
    private AchievementPopup achievements;
    private boolean ready = false;
    private boolean fast = false;
    private int missing = 0;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayDeque<String> problems = new ArrayDeque<>();

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

        root = new RelativeLayout(this);
        root.setBackgroundColor(Color.BLACK);

        String wallpaper = getIntent().getStringExtra(EngineLauncher.EXTRA_WALLPAPER);
        boolean hasWallpaper = false;
        if (wallpaper != null && !wallpaper.isEmpty() && !getIntent().getBooleanExtra(EngineLauncher.EXTRA_FILL, false)) {
            Bitmap bg = BitmapFactory.decodeFile(wallpaper);
            if (bg != null) {
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(bg);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                root.addView(iv, new RelativeLayout.LayoutParams(-1, -1));
                hasWallpaper = true;
            }
        }
        final boolean wall = hasWallpaper;

        web = new WebView(this);
        root.addView(web, new RelativeLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUseWideViewPort(false);
        s.setLoadWithOverviewMode(false);
        web.setBackgroundColor(wall ? Color.TRANSPARENT : Color.BLACK);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                String line = m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")";
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.e(TAG, "console: " + line);
                    problems.add(line);
                    while (problems.size() > 8) problems.poll();
                } else {
                    Log.w(TAG, "console: " + line);
                }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serve(request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                view.evaluateJavascript(
                        "window.addEventListener('error',function(e){console.error('JS error: '+e.message+' @'+(e.filename||'')+':'+(e.lineno||0))});"
                                + "window.addEventListener('unhandledrejection',function(e){console.error('Promise error: '+(e.reason&&e.reason.message||e.reason))});",
                        null);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (wall) {
                    view.evaluateJavascript("var st=document.createElement('style');st.textContent='html,body{background:transparent!important}';document.head.appendChild(st);", null);
                }
                if (getIntent().getBooleanExtra(EngineLauncher.EXTRA_SHOW_FPS, false)) {
                    view.evaluateJavascript(FPS_JS, null);
                }
            }
        });

        setupControls();
        web.loadUrl("https://" + host + "/index.html");
        web.requestFocus();
        watchLoading(0);
    }

    private static final String FPS_JS =
            "(function(){var d=document.createElement('div');d.style.cssText='position:fixed;left:8px;top:8px;color:#fff;font:12px monospace;z-index:99999;opacity:.7;pointer-events:none;text-shadow:0 0 3px #000';"
                    + "document.body.appendChild(d);var n=0,t=performance.now();function f(){n++;var x=performance.now();if(x-t>=1000){d.textContent=Math.round(n*1000/(x-t))+' FPS';n=0;t=x}requestAnimationFrame(f)}requestAnimationFrame(f)})();";

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
            File f = new File(webRoot, android.net.Uri.decode(p)).getCanonicalFile();
            if (!f.getPath().startsWith(webRoot.getCanonicalPath()) || !f.isFile()) {
                if (missing++ < 25) Log.e(TAG, "404 " + p);
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", cors, new ByteArrayInputStream(new byte[0]));
            }
            String name = f.getName();
            int dot = name.lastIndexOf('.');
            String ext = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
            boolean text = mime.startsWith("text/") || mime.equals("application/javascript") || mime.equals("application/json");
            cors.put("Cache-Control", "no-cache");
            return new WebResourceResponse(mime, text ? "utf-8" : null, 200, "OK", cors, new FileInputStream(f));
        } catch (Exception e) {
            Log.e(TAG, "serve failed: " + e);
            return new WebResourceResponse("text/plain", "utf-8", 500, "Error", cors, new ByteArrayInputStream(new byte[0]));
        }
    }

    // ---- loading watchdog: tells the player (and the log) why a game doesn't start ----

    private void watchLoading(final int round) {
        ui.postDelayed(() -> {
            if (isFinishing() || ready) return;
            web.evaluateJavascript("(typeof SceneManager!=='undefined'&&SceneManager._scene)?'1':'0'", value -> {
                if (value != null && value.contains("1")) {
                    ready = true;
                    return;
                }
                StringBuilder msg = new StringBuilder("The game hasn't shown its first screen yet.");
                if (!problems.isEmpty()) {
                    msg.append("\n\nErrors from the game:\n");
                    for (String pr : problems) msg.append("• ").append(pr).append('\n');
                } else {
                    msg.append("\n\nThe game reported no errors, so it may just be loading a lot of files.");
                }
                if (missing > 0) msg.append("\nMissing files: ").append(missing).append(" (listed in the log)");
                Log.e(TAG, "Not ready after round " + round + ": " + msg.toString().replace('\n', ' '));
                new AlertDialog.Builder(this)
                        .setTitle("Still loading...")
                        .setMessage(msg.toString())
                        .setPositiveButton("Keep waiting", (d, w) -> watchLoading(round + 1))
                        .setNegativeButton("Exit game", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
            });
        }, round == 0 ? 30_000 : 60_000);
    }

    // ---- on-screen controls (shared with the RGSS engine) ----

    private void tick() {
        if (!haptics || vibrator == null) return;
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(12, 80));
        else vibrator.vibrate(12);
    }

    private void key(int code, boolean down) {
        if (web == null) return;
        web.dispatchKeyEvent(new KeyEvent(down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code));
    }

    private void setupControls() {
        int opacity = Math.round(getIntent().getFloatExtra(EngineLauncher.EXTRA_CONTROLS_OPACITY, 0.6f) * 100f);
        GamepadConfig cfg = new GamepadConfig();
        cfg.opacity = Math.max(10, Math.min(100, opacity));
        cfg.scale = Math.max(50, Math.min(150, getIntent().getIntExtra(EngineLauncher.EXTRA_CONTROLS_SCALE, 100)));
        cfg.diagonalMovement = getIntent().getBooleanExtra(EngineLauncher.EXTRA_DIAGONAL, false);

        Gamepad pad = new Gamepad();
        pad.init(cfg, false);
        final GamepadButton.OnKeyDownListener downL = k -> {
            tick();
            key(k, true);
        };
        final GamepadButton.OnKeyUpListener upL = k -> key(k, false);
        pad.setOnKeyDownListener(downL::onKeyDown);
        pad.setOnKeyUpListener(upL::onKeyUp);
        pad.attachTo(this, root);

        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        String layout = getIntent().getStringExtra(EngineLauncher.EXTRA_LAYOUT);
        if (layout == null || layout.isEmpty()) layout = Shots.INSTANCE.key(gameId);
        controls = new ControlsEditor(this, root,
                new File(base, "controls/" + layout + ".json"),
                new File(base, "controls/default.json"),
                getIntent().getBooleanExtra(EngineLauncher.EXTRA_JOYSTICK, false),
                cfg.diagonalMovement, cfg.opacity, downL, upL, this::showMenu);
        controls.install();

        gestures = new GestureHandler(root,
                getIntent().getStringExtra(EngineLauncher.EXTRA_GESTURES),
                getResources().getDisplayMetrics().density,
                this::tick,
                k -> key(k, true), k -> key(k, false));
        achievements = new AchievementPopup(this, root, () -> {
            if (segmentStart > 0) {
                long now = System.currentTimeMillis();
                PlayLog.append(getApplicationContext(), gameId, segmentStart, now);
                segmentStart = now;
            }
        });
        achievements.start();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (gestures != null && !(controls != null && controls.isEditing())) gestures.onTouch(ev);
        return super.dispatchTouchEvent(ev);
    }

    private void showMenu() {
        final String[] items = {
                "Screenshot",
                fast ? "Fast forward: turn off" : "Fast forward: on",
                "Edit layout",
                "Exit game",
        };
        new AlertDialog.Builder(this)
                .setTitle("Menu")
                .setItems(items, (d, i) -> {
                    switch (i) {
                        case 0: screenshot(); break;
                        case 1: toggleFast(); break;
                        case 2: if (controls != null) controls.toggle(); break;
                        case 3: confirmExit(); break;
                        default: break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    /** Runs the game's logic N times per frame (MV and MZ both use SceneManager.updateScene). */
    private void toggleFast() {
        fast = !fast;
        int n = fast ? Math.max(1, Math.min(16, Math.round(getIntent().getFloatExtra(EngineLauncher.EXTRA_FF_SPEED, 2f)))) : 1;
        web.evaluateJavascript("(function(n){var S=window.SceneManager;if(!S)return;if(!S.__kOrig)S.__kOrig=S.updateScene;"
                + "S.updateScene=function(){var f=S.__kOrig;for(var i=0;i<n;i++)f.apply(this,arguments)}})(" + n + ");", null);
        Toast.makeText(this, fast ? "Fast forward " + n + "x" : "Normal speed", Toast.LENGTH_SHORT).show();
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("Exit the game?")
                .setMessage("Progress that wasn't saved in the game is lost.")
                .setPositiveButton("Exit", (d, w) -> finish())
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
        if (achievements != null) achievements.stop();
        ui.removeCallbacksAndMessages(null);
        flush();
        SessionLog.gameEnded(getApplicationContext());
        if (web != null) {
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
        }
        super.onDestroy();
        System.exit(0);
    }
}
