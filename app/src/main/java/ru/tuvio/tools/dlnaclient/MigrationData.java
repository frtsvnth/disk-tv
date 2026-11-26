package ru.tuvio.tools.dlnaclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class MigrationData {
    static final String LEGACY_PACKAGE = "ru.tuvio.tools.dlnaclient";
    static final String CURRENT_PACKAGE = "io.frtsvnth.disktv";
    static Uri historyUri() {
        return Uri.parse("content://" + LEGACY_PACKAGE + ".migration/history");
    }
    private static final String[] SETTINGS = {"history", "browse_settings", "player_settings", "playback_settings"};
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final ExecutorService BACKGROUND = Executors.newSingleThreadExecutor();

    private MigrationData() {}

    static boolean isLegacy() {
        return LEGACY_PACKAGE.equals(BuildConfig.APPLICATION_ID);
    }

    private static SharedPreferences settings(Context context, String name) {
        if (name.equals("history")) return PreferenceManager.getDefaultSharedPreferences(context);
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    static JSONObject exportSettings(Context context) throws JSONException {
        JSONObject result = new JSONObject();
        for (String name : SETTINGS) {
            JSONObject entries = new JSONObject();
            for (Map.Entry<String, ?> entry : settings(context, name).getAll().entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String || value instanceof Boolean || value instanceof Integer) {
                    entries.put(entry.getKey(), value);
                }
            }
            result.put(name, entries);
        }
        return result;
    }

    static void importOnStart(android.app.Activity activity, Runnable onImported) {
        if (isLegacy()) return;
        Context context = activity.getApplicationContext();
        BACKGROUND.execute(() -> {
            SharedPreferences state = context.getSharedPreferences("migration", Context.MODE_PRIVATE);
            if (state.getBoolean("imported", false)) return;
            try (InputStream in = context.getContentResolver().openInputStream(historyUri())) {
                if (in == null) return;
                byte[] buffer = new byte[8192];
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                for (int n; (n = in.read(buffer)) != -1;) {
                    if (data.size() + n > MAX_BYTES) return;
                    data.write(buffer, 0, n);
                }
                JSONObject payload = new JSONObject(new String(data.toByteArray(), StandardCharsets.UTF_8));
                for (String name : SETTINGS) {
                    SharedPreferences preferences = settings(context, name);
                    if (!mergeMissing(preferences, payload.getJSONObject(name))) return;
                }
                if (state.edit().putBoolean("imported", true).commit()) {
                    activity.runOnUiThread(onImported);
                }
            } catch (Exception ignored) {
                // The legacy app may be missing or still on a version without the migration provider.
            }
        });
    }

    static boolean mergeMissing(SharedPreferences preferences, JSONObject source) throws JSONException {
        SharedPreferences.Editor editor = preferences.edit();
        for (Iterator<String> it = source.keys(); it.hasNext();) {
            String key = it.next();
            if (preferences.contains(key)) continue;
            Object value = source.get(key);
            if (value instanceof String) editor.putString(key, (String) value);
            else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        }
        return editor.commit();
    }
}
