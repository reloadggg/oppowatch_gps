package com.kartlap.se.export

import android.content.Context
import com.kartlap.se.export.csv.CSVExporter
import com.kartlap.se.export.gpx.GPXExporter
import com.kartlap.se.session.TrackPoint
import com.kartlap.se.session.TrackSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportManager(
    private val context: Context,
    private val sessionManager: TrackSessionManager,
    private val gpxExporter: GPXExporter = GPXExporter(),
    private val csvExporter: CSVExporter = CSVExporter(),
) {

    suspend fun export(sessionId: String, points: List<TrackPoint>, formats: Set<ExportFormat>): List<File> =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "exports")
            if (!dir.exists()) dir.mkdirs()
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val baseName = "session_${sessionId.take(8)}_$timestamp"
            val outputs = mutableListOf<File>()
            if (formats.contains(ExportFormat.GPX)) {
                outputs += gpxExporter.export(dir, baseName, points)
            }
            if (formats.contains(ExportFormat.CSV)) {
                outputs += csvExporter.export(dir, baseName, points)
            }
            sessionManager.markCompleted(sessionId)
            outputs
        }

    enum class ExportFormat {
        GPX, CSV
    }
}
