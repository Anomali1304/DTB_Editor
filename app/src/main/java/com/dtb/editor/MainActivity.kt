package com.dtb.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dtb.editor.ui.screens.CpuTopologyScreen
import com.dtb.editor.ui.screens.EditorScreen
import com.dtb.editor.ui.theme.DtbEditorTheme
import com.dtb.editor.viewmodel.AppTab
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.OpenFileState
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DtbEditorTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: DtbViewModel = viewModel()) {
    val context = LocalContext.current
    val fileState by viewModel.openFileState.collectAsState()
    val pendingMulti by viewModel.pendingMultiDtb.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    var showDiscardDialog by remember { mutableStateOf(false) }

    // Handle back navigation
    BackHandler(enabled = fileState != null) {
        if (fileState?.isModified == true) {
            showDiscardDialog = true
        } else {
            viewModel.closeFile()
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Discard them and return to the start screen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.closeFile()
                    showDiscardDialog = false
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // SAF Open Launcher
    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openUri(uri, context)
        }
    }

    // SAF Save Launcher
    var bytesToSave by remember { mutableStateOf<ByteArray?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val bytes = bytesToSave
        if (uri != null && bytes != null) {
            viewModel.writeSavedFile(uri, context, bytes)
            bytesToSave = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (fileState != null) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = fileState!!.fileName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val entry = fileState!!.dtbEntries.getOrNull(fileState!!.selectedEntryIndex)
                            val entryName = entry?.model ?: entry?.compatible ?: "Entry ${fileState!!.selectedEntryIndex}"
                            Text(
                                text = entryName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val prepared = viewModel.prepareSaveBytes()
                                if (prepared != null) {
                                    bytesToSave = prepared
                                    saveLauncher.launch(viewModel.getSuggestedFileName())
                                }
                            },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (fileState != null) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Memory, contentDescription = null) },
                        label = { Text("CPU") },
                        selected = activeTab == AppTab.CPU,
                        onClick = { viewModel.selectTab(AppTab.CPU) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Code, contentDescription = null) },
                        label = { Text("DTS") },
                        selected = activeTab == AppTab.DTS,
                        onClick = { viewModel.selectTab(AppTab.DTS) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            val currentFile = fileState
            if (currentFile == null) {
                // Start Screen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "DTB Editor",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { openLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.import_vb), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { openLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.import_dtb), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                // Work Screen
                Column(modifier = Modifier.fillMaxSize()) {
                    if (currentFile.hasAvbFooter) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "The AVB footer will be removed. Verification must already be disabled on the device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (activeTab) {
                            AppTab.CPU -> CpuTopologyScreen(fileState = currentFile, viewModel = viewModel)
                            AppTab.DTS -> EditorScreen(fileState = currentFile, viewModel = viewModel)
                        }
                    }
                }
            }

            if (pendingMulti != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.cancelMultiDtbSelection() },
                    title = { Text("Select DTB Entry") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Select a DTB entry to edit:")
                            for (entry in pendingMulti!!.dtbEntries) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(entry.displayLabel, style = MaterialTheme.typography.bodyMedium)
                                        TextButton(onClick = { viewModel.selectDtbEntry(entry.index) }) {
                                            Text("Open")
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { viewModel.cancelMultiDtbSelection() }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
