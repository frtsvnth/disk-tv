package ru.tuvio.tools.dlnaclient;

import java.io.IOException;
import java.net.URL;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateReleaseTest {
    private JSONObject manifest() throws JSONException {
        return new JSONObject()
                .put("versionCode", 6).put("versionName", "1.4.0").put("size", 4096)
                .put("sha256", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .put("apkUrl", "https://github.com/frtsvnth/disk-tv/releases/download/v1.4.0/DiskTV.apk");
    }

    @Test public void readsVersionAndAssetFromRelease() throws Exception {
        UpdateRelease update = UpdateRelease.parse(manifest().toString());
        assertEquals(6L, update.versionCode);
        assertEquals(4096L, update.size);
        assertEquals(manifest().getString("apkUrl"), update.apkUrl());
    }

    @Test public void rejectsForeignAssetAndInvalidHashOrSize() throws Exception {
        assertThrows(JSONException.class, () -> UpdateRelease.parse(
                manifest().put("apkUrl", "https://example.invalid/DiskTV.apk").toString()));
        assertThrows(JSONException.class, () -> UpdateRelease.parse(
                manifest().put("sha256", "incomplete").toString()));
        assertThrows(JSONException.class, () -> UpdateRelease.parse(
                manifest().put("size", UpdateRelease.MAX_APK_BYTES + 1).toString()));
    }

    @Test public void permitsOnlyGitHubReleaseRedirectHostsOverHttps() throws Exception {
        UpdateRelease.requireTrustedUrl(new URL("https://release-assets.githubusercontent.com/file"));
        assertThrows(IOException.class, () -> UpdateRelease.requireTrustedUrl(new URL("http://github.com/file")));
        assertThrows(IOException.class, () -> UpdateRelease.requireTrustedUrl(new URL("https://github.com.example.org/file")));
        assertThrows(IOException.class, () -> UpdateRelease.requireTrustedUrl(new URL("https://github.com:444/file")));
    }
}
