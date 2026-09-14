package com.stripedlens.calcloner.viewmodel

import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.SyncPair

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

    // Operations & Telemetry Progress
    val isOperating: Boolean = false,
    val isSyncing: Boolean = false,
    val isSyncingAll: Boolean = false,
    val syncingPairId: String? = null,
    val isClearing: Boolean = false,
    val isNuking: Boolean = false,
    val operationDone: Boolean = false,
    val progressFraction: Float = 0f,
    val progressStatusText: String = "",

    // Modal & Sheet Presentation
    val showAddEditSheet: Boolean = false,
    val pairToEdit: SyncPair? = null,
    val pairToDelete: SyncPair? = null,
    val pairToClear: SyncPair? = null,
    val pairToNuke: SyncPair? = null,
    val clearTargetEventCount: Int = 0,

    // One-shot User Feedback Toasts/Messages
    val userToastMessage: String? = null
)
