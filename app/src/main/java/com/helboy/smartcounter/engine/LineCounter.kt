package com.helboy.smartcounter.engine

class LineCounter(
    var linePositionRatio: Float = 0.55f,
    var isVertical: Boolean = false,
    private val hysteresis: Float = 0.035f,
    private val minHitsForCount: Int = 2,
    var onCountIncrement: ((track: TrackedObject, direction: String) -> Unit)? = null
) {
    var totalCount: Int = 0
        private set
    var countForward: Int = 0  // top-to-bottom or left-to-right
        private set
    var countBackward: Int = 0 // bottom-to-top or right-to-left
        private set

    private val countedTrackIds = mutableSetOf<Int>()

    fun processTracks(tracks: List<TrackedObject>) {
        for (track in tracks) {
            if (track.hits < minHitsForCount) continue
            if (track.id in countedTrackIds) continue

            val currentCoord = if (isVertical) track.centroidX else track.centroidY
            val currentSide = if (currentCoord >= linePositionRatio) "positive" else "negative"

            val prevSide = track.previousSide
            if (prevSide == null) {
                // First observation of stable track: record side
                track.previousSide = currentSide
                continue
            }

            // Crossing check: side changed and crossed past hysteresis zone
            if (prevSide != currentSide) {
                val distancePastLine = kotlin.math.abs(currentCoord - linePositionRatio)
                if (distancePastLine >= hysteresis) {
                    countedTrackIds.add(track.id)
                    track.crossed = true
                    totalCount++

                    val direction = if (currentSide == "positive") {
                        countForward++
                        "forward"
                    } else {
                        countBackward++
                        "backward"
                    }

                    track.previousSide = currentSide
                    onCountIncrement?.invoke(track, direction)
                }
            }
        }

        // Clean up tracks that expired
        val activeIds = tracks.map { it.id }.toSet()
        countedTrackIds.retainAll(activeIds)
    }

    fun reset() {
        totalCount = 0
        countForward = 0
        countBackward = 0
        countedTrackIds.clear()
    }
}
