package ru.tuvio.tools.dlnaclient;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import org.json.*;
import java.util.*;

final class RecentStore {
    private final SharedPreferences prefs;
    RecentStore(Context context) { this(PreferenceManager.getDefaultSharedPreferences(context)); }
    RecentStore(SharedPreferences prefs) { this.prefs = prefs; }
    List<MainActivity.RecentFolder> load() {
        List<MainActivity.RecentFolder> list = new ArrayList<>();
        try {
            JSONArray entries = new JSONArray(prefs.getString("recent_folders_v1", "[]"));
            for (int i = 0; i < entries.length(); i++) {
                try {
                    JSONObject r = entries.getJSONObject(i);
                    Server s = new Server(r.getString("udn"), r.getString("name"), r.getString("base"),
                            r.getString("control"), r.getString("address"), r.getInt("port"), r.getString("type"));
                    list.add(new MainActivity.RecentFolder(s, r.getString("id"), r.getString("title")));
                } catch (JSONException ignored) {}
            }
        } catch (JSONException ignored) {}
        return list;
    }
    void add(Server server, DmsItem folder) {
        List<MainActivity.RecentFolder> entries = load();
        entries.removeIf(r -> r.server.udn.equals(server.udn) && r.objectId.equals(folder.objectId));
        entries.add(0, new MainActivity.RecentFolder(server, folder.objectId, folder.title));
        JSONArray json = new JSONArray();
        for (MainActivity.RecentFolder r : entries.subList(0, Math.min(10, entries.size()))) {
            try {
                Server s = r.server;
                json.put(new JSONObject().put("udn", s.udn).put("name", s.friendlyName)
                        .put("base", s.baseUrl).put("control", s.controlUrl).put("address", s.address)
                        .put("port", s.port).put("type", s.serviceType).put("id", r.objectId).put("title", r.title));
            } catch (JSONException ignored) {}
        }
        prefs.edit().putString("recent_folders_v1", json.toString()).apply();
    }
}
