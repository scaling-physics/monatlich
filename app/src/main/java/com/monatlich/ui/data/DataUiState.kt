package com.monatlich.ui.data

import android.net.Uri
import androidx.compose.runtime.Immutable

/** Backs the "Manage data" screen: CSV export plus full local backup / restore. */
@Immutable
data class DataUiState(
    val isExporting: Boolean = false,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    /** One-shot snackbar text; cleared with [DataEvent.MessageShown]. */
    val message: String? = null,
    val showRestoreConfirm: Boolean = false,
    /** Set once a restore has been written to disk; the screen restarts the app on this. */
    val restoreCompleted: Boolean = false,
) {
    val isBusy: Boolean get() = isExporting || isBackingUp || isRestoring
}

sealed interface DataEvent {
    data class ExportCsv(val destination: Uri) : DataEvent
    data class Backup(val destination: Uri) : DataEvent
    data object RestoreClicked : DataEvent
    data object RestoreDialogDismissed : DataEvent
    data class Restore(val source: Uri) : DataEvent
    data object MessageShown : DataEvent
}
