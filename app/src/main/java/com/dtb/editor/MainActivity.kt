package com.dtb.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openUri(uri, context)
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            viewModel.saveToUri(uri, context)
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
                                saveLauncher.launch(viewModel.getSuggestedFileName())
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (isProcessing) 10.dp else 0.dp)
            ) {
                val currentFile = fileState
                if (currentFile == null) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("DTB Editor", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = { openLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Import Vendor Boot Image", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { openLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Icon(Icons.Default.Code, null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Import Device Tree Blob", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                } else {
                    val isMt6789 = currentFile.cpuTopology.model.equals("MT6789", ignoreCase = true)
                    if (isMt6789) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (currentFile.hasAvbFooter) {
                                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                                    Text("The AVB footer will be removed. Verification must already be disabled on the device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(12.dp))
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                AnimatedContent(targetState = activeTab, transitionSpec = { fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300)) }, label = "tab_transition") { targetTab ->
                                    when (targetTab) {
                                        AppTab.CPU -> CpuTopologyScreen(fileState = currentFile, viewModel = viewModel)
                                        AppTab.DTS -> EditorScreen(fileState = currentFile, viewModel = viewModel)
                                    }
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Unsupported device model: ${currentFile.cpuTopology.model}\nThis app is exclusively for MT6789.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
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
                                pendingMulti!!.dtbEntries.forEach { entry ->
                                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(entry.displayLabel, style = MaterialTheme.typography.bodyMedium)
                                            TextButton(onClick = { viewModel.selectDtbEntry(entry.index) }) { Text("Open") }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { viewModel.cancelMultiDtbSelection() }) { Text("Cancel") } }
                    )
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
                    contentAlignment = Alignment.Center
                ) {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), tonalElevation = 12.dp, modifier = Modifier.padding(32.dp)) {
                        Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Text("Processing file...", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Please wait a moment...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
