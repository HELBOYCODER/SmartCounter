package com.helboy.smartcounter

import android.graphics.RectF
import com.helboy.smartcounter.engine.IouTracker
import com.helboy.smartcounter.engine.LineCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IouTrackerTest {

    @Test
    fun testIouCalculation() {
        val r1 = RectF(0f, 0f, 10f, 10f)
        val r2 = RectF(0f, 0f, 10f, 10f)
        val iou = IouTracker.calculateIou(r1, r2)
        assertEquals(1.0f, iou, 0.001f)

        val r3 = RectF(20f, 20f, 30f, 30f)
        val iouDisjoint = IouTracker.calculateIou(r1, r3)
        assertEquals(0.0f, iouDisjoint, 0.001f)
    }

    @Test
    fun testLineCounterCrossing() {
        val counter = LineCounter(linePositionRatio = 0.5f, isVertical = false)
        val tracker = IouTracker()

        // 1. Initial position above line (centroid = 0.3f < 0.5f)
        val boxAbove = RectF(0.2f, 0.2f, 0.4f, 0.4f)
        var tracks = tracker.update(listOf(Pair(boxAbove, 0.9f)))
        counter.processTracks(tracks)
        assertEquals(0, counter.totalCount)

        // 2. Stable second frame above line
        tracks = tracker.update(listOf(Pair(boxAbove, 0.9f)))
        counter.processTracks(tracks)
        assertEquals(0, counter.totalCount)

        // 3. Move below line (centroid = 0.7f > 0.5f)
        val boxBelow = RectF(0.2f, 0.6f, 0.4f, 0.8f)
        tracks = tracker.update(listOf(Pair(boxBelow, 0.9f)))
        counter.processTracks(tracks)
        assertEquals(1, counter.totalCount)
        assertEquals(1, counter.countForward)

        // 4. Repeated frames below line: no double count
        tracks = tracker.update(listOf(Pair(boxBelow, 0.9f)))
        counter.processTracks(tracks)
        assertEquals(1, counter.totalCount)
    }
}
