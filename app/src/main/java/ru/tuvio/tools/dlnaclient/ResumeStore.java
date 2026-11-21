package ru.tuvio.tools.dlnaclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.json.JSONObject;

/** Position is keyed by persistent server UDN and media object ID. */
public class ResumeStore {
    private final SharedPreferences prefs;
    public ResumeStore(Context context) {
        this(PreferenceManager.getDefaultSharedPreferences(context));
    }
    ResumeStore(SharedPreferences prefs) { this.prefs = prefs; }
    public String entryKey(String server, String objectId) {
        return "resume_" + server.length() + ":" + server + objectId;
    }
    public void save(String server, String id, String title, String url, long position, long duration) {
        if (server == null || id == null) return;
        try {
            JSONObject value = new JSONObject().put("title", title).put("url", url)
                    .put("pos", Math.max(0, position)).put("dur", duration);
            prefs.edit().putString(entryKey(server, id), value.toString()).apply();
        } catch (org.json.JSONException ignored) {}
    }
    public ResumeEntry load(String server, String id) {
        String raw = prefs.getString(entryKey(server, id), null);
        if (raw == null) return null;
        try {
            JSONObject value = new JSONObject(raw);
            return new ResumeEntry(value.optString("title"), value.optString("url"),
                    value.getLong("pos"), value.optLong("dur", -1));
        } catch (Exception e) { return null; }
    }
    public void remove(String server, String id) {
        prefs.edit().remove(entryKey(server, id)).apply();
    }
    public static class ResumeEntry {
        public final String title, url;
        public final long positionMs, durationMs;
        public ResumeEntry(String title, String url, long position, long duration) {
            this.title = title; this.url = url; positionMs = position; durationMs = duration;
        }
        public boolean hasResume() {
            return positionMs > 1000 && (durationMs <= 0 || durationMs > positionMs + 5000);
        }
    }
}
