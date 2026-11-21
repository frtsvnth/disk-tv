package ru.tuvio.tools.dlnaclient;

import android.content.Context;
import android.content.SharedPreferences;

/** App-local default; does not change Android's defaults for other applications. */
final class PlayerPreferences {
    static final String INTERNAL = "internal";
    private static final String KEY = "default_player";
    private final SharedPreferences prefs;
    PlayerPreferences(Context context) { this(context.getSharedPreferences("player_settings", Context.MODE_PRIVATE)); }
    PlayerPreferences(SharedPreferences prefs) { this.prefs = prefs; }
    String get() {
        try {
            String value = prefs.getString(KEY, INTERNAL);
            return value == null || value.isEmpty() ? INTERNAL : value;
        } catch (ClassCastException ignored) { return INTERNAL; }
    }
    void set(String id) {
        prefs.edit().putString(KEY, id == null || id.isEmpty() ? INTERNAL : id).apply();
    }
}
