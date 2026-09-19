package com.stripedlens.calcloner.ui.components.sheets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.stripedlens.calcloner.ui.components.filters.EventConditionFilterCard

/**
 * Delegating bridge pointing to the modular [EventConditionFilterCard].
 */
@Composable
fun SyncConditionsCard(
    modifier: Modifier = Modifier
) {
    EventConditionFilterCard(modifier = modifier)
}
