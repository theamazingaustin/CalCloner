package com.stripedlens.calcloner.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.CalendarSyncEngine
import com.stripedlens.calcloner.CalendarSyncScheduler
import com.stripedlens.calcloner.SettingsRepository
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel orchestrating business logic, background sync operations, and UI state
 * for [com.stripedlens.calcloner.MainActivity].
 *
 * Adheres strictly to Android clean architecture principles:
 * - Decouples UI composables from Android system services and DataStore calls.
 * - Manages long-running sync, prune, clear, and wipe coroutines safely across configuration changes.
 * - Exposes a single reactive [uiState] stream.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)
    private val context: Context get() = getApplication<Application>().applicationContext

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Observe persistent storage streams from SettingsRepository
        viewModelScope.launch {
            combine(
                repo.syncPairsFlow,
                repo.syncIntervalFlow,
                repo.syncOnLowBatteryFlow
            ) { pairs, interval, onLow ->
                Triple(pairs, interval, onLow)
            }.collect { (pairs, interval, onLow) ->
                _uiState.update {
                    it.copy(
                        syncPairs = pairs,
                        syncIntervalMinutes = interval,
                        syncOnLowBattery = onLow
                    )
                }
                rescheduleBackgroundSync()
            }
        }

        viewModelScope.launch {
            repo.disclaimerAcceptedFlow.collect { accepted ->
                _uiState.update { it.copy(isDisclaimerAccepted = accepted) }
            }
        }

        checkBatteryOptimizationStatus()
    }

    /**
     * Checks if the app is excluded from system battery optimization / deep sleep.
     */
    fun checkBatteryOptimizationStatus() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        _uiState.update { it.copy(isIgnoringBatteryOptimizations = isIgnoring) }
    }

    /**
     * Updates calendar permission status and triggers calendar discovery if granted.
     */
    fun setCalendarPermissions(hasPermissions: Boolean) {
        _uiState.update { it.copy(hasCalendarPermissions = hasPermissions) }
        if (hasPermissions) {
            refreshCalendars()
        }
    }

    /**
     * Queries the Android CalendarProvider for all calendars present on device.
     */
    fun refreshCalendars() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val calendars = CalendarSyncEngine.getAvailableCalendars(context)
                _uiState.update { it.copy(availableCalendars = calendars) }
            } catch (e: Exception) {
                _uiState.update { it.copy(userToastMessage = "Failed to load calendars: ${e.message}") }
            }
        }
    }

    /**
     * Dynamically updates background WorkManager and JobService triggers
     * based on user's active sync pairs and configured interval.
     */
    private fun rescheduleBackgroundSync() {
        val state = _uiState.value
        val enabledPairs = state.syncPairs.filter { it.isEnabled }
        val interval = state.syncIntervalMinutes
        val syncOnLowBattery = state.syncOnLowBattery

        if (enabledPairs.isEmpty() || interval == -1) {
            CalendarSyncScheduler.cancelReactiveSync(context)
            CalendarSyncScheduler.cancelSync(context)
        } else if (interval == 0) {
            CalendarSyncScheduler.scheduleReactiveSync(context, syncOnLowBattery)
            CalendarSyncScheduler.cancelSync(context)
        } else {
            CalendarSyncScheduler.cancelReactiveSync(context)
            CalendarSyncScheduler.scheduleSync(context, interval, syncOnLowBattery)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Core Sync Orchestration
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Triggered by in-app CalendarContentObserver when local calendars mutate while app is foregrounded.
     * Refreshes calendar list and automatically triggers reactive sync if configured in Instant mode.
     */
    fun onForegroundCalendarChanged() {
        refreshCalendars()
        val state = _uiState.value
        if (state.syncIntervalMinutes == 0 && !state.isOperating && state.syncPairs.any { it.isEnabled }) {
            syncAll()
        }
    }

    /**
     * Sequentially syncs all enabled sync pairs with live progress reporting.
     */
    fun syncAll() {
        val state = _uiState.value
        val enabledPairs = state.syncPairs.filter { it.isEnabled }
        if (enabledPairs.isEmpty() || state.isOperating) return

        _uiState.update {
            it.copy(
                isOperating = true,
                isSyncing = true,
                isSyncingAll = true,
                syncingPairId = null,
                operationDone = false,
                progressFraction = 0f,
                progressStatusText = "Preparing Master Sync for ${enabledPairs.size} active pair(s)..."
            )
        }

        viewModelScope.launch {
            var anyError = false
            var totalAdded = 0
            var totalUpdated = 0
            var totalRemoved = 0

            try {
                val totalPairs = enabledPairs.size
                enabledPairs.forEachIndexed { index, pair ->
                    if (!pair.isConfigValidForSync) {
                        val validationMsg = "Sync blocked: ${pair.fieldValidationError}"
                        repo.updatePairSyncStatus(pair.id, System.currentTimeMillis(), validationMsg)
                        anyError = true
                        return@forEachIndexed
                    }

                    _uiState.update {
                        it.copy(
                            syncingPairId = pair.id,
                            progressFraction = index.toFloat() / totalPairs,
                            progressStatusText = "Syncing [${index + 1}/$totalPairs]: '${pair.fromCalendarName} → ${pair.toCalendarName}'..."
                        )
                    }

                    val startTime = System.currentTimeMillis()
                    try {
                        val res = withContext(Dispatchers.IO) {
                            CalendarSyncEngine.syncEventsToTarget(
                                context = context,
                                fromCalendarId = pair.fromCalendarId,
                                toCalendarId = pair.toCalendarId,
                                daysPast = pair.daysPast,
                                daysFuture = pair.daysFuture,
                                pairId = pair.id,
                                syncTitle = pair.syncTitle,
                                customTitle = pair.customTitle,
                                titlePrefix = pair.titlePrefix,
                                titleSuffix = pair.titleSuffix,
                                syncDescription = pair.syncDescription,
                                customDescription = pair.customDescription,
                                descriptionPrefix = pair.descriptionPrefix,
                                descriptionSuffix = pair.descriptionSuffix,
                                syncLocation = pair.syncLocation,
                                customLocation = pair.customLocation,
                                syncReminders = pair.syncReminders,
                                syncAvailability = pair.syncAvailability,
                                customAvailability = pair.customAvailability,
                                syncStatus = pair.syncStatus,
                                customStatus = pair.customStatus,
                                activePairIds = state.syncPairs.map { it.id }.toSet(),
                                onProgress = { cur, tot, msg ->
                                    val pairBase = index.toFloat() / totalPairs
                                    val pairShare = 1f / totalPairs
                                    val curFraction = if (tot > 0) cur.toFloat() / tot else 0f
                                    _uiState.update {
                                        it.copy(
                                            progressFraction = pairBase + (pairShare * curFraction),
                                            progressStatusText = "[${index + 1}/$totalPairs] $msg"
                                        )
                                    }
                                }
                            )
                        }

                        totalAdded += res.insertedCount
                        totalUpdated += res.updatedCount
                        totalRemoved += res.deletedCount

                        val durationMs = System.currentTimeMillis() - startTime
                        val parts = mutableListOf<String>()
                        if (res.insertedCount > 0) parts.add("${res.insertedCount} added")
                        if (res.updatedCount > 0) parts.add("${res.updatedCount} updated")
                        if (res.deletedCount > 0) parts.add("${res.deletedCount} removed")
                        val statusMsg = if (parts.isEmpty()) "Sync complete: 0 changes." else "Sync complete: ${parts.joinToString(", ")}."

                        repo.updatePairSyncStatus(
                            pairId = pair.id,
                            timestamp = System.currentTimeMillis(),
                            status = statusMsg,
                            insertedCount = res.insertedCount,
                            updatedCount = res.updatedCount,
                            deletedCount = res.deletedCount,
                            durationMs = res.durationMs.takeIf { it > 0 } ?: durationMs
                        )
                    } catch (e: Exception) {
                        anyError = true
                        val errorMsg = "Sync failed: ${e.message}"
                        repo.updatePairSyncStatus(pair.id, System.currentTimeMillis(), errorMsg)
                    }
                }

                _uiState.update {
                    it.copy(
                        progressFraction = 1f,
                        operationDone = true,
                        progressStatusText = if (anyError) {
                            "Master Sync completed with errors. Cloned: $totalAdded, Updated: $totalUpdated, Removed: $totalRemoved."
                        } else {
                            "Master Sync complete! $totalAdded added, $totalUpdated updated, $totalRemoved removed."
                        }
                    )
                }
            } finally {
                delay(1200L)
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        isSyncing = false,
                        isSyncingAll = false,
                        syncingPairId = null
                    )
                }
            }
        }
    }

    /**
     * Executes sync for a single configured [SyncPair].
     */
    fun syncSinglePair(pair: SyncPair) {
        val state = _uiState.value
        if (state.isOperating) return

        if (!pair.isConfigValidForSync) {
            _uiState.update { it.copy(userToastMessage = "Cannot sync: ${pair.fieldValidationError}") }
            return
        }

        _uiState.update {
            it.copy(
                isOperating = true,
                isSyncing = true,
                syncingPairId = pair.id,
                operationDone = false,
                progressFraction = 0f,
                progressStatusText = "Syncing '${pair.fromCalendarName} → ${pair.toCalendarName}'..."
            )
        }

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val res = withContext(Dispatchers.IO) {
                    CalendarSyncEngine.syncEventsToTarget(
                        context = context,
                        fromCalendarId = pair.fromCalendarId,
                        toCalendarId = pair.toCalendarId,
                        daysPast = pair.daysPast,
                        daysFuture = pair.daysFuture,
                        pairId = pair.id,
                        syncTitle = pair.syncTitle,
                        customTitle = pair.customTitle,
                        titlePrefix = pair.titlePrefix,
                        titleSuffix = pair.titleSuffix,
                        syncDescription = pair.syncDescription,
                        customDescription = pair.customDescription,
                        descriptionPrefix = pair.descriptionPrefix,
                        descriptionSuffix = pair.descriptionSuffix,
                        syncLocation = pair.syncLocation,
                        customLocation = pair.customLocation,
                        syncReminders = pair.syncReminders,
                        syncAvailability = pair.syncAvailability,
                        customAvailability = pair.customAvailability,
                        syncStatus = pair.syncStatus,
                        customStatus = pair.customStatus,
                        activePairIds = state.syncPairs.map { it.id }.toSet(),
                        onProgress = { cur, tot, msg ->
                            _uiState.update {
                                it.copy(
                                    progressFraction = if (tot > 0) cur.toFloat() / tot else 0f,
                                    progressStatusText = msg
                                )
                            }
                        }
                    )
                }

                val durationMs = System.currentTimeMillis() - startTime
                val parts = mutableListOf<String>()
                if (res.insertedCount > 0) parts.add("${res.insertedCount} added")
                if (res.updatedCount > 0) parts.add("${res.updatedCount} updated")
                if (res.deletedCount > 0) parts.add("${res.deletedCount} removed")
                val statusMsg = if (parts.isEmpty()) "Sync complete: 0 changes." else "Sync complete: ${parts.joinToString(", ")}."

                repo.updatePairSyncStatus(
                    pairId = pair.id,
                    timestamp = System.currentTimeMillis(),
                    status = statusMsg,
                    insertedCount = res.insertedCount,
                    updatedCount = res.updatedCount,
                    deletedCount = res.deletedCount,
                    durationMs = res.durationMs.takeIf { it > 0 } ?: durationMs
                )

                _uiState.update {
                    it.copy(
                        progressFraction = 1f,
                        operationDone = true,
                        progressStatusText = "${pair.fromCalendarName} → ${pair.toCalendarName}: $statusMsg"
                    )
                }
            } catch (e: Exception) {
                val errorMsg = "Sync failed: ${e.message}"
                repo.updatePairSyncStatus(pair.id, System.currentTimeMillis(), errorMsg)
                _uiState.update { it.copy(progressStatusText = errorMsg) }
            } finally {
                val elapsed = System.currentTimeMillis() - startTime
                val remaining = 2000L - elapsed
                if (remaining > 0) {
                    delay(remaining)
                }
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        isSyncing = false,
                        syncingPairId = null
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Pair Persistence & Mutation Handlers
    // ─────────────────────────────────────────────────────────────────────────────

    fun upsertPair(pair: SyncPair) {
        viewModelScope.launch {
            repo.upsertSyncPair(pair)
            dismissAddEditSheet()
        }
    }

    fun saveAndSyncPair(pair: SyncPair) {
        viewModelScope.launch {
            repo.upsertSyncPair(pair)
            _uiState.update { it.copy(pairToEdit = pair) }
            syncSinglePair(pair)
        }
    }

    fun togglePairEnabled(pairId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repo.togglePairEnabled(pairId, isEnabled)
        }
    }

    fun toggleAllPairs(checked: Boolean) {
        viewModelScope.launch {
            _uiState.value.syncPairs.forEach { pair ->
                repo.togglePairEnabled(pair.id, checked)
            }
        }
    }

    fun deletePair(pair: SyncPair, deleteClonedEvents: Boolean) {
        val state = _uiState.value
        dismissAddEditSheet()
        dismissDeletePairDialog()

        viewModelScope.launch {
            if (deleteClonedEvents) {
                _uiState.update {
                    it.copy(
                        isOperating = true,
                        isPurgingCloned = true,
                        operationDone = false,
                        progressFraction = 0f,
                        progressStatusText = "Deleting cloned events for '${pair.displayName}'..."
                    )
                }

                val activeIds = state.syncPairs.map { it.id }.toSet()
                val deleted = withContext(Dispatchers.IO) {
                    CalendarSyncEngine.deleteClonedCalendarEvents(
                        context = context,
                        toCalendarId = pair.toCalendarId,
                        fromCalendarId = pair.fromCalendarId,
                        pairId = pair.id,
                        activePairIds = activeIds,
                        onProgress = { msg ->
                            _uiState.update { it.copy(progressStatusText = msg) }
                        }
                    )
                }

                repo.deleteSyncPair(pair.id)
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        isPurgingCloned = false,
                        operationDone = true,
                        progressStatusText = "Deleted pair '${pair.displayName}' and removed $deleted cloned event(s)."
                    )
                }
            } else {
                repo.deleteSyncPair(pair.id)
                _uiState.update {
                    it.copy(
                        operationDone = true,
                        progressStatusText = "Deleted sync pair '${pair.displayName}'. Cloned events were kept on target calendar."
                    )
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Navigation & Tab Management
    // ─────────────────────────────────────────────────────────────────────────────

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Delete Screen Actions
    // ─────────────────────────────────────────────────────────────────────────────

    fun selectDeleteCalendar(calendar: CalendarInfo?) {
        _uiState.update {
            it.copy(
                selectedDeleteCalendar = calendar,
                deleteConfirmationText = ""
            )
        }
    }

    fun setDeleteOperationType(type: DeleteOperationType) {
        _uiState.update { it.copy(deleteOperationType = type) }
    }

    fun setDeleteConfirmationText(text: String) {
        _uiState.update { it.copy(deleteConfirmationText = text) }
    }

    fun executeCalendarDelete() {
        val calendar = _uiState.value.selectedDeleteCalendar ?: return
        val type = _uiState.value.deleteOperationType
        val requiredText = "delete ${calendar.displayName.trim()}"
        val userText = _uiState.value.deleteConfirmationText.trim()

        if (!userText.equals(requiredText, ignoreCase = true)) {
            _uiState.update { it.copy(userToastMessage = "Confirmation text does not match.") }
            return
        }

        when (type) {
            DeleteOperationType.PURGE_CLONED -> executePurgeClonedEvents(calendar)
            DeleteOperationType.CLEAR_ALL -> executeClearAllEvents(calendar)
            DeleteOperationType.WIPE_ALL -> executeWipeAllEvents(calendar)
        }
    }

    private fun executeClearAllEvents(calendar: CalendarInfo) {
        _uiState.update {
            it.copy(
                isOperating = true,
                isClearingAll = true,
                operationDone = false,
                progressFraction = 0f,
                progressStatusText = "Clearing all events from '${calendar.displayName}'...",
                deleteConfirmationText = ""
            )
        }

        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    CalendarSyncEngine.clearAllCalendarEvents(
                        context = context,
                        toCalendarId = calendar.id,
                        fromCalendarId = null,
                        onProgress = { msg ->
                            _uiState.update { it.copy(progressStatusText = msg) }
                        }
                    )
                }
                _uiState.update {
                    it.copy(
                        operationDone = true,
                        progressStatusText = if (deleted > 0) {
                            "Clear complete: $deleted event(s) removed from '${calendar.displayName}'."
                        } else {
                            "Calendar '${calendar.displayName}' is already empty (0 deleted)."
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(progressStatusText = "Clear failed: ${e.message}") }
            } finally {
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        isClearingAll = false
                    )
                }
                refreshCalendars()
            }
        }
    }

    private fun executePurgeClonedEvents(calendar: CalendarInfo) {
        _uiState.update {
            it.copy(
                isOperating = true,
                isPurgingCloned = true,
                operationDone = false,
                progressFraction = 0f,
                progressStatusText = "Purging cloned events from '${calendar.displayName}'...",
                deleteConfirmationText = ""
            )
        }

        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    CalendarSyncEngine.deleteClonedCalendarEvents(
                        context = context,
                        toCalendarId = calendar.id,
                        fromCalendarId = null,
                        pairId = null,
                        activePairIds = emptySet(),
                        onProgress = { msg ->
                            _uiState.update { it.copy(progressStatusText = msg) }
                        }
                    )
                }
                _uiState.update {
                    it.copy(
                        operationDone = true,
                        progressStatusText = if (deleted > 0) {
                            "Purge complete: $deleted cloned event(s) removed from '${calendar.displayName}'."
                        } else {
                            "No cloned events found in '${calendar.displayName}' (0 deleted)."
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(progressStatusText = "Purge failed: ${e.message}") }
            } finally {
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        isPurgingCloned = false
                    )
                }
                refreshCalendars()
            }
        }
    }

    private fun executeWipeAllEvents(calendar: CalendarInfo) {
        _uiState.update {
            it.copy(
                isOperating = true,
                isWipingAll = true,
                operationDone = false,
                progressFraction = 0f,
                progressStatusText = "Deleting all events from '${calendar.displayName}'...",
                deleteConfirmationText = ""
            )
        }

        viewModelScope.launch {
            try {
                val deleted = withContext(Dispatchers.IO) {
                    CalendarSyncEngine.deleteAllCalendarEvents(
                        context = context,
                        toCalendarId = calendar.id,
                        fromCalendarId = null,
                        onProgress = { msg ->
                            _uiState.update { it.copy(progressStatusText = msg) }
                        }
                    )
                }
                _uiState.update {
                    it.copy(
                        operationDone = true,
                        progressStatusText = "Wipe complete: $deleted event(s) deleted from '${calendar.displayName}'."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(progressStatusText = "Wipe failed: ${e.message}") }
            } finally {
                _uiState.update {
                    it.copy(
                        isOperating = false,
                        isWipingAll = false
                    )
                }
                refreshCalendars()
            }
        }
    }

    fun executePurgeTombstones(calendar: CalendarInfo? = null) {
        val targetName = calendar?.displayName ?: "all calendars"
        _uiState.update {
            it.copy(
                isOperating = true,
                operationDone = false,
                progressFraction = 0f,
                progressStatusText = "Scanning and purging synchronized tombstones from $targetName..."
            )
        }

        viewModelScope.launch {
            try {
                val purged = withContext(Dispatchers.IO) {
                    CalendarSyncEngine.purgeSyncedTombstones(
                        context = context,
                        calendarId = calendar?.id,
                        onProgress = { msg ->
                            _uiState.update { it.copy(progressStatusText = msg) }
                        }
                    )
                }
                _uiState.update {
                    it.copy(
                        operationDone = true,
                        progressStatusText = if (purged > 0) {
                            "Optimization complete: Purged $purged dead tombstone(s) from $targetName."
                        } else {
                            "Database clean: 0 tombstones found for $targetName."
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(progressStatusText = "Tombstone purge failed: ${e.message}") }
            } finally {
                _uiState.update { it.copy(isOperating = false) }
                refreshCalendars()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // App Settings & Configuration (Import/Export, Disclaimer, Theme)
    // ─────────────────────────────────────────────────────────────────────────────

    fun saveSyncInterval(minutes: Int) {
        viewModelScope.launch {
            repo.saveSyncInterval(minutes)
        }
    }

    fun saveSyncOnLowBattery(enabled: Boolean) {
        viewModelScope.launch {
            repo.saveSyncOnLowBattery(enabled)
        }
    }

    fun acceptDisclaimer() {
        viewModelScope.launch {
            repo.acceptDisclaimer()
        }
    }

    fun saveThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repo.saveThemeMode(mode)
        }
    }

    fun exportConfiguration(context: Context) {
        viewModelScope.launch {
            try {
                val pairs = repo.syncPairsFlow.first()
                if (pairs.isEmpty()) {
                    _uiState.update { it.copy(userToastMessage = "No sync pairs to export") }
                    return@launch
                }
                val jsonString = SyncPair.listToJsonString(pairs)
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                val file = File(exportDir, "calcloner_backup_$timeStamp.json")
                file.writeText(jsonString)

                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_SUBJECT, "CalCloner Backup $timeStamp")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Export CalCloner Configuration"))
            } catch (e: Exception) {
                _uiState.update { it.copy(userToastMessage = "Export failed: ${e.message}") }
            }
        }
    }

    fun importConfiguration(jsonString: String) {
        viewModelScope.launch {
            try {
                val importedPairs = SyncPair.listFromJsonString(jsonString)
                if (importedPairs.isEmpty()) {
                    _uiState.update { it.copy(userToastMessage = "No valid sync pairs found in file") }
                    return@launch
                }

                val available = _uiState.value.availableCalendars
                val validated = importedPairs.map { pair ->
                    validateAndRemapPair(pair, available)
                }

                _uiState.update {
                    it.copy(importPreview = ImportPreviewState(pairs = validated))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(userToastMessage = "Import failed: ${e.message}") }
            }
        }
    }

    private fun validateAndRemapPair(
        pair: SyncPair,
        available: List<CalendarInfo>
    ): ValidatedImportPair {
        val directFrom = available.find { it.id == pair.fromCalendarId }
        val nameMatchedFrom = available.find { it.displayName.equals(pair.fromCalendarName, ignoreCase = true) }
        val resolvedFrom = directFrom ?: nameMatchedFrom
        val remappedFrom = directFrom == null && nameMatchedFrom != null

        val directTo = available.find { it.id == pair.toCalendarId }
        val nameMatchedTo = available.find { it.displayName.equals(pair.toCalendarName, ignoreCase = true) }
        val resolvedTo = directTo ?: nameMatchedTo
        val remappedTo = directTo == null && nameMatchedTo != null

        return when {
            resolvedFrom == null && resolvedTo == null -> {
                ValidatedImportPair(
                    originalPair = pair,
                    resolvedPair = pair,
                    status = ImportPairStatus.UNRESOLVED,
                    issueDescription = "Neither source '${pair.fromCalendarName}' nor target '${pair.toCalendarName}' found on this device"
                )
            }
            resolvedFrom == null -> {
                ValidatedImportPair(
                    originalPair = pair,
                    resolvedPair = pair,
                    status = ImportPairStatus.UNRESOLVED,
                    issueDescription = "Source calendar '${pair.fromCalendarName}' not found on this device"
                )
            }
            resolvedTo == null -> {
                ValidatedImportPair(
                    originalPair = pair,
                    resolvedPair = pair,
                    status = ImportPairStatus.UNRESOLVED,
                    issueDescription = "Target calendar '${pair.toCalendarName}' not found on this device"
                )
            }
            !resolvedTo.canWrite -> {
                ValidatedImportPair(
                    originalPair = pair,
                    resolvedPair = pair,
                    status = ImportPairStatus.UNRESOLVED,
                    issueDescription = "Target calendar '${resolvedTo.displayName}' is read-only on this device"
                )
            }
            remappedFrom || remappedTo -> {
                val remappedPair = pair.copy(
                    fromCalendarId = resolvedFrom.id,
                    fromCalendarName = resolvedFrom.displayName,
                    toCalendarId = resolvedTo.id,
                    toCalendarName = resolvedTo.displayName
                )
                val msg = buildList {
                    if (remappedFrom) add("Source remapped to '${resolvedFrom.displayName}' (ID: ${resolvedFrom.id})")
                    if (remappedTo) add("Target remapped to '${resolvedTo.displayName}' (ID: ${resolvedTo.id})")
                }.joinToString("; ")
                ValidatedImportPair(
                    originalPair = pair,
                    resolvedPair = remappedPair,
                    status = ImportPairStatus.REMAPPED,
                    remappedFrom = remappedFrom,
                    remappedTo = remappedTo,
                    issueDescription = msg
                )
            }
            else -> {
                ValidatedImportPair(
                    originalPair = pair,
                    resolvedPair = pair,
                    status = ImportPairStatus.READY
                )
            }
        }
    }

    fun applyImportPreview() {
        val preview = _uiState.value.importPreview ?: return
        viewModelScope.launch {
            try {
                val currentPairs = repo.syncPairsFlow.first().toMutableList()
                var addedCount = 0
                var updatedCount = 0
                var remappedApplied = 0

                val validItems = preview.pairs.filter { it.status != ImportPairStatus.UNRESOLVED }
                for (item in validItems) {
                    val resolved = item.resolvedPair
                    val idx = currentPairs.indexOfFirst { it.id == resolved.id }
                    if (idx >= 0) {
                        currentPairs[idx] = resolved
                        updatedCount++
                    } else {
                        currentPairs.add(resolved)
                        addedCount++
                    }
                    if (item.status == ImportPairStatus.REMAPPED) {
                        remappedApplied++
                    }
                }
                repo.saveSyncPairs(currentPairs)
                _uiState.update {
                    it.copy(
                        importPreview = null,
                        userToastMessage = "Imported ${validItems.size} pair(s) ($addedCount new, $updatedCount updated${if (remappedApplied > 0) ", $remappedApplied remapped" else ""})"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        importPreview = null,
                        userToastMessage = "Failed to apply import: ${e.message}"
                    )
                }
            }
        }
    }

    fun dismissImportPreview() {
        _uiState.update { it.copy(importPreview = null) }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // UI Navigation & Sheet / Dialog State Setters
    // ─────────────────────────────────────────────────────────────────────────────

    fun openAddPairSheet() {
        _uiState.update {
            it.copy(
                pairToEdit = null,
                showAddEditSheet = true
            )
        }
    }

    fun openEditPairSheet(pair: SyncPair) {
        _uiState.update {
            it.copy(
                pairToEdit = pair,
                showAddEditSheet = true
            )
        }
    }

    fun openEditPairSheetById(pairId: String) {
        viewModelScope.launch {
            val pairs = repo.syncPairsFlow.first()
            val target = pairs.find { it.id == pairId }
            if (target != null) {
                _uiState.update {
                    it.copy(
                        selectedTab = AppTab.SYNC,
                        pairToEdit = target,
                        showAddEditSheet = true
                    )
                }
            }
        }
    }

    fun dismissAddEditSheet() {
        _uiState.update {
            it.copy(
                showAddEditSheet = false,
                pairToEdit = null
            )
        }
    }

    fun promptDeletePair(pair: SyncPair) {
        _uiState.update { it.copy(pairToDelete = pair) }
    }

    fun dismissDeletePairDialog() {
        _uiState.update { it.copy(pairToDelete = null) }
    }

    fun clearToastMessage() {
        _uiState.update { it.copy(userToastMessage = null) }
    }
}
