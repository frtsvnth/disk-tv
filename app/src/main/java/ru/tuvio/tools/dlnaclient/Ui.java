package ru.tuvio.tools.dlnaclient;

import android.content.Context;
import android.text.format.Formatter;
import android.view.View;
import java.util.Locale;

/** Shared TV presentation: readable names, metadata and restrained remote focus. */
final class Ui {
    static void focus(View view, boolean card) {
        view.setOnFocusChangeListener((v, focused) -> {
            float scale = focused && card ? 1.025f : 1f;
            v.animate().scaleX(scale).scaleY(scale)
                    .translationZ(focused ? dp(v.getContext(), 6) : 0).setDuration(140).start();
        });
    }
    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
    static long identity(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
    static String leaf(String path) {
        if (path == null) return "";
        int split = path.lastIndexOf(" / ");
        return split < 0 ? path : path.substring(split + 3);
    }
    static String parent(String path) {
        if (path == null) return "";
        int split = path.lastIndexOf(" / ");
        return split < 0 ? "" : path.substring(0, split);
    }
    static String time(long ms) {
        long sec = Math.max(0, ms) / 1000;
        return sec < 3600 ? String.format(Locale.getDefault(), "%d:%02d", sec / 60, sec % 60)
                : String.format(Locale.getDefault(), "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60);
    }
    static String fileInfo(Context context, DmsItem item) {
        if (item.isFolder) return context.getString(R.string.folder);
        String mime = item.mimeType == null ? "" : item.mimeType.toLowerCase(Locale.ROOT);
        String type = context.getString(mime.startsWith("video/") || mime.contains("videoitem") ? R.string.video : R.string.file);
        if (mime.contains("matroska")) type = "MKV";
        else if (mime.contains("mp4")) type = "MP4";
        else if (mime.contains("msvideo") || mime.contains("avi")) type = "AVI";
        else if (mime.contains("quicktime")) type = "MOV";
        else if (mime.contains("mp2t")) type = "TS";
        else if (mime.contains("mpeg")) type = "MPEG";
        return item.size > 0 ? type + " · " + Formatter.formatShortFileSize(context, item.size) : type;
    }
}
