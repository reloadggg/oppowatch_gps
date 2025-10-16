package com.kartlap.se.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.kartlap.se.render.PathProjector
import com.kartlap.se.session.TrackPoint

class TrackView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val projector = PathProjector()
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val endPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }

    private var projected: List<PathProjector.Projected> = emptyList()

    fun update(points: List<TrackPoint>) {
        projected = projector.project(points)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (projected.size < 2) return
        val width = width.toFloat()
        val height = height.toFloat()
        val scaled = projected.map { it.x * width to (1f - it.y) * height }
        for (i in 0 until scaled.size - 1) {
            val (x1, y1) = scaled[i]
            val (x2, y2) = scaled[i + 1]
            canvas.drawLine(x1, y1, x2, y2, pathPaint)
        }
        val (startX, startY) = scaled.first()
        val (endX, endY) = scaled.last()
        canvas.drawCircle(startX, startY, 8f, startPaint)
        canvas.drawCircle(endX, endY, 8f, endPaint)
    }
}
