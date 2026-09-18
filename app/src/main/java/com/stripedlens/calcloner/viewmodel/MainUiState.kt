package com.stripedlens.calcloner.viewmodel

import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.SyncPair

enum class AppTab {
    SYNC,
    ONE_TIME,
    DELETE
}

enum class DeleteOperationType {
    PURGE_CLONED,
    CLEAR_ALL,
    WIPE_ALL
}

/**
 * Immutable representation of the complete UI state for [com.stripedlens.calcloner.MainActivity].
 *
 * All state mutations are handled exclusively through [MainViewModel] to provide a
 * single source of truth and unidirectional data flow.
 */
data class MainUiState(
    // Calendar & Pair Configurations
    val syncPairs: List<SyncPair> = emptyList(),
    val availableCalendars: List<CalendarInfo> = emptyList(),
    val syncIntervalMinutes: Int = 0,
    val syncOnLowBattery: Boolean = false,
    val isDisclaimerAccepted: Boolean = true,
    val hasCalendarPermissions: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = true,

    // Navigation Tab
    val selectedTab: AppTab = AppTab.SYNC,

    // Delete Screen State
    val selectedDeleteCalendar: CalendarInfo? = null,
    val deleteOperationType: DeleteOperationType = DeleteOperationType.PURGE_CLONED,
    val deleteConfirmationText: String = "",

    // Operations & Telemetry Progress
    val isOperating: Boolean = false,
    val isSyncing: Boolean = false,
    val isSyncingAll: Boolean = false,
    val syncingPairId: String? = null,
    val isPurgingCloned: Boolean = false,
    val isClearingAll: Boolean = false,
    val isWipingAll: Boolean = false,
    val operationDone: Boolean = false,
    val progressFraction: Float = 0f,
    val progressStatusText: String = "",

    // Modal & Sheet Presentation
    val showAddEditSheet: Boolean = false,
    val pairToEdit: SyncPair? = null,
    val pairToDelete: SyncPair? = null,
    val importPreview: ImportPreviewState? = null,

    // One-shot User Feedback Toasts/Messages
    val userToastMessage: String? = null
)

enum class ImportPairStatus {
    READY,
    REMAPPED,
    UNRESOLVED
}

data class ValidatedImportPair(
    val originalPair: SyncPair,
    val resolvedPair: SyncPair,
    val status: ImportPairStatus,
    val remappedFrom: Boolean = false,
    val remappedTo: Boolean = false,
    val issueDescription: String? = null
)

data class ImportPreviewState(
    val pairs: List<ValidatedImportPair>
) {
    val totalCount: Int get() = pairs.size
    val readyCount: Int get() = pairs.count { it.status == ImportPairStatus.READY }
    val remappedCount: Int get() = pairs.count { it.status == ImportPairStatus.REMAPPED }
    val unresolvedCount: Int get() = pairs.count { it.status == ImportPairStatus.UNRESOLVED }
    val canApply: Boolean get() = pairs.any { it.status != ImportPairStatus.UNRESOLVED }
}

