package com.example.myapplication

import android.graphics.RectF

data class DetectionBox(
    val rect: RectF,
    val label: String,
    val score: Float
)
