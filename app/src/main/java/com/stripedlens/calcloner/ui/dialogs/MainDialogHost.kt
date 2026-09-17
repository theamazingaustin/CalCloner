package com.stripedlens.calcloner.ui.dialogs

import androidx.compose.runtime.Composable
import com.stripedlens.calcloner.SyncPair
import com.stripedlens.calcloner.ui.components.AddEditSyncPairSheet
import com.stripedlens.calcloner.viewmodel.MainUiState

/**
 * Centralized dialog and bottom sheet orchestrator for MainActivity.
 * Declutters the main screen scaffold by coordinating all dialog presentation states.
 */
@Composable
fun MainDialogHost(
    uiState: MainUiState,
    showBatteryInfoDialog: Boolean,
    onAcceptDisclaimer: () -> Unit,
    onContinueBatteryDialog: () -> Unit,
    onDismissBatteryDialog: () -> Unit,
    onDismissAddEditSheet: () -> Unit,
    onSavePair: (SyncPair) -> Unit,
    onSaveAndSync: (SyncPair) -> Unit,
    onSyncSinglePair: (SyncPair) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onDeletePairWithOptions: (SyncPair, Boolean) -> Unit,
    onDismissDeletePairDialog: () -> Unit,
    onApplyImportPreview: () -> Unit,
    onDismissImportPreview: () -> Unit
) {
    // 1. First-Run Consent / Disclaimer Popup
    if (!uiState.isDisclaimerAccepted) {
        DisclaimerConsentDialog(onAccept = onAcceptDisclaimer)
    }

    // 2. Battery Optimization Educational Pre-Dialog
    if (showBatteryInfoDialog) {
        BatteryOptimizationInfoDialog(
            onContinue = onContinueBatteryDialog,
            onDismiss = onDismissBatteryDialog
        )
    }

    // 3. Add / Edit Sync Pair Modal Bottom Sheet
    if (uiState.showAddEditSheet) {
        val currentPairToEdit = uiState.syncPairs.find { it.id == uiState.pairToEdit?.id } ?: uiState.pairToEdit
        AddEditSyncPairSheet(
            pairToEdit = currentPairToEdit,
            existingPairs = uiState.syncPairs,
            availableCalendars = uiState.availableCalendars,
            onDismiss = onDismissAddEditSheet,
            onSavePair = onSavePair,
            onSaveAndSync = onSaveAndSync,
            onSyncNow = onSyncSinglePair,
            isSyncing = uiState.isSyncing && (uiState.syncingPairId == currentPairToEdit?.id),
            isIgnoringBatteryOptimizations = uiState.isIgnoringBatteryOptimizations,
            onOpenBatterySettings = onOpenBatterySettings,
            onDeletePairWithOptions = onDeletePairWithOptions
        )
    }

    // 4. Delete Pair Confirmation Dialog
    uiState.pairToDelete?.let { pair ->
        DeletePairDialog(
            pair = pair,
            onDismiss = onDismissDeletePairDialog,
            onConfirmDelete = { deleteClonedEvents ->
                onDeletePairWithOptions(pair, deleteClonedEvents)
            }
        )
    }

    // 5. Configuration Import Preview Dialog
    uiState.importPreview?.let { preview ->
        ConfigImportPreviewDialog(
            previewState = preview,
            onConfirmApply = onApplyImportPreview,
            onDismiss = onDismissImportPreview
        )
    }
}
