package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelChainTest {
    private val defaults = listOf("heavy", "mid", "lite")

    @Test fun parseUsesDefaultsWhenEmpty() {
        assertEquals(defaults, ModelChain.parse("", defaults))
        assertEquals(defaults, ModelChain.parse("  ", defaults))
    }

    @Test fun singleModelGoesFirstThenDefaults() {
        assertEquals(listOf("mid", "heavy", "lite"), ModelChain.parse("mid", defaults))
        assertEquals(listOf("solo"), ModelChain.parse("solo", emptyList()))
    }

    @Test fun userListIsKeptExactly() {
        assertEquals(listOf("x", "y", "z"), ModelChain.parse("x, y ,z", defaults))
        assertEquals(listOf("a", "b"), ModelChain.parse("a, a, b", defaults))
    }

    @Test fun keysAreUniquePerProviderAndModel() {
        assertEquals("gemini|m1", ModelChain.key("gemini", "m1"))
        assertEquals(false, ModelChain.key("a", "m") == ModelChain.key("b", "m"))
    }

    @Test fun interleaveIsTierMajorKeepingProviderOrder() {
        val chains = listOf(
            Pair("gemini", listOf("g1", "g2", "g3")),
            Pair("groq", listOf("q1", "q2")),
            Pair("mistral", listOf("m1"))
        )
        val r = ModelChain.interleave(chains)
        assertEquals(
            listOf(
                Pair("gemini", "g1"), Pair("groq", "q1"), Pair("mistral", "m1"),
                Pair("gemini", "g2"), Pair("groq", "q2"),
                Pair("gemini", "g3")
            ),
            r
        )
    }

    @Test fun interleaveHandlesEmpty() {
        assertEquals(emptyList<Pair<String, String>>(), ModelChain.interleave(emptyList()))
        assertEquals(
            listOf(Pair("a", "x")),
            ModelChain.interleave(listOf(Pair("a", listOf("x")), Pair("b", emptyList())))
        )
    }
}
