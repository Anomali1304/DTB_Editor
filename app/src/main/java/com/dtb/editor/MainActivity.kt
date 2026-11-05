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
import androidx.compose.material.icons.filled.FlashOn
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
import com.dtb.editor.ui.screens.KernelSuFlasherScreen
import androidx.compose.material.icons.filled.Security
import com.dtb.editor.ui.theme.DtbEditorTheme
import com.dtb.editor.viewmodel.AppTab
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.OpenFileState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// DTB Editor v1.0.1 - Ensuring English UI
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

    val coroutineScope = rememberCoroutineScope()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showFlashDialog by remember { mutableStateOf(false) }
    var showFlasherScreen by remember { mutableStateOf(false) }
    var showRootRequiredDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = fileState != null || showFlasherScreen) {
        if (showFlasherScreen) {
            showFlasherScreen = false
        } else if (fileState?.isModified == true) {
            showDiscardDialog = true
        } else {
            viewModel.closeFile()
        }
    }

    if (showRootRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showRootRequiredDialog = false },
            icon = {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Membutuhkan Akses Root",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Fitur Root Flasher memerlukan akses Root untuk membaca dan menulis partisi vendor_boot.\n\nSilakan berikan izin Root pada Root Manager Anda (Magisk / KernelSU / APatch) lalu tekan Coba Lagi.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val rooted = viewModel.checkRootAccess()
                            if (rooted) {
                                showRootRequiredDialog = false
                                showFlasherScreen = true
                            }
                        }
                    }
                ) {
                    Text("Coba Lagi")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRootRequiredDialog = false }) {
                    Text("Batal")
                }
            }
        )
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

    if (showFlashDialog) {
        FlashPartitionDialog(
            viewModel = viewModel,
            onDismiss = { showFlashDialog = false },
            onFlash = { targetPartition ->
                showFlashDialog = false
                viewModel.flashToPartition(targetPartition, context)
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
            if (fileState != null && !showFlasherScreen) {
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
                                coroutineScope.launch {
                                    val rooted = viewModel.checkRootAccess()
                                    if (rooted) {
                                        showFlasherScreen = true
                                    } else {
                                        showRootRequiredDialog = true
                                    }
                                }
                            },
                            enabled = !isProcessing
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = "Flash to Partition", tint = MaterialTheme.colorScheme.primary)
                        }
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
            if (fileState != null && !showFlasherScreen) {
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
                if (showFlasherScreen) {
                    KernelSuFlasherScreen(
                        viewModel = viewModel,
                        onClose = { showFlasherScreen = false }
                    )
                } else if (currentFile == null) {
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
                        Spacer(modifier = Modifier.height(16.dp))
                        ElevatedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val rooted = viewModel.checkRootAccess()
                                    if (rooted) {
                                        showFlasherScreen = true
                                    } else {
                                        showRootRequiredDialog = true
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(Icons.Default.FlashOn, null)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Root Partition Flasher", style = MaterialTheme.typography.titleMedium)
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

@Composable
fun FlashPartitionDialog(
    viewModel: DtbViewModel,
    onDismiss: () -> Unit,
    onFlash: (targetPartition: String) -> Unit
) {
    var slotInfo by remember { mutableStateOf<com.dtb.editor.root.FlashSlotInfo?>(null) }
    var selectedTarget by remember { mutableStateOf("active") }

    LaunchedEffect(Unit) {
        val info = viewModel.detectSlotInfo()
        slotInfo = info
    }

    val activeSlotLabel = remember(slotInfo) {
        val suffix = slotInfo?.activeSlotSuffix ?: ""
        if (suffix.equals("_a", ignoreCase = true)) "Slot A (_a)"
        else if (suffix.equals("_b", ignoreCase = true)) "Slot B (_b)"
        else if (suffix.isNotEmpty()) "Slot ($suffix)"
        else "Partition Non-A/B (vendor_boot)"
    }

    val activeDefaultPart = slotInfo?.defaultPartitionName ?: "vendor_boot"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.FlashOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                "Flash to vendor_boot",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Memory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Active Slot Detected:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                activeSlotLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Text(
                    "Pilih Target Partisi:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTarget = "active" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedTarget == "active"),
                            onClick = { selectedTarget = "active" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "Slot Aktif ($activeDefaultPart)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Rekomendasi - Flash ke slot yang sedang berjalan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTarget = "vendor_boot_a" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedTarget == "vendor_boot_a"),
                            onClick = { selectedTarget = "vendor_boot_a" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Slot A (vendor_boot_a)", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTarget = "vendor_boot_b" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedTarget == "vendor_boot_b"),
                            onClick = { selectedTarget = "vendor_boot_b" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Slot B (vendor_boot_b)", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTarget = "both" },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedTarget == "both"),
                            onClick = { selectedTarget = "both" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kedua Slot (vendor_boot_a & _b)", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "⚠️ Membutuhkan Akses Root! Silakan izinkan permintaan Root di Magisk / KernelSU / APatch saat tombol Flash ditekan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetPart = if (selectedTarget == "active") activeDefaultPart else selectedTarget
                    onFlash(targetPart)
                }
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Flash Sekarang")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

