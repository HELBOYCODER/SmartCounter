package com.helboy.smartcounter.data

data class CounterSession(
    val id: Long = 0,
    val timestamp: String,
    val totalCount: Int,
    val countForward: Int,
    val countBackward: Int,
    val mode: String
)
