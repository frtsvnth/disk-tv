package ru.tuvio.tools.dlnaclient;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import javax.net.ssl.HttpsURLConnection;

final class UpdateClient {
    private static final int MAX_MANIFEST_BYTES = 8192;

    private UpdateClient() {}

    static UpdateRelease latest(boolean legacy) throws Exception {
        HttpURLConnection connection = open(UpdateRelease.manifestUrl(legacy));
        try {
            if (connection.getResponseCode() == 404) return null;
            requireSuccess(connection);
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int n; (n = in.read(buffer)) != -1;) {
                    if (data.size() + n > MAX_MANIFEST_BYTES) throw new IOException("Manifest too large");
                    data.write(buffer, 0, n);
                }
                return UpdateRelease.parse(new String(data.toByteArray(), StandardCharsets.UTF_8), legacy);
            }
        } finally {
            connection.disconnect();
        }
    }

    static File download(Context context, UpdateRelease release) throws Exception {
        File directory = new File(context.getCacheDir(), "updates");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("No update cache");
        File[] oldFiles = directory.listFiles();
        if (oldFiles != null) {
            long cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
            for (File old : oldFiles) if (old.lastModified() < cutoff) old.delete();
        }
        File apk = File.createTempFile("update-", ".apk", directory);
        boolean verified = false;
        try {
            HttpURLConnection connection = open(release.apkUrl());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            try {
                requireSuccess(connection);
                try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(apk)) {
                    byte[] buffer = new byte[64 * 1024];
                    for (int n; (n = in.read(buffer)) != -1;) {
                        bytes += n;
                        if (bytes > release.size) throw new IOException("APK exceeds declared size");
                        digest.update(buffer, 0, n);
                        out.write(buffer, 0, n);
                    }
                }
            } finally {
                connection.disconnect();
            }
            if (bytes != release.size || !release.sha256.equals(hex(digest.digest()))) {
                throw new IOException("APK hash or size mismatch");
            }
            verifyPackage(context, apk, release);
            verified = true;
            return apk;
        } finally {
            if (!verified) apk.delete();
        }
    }

    // GET_SIGNATURES keeps the same signing comparison on Android 8 and newer versions.
    @SuppressWarnings("deprecation")
    private static void verifyPackage(Context context, File apk, UpdateRelease release) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo installed = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
        PackageInfo candidate = pm.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
        String expectedPackage = release.legacy ? MigrationData.LEGACY_PACKAGE : MigrationData.CURRENT_PACKAGE;
        if (candidate == null || !expectedPackage.equals(candidate.packageName)
                || candidate.versionCode != release.versionCode
                || (expectedPackage.equals(installed.packageName) && candidate.versionCode <= installed.versionCode)
                || !release.versionName.equals(candidate.versionName)
                || installed.signatures == null || installed.signatures.length == 0
                || candidate.signatures == null
                || !new HashSet<>(Arrays.asList(installed.signatures)).equals(
                    new HashSet<>(Arrays.asList(candidate.signatures)))) {
            throw new IOException("APK package, version or signing certificate mismatch");
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    private static HttpURLConnection open(String address) throws IOException {
        URL url = new URL(address);
        for (int hop = 0; hop < 6; hop++) {
            UpdateRelease.requireTrustedUrl(url);
            HttpURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "DiskTV-Android");
            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException error) {
                connection.disconnect();
                throw error;
            }
            if (status != 301 && status != 302 && status != 303 && status != 307 && status != 308) {
                return connection;
            }
            String redirect = connection.getHeaderField("Location");
            connection.disconnect();
            if (redirect == null) throw new IOException("Missing update redirect");
            url = new URL(url, redirect);
        }
        throw new IOException("Too many redirects");
    }

    private static void requireSuccess(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        if (code != 200) throw new IOException("Update HTTP " + code);
    }
}
