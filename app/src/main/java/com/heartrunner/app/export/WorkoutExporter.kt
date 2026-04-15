package com.heartrunner.app.export

import android.location.Location
import com.heartrunner.app.data.WorkoutPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class ExportFormat { GPX, TCX }

object WorkoutExporter {

    private val isoFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun exportGpx(points: List<WorkoutPoint>, name: String = "HeartRunner Workout"): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="HeartRunner"""")
        sb.appendLine("""  xmlns="http://www.topografix.com/GPX/1/1"""")
        sb.appendLine("""  xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1">""")
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>${escapeXml(name)}</name>")
        if (points.isNotEmpty()) {
            sb.appendLine("    <time>${isoFormat.format(Date(points.first().timestamp))}</time>")
        }
        sb.appendLine("  </metadata>")
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${escapeXml(name)}</name>")
        sb.appendLine("    <trkseg>")
        for (point in points) {
            sb.appendLine("""      <trkpt lat="${point.latitude}" lon="${point.longitude}">""")
            sb.appendLine("        <ele>${String.format(Locale.US, "%.1f", point.altitude)}</ele>")
            sb.appendLine("        <time>${isoFormat.format(Date(point.timestamp))}</time>")
            if (point.heartRate > 0) {
                sb.appendLine("        <extensions>")
                sb.appendLine("          <gpxtpx:TrackPointExtension>")
                sb.appendLine("            <gpxtpx:hr>${point.heartRate}</gpxtpx:hr>")
                sb.appendLine("          </gpxtpx:TrackPointExtension>")
                sb.appendLine("        </extensions>")
            }
            sb.appendLine("      </trkpt>")
        }
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    fun exportTcx(points: List<WorkoutPoint>, name: String = "HeartRunner Workout"): String {
        val sb = StringBuilder()
        val startTime = if (points.isNotEmpty()) isoFormat.format(Date(points.first().timestamp)) else ""
        val totalTimeSec = if (points.size >= 2) {
            (points.last().timestamp - points.first().timestamp) / 1000.0
        } else 0.0

        var totalDistance = 0.0
        for (i in 1 until points.size) {
            totalDistance += distanceBetween(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
        }

        val heartRates = points.map { it.heartRate }.filter { it > 0 }
        val avgHr = if (heartRates.isNotEmpty()) heartRates.average().toInt() else 0
        val maxHr = heartRates.maxOrNull() ?: 0

        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<TrainingCenterDatabase xmlns="http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2">""")
        sb.appendLine("  <Activities>")
        sb.appendLine("""    <Activity Sport="Running">""")
        sb.appendLine("      <Id>$startTime</Id>")
        sb.appendLine("""      <Lap StartTime="$startTime">""")
        sb.appendLine("        <TotalTimeSeconds>${String.format(Locale.US, "%.1f", totalTimeSec)}</TotalTimeSeconds>")
        sb.appendLine("        <DistanceMeters>${String.format(Locale.US, "%.1f", totalDistance)}</DistanceMeters>")
        if (maxHr > 0) {
            sb.appendLine("        <MaximumHeartRateBpm><Value>$maxHr</Value></MaximumHeartRateBpm>")
            sb.appendLine("        <AverageHeartRateBpm><Value>$avgHr</Value></AverageHeartRateBpm>")
        }
        sb.appendLine("        <Track>")

        var runningDistance = 0.0
        for (i in points.indices) {
            val point = points[i]
            if (i > 0) {
                runningDistance += distanceBetween(
                    points[i - 1].latitude, points[i - 1].longitude,
                    point.latitude, point.longitude
                )
            }
            sb.appendLine("          <Trackpoint>")
            sb.appendLine("            <Time>${isoFormat.format(Date(point.timestamp))}</Time>")
            sb.appendLine("            <Position>")
            sb.appendLine("              <LatitudeDegrees>${point.latitude}</LatitudeDegrees>")
            sb.appendLine("              <LongitudeDegrees>${point.longitude}</LongitudeDegrees>")
            sb.appendLine("            </Position>")
            sb.appendLine("            <AltitudeMeters>${String.format(Locale.US, "%.1f", point.altitude)}</AltitudeMeters>")
            sb.appendLine("            <DistanceMeters>${String.format(Locale.US, "%.1f", runningDistance)}</DistanceMeters>")
            if (point.heartRate > 0) {
                sb.appendLine("            <HeartRateBpm><Value>${point.heartRate}</Value></HeartRateBpm>")
            }
            sb.appendLine("          </Trackpoint>")
        }

        sb.appendLine("        </Track>")
        sb.appendLine("      </Lap>")
        sb.appendLine("    </Activity>")
        sb.appendLine("  </Activities>")
        sb.appendLine("</TrainingCenterDatabase>")
        return sb.toString()
    }

    private fun distanceBetween(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
