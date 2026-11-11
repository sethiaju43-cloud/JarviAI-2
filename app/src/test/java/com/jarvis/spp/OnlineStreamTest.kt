package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineStreamTest {

    @Test
    fun dataLine() {
        assertEquals("{\"a\":1}", OnlineStream.dataOf("data: {\"a\":1}"))
        assertEquals("[DONE]", OnlineStream.dataOf("data: [DONE]"))
        assertNull(OnlineStream.dataOf(": keep-alive"))
        assertNull(OnlineStream.dataOf("event: ping"))
    }

    @Test
    fun openAiDeltaText() {
        val d = "{\"choices\":[{\"delta\":{\"content\":\"Namaste\"}}]}"
        assertEquals("Namaste", OnlineStream.openAiDelta(d))
    }

    @Test
    fun openAiDeltaEmptyCases() {
        assertEquals("", OnlineStream.openAiDelta("[DONE]"))
        assertEquals("", OnlineStream.openAiDelta("{\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}"))
        assertEquals("", OnlineStream.openAiDelta("{\"choices\":[]}"))
        assertEquals("", OnlineStream.openAiDelta("not json"))
    }

    @Test
    fun geminiDeltaSkipsThought() {
        val d = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"soch\",\"thought\":true},{\"text\":\"Haan\"}]}}]}"
        assertEquals("Haan", OnlineStream.geminiDelta(d))
        assertEquals("", OnlineStream.geminiDelta("{}"))
    }

    @Test
    fun errorInsideStream() {
        assertEquals("quota khatam", OnlineStream.errorOf("{\"error\":{\"message\":\"quota khatam\"}}"))
        assertNull(OnlineStream.errorOf("{\"choices\":[]}"))
        assertNull(OnlineStream.errorOf("[DONE]"))
        assertNotNull(OnlineStream.errorOf("{\"error\":\"bad\"}"))
    }
}
