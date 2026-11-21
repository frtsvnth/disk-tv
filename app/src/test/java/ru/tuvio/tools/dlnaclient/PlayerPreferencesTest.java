package ru.tuvio.tools.dlnaclient;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerPreferencesTest {
    @Test public void startsWithInternalPlayer() {
        assertEquals(PlayerPreferences.INTERNAL, new PlayerPreferences(new MemoryPreferences()).get());
    }
    @Test public void externalChoiceSurvivesReopeningStore() {
        MemoryPreferences prefs = new MemoryPreferences();
        new PlayerPreferences(prefs).set("package:org.example.player");
        assertEquals("package:org.example.player", new PlayerPreferences(prefs).get());
    }
    @Test public void canReturnToInternalPlayer() {
        MemoryPreferences prefs = new MemoryPreferences();
        PlayerPreferences choice = new PlayerPreferences(prefs);
        choice.set("package:org.example.player"); choice.set(PlayerPreferences.INTERNAL);
        assertEquals(PlayerPreferences.INTERNAL, new PlayerPreferences(prefs).get());
    }
    @Test public void nullAndEmptyChoicesRestoreInternalPlayer() {
        PlayerPreferences choice = new PlayerPreferences(new MemoryPreferences());
        choice.set(null); assertEquals(PlayerPreferences.INTERNAL, choice.get());
        choice.set(""); assertEquals(PlayerPreferences.INTERNAL, choice.get());
    }
    @Test public void damagedPreferenceDoesNotCrashSettings() {
        MemoryPreferences prefs = new MemoryPreferences();
        prefs.edit().putInt("default_player", 42).apply();
        assertEquals(PlayerPreferences.INTERNAL, new PlayerPreferences(prefs).get());
    }
}
