package com.dtb.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileState?.fileName ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (fileState != null) {
                            val entry = fileState!!.dtbEntries.getOrNull(fileState!!.selectedEntryIndex)
                            val entryName = entry?.model ?: entry?.compatible ?: "Entri ${fileState!!.selectedEntryIndex}"
                            Text(
                                text = entryName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (fileState != null) {
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
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (fileState != null) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Memory, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_cpu)) },
                        selected = activeTab == AppTab.CPU,
                        onClick = { viewModel.selectTab(AppTab.CPU) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Code, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_dts)) },
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
                // Layar Awal
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
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
                        Text(stringResource(R.string.open_vendor_boot), style = MaterialTheme.typography.titleMedium)
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
                        Text(stringResource(R.string.open_dtb), style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                // Layar Kerja
                Column(modifier = Modifier.fillMaxSize()) {
                    if (currentFile.hasAvbFooter) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.avb_footer_warning),
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

            // Dialog pilih entri DTB bila > 1
            if (pendingMulti != null) {
                AlertDialog(
                    onDismissRequest = { viewModel.cancelMultiDtbSelection() },
                    title = { Text(stringResource(R.string.select_dtb_entry)) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.select_entry_desc))
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
                                            Text(stringResource(R.string.open))
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { viewModel.cancelMultiDtbSelection() }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
        }
    }
}
