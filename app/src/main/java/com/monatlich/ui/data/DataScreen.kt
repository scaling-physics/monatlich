package com.monatlich.ui.data

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monatlich.ui.theme.MonatlichTheme
import java.time.LocalDate

const val DATA_SCREEN_TAG = "data_screen"
const val DATA_EXPORT_CSV_TAG = "data_export_csv"
const val DATA_BACKUP_TAG = "data_backup"
const val DATA_RESTORE_TAG = "data_restore"
const val DATA_RESTORE_CONFIRM_TAG = "data_restore_confirm_dialog"

/** Hilt entry point for the "Manage data" drill-down from Settings. */
@Composable
fun DataRoute(
    onBack: () -> Unit,
    viewModel: DataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DataScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    state: DataUiState,
    onEvent: (DataEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { onEvent(DataEvent.ExportCsv(it)) }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { onEvent(DataEvent.Backup(it)) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onEvent(DataEvent.Restore(it)) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            onEvent(DataEvent.MessageShown)
        }
    }
    LaunchedEffect(state.restoreCompleted) {
        if (state.restoreCompleted) restartApp(context)
    }

    Scaffold(
        modifier = modifier.testTag(DATA_SCREEN_TAG),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Manage data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "export-header") { SectionHeader("Export") }
            item(key = "export-csv") {
                DataRow(
                    icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                    title = "Export to CSV",
                    subtitle = "Every transaction, as a spreadsheet file",
                    busy = state.isExporting,
                    enabled = !state.isBusy,
                    onClick = { exportLauncher.launch(defaultFileName("monatlich-transactions", "csv")) },
                    modifier = Modifier.testTag(DATA_EXPORT_CSV_TAG),
                )
            }

            item(key = "backup-header") { SectionHeader("Backup") }
            item(key = "backup") {
                DataRow(
                    icon = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
                    title = "Back up all data",
                    subtitle = "One file with every category, budget and transaction",
                    busy = state.isBackingUp,
                    enabled = !state.isBusy,
                    onClick = { backupLauncher.launch(defaultFileName("monatlich-backup", "db")) },
                    modifier = Modifier.testTag(DATA_BACKUP_TAG),
                )
            }
            item(key = "restore") {
                DataRow(
                    icon = { Icon(Icons.Outlined.CloudDownload, contentDescription = null) },
                    title = "Restore from backup",
                    subtitle = "Replaces everything currently on this device",
                    busy = state.isRestoring,
                    enabled = !state.isBusy,
                    onClick = { onEvent(DataEvent.RestoreClicked) },
                    modifier = Modifier.testTag(DATA_RESTORE_TAG),
                )
            }
        }
    }

    if (state.showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { onEvent(DataEvent.RestoreDialogDismissed) },
            modifier = Modifier.testTag(DATA_RESTORE_CONFIRM_TAG),
            title = { Text("Restore from backup?") },
            text = {
                Text(
                    "This replaces every category, budget and transaction currently on this device " +
                        "with what's in the backup file. This can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(DataEvent.RestoreDialogDismissed)
                    restoreLauncher.launch(arrayOf("*/*"))
                }) {
                    Text("Choose file", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(DataEvent.RestoreDialogDismissed) }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, start = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun DataRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun defaultFileName(prefix: String, extension: String): String = "$prefix-${LocalDate.now()}.$extension"

/** Relaunches the app's main activity in a fresh task, then kills this process. */
private fun restartApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    val restartIntent = Intent.makeRestartActivityTask(launchIntent.component)
    context.startActivity(restartIntent)
    Runtime.getRuntime().exit(0)
}

// --- Previews ------------------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun DataScreenPreview() {
    MonatlichTheme {
        DataScreen(state = DataUiState(), onEvent = {}, onBack = {})
    }
}
