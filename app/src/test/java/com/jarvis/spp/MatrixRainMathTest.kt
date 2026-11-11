package com.jarvis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixRainMathTest {

    @Test
    fun columnsByLevel() {
        assertEquals(0, RainMath.columns(1000, 30f, 0))
        val full = RainMath.columns(1000, 30f, 2)
        val half = RainMath.columns(1000, 30f, 1)
        assertTrue(full > half)
        assertTrue(half >= full / 2)
    }

    @Test
    fun trailFadesAndLevelDims() {
        assertEquals(255, RainMath.trailAlpha(0, 10, 2))
        assertTrue(RainMath.trailAlpha(9, 10, 2) < 40)
        assertTrue(RainMath.trailAlpha(0, 10, 1) < RainMath.trailAlpha(0, 10, 2))
        assertEquals(0, RainMath.trailAlpha(10, 10, 2))
        assertEquals(0, RainMath.trailAlpha(-1, 10, 2))
        assertEquals(0, RainMath.trailAlpha(0, 10, 0))
    }

    @Test
    fun speedBands() {
        assertEquals(0f, RainMath.speedFactor(0), 0.0001f)
        assertTrue(RainMath.speedFactor(1) < RainMath.speedFactor(2))
    }

    @Test
    fun levelClamped() {
        assertEquals(0, RainMath.normalizeLevel(-3))
        assertEquals(2, RainMath.normalizeLevel(9))
        assertEquals(1, RainMath.normalizeLevel(1))
    }
}
