package com.dtb.editor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dtb.editor.ui.components.ExportDialog
import com.dtb.editor.ui.screens.*
import com.dtb.editor.ui.theme.DarkBackground
import com.dtb.editor.ui.theme.DarkSurface
import com.dtb.editor.ui.theme.MyApplicationTheme
import com.dtb.editor.ui.theme.TerminalCyan
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.ExportType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: DtbViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentTab by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingExportType by remember { mutableStateOf<ExportType?>(null) }
    var exportMimeType by remember { mutableStateOf("application/octet-stream") }

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
            viewModel.exportToUri(context, uri, pendingExportType!!)
            pendingExportType = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 4.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        indicatorColor = TerminalCyan
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.AccountTree, contentDescription = "Explorer") },
                    label = { Text("Explorer") },
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        indicatorColor = TerminalCyan
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Code, contentDescription = "Editor") },
                    label = { Text("Editor") },
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        indicatorColor = TerminalCyan
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Memory, contentDescription = "Topology") },
                    label = { Text("Topology") },
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        indicatorColor = TerminalCyan
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = "Compare") },
                    label = { Text("Compare") },
                    selected = currentTab == 4,
                    onClick = { currentTab = 4 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        indicatorColor = TerminalCyan
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(innerPadding)
        ) {
            when (currentTab) {
                0 -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToTab = { currentTab = it },
                    onLaunchImport = {
                        importLauncher.launch(arrayOf("*/*"))
                    },
                    onOpenExport = { showExportDialog = true }
                )
                1 -> ExplorerScreen(viewModel = viewModel)
                2 -> EditorScreen(viewModel = viewModel)
                3 -> CpuTopologyScreen(viewModel = viewModel)
                4 -> CompareScreen(viewModel = viewModel)
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
