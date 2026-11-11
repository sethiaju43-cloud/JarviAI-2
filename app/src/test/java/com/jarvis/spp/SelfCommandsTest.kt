package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfCommandsTest {

    @Test
    fun forgetDetected() {
        val c = SelfCommands.parse("chai wali baat bhool jao")
        assertTrue(c is SelfCommand.Forget)
        assertEquals("chai wali baat", (c as SelfCommand.Forget).about)
    }

    @Test
    fun forgetEnglish() {
        val c = SelfCommands.parse("forget my favourite colour")
        assertTrue(c is SelfCommand.Forget)
    }

    @Test
    fun plainBhoolJaoIsNotHandledHere() {
        assertNull(SelfCommands.parse("bhool jao"))
        assertNull(SelfCommands.parse("sab bhool jao"))
        assertNull(SelfCommands.parse("wo bhool jao"))
    }

    @Test
    fun correctDetected() {
        val c = SelfCommands.parse("sudhaar: main Mumbai mein rehta hun")
        assertTrue(c is SelfCommand.Correct)
        assertEquals("main Mumbai mein rehta hun", (c as SelfCommand.Correct).fact)
    }

    @Test
    fun recallDetected() {
        assertTrue(SelfCommands.parse("mere baare mein kya yaad hai") is SelfCommand.Recall)
        assertTrue(SelfCommands.parse("what do you remember about me") is SelfCommand.Recall)
    }

    @Test
    fun repeatDetected() {
        assertTrue(SelfCommands.parse("dobara bolo") is SelfCommand.Repeat)
        assertTrue(SelfCommands.parse("Jarvi, woh dobara bolo!") is SelfCommand.Repeat)
    }

    @Test
    fun speedDetected() {
        assertEquals(-1, (SelfCommands.parse("thoda dheere bolo") as SelfCommand.Speed).dir)
        assertEquals(1, (SelfCommands.parse("tez bolo") as SelfCommand.Speed).dir)
        assertEquals(0, (SelfCommands.parse("normal speed") as SelfCommand.Speed).dir)
    }

    @Test
    fun normalSentencesAreNotCommands() {
        assertNull(SelfCommands.parse("aaj mausam kaisa hai"))
        assertNull(SelfCommands.parse("mujhe dobara bolne ka mann nahi hai"))
        assertNull(SelfCommands.parse("YouTube kholo"))
    }

    @Test
    fun nextSpeedStaysInRange() {
        assertEquals(0.85f, SelfCommands.nextSpeed(1.0f, -1), 0.001f)
        assertEquals(1.15f, SelfCommands.nextSpeed(1.0f, 1), 0.001f)
        assertEquals(0.6f, SelfCommands.nextSpeed(0.6f, -1), 0.001f)
        assertEquals(1.5f, SelfCommands.nextSpeed(1.5f, 1), 0.001f)
        assertEquals(1.0f, SelfCommands.nextSpeed(0.7f, 0), 0.001f)
    }

    @Test
    fun bestMatchPicksRightMemory() {
        val texts = listOf("main Delhi mein rehta hun", "mujhe chai pasand hai")
        val m = SelfCommands.bestMatch("chai wali baat", texts, 0.5)
        assertEquals(1, m.index)
        assertFalse(m.ambiguous)
    }

    @Test
    fun bestMatchNoMatchGivesMinusOne() {
        val m = SelfCommands.bestMatch("cricket", listOf("main Delhi mein rehta hun"), 0.5)
        assertEquals(-1, m.index)
    }

    @Test
    fun bestMatchAmbiguousWhenTwoEqual() {
        val m = SelfCommands.bestMatch("pasand", listOf("mujhe chai pasand hai", "mujhe kofi pasand hai"), 0.5)
        assertTrue(m.index >= 0)
        assertTrue(m.ambiguous)
    }

    @Test
    fun fillerWordsAreIgnored() {
        assertNull(SelfCommands.parse("wali baat bhool jao"))
    }

    @Test
    fun correctionFindsOldCity() {
        val texts = listOf("main Delhi mein rehta hun", "mujhe chai pasand hai")
        val m = SelfCommands.bestMatch("main Mumbai mein rehta hun", texts, 0.5)
        assertEquals(0, m.index)
    }
}
