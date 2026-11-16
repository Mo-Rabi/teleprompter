package com.arabicwithmo.floating;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class ScriptCatalog {
    public static final class Script {
        public final int id;
        public final String title;
        public final String text;
        Script(int id, String title, String text) {
            this.id = id; this.title = title; this.text = text;
        }
        @Override public String toString() { return "#" + id + "  " + title; }
    }

    private final ArrayList<Script> scripts = new ArrayList<>();
    public ScriptCatalog(Context context) {
        try (InputStream stream = context.getAssets().open("scripts.json");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = stream.read(buf)) != -1) output.write(buf, 0, n);
            JSONArray data = new JSONArray(output.toString("UTF-8"));
            for (int i = 0; i < data.length(); i++) {
                JSONObject object = data.getJSONObject(i);
                JSONArray beats = object.getJSONArray("beats");
                StringBuilder spoken = new StringBuilder();
                for (int j = 0; j < beats.length(); j++) {
                    JSONObject beat = beats.getJSONObject(j);
                    String kind = beat.optString("t");
                    if (!"say".equals(kind)) continue; // Never read delivery cues as speech.
                    if (spoken.length() != 0) spoken.append("\n\n");
                    spoken.append(beat.optString("x"));
                }
                scripts.add(new Script(object.getInt("n"), object.optString("title"), spoken.toString()));
            }
            scripts.sort((a,b) -> Integer.compare(a.id,b.id));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load the embedded script catalog", ex);
        }
    }
    public List<Script> all() { return scripts; }
    public Script byId(int id) {
        for (Script s : scripts) if (s.id == id) return s;
        return scripts.isEmpty() ? null : scripts.get(0);
    }
    public Script neighbor(int id, int direction) {
        for (int i = 0; i < scripts.size(); i++) if (scripts.get(i).id == id) {
            int next = Math.min(scripts.size()-1,Math.max(0,i+direction));
            return scripts.get(next);
        }
        return byId(id);
    }
}
