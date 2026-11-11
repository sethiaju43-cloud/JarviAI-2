package com.jarvis.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionWordsTest {
    @Test fun questionsAreNotActions() {
        assertFalse(ActionWords.any("tumhara naam kya hai aur tumhe kisne banaya"))
        assertFalse(ActionWords.any("aaj ka din kaisa hai aur tum kaisi ho"))
    }

    @Test fun commandsAreActions() {
        assertTrue(ActionWords.any("youtube kholo aur gaana chalao"))
        assertTrue(ActionWords.any("video ko fullscreen kar do"))
        assertTrue(ActionWords.any("scroll karte raho"))
    }
}
