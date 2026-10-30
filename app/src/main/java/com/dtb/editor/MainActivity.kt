package com.dtb.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
            MyApplicationTheme {
                MainWorkstationScreen()
            }
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

    // SAF Import Launcher
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
            // Workstation Top Bar
            Surface(
                color = IdeSurfaceHeader,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Title / Active File Indicator
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

                            OutlinedButton(
                                onClick = { viewModel.loadSampleFile() },
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Sample", fontSize = 11.sp, color = TextSecondary)
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

                    HorizontalDivider(color = IdeBorder)

                    // Workstation Navigation Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(IdeSurface)
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
            // Workstation Status Bar
            Surface(
                color = IdeSurfaceHeader,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
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

    if (showExportDialog) {
        ExportDialog(
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
