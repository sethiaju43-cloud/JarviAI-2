package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodToneTest {

    @Test
    fun happyDetected() {
        assertEquals(Mood.HAPPY, MoodTone.detect("aaj main bahut khush hun").mood)
        assertEquals(Mood.HAPPY, MoodTone.detect("thanks yaar").mood)
    }

    @Test
    fun excitedDetected() {
        assertEquals(Mood.EXCITED, MoodTone.detect("mera selection ho gaya").mood)
    }

    @Test
    fun frustratedDetected() {
        assertEquals(Mood.FRUSTRATED, MoodTone.detect("ye kaam nahi kar raha bakwas hai").mood)
    }

    @Test
    fun sadWinsOverFrustrated() {
        assertEquals(Mood.SAD, MoodTone.detect("main bahut udaas hun aur pareshan bhi").mood)
    }

    @Test
    fun confusedDetected() {
        assertEquals(Mood.CONFUSED, MoodTone.detect("mujhe kuch samajh nahi aaya").mood)
    }

    @Test
    fun urgentDetected() {
        assertEquals(Mood.URGENT, MoodTone.detect("jaldi batao").mood)
    }

    @Test
    fun technicalDetected() {
        assertEquals(Depth.TECHNICAL, MoodTone.detect("gradle build fail ho raha hai error aa raha").depth)
    }

    @Test
    fun wordInsideAnotherWordIsNotTechnical() {
        // "decode" mein "code" hai, par shabd ke beech mein hai
        assertFalse(MoodTone.detect("main ek rishta decode kar raha hun").depth == Depth.TECHNICAL)
    }

    @Test
    fun sadakIsNotSad() {
        assertEquals(Mood.NEUTRAL, MoodTone.detect("kal sadak par gaya tha").mood)
    }

    @Test
    fun shortQuestionIsShort() {
        assertEquals(Depth.SHORT, MoodTone.detect("time kya hai").depth)
    }

    @Test
    fun neutralNormalGivesEmptyLine() {
        assertEquals("", MoodTone.line("aaj mausam kaisa rahega is hafte ke liye batao zara"))
    }

    @Test
    fun lineMentionsItIsAGuess() {
        val l = MoodTone.line("bahut udaas hun")
        assertTrue(l.contains("andaaza"))
        assertTrue(l.contains("udaas"))
    }

    @Test
    fun emptyTextIsNeutral() {
        assertEquals(Mood.NEUTRAL, MoodTone.detect("").mood)
    }
}
