package com.jarvis.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsTest {

    @Test
    fun formatWithCalls() {
        val s = UsageStats.format(linkedMapOf("Google Gemini" to 14, "Groq" to 3), 9, 63)
        assertTrue(s.contains("17 calls"))
        assertTrue(s.contains("Google Gemini 14"))
        assertTrue(s.contains("Kal: 9"))
        assertTrue(s.contains("63"))
    }

    @Test
    fun formatWithNoCalls() {
        val s = UsageStats.format(emptyMap(), 0, 0)
        assertTrue(s.contains("koi call nahi"))
    }

    @Test
    fun usageQuestionDetected() {
        assertTrue(UsageStats.isUsageQuestion("API kitni use hui aaj"))
        assertTrue(UsageStats.isUsageQuestion("aaj kitni api calls hui"))
    }

    @Test
    fun otherQuestionsNotHijacked() {
        assertFalse(UsageStats.isUsageQuestion("API key kaise banate hain, kitna time lagta hai"))
        assertFalse(UsageStats.isUsageQuestion("torch on karo"))
        assertFalse(UsageStats.isUsageQuestion("kitni der mein pahunchenge"))
    }
}
