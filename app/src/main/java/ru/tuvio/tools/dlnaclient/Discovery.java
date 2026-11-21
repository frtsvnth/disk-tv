package ru.tuvio.tools.dlnaclient;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** SSDP responses are unicast to the ephemeral search socket. Works over Ethernet and Wi-Fi. */
public class Discovery {
    public interface Callback {
        void onServers(List<Server> servers);
        void onError(String message);
    }
    private final Context context;
    private final Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean scanning, stopped;
    private volatile DatagramSocket socket;

    public Discovery(Context context, Callback callback) {
        this.context = context.getApplicationContext(); this.callback = callback;
    }

    public void start() {
        if (scanning || stopped) return;
        scanning = true;
        new Thread(this::scan, "dlna-discovery").start();
    }

    public void stop() {
        stopped = true; scanning = false;
        DatagramSocket active = socket;
        if (active != null) active.close();
        main.removeCallbacksAndMessages(null);
    }

    private void scan() {
        Map<String, Server> found = new LinkedHashMap<>();
        Set<String> locations = new LinkedHashSet<>();
        WifiManager.MulticastLock lock = null;
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                lock = wifi.createMulticastLock("DiskTV-discovery");
                lock.setReferenceCounted(false); lock.acquire();
            }
            try (DatagramSocket s = new DatagramSocket()) {
                socket = s;
                if (stopped) return;
                s.setSoTimeout(250);
                InetAddress group = InetAddress.getByName("239.255.255.250");
                String request = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n"
                        + "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n";
                byte[] data = request.getBytes(StandardCharsets.US_ASCII);
                long deadline = SystemClock.elapsedRealtime() + 4500, nextSend = 0;
                while (!stopped && SystemClock.elapsedRealtime() < deadline) {
                    if (SystemClock.elapsedRealtime() >= nextSend) {
                        s.send(new DatagramPacket(data, data.length, group, 1900));
                        nextSend = SystemClock.elapsedRealtime() + 1500;
                    }
                    try {
                        DatagramPacket packet = new DatagramPacket(new byte[8192], 8192);
                        s.receive(packet);
                        String response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        for (String line : response.split("\r?\n")) {
                            int colon = line.indexOf(':');
                            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("location"))
                                locations.add(line.substring(colon + 1).trim());
                        }
                    } catch (SocketTimeoutException ignored) {}
                }
            } finally { socket = null; }
            for (String location : locations) {
                if (stopped) return;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(location).openConnection();
                    try {
                        c.setConnectTimeout(3000); c.setReadTimeout(3000);
                        try (InputStream in = c.getInputStream()) {
                            Server server = UpnpXml.description(location, UpnpClient.read(in));
                            if (server != null) found.put(server.udn, server);
                        }
                    } finally { c.disconnect(); }
                } catch (Exception e) { Log.w("DiskTV/Discovery", "Cannot read device description", e); }
            }
            main.post(() -> {
                scanning = false;
                if (!stopped) callback.onServers(new ArrayList<>(found.values()));
            });
        } catch (Exception e) {
            main.post(() -> {
                scanning = false;
                if (!stopped) callback.onError(e.getMessage());
            });
        } finally {
            if (lock != null && lock.isHeld()) lock.release();
        }
    }
}
