package com.kartlap.se.export.csv

import com.kartlap.se.persistence.AtomicFileUtils
import com.kartlap.se.session.TrackPoint
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter

class CSVExporter {

    fun export(dir: File, baseName: String, points: List<TrackPoint>): File {
        val file = File(dir, "$baseName.csv")
        AtomicFileUtils.ensureParentExists(file)
        file.outputStream().use { outputStream ->
            BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                writer.appendLine("timestamp,lat,lon,alt,speed,accuracy,bearing")
                points.forEach { point ->
                    writer.appendLine(listOf(
                        point.timestamp,
                        point.latitude,
                        point.longitude,
                        point.altitude,
                        point.speed,
                        point.accuracy,
                        point.bearing
                    ).joinToString(","))
                }
            }
        }
        return file
    }
}
