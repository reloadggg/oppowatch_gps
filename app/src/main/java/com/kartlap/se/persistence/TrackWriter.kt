package com.kartlap.se.persistence

import android.content.Context
import android.util.AtomicFile
import com.kartlap.se.session.TrackPoint
import java.io.File
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persists track points using [AtomicFile] to guarantee crash-safe writes.
 */
class TrackWriter(
    private val context: Context,
) {

    private var atomicFile: AtomicFile? = null
    private var buffer: MutableList<TrackPoint> = mutableListOf()
    private val persistedPoints: MutableList<TrackPoint> = mutableListOf()
    private val writing = AtomicBoolean(false)

    fun startNew(sessionId: String) {
        val fileName = "session_$sessionId.tmp"
        val atomic = AtomicFileUtils.create(context, fileName)
        atomicFile = atomic
        buffer.clear()
        persistedPoints.clear()
        if (atomic.baseFile.exists()) {
            atomic.baseFile.forEachLine { line ->
                if (!line.startsWith("EOF")) {
                    val values = line.split(",")
                    if (values.size >= 7) {
                        persistedPoints += TrackPoint(
                            timestamp = values[0].toLong(),
                            latitude = values[1].toDouble(),
                            longitude = values[2].toDouble(),
                            altitude = values[3].toDouble(),
                            speed = values[4].toFloat(),
                            accuracy = values[5].toFloat(),
                            bearing = values[6].toFloat(),
                        )
                    }
                }
            }
        }
    }

    fun append(point: TrackPoint) {
        buffer.add(point)
        if (buffer.size >= 10 && writing.compareAndSet(false, true)) {
            flushInternal()
        }
    }

    fun flush() {
        if (buffer.isNotEmpty()) {
            flushInternal()
        }
    }

    fun persisted(): List<TrackPoint> = persistedPoints.toList()

    private fun flushInternal() {
        val points = buffer.toList()
        if (points.isEmpty()) {
            writing.set(false)
            return
        }
        buffer.clear()
        val file = atomicFile ?: return
        persistedPoints.addAll(points)
        AtomicFileUtils.writeText(file) {
            BufferedWriter(OutputStreamWriter(this)).use { writer ->
                persistedPoints.forEach { point ->
                    writer.appendLine(point.serialize())
                }
                writer.appendLine("EOF:${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())}")
            }
        }
        writing.set(false)
    }

    fun complete(sessionId: String): String? {
        flush()
        val file = atomicFile?.baseFile ?: return null
        val finalFile = File(file.parentFile, "session_$sessionId.log")
        AtomicFileUtils.ensureParentExists(finalFile)
        if (file.exists()) {
            file.renameTo(finalFile)
        }
        persistedPoints.clear()
        return finalFile.absolutePath
    }

    fun cancel() {
        atomicFile?.baseFile?.takeIf { it.exists() }?.delete()
        atomicFile = null
        persistedPoints.clear()
    }

    private fun TrackPoint.serialize(): String =
        listOf(
            timestamp,
            latitude,
            longitude,
            altitude,
            speed,
            accuracy,
            bearing
        ).joinToString(",")
}
