package com.stripedlens.calcloner.engine

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.DocumentsContract
import android.util.Log
import com.stripedlens.calcloner.SyncEvent
import com.stripedlens.calcloner.domain.ics.ExportFormat
import com.stripedlens.calcloner.domain.ics.IcsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.absoluteValue

data class IcsExportResult(
    val exportedCount: Int,
    val totalEvents: Int,
    val fileCount: Int,
    val destinationSummary: String,
    val durationMs: Long
)

data class IcsImportResult(
    val importedCount: Int,
    val skippedCount: Int,
    val totalParsed: Int,
    val calendarName: String,
    val durationMs: Long
)

/**
 * CalendarIcsManager handles exporting one or more calendars to individual .ics / .csv files
 * (either directly into a SAF folder tree or packed into a ZIP archive with a manifest),
 * and importing single .ics files into any writable Android calendar with duplicate prevention
 * and CalCloner rollback tagging.
 */
object CalendarIcsManager {
    private const val TAG = "CalendarIcsManager"

    /**
     * Exports selected calendars to individual files inside a user-selected SAF folder tree.
     */
    suspend fun exportCalendarsToFolder(
        context: Context,
        calendarIds: List<Long>,
        format: ExportFormat = ExportFormat.ICS,
        daysPast: Int = 30,
        daysFuture: Int = 90,
        folderUri: Uri,
        onProgress: ((current: Int, total: Int, calendarName: String) -> Unit)? = null
    ): IcsExportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        if (calendarIds.isEmpty()) {
            return@withContext IcsExportResult(0, 0, 0, "No calendars selected", 0)
        }

        val allCals = CalendarProviderReader.getAvailableCalendars(context).associateBy { it.id }
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
        )

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        var totalExportedEvents = 0
        var fileCount = 0

        calendarIds.forEachIndexed { index, calId ->
            val cal = allCals[calId]
            val rawName = cal?.displayName?.ifBlank { null } ?: "Calendar_$calId"
            val safeName = sanitizeFilename(rawName)
            val fileName = "$safeName - Export - $timestamp.${format.extension}"

            onProgress?.invoke(index + 1, calendarIds.size, rawName)

            val events = CalendarProviderReader.readSourceEvents(
                context = context,
                fromCalendarId = calId,
                daysPast = daysPast,
                daysFuture = daysFuture
            )

            val colorHex = cal?.color?.let { String.format("#%06X", 0xFFFFFF and it) }

            val docFileUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentDocUri,
                format.mimeType,
                fileName
            )

            if (docFileUri != null) {
                context.contentResolver.openOutputStream(docFileUri)?.use { outputStream ->
                    writeEventsToStream(events, rawName, colorHex, format, outputStream)
                }
                fileCount++
                totalExportedEvents += events.size
            } else {
                Log.e(TAG, "Failed to create document for calendar: $rawName ($fileName)")
            }
        }

        val duration = System.currentTimeMillis() - startTime
        IcsExportResult(
            exportedCount = totalExportedEvents,
            totalEvents = totalExportedEvents,
            fileCount = fileCount,
            destinationSummary = "Exported $fileCount file(s) to folder",
            durationMs = duration
        )
    }

    /**
     * Exports selected calendars to individual files packaged inside a single .zip archive
     * alongside an export_summary.txt manifest.
     */
    suspend fun exportCalendarsToZip(
        context: Context,
        calendarIds: List<Long>,
        format: ExportFormat = ExportFormat.ICS,
        daysPast: Int = 30,
        daysFuture: Int = 90,
        zipUri: Uri,
        onProgress: ((current: Int, total: Int, calendarName: String) -> Unit)? = null
    ): IcsExportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        if (calendarIds.isEmpty()) {
            return@withContext IcsExportResult(0, 0, 0, "No calendars selected", 0)
        }

        val allCals = CalendarProviderReader.getAvailableCalendars(context).associateBy { it.id }
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

        var totalExportedEvents = 0
        var fileCount = 0
        val manifestLines = mutableListOf<String>()
        manifestLines.add("CalCloner Calendar Export Manifest")
        manifestLines.add("=================================")
        manifestLines.add("Export Date: $formattedDate")
        manifestLines.add("Format: ${format.displayName}")
        manifestLines.add("Date Range: -$daysPast days to +$daysFuture days")
        manifestLines.add("")
        manifestLines.add("Exported Calendars:")

        val outputStream = context.contentResolver.openOutputStream(zipUri)
            ?: throw IllegalStateException("Cannot open output stream for ZIP file")

        ZipOutputStream(outputStream).use { zipOut ->
            calendarIds.forEachIndexed { index, calId ->
                val cal = allCals[calId]
                val rawName = cal?.displayName?.ifBlank { null } ?: "Calendar_$calId"
                val safeName = sanitizeFilename(rawName)
                val fileName = "$safeName - Export - $timestamp.${format.extension}"

                onProgress?.invoke(index + 1, calendarIds.size, rawName)

                val events = CalendarProviderReader.readSourceEvents(
                    context = context,
                    fromCalendarId = calId,
                    daysPast = daysPast,
                    daysFuture = daysFuture
                )

                val colorHex = cal?.color?.let { String.format("#%06X", 0xFFFFFF and it) }

                // Add ZIP entry for this calendar
                val zipEntry = ZipEntry(fileName)
                zipOut.putNextEntry(zipEntry)
                writeEventsToStream(events, rawName, colorHex, format, zipOut)
                zipOut.closeEntry()

                fileCount++
                totalExportedEvents += events.size
                manifestLines.add(" - $rawName: ${events.size} events ($fileName)")
            }

            // Write export_summary.txt manifest
            manifestLines.add("")
            manifestLines.add("Total Calendars: $fileCount")
            manifestLines.add("Total Events: $totalExportedEvents")
            val summaryEntry = ZipEntry("export_summary.txt")
            zipOut.putNextEntry(summaryEntry)
            zipOut.write(manifestLines.joinToString("\n").toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }

        val duration = System.currentTimeMillis() - startTime
        IcsExportResult(
            exportedCount = totalExportedEvents,
            totalEvents = totalExportedEvents,
            fileCount = fileCount,
            destinationSummary = "Saved $fileCount calendar file(s) into ZIP archive",
            durationMs = duration
        )
    }

    /**
     * Inspects and parses an external .ics file for pre-import validation and preview.
     */
    suspend fun inspectIcsFile(
        context: Context,
        fileUri: Uri
    ): List<SyncEvent> = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(fileUri)
            ?: throw IllegalStateException("Cannot read selected file: $fileUri")
        inputStream.use { stream ->
            IcsEngine.parse(stream)
        }
    }

    /**
     * Imports parsed [SyncEvent] list into the target calendar.
     * Prevents duplicates by checking existing events with identical title + start time,
     * and tags all inserted events with [CalCloner-ID: import:<id>] and calcloner://event/import/<id>.
     */
    suspend fun importIcsEvents(
        context: Context,
        targetCalendarId: Long,
        events: List<SyncEvent>,
        onProgress: ((current: Int, total: Int, message: String) -> Unit)? = null,
        onSelfWrite: (() -> Unit)? = null
    ): IcsImportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val allCals = CalendarProviderReader.getAvailableCalendars(context).associateBy { it.id }
        val targetCal = allCals[targetCalendarId]
        val targetCalName = targetCal?.displayName ?: "Calendar_$targetCalendarId"

        if (events.isEmpty()) {
            return@withContext IcsImportResult(0, 0, 0, targetCalName, 0)
        }

        // Find date boundaries of events to import for fast duplicate detection query
        val minDtStart = events.minOfOrNull { it.dtStart } ?: System.currentTimeMillis()
        val maxDtStart = events.maxOfOrNull { it.dtStart } ?: System.currentTimeMillis()

        // Query target calendar existing events in that span
        val existingKeys = mutableSetOf<String>()
        val queryUri = CalendarContract.Events.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.ALL_DAY
        )
        val selection = "${CalendarContract.Events.CALENDAR_ID} = ? AND ${CalendarContract.Events.DELETED} = 0 AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(
            targetCalendarId.toString(),
            (minDtStart - 86400000L).toString(),
            (maxDtStart + 86400000L).toString()
        )

        context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
            val titleCol = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val startCol = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val allDayCol = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
            while (cursor.moveToNext()) {
                val title = if (titleCol != -1) cursor.getString(titleCol) ?: "" else ""
                val start = if (startCol != -1) cursor.getLong(startCol) else 0L
                val allDay = if (allDayCol != -1) cursor.getInt(allDayCol) else 0
                existingKeys.add("${title.trim()}_${start}_$allDay")
            }
        }

        var importedCount = 0
        var skippedCount = 0
        val total = events.size

        val defaultTz = TimeZone.getDefault().id

        events.forEachIndexed { index, event ->
            val title = event.title?.trim() ?: ""
            val key = "${title}_${event.dtStart}_${event.allDay}"

            if (existingKeys.contains(key)) {
                skippedCount++
                onProgress?.invoke(index + 1, total, "Skipping duplicate: $title")
                return@forEachIndexed
            }

            onProgress?.invoke(index + 1, total, "Importing: ${title.ifEmpty { "Event" }}")

            val safeId = if (event.id > 0) event.id else (System.currentTimeMillis() + index).absoluteValue

            val values = CalendarEventWriter.buildEventValues(
                context = context,
                event = event.copy(id = safeId),
                toCalendarId = targetCalendarId,
                uriPrefix = "calcloner://event/",
                defaultTimeZone = defaultTz,
                targetParentId = null,
                pairId = "import",
                syncTitle = true,
                syncDescription = true,
                syncLocation = true,
                syncAvailability = true,
                syncStatus = true,
                syncAccessLevel = true,
                syncAttendees = false
            )

            try {
                val insertedUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                if (insertedUri != null) {
                    val newEventId = ContentUris.parseId(insertedUri)
                    if (event.reminders.isNotEmpty()) {
                        CalendarEventWriter.syncReminders(context, newEventId, event.reminders)
                    }
                    existingKeys.add(key)
                    importedCount++
                } else {
                    skippedCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert imported event: ${event.title}", e)
                skippedCount++
            }
        }

        if (importedCount > 0) {
            onSelfWrite?.invoke()
        }

        val duration = System.currentTimeMillis() - startTime
        IcsImportResult(
            importedCount = importedCount,
            skippedCount = skippedCount,
            totalParsed = total,
            calendarName = targetCalName,
            durationMs = duration
        )
    }

    private fun writeEventsToStream(
        events: List<SyncEvent>,
        calendarName: String,
        calendarColorHex: String?,
        format: ExportFormat,
        outputStream: OutputStream
    ) {
        when (format) {
            ExportFormat.ICS -> {
                IcsEngine.serializeToStream(
                    events = events,
                    calendarName = calendarName,
                    calendarColorHex = calendarColorHex,
                    outputStream = outputStream
                )
            }
            ExportFormat.CSV -> {
                IcsEngine.serializeCsvToStream(
                    events = events,
                    outputStream = outputStream
                )
            }
        }
    }

    /**
     * Replaces illegal filesystem characters in calendar names with underscores.
     */
    fun sanitizeFilename(name: String): String {
        val sanitized = name
            .replace(Regex("[\\x00-\\x1F\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\.{2,}"), "_")
            .replace(Regex("^\\.+|\\.+$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (sanitized.isEmpty() || sanitized.all { it == '_' || it == '.' }) {
            "Calendar"
        } else {
            sanitized
        }
    }
}
