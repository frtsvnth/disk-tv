package ru.tuvio.tools.dlnaclient;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import org.json.JSONException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** One-way export for the signed replacement app on this device. */
public final class MigrationProvider extends ContentProvider {
    @Override public boolean onCreate() {
        return true;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Context context = getContext();
        if (context == null || !MigrationData.isLegacy() || !MigrationData.historyUri().equals(uri)
                || !"r".equals(mode) || !MigrationData.CURRENT_PACKAGE.equals(getCallingPackage())
                || context.getPackageManager().checkSignatures(context.getPackageName(),
                    MigrationData.CURRENT_PACKAGE) != PackageManager.SIGNATURE_MATCH) {
            throw new FileNotFoundException("Migration export unavailable");
        }
        byte[] export;
        try {
            export = MigrationData.exportSettings(context).toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException error) {
            throw new FileNotFoundException("Could not serialize migration data");
        }
        return openPipeHelper(uri, "application/json", null, export, (output, target, type, options, bytes) -> {
            try (ParcelFileDescriptor.AutoCloseOutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(output)) {
                out.write(bytes);
            } catch (IOException ignored) {
                // The replacement app can retry if the transfer was interrupted.
            }
        });
    }

    @Override public Cursor query(Uri uri, String[] p, String s, String[] args, String order) {
        throw new UnsupportedOperationException();
    }
    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String s, String[] args) {
        throw new UnsupportedOperationException();
    }
}
