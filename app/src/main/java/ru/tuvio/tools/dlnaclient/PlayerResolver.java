package ru.tuvio.tools.dlnaclient;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import java.util.*;

/** Discovers exported video handlers through Android's standard ACTION_VIEW contract. */
final class PlayerResolver {
    static final class Option {
        final String id, label;
        final Drawable icon;
        final ComponentName component;
        Option(String id, String label, Drawable icon, ComponentName component) {
            this.id = id; this.label = label; this.icon = icon; this.component = component;
        }
        boolean internal() { return component == null; }
    }
    static String videoMime(DmsItem item) {
        String mime = item.mimeType == null ? "" : item.mimeType.toLowerCase(Locale.ROOT);
        return mime.startsWith("video/") ? mime : "video/*";
    }
    static Intent videoIntent(DmsItem item) {
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.url), videoMime(item));
        intent.putExtra(Intent.EXTRA_TITLE, item.title);
        return intent;
    }
    static List<Option> list(Context context, DmsItem item) {
        PackageManager pm = context.getPackageManager();
        Map<String, ResolveInfo> handlers = new TreeMap<>();
        List<Intent> queries = new ArrayList<>();
        if (item != null) queries.add(videoIntent(item));
        else {
            for (String scheme : new String[]{"http", "https"}) {
                String[][] formats = {{"mp4", "video/*"}, {"mp4", "video/mp4"},
                        {"mkv", "video/x-matroska"}, {"avi", "video/x-msvideo"}};
                for (String[] format : formats)
                    queries.add(new Intent(Intent.ACTION_VIEW).setDataAndType(
                            Uri.parse(scheme + "://example.invalid/video." + format[0]), format[1]));
            }
        }
        for (Intent query : queries) {
            for (ResolveInfo info : pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY)) {
                ActivityInfo activity = info.activityInfo;
                if (activity == null || !activity.exported || !activity.enabled
                        || !activity.applicationInfo.enabled || context.getPackageName().equals(activity.packageName)
                        || "android".equals(activity.packageName)) continue;
                ResolveInfo previous = handlers.get(activity.packageName);
                if (previous == null || info.match > previous.match) handlers.put(activity.packageName, info);
            }
        }
        List<Option> external = new ArrayList<>();
        for (ResolveInfo info : handlers.values()) {
            ComponentName component = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
            external.add(new Option("package:" + info.activityInfo.packageName, info.activityInfo.applicationInfo.loadLabel(pm).toString(), info.loadIcon(pm), component));
        }
        external.sort((a, b) -> java.text.Collator.getInstance().compare(a.label, b.label));
        List<Option> options = new ArrayList<>();
        options.add(new Option(PlayerPreferences.INTERNAL, context.getString(R.string.internal_player),
                androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.brand_mark), null));
        options.addAll(external);
        return options;
    }
    static Option find(Context context, DmsItem item, String id) {
        for (Option option : list(context, item)) if (option.id.equals(id)) return option;
        return null;
    }
    static String preferredLabel(Context context) {
        String preferred = new PlayerPreferences(context).get();
        Option option = find(context, null, preferred);
        return option == null ? context.getString(R.string.unavailable_player) : option.label;
    }
    static boolean launch(Context context, DmsItem item, Option option) {
        try {
            context.startActivity(videoIntent(item).setComponent(option.component));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            android.util.Log.w("DiskTV/Player", "Selected player cannot open this video", e);
            return false;
        }
    }
}
