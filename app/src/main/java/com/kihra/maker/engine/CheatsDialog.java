package com.kihra.maker.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Cheat menu. Besides the general cheats it shows the game's own switches, variables and items
 * (read from the game's database through the preload script) so cheats are specific to the game.
 * Long-press an entry to pin it to the top of this game's cheat list.
 */
final class CheatsDialog {
    interface Sender {
        void send(String command);
    }

    private static final class Entry {
        final char type; // S, V, I
        final int id;
        final String name;
        int value;

        Entry(char type, int id, String name, int value) {
            this.type = type;
            this.id = id;
            this.name = name;
            this.value = value;
        }

        String label() {
            String v = type == 'S' ? (value != 0 ? "ON" : "off") : String.valueOf(value);
            String kind = type == 'S' ? "Switch" : type == 'V' ? "Variable" : "Item";
            return name + "\n" + kind + " " + id + "  ·  " + v;
        }
    }

    private final Activity activity;
    private final File dbFile;
    private final File pinsFile;
    private final Sender sender;
    private final Handler handler = new Handler(Looper.getMainLooper());

    CheatsDialog(Activity activity, File gameDir, File pinsFile, Sender sender) {
        this.activity = activity;
        this.dbFile = new File(gameDir, "kihra_db.txt");
        this.pinsFile = pinsFile;
        this.sender = sender;
    }

    private List<Entry> readEntries(File f) {
        List<Entry> out = new ArrayList<>();
        if (!f.isFile()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t");
                if (p.length < 3 || p[0].isEmpty()) continue;
                try {
                    int val = p.length > 3 ? Integer.parseInt(p[3].trim()) : 0;
                    out.add(new Entry(p[0].charAt(0), Integer.parseInt(p[1]), p[2], val));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private boolean isPinned(List<Entry> pins, Entry e) {
        for (Entry p : pins) if (p.type == e.type && p.id == e.id) return true;
        return false;
    }

    private void savePins(List<Entry> pins) {
        try {
            File dir = pinsFile.getParentFile();
            if (dir != null) dir.mkdirs();
            try (FileWriter w = new FileWriter(pinsFile)) {
                for (Entry e : pins) w.write(e.type + "\t" + e.id + "\t" + e.name + "\t0\n");
            }
        } catch (Exception ignored) {
        }
    }

    // ---- main menu ----

    void show() {
        sender.send("dump");
        final List<Entry> pins = readEntries(pinsFile);
        final List<String> items = new ArrayList<>();
        for (Entry e : pins) items.add("★ " + e.name + " (" + (e.type == 'S' ? "switch" : e.type == 'V' ? "variable" : "item") + " " + e.id + ")");
        final int base = items.size();
        items.add("This game's switches, variables and items...");
        items.add("Add gold...");
        items.add("Heal the party");
        items.add("Auto-heal (on/off)");
        items.add("Walk through walls (on/off)");
        items.add("No random battles (on/off, VX/Ace)");
        items.add("Party +1 level (VX Ace)");
        new AlertDialog.Builder(activity)
                .setTitle("Cheats")
                .setItems(items.toArray(new String[0]), (d, i) -> {
                    if (i < base) {
                        apply(pins.get(i), pins, null);
                        return;
                    }
                    switch (i - base) {
                        case 0: browse(); break;
                        case 1: prompt("Add gold", new String[]{"Amount"}, v -> sender.send("gold " + v[0])); break;
                        case 2: sender.send("heal"); break;
                        case 3: sender.send("god"); break;
                        case 4: sender.send("through"); break;
                        case 5: sender.send("noenc"); break;
                        case 6: sender.send("level"); break;
                        default: break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void apply(final Entry e, final List<Entry> pins, final Runnable after) {
        if (e.type == 'S') {
            sender.send("switch " + e.id + " " + (e.value != 0 ? 0 : 1));
            e.value = e.value != 0 ? 0 : 1;
            Toast.makeText(activity, e.name + (e.value != 0 ? ": ON" : ": off"), Toast.LENGTH_SHORT).show();
            if (after != null) after.run();
        } else if (e.type == 'V') {
            prompt(e.name + " (now " + e.value + ")", new String[]{"New value"}, v -> {
                sender.send("var " + e.id + " " + v[0]);
                try {
                    e.value = Integer.parseInt(v[0]);
                } catch (NumberFormatException ignored) {
                }
                if (after != null) after.run();
            });
        } else {
            prompt(e.name + " (you have " + e.value + ")", new String[]{"How many to add"}, v -> {
                sender.send("item " + e.id + " " + v[0]);
                if (after != null) after.run();
            });
        }
    }

    // ---- searchable list of this game's data ----

    private void browse() {
        // Wait a moment for the game to write its current values.
        sender.send("dump");
        handler.postDelayed(this::openBrowser, 450);
    }

    private void openBrowser() {
        final List<Entry> all = readEntries(dbFile);
        if (all.isEmpty()) {
            Toast.makeText(activity, "Nothing named in this game's database yet. Try again in a moment, in a map.", Toast.LENGTH_LONG).show();
            return;
        }
        final List<Entry> pins = readEntries(pinsFile);
        final List<Entry> shown = new ArrayList<>(all);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, new ArrayList<String>());
        final String[] filter = {""};
        final Runnable refill = () -> {
            shown.clear();
            adapter.clear();
            String q = filter[0].toLowerCase();
            for (Entry e : all) {
                if (!q.isEmpty() && !e.name.toLowerCase().contains(q) && !String.valueOf(e.id).equals(q)) continue;
                shown.add(e);
                adapter.add((isPinned(pins, e) ? "★ " : "") + e.label());
            }
            adapter.notifyDataSetChanged();
        };

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        final EditText search = new EditText(activity);
        search.setHint("Search by name or number");
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        box.addView(search);
        ListView list = new ListView(activity);
        list.setAdapter(adapter);
        box.addView(list, new LinearLayout.LayoutParams(-1, Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.55f)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                filter[0] = s.toString().trim();
                refill.run();
            }
        });
        list.setOnItemClickListener((p, v, pos, id) -> apply(shown.get(pos), pins, refill));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            Entry e = shown.get(pos);
            Entry found = null;
            for (Entry x : pins) if (x.type == e.type && x.id == e.id) found = x;
            if (found != null) {
                pins.remove(found);
                Toast.makeText(activity, "Unpinned", Toast.LENGTH_SHORT).show();
            } else {
                pins.add(e);
                Toast.makeText(activity, "Pinned to this game's cheat list", Toast.LENGTH_SHORT).show();
            }
            savePins(pins);
            refill.run();
            return true;
        });
        refill.run();
        new AlertDialog.Builder(activity)
                .setTitle("Tap to use, long-press to pin")
                .setView(box)
                .setNegativeButton("Close", null)
                .show();
    }

    // ---- number prompt ----

    private interface Done {
        void done(String[] values);
    }

    private void prompt(String title, String[] hints, final Done cb) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        final EditText[] fields = new EditText[hints.length];
        for (int i = 0; i < hints.length; i++) {
            fields[i] = new EditText(activity);
            fields[i].setHint(hints[i]);
            fields[i].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            box.addView(fields[i]);
        }
        new AlertDialog.Builder(activity)
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
}
