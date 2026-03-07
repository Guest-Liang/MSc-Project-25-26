package icu.guestliang.nfcworkflow.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import icu.guestliang.nfcworkflow.R

/**
 * Returns a localized string based on the provided status.
 */
@Composable
fun getLocalizedStatus(status: String): String {
    return when (status.lowercase()) {
        "created" -> stringResource(R.string.status_created)
        "assigned" -> stringResource(R.string.status_assigned)
        "unassigned" -> stringResource(R.string.status_unassigned)
        "completed" -> stringResource(R.string.status_completed)
        else -> status
    }
}