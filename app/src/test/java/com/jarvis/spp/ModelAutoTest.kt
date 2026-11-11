package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelAutoTest {
    private val gem = listOf(
        "gemini-3.1-flash-lite", "gemini-3.5-flash-lite", "gemini-3.8-flash",
        "gemini-3.8-pro", "text-embedding-005", "gemini-3.8-flash-image", "gemini-live-3.8-flash"
    )

    @Test fun nonChatModelsAreSkipped() {
        assertFalse(ModelAuto.isChatModel("gemini", "text-embedding-005"))
        assertFalse(ModelAuto.isChatModel("groq", "whisper-large-v3"))
        assertFalse(ModelAuto.isChatModel("gemini", "gemini-3.8-flash-image"))
        assertTrue(ModelAuto.isChatModel("gemini", "gemini-3.8-flash"))
    }

    @Test fun gemmaSkippedOnGeminiOnly() {
        assertFalse(ModelAuto.isChatModel("gemini", "gemma-3-27b-it"))
        assertTrue(ModelAuto.isChatModel("groq", "gemma2-9b-it"))
    }

    @Test fun openRouterOnlyFree() {
        assertTrue(ModelAuto.isChatModel("openrouter", "meta-llama/llama-3.3-70b-instruct:free"))
        assertFalse(ModelAuto.isChatModel("openrouter", "openai/gpt-4o"))
    }

    @Test fun strongFirstOrderPutsGeminiProLast() {
        // Gemini mein 'pro' sabse peeche (free key par aksar chalta nahi): flash, phir lite, phir pro
        val r = ModelAuto.order("gemini", gem, true, 4)
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.8-pro"), r
        )
    }

    @Test fun fastFirstOrder() {
        val r = ModelAuto.order("gemini", gem, false, 3)
        // Wahi behtar top-3 (flash, 3.5-lite, 3.1-lite), bas halka pehle
        assertEquals(listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite", "gemini-3.8-flash"), r)
    }

    @Test fun proNotDemotedOnOtherProviders() {
        val r = ModelAuto.order("groq", listOf("llama-3.1-8b-instant", "some-pro-70b"), true, 2)
        assertEquals("some-pro-70b", r[0])
    }

    @Test fun maxAndDistinctAndEmpty() {
        assertEquals(1, ModelAuto.order("gemini", gem, true, 1).size)
        assertEquals(2, ModelAuto.order("groq", listOf("a-70b", "a-70b", "b-8b"), true, 5).size)
        assertTrue(ModelAuto.order("gemini", emptyList(), true, 3).isEmpty())
    }
}
