package com.stripedlens.calcloner.viewmodel

import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.SyncPair

enum class AppTab {
    SYNC,
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

    // One-shot User Feedback Toasts/Messages
    val userToastMessage: String? = null
)
