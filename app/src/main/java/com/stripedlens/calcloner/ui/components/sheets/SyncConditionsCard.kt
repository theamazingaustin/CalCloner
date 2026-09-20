package com.stripedlens.calcloner.ui.components.sheets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stripedlens.calcloner.ui.components.filters.EventConditionFilterCard
import com.stripedlens.calcloner.ui.components.filters.EventFilterUiState

/**
 * Delegating bridge pointing to the modular [EventConditionFilterCard].
 */
@Composable
fun SyncConditionsCard(
    modifier: Modifier = Modifier,
    initialState: EventFilterUiState = EventFilterUiState(),
    onStateChange: (EventFilterUiState) -> Unit = {}
) {
    EventConditionFilterCard(
        modifier = modifier,
        initialState = initialState,
        onStateChange = onStateChange
    )
}
