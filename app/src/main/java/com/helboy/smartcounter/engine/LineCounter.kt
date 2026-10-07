package com.helboy.smartcounter.engine

import kotlin.math.abs
import kotlin.math.sqrt

class LineCounter(
    var linePositionRatio: Float = 0.55f,
    var isVertical: Boolean = false,
    private val hysteresis: Float = 0.055f,
    private val minHitsForCount: Int = 3,
    var onCountIncrement: ((track: TrackedObject, direction: String) -> Unit)? = null
) {
    var totalCount: Int = 0
        private set
    var countForward: Int = 0  // top-to-bottom or left-to-right
        private set
    var countBackward: Int = 0 // bottom-to-top or right-to-left
        private set

    // FIFO persistent cache of counted track IDs (does NOT purge on transient track loss!)
    private val countedTrackIds = LinkedHashSet<Int>()
    private val maxCountedHistory = 1000

    // Spatial & Temporal deduplication: prevents flickering near line or jitter from counting twice
    private data class SpatialCrossing(val x: Float, val y: Float, val timeMs: Long)
    private val recentCrossings = mutableListOf<SpatialCrossing>()
    private val spatialDedupRadius = 0.12f // 12% of screen dimension
    private val temporalDedupWindowMs = 1500L // 1.5 seconds cooldown in the same region

    fun processTracks(tracks: List<TrackedObject>) {
        val now = System.currentTimeMillis()

        // Clean up old spatial crossings older than cooldown window
        recentCrossings.removeAll { now - it.timeMs > temporalDedupWindowMs }

        for (track in tracks) {
            // Noise rejection: Must have stable observation across multiple frames
            if (track.hits < minHitsForCount) continue
            if (track.id in countedTrackIds) continue

            val currentCoord = if (isVertical) track.centroidX else track.centroidY
            val currentSide = if (currentCoord >= linePositionRatio) "positive" else "negative"

            val prevSide = track.previousSide
            if (prevSide == null) {
                // First stable observation: Record the starting side of the object
                track.previousSide = currentSide
                continue
            }

            // Directional Crossing Verification:
            // Side must change AND centroid must clearly move past the hysteresis safety zone
            if (prevSide != currentSide) {
                val distancePastLine = abs(currentCoord - linePositionRatio)
                if (distancePastLine >= hysteresis) {

                    // Spatial deduplication check: Did an object already cross in this exact spot recently?
                    val isDuplicateSpatial = recentCrossings.any { crossing ->
                        val dx = track.centroidX - crossing.x
                        val dy = track.centroidY - crossing.y
                        val dist = sqrt(dx * dx + dy * dy)
                        dist < spatialDedupRadius && (now - crossing.timeMs) < temporalDedupWindowMs
                    }

                    if (!isDuplicateSpatial) {
                        // Mark track as counted
                        track.crossed = true
                        track.previousSide = currentSide

                        // Add to persistent ID cache
                        countedTrackIds.add(track.id)
                        if (countedTrackIds.size > maxCountedHistory) {
                            val oldest = countedTrackIds.iterator().next()
                            countedTrackIds.remove(oldest)
                        }

                        // Record spatial crossing
                        recentCrossings.add(SpatialCrossing(track.centroidX, track.centroidY, now))

                        totalCount++
                        val direction = if (currentSide == "positive") {
                            countForward++
                            "forward"
                        } else {
                            countBackward++
                            "backward"
                        }

                        onCountIncrement?.invoke(track, direction)
                    } else {
                        // Suppress duplicate spatial trigger but update side
                        track.previousSide = currentSide
                    }
                }
            }
        }
    }

    fun reset() {
        totalCount = 0
        countForward = 0
        countBackward = 0
        countedTrackIds.clear()
        recentCrossings.clear()
    }
}
