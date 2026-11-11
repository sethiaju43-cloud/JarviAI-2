package com.jarvis.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceHintsTest {

    @Before
    fun reset() {
        VoiceHints.forget()
    }

    @Test
    fun alternativesAppearForMatchingText() {
        VoiceHints.record("mujhe call lagao", listOf("mujhe kal lagao", "mujhe call laga"), 0.9f, now = 1000L)
        val line = VoiceHints.lineFor("Mujhe call lagao!", now = 2000L)
        assertTrue(line.contains("SUNNE KI SAHULAT"))
        assertTrue(line.contains("mujhe kal lagao"))
    }

    @Test
    fun noAlternativesWhenTextWasEdited() {
        VoiceHints.record("a b", listOf("c d"), 0.9f, now = 1000L)
        val line = VoiceHints.lineFor("kuch aur likha", now = 2000L)
        assertFalse(line.contains("VIKALP"))
        assertTrue(line.contains("SUNNE KI SAHULAT"))
    }

    @Test
    fun oldHeardIsIgnored() {
        VoiceHints.record("a b", listOf("c d"), 0.9f, now = 1000L)
        val line = VoiceHints.lineFor("a b", now = 1000L + 121_000L)
        assertFalse(line.contains("VIKALP"))
    }

    @Test
    fun duplicateAlternativesRemoved() {
        VoiceHints.record("a", listOf("A", "b", "b"), 0.9f, now = 1000L)
        val line = VoiceHints.lineFor("a", now = 1500L)
        assertTrue(line.split("'b'").size - 1 == 1)
    }

    @Test
    fun doubtOnlyWhenLowConfidence() {
        VoiceHints.record("amma ko call lagao", emptyList(), 0.3f, now = 1000L)
        assertNotNull(VoiceHints.doubtQuestion("amma ko call lagao", true, now = 2000L))
        assertNull(VoiceHints.doubtQuestion("amma ko call lagao", false, now = 2000L))

        VoiceHints.record("amma ko call lagao", emptyList(), 0.9f, now = 3000L)
        assertNull(VoiceHints.doubtQuestion("amma ko call lagao", true, now = 4000L))
    }

    @Test
    fun unknownConfidenceNeverAsks() {
        // Kuch recogniser confidence dete hi nahi (0 ya -1): tab har baar mat poochho
        VoiceHints.record("amma ko call lagao", emptyList(), 0f, now = 1000L)
        assertNull(VoiceHints.doubtQuestion("amma ko call lagao", true, now = 2000L))
        VoiceHints.record("amma ko call lagao", emptyList(), -1f, now = 3000L)
        assertNull(VoiceHints.doubtQuestion("amma ko call lagao", true, now = 4000L))
    }
}
