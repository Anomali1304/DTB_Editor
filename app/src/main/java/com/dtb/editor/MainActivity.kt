package com.dtb.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dtb.editor.ui.components.ExportDialog
import com.dtb.editor.ui.screens.*
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.ExportTargetType
import com.dtb.editor.viewmodel.WorkspaceTab

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RootContent()
        }
    }

    @Composable
    private fun RootContent() {
        MyApplicationTheme {
            MainWorkstationScreen()
        }
    }
}

@Composable
fun MainWorkstationScreen(
    viewModel: DtbViewModel = viewModel()
) {
    val context = LocalContext.current
    val workspace by viewModel.workspace.collectAsState()
    val activeTab by viewModel.activeTab.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }
    var pendingExportType by remember { mutableStateOf<ExportTargetType?>(null) }
    var exportMimeType by remember { mutableStateOf("text/plain") }

    // SAF Import Launchers
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importUri(uri, context)
        }
    }

    // SAF Export Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(exportMimeType)
    ) { uri ->
        if (uri != null && pendingExportType != null) {
            viewModel.exportFile(context, uri, pendingExportType!!)
            pendingExportType = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Workstation Top Bar with Status Bar Inset Padding
            Surface(
                color = IdeSurfaceHeader,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // App Title / File Name
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DTB Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IdePrimaryCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            VerticalDivider(color = IdeBorder, modifier = Modifier.height(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))

                            if (workspace != null) {
                                val ws = workspace!!
                                Text(
                                    text = ws.fileName + if (ws.isDirty) "*" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ws.isDirty) IdeWarningAmber else TextPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = IdeBorder
                                ) {
                                    Text(
                                        text = ws.fileType.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "No File Loaded",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Compact Header Actions
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = IdePrimaryCyan, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open", fontSize = 11.sp, color = TextPrimary)
                            }

                            if (workspace != null) {
                                Button(
                                    onClick = { viewModel.saveWorkspace() },
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IdePrimaryCyan),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, tint = IdeBackground, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save", fontSize = 11.sp, color = IdeBackground, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = { showExportDialog = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.IosShare, contentDescription = "Export", tint = TextPrimary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Android DT Table Entry Switcher Bar (if imported file is a DT Table image)
                    if (workspace?.dtTableContainer != null) {
                        val container = workspace!!.dtTableContainer!!
                        val selectedIndex = workspace!!.selectedDtEntryIndex

                        HorizontalDivider(color = IdeBorder)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(IdeSurface)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "DT TABLE ENTRIES (${container.entries.size}):",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = IdePrimaryCyan,
                                fontWeight = FontWeight.Bold
                            )

                            for (entry in container.entries) {
                                val isSelected = entry.index == selectedIndex
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = if (isSelected) IdePrimaryCyan else IdeBorder.copy(alpha = 0.5f),
                                    modifier = Modifier.clickable { viewModel.selectDtTableEntry(entry.index) }
                                ) {
                                    Text(
                                        text = "Entry ${entry.index} (ID: 0x${entry.id.toString(16)}, ${entry.dtSize} B)",
                                        color = if (isSelected) IdeBackground else TextPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = IdeBorder)

                    // Scrollable Responsive Navigation Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(IdeSurface)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IdeTabItem("Source", Icons.Default.Code, activeTab == WorkspaceTab.SOURCE) {
                            viewModel.selectTab(WorkspaceTab.SOURCE)
                        }
                        IdeTabItem("Tree", Icons.Default.AccountTree, activeTab == WorkspaceTab.TREE) {
                            viewModel.selectTab(WorkspaceTab.TREE)
                        }
                        IdeTabItem("Binary", Icons.Default.Hexagon, activeTab == WorkspaceTab.BINARY) {
                            viewModel.selectTab(WorkspaceTab.BINARY)
                        }
                        IdeTabItem("Topology", Icons.Default.Memory, activeTab == WorkspaceTab.TOPOLOGY) {
                            viewModel.selectTab(WorkspaceTab.TOPOLOGY)
                        }
                        IdeTabItem("Compare", Icons.AutoMirrored.Filled.CompareArrows, activeTab == WorkspaceTab.COMPARE) {
                            viewModel.selectTab(WorkspaceTab.COMPARE)
                        }
                        IdeTabItem("Diagnostics", Icons.AutoMirrored.Filled.Article, activeTab == WorkspaceTab.DIAGNOSTICS) {
                            viewModel.selectTab(WorkspaceTab.DIAGNOSTICS)
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Workstation Status Bar with Navigation Bar Inset Padding
            Surface(
                color = IdeSurfaceHeader,
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = IdePrimaryCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    if (workspace != null) {
                        val ws = workspace!!
                        Text(
                            text = "DTS: ${ws.sourceText.lines().size} lines | ${ws.parsedAst?.totalNodesCount ?: 0} nodes",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IdeBackground)
                .padding(innerPadding)
        ) {
            if (workspace == null) {
                EmptyWorkspaceView(
                    onOpenDts = { importLauncher.launch(arrayOf("*/*")) },
                    onOpenDtb = { importLauncher.launch(arrayOf("*/*")) },
                    onOpenDtbo = { importLauncher.launch(arrayOf("*/*")) },
                    onLoadSample = { viewModel.loadSampleFile() }
                )
            } else {
                when (activeTab) {
                    WorkspaceTab.SOURCE -> EditorScreen(viewModel = viewModel)
                    WorkspaceTab.TREE -> ExplorerScreen(viewModel = viewModel)
                    WorkspaceTab.BINARY -> BinaryView(workspace = workspace)
                    WorkspaceTab.TOPOLOGY -> CpuTopologyScreen(viewModel = viewModel)
                    WorkspaceTab.COMPARE -> CompareScreen(viewModel = viewModel)
                    WorkspaceTab.DIAGNOSTICS -> {
                        val logs by viewModel.diagnosticsLog.collectAsState()
                        DiagnosticsView(diagnostics = logs)
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            hasDtTableContainer = workspace?.dtTableContainer != null,
            onDismiss = { showExportDialog = false },
            onSelectExportType = { type, mime, suggestedName ->
                pendingExportType = type
                exportMimeType = mime
                exportLauncher.launch(suggestedName)
            }
        )
    }
}

@Composable
private fun EmptyWorkspaceView(
    onOpenDts: () -> Unit,
    onOpenDtb: () -> Unit,
    onOpenDtbo: () -> Unit,
    onLoadSample: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeBackground)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 380.dp)
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                tint = IdePrimaryCyan,
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "DTB Studio Workstation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "Select an operation to load or inspect a Device Tree file:",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // File Operations Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenDts,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, tint = IdePrimaryCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open DTS Source (.dts)", fontSize = 12.sp, color = TextPrimary)
                }

                OutlinedButton(
                    onClick = onOpenDtb,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = IdeSecondaryGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Decompile & Open DTB / DT Table (.dtb)", fontSize = 12.sp, color = TextPrimary)
                }

                OutlinedButton(
                    onClick = onOpenDtbo,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Icon(Icons.Default.Hexagon, contentDescription = null, tint = IdeWarningAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Decompile & Open DTBO Overlay (.dtbo)", fontSize = 12.sp, color = TextPrimary)
                }

                Button(
                    onClick = onLoadSample,
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IdeSurfaceHeader),
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = IdePrimaryCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load Sample Qualcomm DTB", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun IdeTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) IdeBackground else IdeSurface,
        shape = RoundedCornerShape(2.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) IdePrimaryCyan else TextMuted,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (selected) TextPrimary else TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
