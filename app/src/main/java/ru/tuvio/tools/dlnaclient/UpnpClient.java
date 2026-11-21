package ru.tuvio.tools.dlnaclient;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small ContentDirectory client; fetches every page before publishing a folder. */
public class UpnpClient {
    public interface Callback {
        void onItems(List<DmsItem> items);
        void onError(String message);
    }

    public static void browse(Server server, String objectId, int start, int count, Callback cb) {
        new Thread(() -> {
            Handler main = new Handler(Looper.getMainLooper());
            try {
                List<DmsItem> items = browseAll(server, objectId, start, count);
                main.post(() -> cb.onItems(items));
            } catch (Exception e) {
                Log.e("DiskTV/Browse", "Browse failed", e);
                main.post(() -> cb.onError(e.getMessage()));
            }
        }, "dlna-browse").start();
    }

    static List<DmsItem> browseAll(Server server, String objectId, int start, int count) throws Exception {
        List<DmsItem> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int index = start;
        while (true) {
            UpnpXml.Page page = request(server, objectId, index, count);
            int added = 0;
            for (DmsItem item : page.items) if (seen.add(item.objectId)) { all.add(item); added++; }
            if (page.returned == 0) break;
            if (added == 0) throw new IOException("DLNA server repeated a page");
            index += page.returned;
            if (page.total >= 0 && index >= page.total) break;
        }
        return all;
    }

    private static UpnpXml.Page request(Server server, String id, int start, int count) throws Exception {
        String type = server.serviceType;
        String soap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>"
                + "<u:Browse xmlns:u=\"" + type + "\"><ObjectID>" + escape(id) + "</ObjectID>"
                + "<BrowseFlag>BrowseDirectChildren</BrowseFlag><Filter>*</Filter>"
                + "<StartingIndex>" + start + "</StartingIndex><RequestedCount>" + count + "</RequestedCount>"
                + "<SortCriteria/></u:Browse></s:Body></s:Envelope>";
        URL url = new URL(new URL(server.baseUrl), server.controlUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
            conn.setRequestProperty("SOAPACTION", "\"" + type + "#Browse\"");
            byte[] bytes = soap.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (stream == null) throw new IOException("DLNA HTTP " + status);
            String body;
            try (InputStream in = stream) { body = read(in); }
            UpnpXml.Page page = UpnpXml.browse(body, server.baseUrl);
            if (status != 200) throw new IOException("DLNA HTTP " + status);
            return page;
        } finally { conn.disconnect(); }
    }

    static String read(InputStream in) throws IOException {
        StringBuilder result = new StringBuilder();
        Reader r = new InputStreamReader(in, StandardCharsets.UTF_8);
        char[] buffer = new char[8192];
        int n;
        while ((n = r.read(buffer)) != -1) result.append(buffer, 0, n);
        return result.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
