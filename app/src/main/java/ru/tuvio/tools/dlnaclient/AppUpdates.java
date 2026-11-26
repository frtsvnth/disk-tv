package ru.tuvio.tools.dlnaclient;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Lifecycle;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AppUpdates {
    private static final String TAG = "DiskTV/Updates";
    private static final String PENDING_APK = "pendingUpdateApk";
    private static final long CHECK_INTERVAL_MS = 30L * 60 * 1000;
    private static final long SNOOZE_MS = 24L * 60 * 60 * 1000;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private final AppCompatActivity activity;
    private final SharedPreferences preferences;
    private final ActivityResultLauncher<Intent> sourcesSettings;
    private File pendingApk;
    private boolean busy;

    AppUpdates(AppCompatActivity activity, Bundle state) {
        this.activity = activity;
        preferences = activity.getSharedPreferences("updates", Context.MODE_PRIVATE);
        if (state != null) {
            String fileName = state.getString(PENDING_APK);
            if (fileName != null && fileName.matches("update-[0-9]+[.]apk")) {
                pendingApk = new File(new File(activity.getCacheDir(), "updates"), fileName);
            }
        }
        sourcesSettings = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            File ready = pendingApk;
            pendingApk = null;
            if (ready != null && activity.getPackageManager().canRequestPackageInstalls()) openInstaller(ready);
        });
    }

    void saveState(Bundle state) {
        if (pendingApk != null) state.putString(PENDING_APK, pendingApk.getName());
    }

    void check(boolean manual) {
        long now = System.currentTimeMillis();
        long elapsed = now - preferences.getLong("lastCheck", 0);
        if (busy || pendingApk != null || (!manual && elapsed >= 0 && elapsed < CHECK_INTERVAL_MS)) return;
        busy = true;
        preferences.edit().putLong("lastCheck", now).apply();
        if (manual) toast(R.string.update_checking);
        WORKER.execute(() -> {
            try {
                UpdateRelease release = UpdateClient.latest();
                long installed = installedVersion();
                activity.runOnUiThread(() -> {
                    busy = false;
                    if (!active()) return;
                    if (release == null || release.versionCode <= installed) {
                        if (manual) toast(R.string.update_current);
                    } else if (manual || !isSnoozed(release)) {
                        offer(release);
                    }
                });
            } catch (Exception error) {
                Log.w(TAG, "Update check failed: " + error.getClass().getSimpleName());
                activity.runOnUiThread(() -> {
                    busy = false;
                    if (manual && active()) toast(R.string.update_check_error);
                });
            }
        });
    }

    @SuppressWarnings("deprecation")
    private long installedVersion() throws android.content.pm.PackageManager.NameNotFoundException {
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionCode;
    }

    private boolean isSnoozed(UpdateRelease release) {
        long age = System.currentTimeMillis() - preferences.getLong("snoozedAt", 0);
        return preferences.getLong("snoozedVersion", 0) == release.versionCode
                && age >= 0 && age < SNOOZE_MS;
    }

    private void offer(UpdateRelease release) {
        Runnable later = () -> preferences.edit().putLong("snoozedVersion", release.versionCode)
                .putLong("snoozedAt", System.currentTimeMillis()).apply();
        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.update_available, release.versionName))
                .setMessage(R.string.update_confirm)
                .setNegativeButton(R.string.update_later, (dialog, which) -> later.run())
                .setPositiveButton(R.string.update_install, (dialog, which) -> download(release))
                .setOnCancelListener(dialog -> later.run())
                .show();
    }

    private void download(UpdateRelease release) {
        if (busy) return;
        busy = true;
        Context context = activity.getApplicationContext();
        AlertDialog progress = new AlertDialog.Builder(activity).setMessage(R.string.update_downloading)
                .setCancelable(false).show();
        WORKER.execute(() -> {
            File ready = null;
            try {
                ready = UpdateClient.download(context, release);
            } catch (Exception error) {
                Log.w(TAG, "Update download failed: " + error.getClass().getSimpleName());
            }
            File apk = ready;
            activity.runOnUiThread(() -> {
                busy = false;
                progress.dismiss();
                if (!active()) return;
                if (apk == null) toast(R.string.update_download_error);
                else if (activity.getPackageManager().canRequestPackageInstalls()) openInstaller(apk);
                else requestInstallPermission(apk);
            });
        });
    }

    private void requestInstallPermission(File apk) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.update_permission_title)
                .setMessage(R.string.update_permission_hint)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.update_open_settings, (dialog, which) -> {
                    pendingApk = apk;
                    Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    try {
                        sourcesSettings.launch(intent);
                    } catch (ActivityNotFoundException | SecurityException error) {
                        pendingApk = null;
                        toast(R.string.update_installer_error);
                    }
                }).show();
    }

    @SuppressWarnings("deprecation")
    private void openInstaller(File apk) {
        if (!apk.isFile()) {
            toast(R.string.update_download_error);
            return;
        }
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".updates", apk);
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            toast(R.string.update_installer_error);
        }
    }

    private boolean active() {
        return !activity.isFinishing() && activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED);
    }

    private void toast(int message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
