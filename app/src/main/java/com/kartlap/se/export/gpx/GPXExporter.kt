package com.kartlap.se.export.gpx

import com.kartlap.se.persistence.AtomicFileUtils
import com.kartlap.se.session.TrackPoint
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GPXExporter {

    fun export(dir: File, baseName: String, points: List<TrackPoint>): File {
        val file = File(dir, "$baseName.gpx")
        AtomicFileUtils.ensureParentExists(file)
        file.outputStream().use { outputStream ->
            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                writer.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                writer.appendLine("<gpx version=\"1.1\" creator=\"KartLap SE\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
                writer.appendLine("  <trk>")
                writer.appendLine("    <name>KartLap Session</name>")
                writer.appendLine("    <trkseg>")
                val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
                points.forEach { point ->
                    writer.appendLine("      <trkpt lat=\"${point.latitude}\" lon=\"${point.longitude}\">")
                    writer.appendLine("        <ele>${point.altitude}</ele>")
                    writer.appendLine("        <time>${formatter.format(Date(point.timestamp))}</time>")
                    writer.appendLine("        <extensions>")
                    writer.appendLine("          <speed>${point.speed}</speed>")
                    writer.appendLine("          <course>${point.bearing}</course>")
                    writer.appendLine("        </extensions>")
                    writer.appendLine("      </trkpt>")
                }
                writer.appendLine("    </trkseg>")
                writer.appendLine("  </trk>")
                writer.appendLine("</gpx>")
            }
        }
        return file
    }
}
