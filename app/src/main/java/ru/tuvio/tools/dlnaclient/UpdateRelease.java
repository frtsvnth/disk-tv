package ru.tuvio.tools.dlnaclient;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

final class UpdateRelease {
    private static final String RELEASES = "https://github.com/frtsvnth/disk-tv/releases/";
    static final String LATEST = RELEASES + "latest/download/update.json";
    static final long MAX_APK_BYTES = 64L * 1024 * 1024;

    final long versionCode;
    final String versionName;
    final long size;
    final String sha256;

    private UpdateRelease(long versionCode, String versionName, long size, String sha256) {
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.size = size;
        this.sha256 = sha256;
    }

    static UpdateRelease parse(String json) throws JSONException {
        JSONObject value = new JSONObject(json);
        long code = value.getLong("versionCode");
        String name = value.getString("versionName");
        long size = value.getLong("size");
        String hash = value.getString("sha256").toLowerCase(Locale.ROOT);
        if (code <= 0 || code > Integer.MAX_VALUE || size <= 0 || size > MAX_APK_BYTES
                || !name.matches("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}")
                || !hash.matches("[a-f0-9]{64}")) {
            throw new JSONException("Invalid update metadata");
        }
        UpdateRelease release = new UpdateRelease(code, name, size, hash);
        if (!release.apkUrl().equals(value.getString("apkUrl"))) {
            throw new JSONException("Unexpected update URL");
        }
        return release;
    }

    String apkUrl() {
        return RELEASES + "download/v" + versionName + "/DiskTV.apk";
    }

    static void requireTrustedUrl(URL url) throws IOException {
        String host = url.getHost();
        if (!url.getProtocol().equals("https") || url.getUserInfo() != null
                || (url.getPort() != -1 && url.getPort() != 443)
                || !(host.equalsIgnoreCase("github.com")
                || host.equalsIgnoreCase("release-assets.githubusercontent.com")
                || host.equalsIgnoreCase("objects.githubusercontent.com"))) {
            throw new IOException("Unexpected update host");
        }
    }
}
