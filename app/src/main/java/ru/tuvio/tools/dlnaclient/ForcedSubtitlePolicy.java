package ru.tuvio.tools.dlnaclient;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.regex.Pattern;

final class ForcedSubtitlePolicy {
    private static final Pattern FORCED_LABEL = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])(?:forced|форсированн\\p{L}*)(?![\\p{L}\\p{N}])");

    private ForcedSubtitlePolicy() {}

    static boolean hasForcedLabel(String label) {
        return label != null && FORCED_LABEL.matcher(label).find();
    }

    static String language(String code) {
        if (code == null || code.isEmpty() || code.equalsIgnoreCase("und")) return "";
        Locale locale = Locale.forLanguageTag(code.replace('_', '-'));
        try {
            return locale.getISO3Language();
        } catch (MissingResourceException ignored) {
            return locale.getLanguage();
        }
    }

    static int score(String audio, String subtitleCode, String label, boolean flagged) {
        if (!flagged && !hasForcedLabel(label)) return 0;
        String subtitle = language(subtitleCode);
        int languagePreference = 2;
        if (subtitle.isEmpty()) languagePreference = 4;
        else if (subtitle.equals(audio)) languagePreference = 8;
        else if (subtitle.equals("rus")) languagePreference = 6;
        return languagePreference + (flagged ? 1 : 0);
    }
}
