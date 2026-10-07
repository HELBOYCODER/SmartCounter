package com.helboy.smartcounter.engine

import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

class IouTracker(
    private val iouThreshold: Float = 0.25f,
    private val maxLost: Int = 15
) {
    private var nextId = 1
    private val tracks = mutableMapOf<Int, TrackedObject>()

    fun update(detectedBoxes: List<Pair<RectF, Float>>): List<TrackedObject> {
        val assignedTrackIds = mutableSetOf<Int>()
        val usedDetections = mutableSetOf<Int>()

        // 1. Match existing tracks to best detected box by IoU
        for ((trackId, track) in tracks.entries.toList()) {
            var bestDetectionIdx = -1
            var bestIou = iouThreshold

            for ((detIdx, det) in detectedBoxes.withIndex()) {
                if (detIdx in usedDetections) continue
                val iou = calculateIou(track.rect, det.first)
                if (iou > bestIou) {
                    bestIou = iou
                    bestDetectionIdx = detIdx
                }
            }

            if (bestDetectionIdx != -1) {
                usedDetections.add(bestDetectionIdx)
                assignedTrackIds.add(trackId)
                val (newRect, conf) = detectedBoxes[bestDetectionIdx]
                track.rect = newRect
                track.centroidX = newRect.centerX()
                track.centroidY = newRect.centerY()
                track.confidence = conf
                track.hits++
                track.lost = 0
            } else {
                track.lost++
            }
        }

        // 2. Remove tracks that were lost for too long
        val iterator = tracks.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lost > maxLost) {
                iterator.remove()
            }
        }

        // 3. Create new tracks for unmatched detections
        for ((detIdx, det) in detectedBoxes.withIndex()) {
            if (detIdx !in usedDetections) {
                val (newRect, conf) = det
                val newTrack = TrackedObject(
                    id = nextId++,
                    rect = newRect,
                    centroidX = newRect.centerX(),
                    centroidY = newRect.centerY(),
                    hits = 1,
                    lost = 0,
                    confidence = conf
                )
                tracks[newTrack.id] = newTrack
            }
        }

        return tracks.values.toList()
    }

    fun reset() {
        tracks.clear()
        nextId = 1
    }

    fun getActiveTracks(): List<TrackedObject> = tracks.values.toList()

    /**
     * Verified tracks: Observed in at least [minHits] frames.
     * Prevents single-frame shadow/reflection glitches from triggering counts.
     */
    fun getVerifiedTracks(minHits: Int = 2): List<TrackedObject> {
        return tracks.values.filter { it.hits >= minHits }
    }

    companion object {
        fun calculateIou(a: RectF, b: RectF): Float {
            val interLeft = max(a.left, b.left)
            val interTop = max(a.top, b.top)
            val interRight = min(a.right, b.right)
            val interBottom = min(a.bottom, b.bottom)

            val interWidth = max(0f, interRight - interLeft)
            val interHeight = max(0f, interBottom - interTop)
            val intersection = interWidth * interHeight

            if (intersection <= 0f) return 0f

            val areaA = a.width() * a.height()
            val areaB = b.width() * b.height()
            val union = areaA + areaB - intersection

            return if (union > 0f) intersection / union else 0f
        }
    }
}
