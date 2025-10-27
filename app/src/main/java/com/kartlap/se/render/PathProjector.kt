package com.kartlap.se.render

import android.location.Location
import com.kartlap.se.session.TrackPoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class PathProjector {

    data class Projected(val x: Float, val y: Float)

    fun project(points: List<TrackPoint>): List<Projected> {
        if (points.isEmpty()) return emptyList()
        val origin = points.first()
        val projected = mutableListOf<Projected>()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        points.forEach { point ->
            val coords = toLocal(origin, point)
            minX = min(minX, coords.x)
            minY = min(minY, coords.y)
            maxX = max(maxX, coords.x)
            maxY = max(maxY, coords.y)
            projected += coords
        }
        val width = max(1f, maxX - minX)
        val height = max(1f, maxY - minY)
        val scale = 1f / max(width, height)
        return projected.map { Projected((it.x - minX) * scale, (it.y - minY) * scale) }
    }

    private fun toLocal(origin: TrackPoint, point: TrackPoint): Projected {
        val earthRadius = 6378137.0
        val dLat = Math.toRadians(point.latitude - origin.latitude)
        val dLon = Math.toRadians(point.longitude - origin.longitude)
        val y = dLat * earthRadius
        val x = dLon * earthRadius * cos(Math.toRadians(origin.latitude))
        return Projected(x.toFloat(), y.toFloat())
    }
}
