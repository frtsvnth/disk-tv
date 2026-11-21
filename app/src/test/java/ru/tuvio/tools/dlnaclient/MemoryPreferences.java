package ru.tuvio.tools.dlnaclient;

import android.content.SharedPreferences;
import java.util.*;

/** In-memory Android preferences contract for testing the stored data format and MRU policy. */
final class MemoryPreferences implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();
    @Override public Map<String, ?> getAll() { return new HashMap<>(values); }
    @Override public String getString(String k, String d) { return (String) values.getOrDefault(k, d); }
    @Override @SuppressWarnings("unchecked") public Set<String> getStringSet(String k, Set<String> d) { return (Set<String>) values.getOrDefault(k, d); }
    @Override public int getInt(String k, int d) { return (int) values.getOrDefault(k, d); }
    @Override public long getLong(String k, long d) { return (long) values.getOrDefault(k, d); }
    @Override public float getFloat(String k, float d) { return (float) values.getOrDefault(k, d); }
    @Override public boolean getBoolean(String k, boolean d) { return (boolean) values.getOrDefault(k, d); }
    @Override public boolean contains(String k) { return values.containsKey(k); }
    @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
    @Override public Editor edit() {
        return new Editor() {
            final Map<String,Object> changes = new HashMap<>();
            boolean clear;
            @Override public Editor putString(String k, String v) { changes.put(k,v); return this; }
            @Override public Editor putStringSet(String k, Set<String> v) { changes.put(k,v); return this; }
            @Override public Editor putInt(String k, int v) { changes.put(k,v); return this; }
            @Override public Editor putLong(String k, long v) { changes.put(k,v); return this; }
            @Override public Editor putFloat(String k, float v) { changes.put(k,v); return this; }
            @Override public Editor putBoolean(String k, boolean v) { changes.put(k,v); return this; }
            @Override public Editor remove(String k) { changes.put(k,null); return this; }
            @Override public Editor clear() { clear = true; return this; }
            @Override public boolean commit() { apply(); return true; }
            @Override public void apply() {
                if (clear) values.clear();
                for (Map.Entry<String,Object> e : changes.entrySet()) {
                    if (e.getValue() == null) values.remove(e.getKey());
                    else values.put(e.getKey(), e.getValue());
                }
            }
        };
    }
}
