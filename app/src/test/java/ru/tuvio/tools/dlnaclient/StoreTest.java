package ru.tuvio.tools.dlnaclient;

import android.content.SharedPreferences;
import org.junit.*;
import static org.junit.Assert.*;

public class StoreTest {
    SharedPreferences prefs;
    @Before public void clear() {
        prefs = new MemoryPreferences();
    }
    @Test public void progressSurvivesReopeningWithQuotedUnicodeTitle() {
        ResumeStore s = new ResumeStore(prefs);
        s.save("uuid:nas", "42", "Фильм \"А\\Б\"\n2026", "http://nas/video?a=1&b=2", 45000, 180000);
        ResumeStore.ResumeEntry e = new ResumeStore(prefs).load("uuid:nas", "42");
        assertEquals("Фильм \"А\\Б\"\n2026", e.title); assertEquals(45000, e.positionMs); assertTrue(e.hasResume());
        s.remove("uuid:nas", "42"); assertNull(s.load("uuid:nas", "42"));
    }
    @Test public void watchedStatusPersistsAndRemovesObsoleteResume() {
        ResumeStore store = new ResumeStore(prefs);
        store.save("nas", "42", "Film", "http://nas/movie", 45000, 180000);
        store.setWatched("nas", "42", true);
        ResumeStore reopened = new ResumeStore(prefs);
        assertTrue(reopened.isWatched("nas", "42"));
        assertFalse(reopened.isWatched("other-nas", "42"));
        assertNull(reopened.load("nas", "42"));
        reopened.setWatched("nas", "42", false);
        assertFalse(store.isWatched("nas", "42"));
    }
    @Test public void unknownDurationAllowsResumeAndNearEndDoesNot() {
        assertTrue(new ResumeStore.ResumeEntry("", "", 10000, -1).hasResume());
        assertFalse(new ResumeStore.ResumeEntry("", "", 98000, 100000).hasResume());
    }
    @Test public void differentServerIdentitiesDoNotCollide() {
        ResumeStore s = new ResumeStore(prefs);
        assertNotEquals(s.entryKey("a:b", "x"), s.entryKey("a_b", "x"));
        assertNotEquals(s.entryKey("a", "bc"), s.entryKey("ab", "c"));
    }
    @Test public void recentsPersistDeduplicateAndKeepTenNewest() {
        Server server = new Server("uuid:nas", "NAS", "http://nas/", "/control", "nas", 80);
        RecentStore s = new RecentStore(prefs);
        for (int i = 0; i < 12; i++) s.add(server, new DmsItem("" + i, "0", "Folder " + i, null, null, 0, true, null, null));
        assertEquals(10, new RecentStore(prefs).load().size());
        assertEquals("11", s.load().get(0).objectId);
        s.add(server, new DmsItem("8", "0", "Eight", null, null, 0, true, null, null));
        assertEquals(10, s.load().size()); assertEquals("8", s.load().get(0).objectId);
        assertEquals("/control", s.load().get(0).server.controlUrl);
    }
    @Test public void malformedSavedDataDoesNotCrash() {
        ResumeStore store = new ResumeStore(prefs);
        prefs.edit()
                .putString(store.entryKey("uuid:test", "x"), "broken json")
                .putString("recent_folders_v1", "broken json").commit();
        assertNull(store.load("uuid:test", "x"));
        assertTrue(new RecentStore(prefs).load().isEmpty());
    }
}
