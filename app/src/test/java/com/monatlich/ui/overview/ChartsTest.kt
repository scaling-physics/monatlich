package com.monatlich.ui.overview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ChartsTest {

    private val size = IntSize(200, 200)
    private val center = Offset(100f, 100f)

    /** A point on the ring's centerline at [angleDeg], measured the same way `drawArc` does: 0deg = 3 o'clock, clockwise. */
    private fun ringPoint(angleDeg: Float): Offset {
        val rad = Math.toRadians(angleDeg.toDouble())
        val radius = 100f - 100f * 0.16f / 2f // diameter/2, matches donutSliceIndexAt's ring centerline
        return center + Offset((cos(rad) * radius).toFloat(), (sin(rad) * radius).toFloat())
    }

    @Test
    fun `a tap at 12 o'clock lands on the first slice`() {
        // Slice 0 starts at -90deg (12 o'clock) same as the draw loop.
        val fractions = listOf(0.5f, 0.3f, 0.2f)
        assertEquals(0, donutSliceIndexAt(ringPoint(-90f), size, fractions))
    }

    @Test
    fun `a tap partway around lands on the slice covering that angle`() {
        // Slice 0 spans -90..90 (50%), slice 1 spans 90..198 (30%), slice 2 spans 198..270 (20%).
        val fractions = listOf(0.5f, 0.3f, 0.2f)
        assertEquals(0, donutSliceIndexAt(ringPoint(0f), size, fractions))
        assertEquals(1, donutSliceIndexAt(ringPoint(150f), size, fractions))
        assertEquals(2, donutSliceIndexAt(ringPoint(230f), size, fractions))
    }

    @Test
    fun `a tap in the donut hole misses every slice`() {
        val fractions = listOf(0.5f, 0.3f, 0.2f)
        assertNull(donutSliceIndexAt(center, size, fractions))
    }

    @Test
    fun `a tap well outside the ring misses every slice`() {
        val fractions = listOf(0.5f, 0.3f, 0.2f)
        assertNull(donutSliceIndexAt(Offset(-500f, -500f), size, fractions))
    }

    @Test
    fun `a single full slice covers every angle`() {
        val fractions = listOf(1f)
        assertEquals(0, donutSliceIndexAt(ringPoint(-90f), size, fractions))
        assertEquals(0, donutSliceIndexAt(ringPoint(45f), size, fractions))
        assertEquals(0, donutSliceIndexAt(ringPoint(269f), size, fractions))
    }
}
