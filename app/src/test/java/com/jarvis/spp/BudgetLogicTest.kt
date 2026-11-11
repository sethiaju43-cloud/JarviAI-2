package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetLogicTest {

    @Test
    fun zeroLimitIsOff() {
        assertEquals(BudgetLevel.OFF, BudgetLogic.level(50.0, 0.0))
        assertEquals(BudgetLevel.OFF, BudgetLogic.level(0.0, -1.0))
    }

    @Test
    fun levelsByRatio() {
        assertEquals(BudgetLevel.OK, BudgetLogic.level(79.0, 100.0))
        assertEquals(BudgetLevel.WARN, BudgetLogic.level(80.0, 100.0))
        assertEquals(BudgetLevel.WARN, BudgetLogic.level(99.0, 100.0))
        assertEquals(BudgetLevel.EXCEEDED, BudgetLogic.level(100.0, 100.0))
        assertEquals(BudgetLevel.EXCEEDED, BudgetLogic.level(150.0, 100.0))
    }

    @Test
    fun worstPicksMoreSerious() {
        assertEquals(BudgetLevel.EXCEEDED, BudgetLogic.worst(BudgetLevel.WARN, BudgetLevel.EXCEEDED))
        assertEquals(BudgetLevel.WARN, BudgetLogic.worst(BudgetLevel.OFF, BudgetLevel.WARN))
        assertEquals(BudgetLevel.OK, BudgetLogic.worst(BudgetLevel.OFF, BudgetLevel.OK))
    }

    @Test
    fun sameRequestGivesSameKey() {
        val h = listOf(Pair("user", "Delhi ka mausam"))
        assertEquals(BudgetLogic.dupKey("CHAT", h), BudgetLogic.dupKey("CHAT", h))
        assertEquals(
            BudgetLogic.dupKey("CHAT", listOf(Pair("user", "Hello "))),
            BudgetLogic.dupKey("CHAT", listOf(Pair("user", "hello")))
        )
    }

    @Test
    fun differentContextGivesDifferentKey() {
        val a = listOf(Pair("user", "dobara batao"))
        val b = listOf(Pair("user", "pehla"), Pair("model", "jawab"), Pair("user", "dobara batao"))
        assertNotEquals(BudgetLogic.dupKey("CHAT", a), BudgetLogic.dupKey("CHAT", b))
        assertNotEquals(BudgetLogic.dupKey("CHAT", a), BudgetLogic.dupKey("CODE", a))
    }

    @Test
    fun freshnessWindow() {
        assertTrue(BudgetLogic.isFresh(1000L, 4000L, 6000L))
        assertFalse(BudgetLogic.isFresh(1000L, 8000L, 6000L))
        assertFalse(BudgetLogic.isFresh(5000L, 1000L, 6000L))
    }
}
