package ru.tuvio.tools.dlnaclient;

import org.junit.Test;
import static org.junit.Assert.*;

public class ForcedSubtitlePolicyTest {
    @Test public void recognizesStandaloneForcedLabelsWithoutContainerFlag() {
        assertTrue(ForcedSubtitlePolicy.hasForcedLabel("Forced (HDrezka)"));
        assertTrue(ForcedSubtitlePolicy.hasForcedLabel("Русские форсированные"));
        assertFalse(ForcedSubtitlePolicy.hasForcedLabel("Unforced"));
        assertEquals(0, ForcedSubtitlePolicy.score("rus", "ru", "Полные", false));
    }

    @Test public void prefersMatchingLanguageButAcceptsOnlyAvailableForcedTrack() {
        String audio = ForcedSubtitlePolicy.language("ru-RU");
        int russian = ForcedSubtitlePolicy.score(audio, "rus", "Forced", false);
        int english = ForcedSubtitlePolicy.score(audio, "eng", "Forced", true);
        assertTrue(russian > english);
        assertTrue(english > 0);
        assertTrue(ForcedSubtitlePolicy.score(audio, "ru", null, true) > russian);
    }
}
