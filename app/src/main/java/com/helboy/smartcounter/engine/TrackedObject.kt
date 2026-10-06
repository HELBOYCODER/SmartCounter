package com.helboy.smartcounter.engine

import android.graphics.RectF

data class TrackedObject(
    val id: Int,
    var rect: RectF,
    var centroidX: Float,
    var centroidY: Float,
    var hits: Int = 1,
    var lost: Int = 0,
    var label: String = "Item",
    var confidence: Float = 1.0f,
    var previousSide: String? = null,
    var crossed: Boolean = false
)
