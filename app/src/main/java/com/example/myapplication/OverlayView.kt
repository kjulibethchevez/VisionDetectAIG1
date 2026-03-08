package com.example.myapplication

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var boxes: List<DetectionBox> = emptyList()

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.RED
        strokeWidth = 4f
    }

    private val textPaint = Paint().apply {
        color = Color.RED
        textSize = 36f
        style = Paint.Style.FILL
    }

    private var imageWidth = 1
    private var imageHeight = 1
    private var scaleX = 1f
    private var scaleY = 1f

    fun setBoxes(detections: List<DetectionBox>) {
        boxes = detections
        invalidate()
    }

    fun setImageBounds(imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int) {
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        scaleX = viewWidth.toFloat() / imageWidth
        scaleY = viewHeight.toFloat() / imageHeight
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        boxes.forEach { box ->
            val left = box.rect.left * scaleX
            val top = box.rect.top * scaleY
            val right = box.rect.right * scaleX
            val bottom = box.rect.bottom * scaleY

            canvas.drawRect(left, top, right, bottom, boxPaint)
            canvas.drawText("${box.label} (${(box.score * 100).toInt()}%)", left, top - 10, textPaint)
        }
    }
}
