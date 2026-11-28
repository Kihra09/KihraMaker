package com.kihra.maker.engine;

import android.app.Activity;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** File access for assets/kihra_nw.js. Paths are virtual: /game/... is the game's web folder; nothing outside it is reachable. */
public class NwBridge {
    private final File root;
    private final Activity activity;

    public NwBridge(Activity activity, File root) {
        this.activity = activity;
        this.root = root;
    }

    private File resolve(String virtual) {
        try {
            String p = virtual == null ? "" : virtual;
            if (p.startsWith("/game")) p = p.substring(5);
            File f = new File(root, p).getCanonicalFile();
            return f.getPath().startsWith(root.getCanonicalPath()) ? f : null;
        } catch (Exception e) {
            return null;
        }
    }

    @JavascriptInterface
    public boolean exists(String p) {
        File f = resolve(p);
        return f != null && f.exists();
    }

    @JavascriptInterface
    public boolean isDir(String p) {
        File f = resolve(p);
        return f != null && f.isDirectory();
    }

    @JavascriptInterface
    public String size(String p) {
        File f = resolve(p);
        return String.valueOf(f == null ? 0 : f.length());
    }

    private byte[] bytes(String p) throws Exception {
        File f = resolve(p);
        if (f == null || !f.isFile()) throw new java.io.FileNotFoundException(p);
        try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    @JavascriptInterface
    public String readText(String p) {
        try {
            return new String(bytes(p), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public String readBase64(String p) {
        try {
            return Base64.encodeToString(bytes(p), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private boolean write(String p, byte[] data) {
        File f = resolve(p);
        if (f == null) return false;
        try {
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(data);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @JavascriptInterface
    public boolean writeText(String p, String text) {
        return write(p, text.getBytes(StandardCharsets.UTF_8));
    }

    @JavascriptInterface
    public boolean writeBase64(String p, String b64) {
        return write(p, Base64.decode(b64, Base64.DEFAULT));
    }

    @JavascriptInterface
    public boolean mkdirs(String p) {
        File f = resolve(p);
        return f != null && (f.isDirectory() || f.mkdirs());
    }

    @JavascriptInterface
    public String list(String p) {
        File f = resolve(p);
        JSONArray a = new JSONArray();
        String[] names = f == null ? null : f.list();
        if (names != null) for (String n : names) a.put(n);
        return a.toString();
    }

    @JavascriptInterface
    public boolean remove(String p) {
        File f = resolve(p);
        return f != null && f.delete();
    }

    @JavascriptInterface
    public void exit() {
        activity.runOnUiThread(activity::finish);
    }
}
