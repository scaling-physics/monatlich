package com.monatlich.ui.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monatlich.data.local.DatabaseBackup
import com.monatlich.domain.usecase.ExportTransactionsCsv
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the "Manage data" screen: CSV export plus full local backup / restore. */
@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exportTransactionsCsv: ExportTransactionsCsv,
    private val databaseBackup: DatabaseBackup,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    fun onEvent(event: DataEvent) {
        when (event) {
            is DataEvent.ExportCsv -> exportCsv(event.destination)
            is DataEvent.Backup -> backup(event.destination)
            DataEvent.RestoreClicked -> _uiState.update { it.copy(showRestoreConfirm = true) }
            DataEvent.RestoreDialogDismissed -> _uiState.update { it.copy(showRestoreConfirm = false) }
            is DataEvent.Restore -> restore(event.source)
            DataEvent.MessageShown -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun exportCsv(destination: Uri) {
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val result = runCatching {
                val csv = exportTransactionsCsv()
                val out = context.contentResolver.openOutputStream(destination)
                    ?: error("Could not open the chosen file for writing")
                out.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
            }
            _uiState.update {
                it.copy(
                    isExporting = false,
                    message = result.fold({ "Exported to CSV" }, { e -> "Export failed: ${e.message}" }),
                )
            }
        }
    }

    private fun backup(destination: Uri) {
        _uiState.update { it.copy(isBackingUp = true) }
        viewModelScope.launch {
            val result = runCatching { databaseBackup.backupTo(destination) }
            _uiState.update {
                it.copy(
                    isBackingUp = false,
                    message = result.fold({ "Backup saved" }, { e -> "Backup failed: ${e.message}" }),
                )
            }
        }
    }

    private fun restore(source: Uri) {
        _uiState.update { it.copy(isRestoring = true, showRestoreConfirm = false) }
        viewModelScope.launch {
            val result = runCatching { databaseBackup.restoreFrom(source) }
            _uiState.update {
                it.copy(
                    isRestoring = false,
                    message = result.fold({ null }, { e -> "Restore failed: ${e.message}" }),
                    restoreCompleted = result.isSuccess,
                )
            }
        }
    }
}
