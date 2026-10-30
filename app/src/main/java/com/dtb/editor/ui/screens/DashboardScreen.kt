package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.DtbViewModel

@Composable
fun DashboardScreen(
    viewModel: DtbViewModel,
    onNavigateToTab: (Int) -> Unit,
    onLaunchImport: () -> Unit,
    onOpenExport: () -> Unit
) {
    val project by viewModel.project.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val recentFiles by viewModel.recentFiles.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. App Title & Engine Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "DTB Studio",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = TerminalCyan
                            )
                            Text(
                                text = "Device Tree Compiler & Editor • v1.0.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TerminalGreen.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "OFFLINE DTC",
                                color = TerminalGreen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Native Android Linux Device Tree workstation. Decompile .dtb/.dtbo to .dts, edit topology, and recompile without root or Termux.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // Error message banner if any
        if (errorMessage != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TerminalRed.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = TerminalRed)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextSecondary)
                        }
                    }
                }
            }
        }

        // 2. Import & Quick Actions
        item {
            Text(
                text = "FILE ACTIONS",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onLaunchImport,
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, tint = DarkBackground)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import File", color = DarkBackground, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.loadSample() },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TerminalGreen),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Science, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Load Sample")
                }
            }
        }

        // 3. Active Project Status Card
        item {
            Text(
                text = "ACTIVE WORKSPACE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (project != null) {
                val proj = project!!
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = TerminalCyan.copy(alpha = 0.2f),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Description, contentDescription = null, tint = TerminalCyan)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = proj.fileName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${proj.fileType.displayName} • ${proj.originalSizeBytes} bytes",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = DarkSurfaceVariant)
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            WorkspaceStat(label = "Nodes", value = "${proj.rootNode.totalNodesCount}")
                            WorkspaceStat(label = "Properties", value = "${proj.rootNode.totalPropertiesCount}")
                            WorkspaceStat(label = "Lines", value = "${proj.currentDts.lines().size}")
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick buttons to tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onNavigateToTab(1) }, // Explorer
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Explorer")
                            }
                            FilledTonalButton(
                                onClick = { onNavigateToTab(2) }, // Editor
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Editor")
                            }
                            FilledTonalButton(
                                onClick = onOpenExport,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Export")
                            }
                        }
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Device Tree Loaded",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Import a .dtb, .dtbo, or .dts file, or click 'Load Sample' to begin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // 4. Quick Tools Navigation Cards
        item {
            Text(
                text = "STUDIO MODULES",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            ModuleCard(
                icon = Icons.Default.AccountTree,
                iconTint = TerminalCyan,
                title = "DTB Tree Explorer",
                subtitle = "Inspect /cpus, thermal-zones, regulators, clocks & memory hierarchy",
                onClick = { onNavigateToTab(1) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModuleCard(
                icon = Icons.Default.Code,
                iconTint = TerminalGreen,
                title = "DTS Code Editor",
                subtitle = "Syntax highlighting, find & replace, undo/redo, compile to DTB",
                onClick = { onNavigateToTab(2) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModuleCard(
                icon = Icons.Default.Memory,
                iconTint = TerminalOrange,
                title = "CPU Cluster Topology",
                subtitle = "Visual core groupings (4+4, 4+2+2, 2+3+3) & phandle validation",
                onClick = { onNavigateToTab(3) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            ModuleCard(
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                iconTint = TerminalPurple,
                title = "Diff & Comparison",
                subtitle = "Compare original vs modified nodes, properties, and values",
                onClick = { onNavigateToTab(4) }
            )
        }

        // 5. Status / Compiler Console
        item {
            Text(
                text = "OPERATIONS CONSOLE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = TerminalCyan
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isProcessing) "PROCESSING..." else "SYSTEM STATUS",
                            color = if (isProcessing) TerminalYellow else TerminalCyan,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusMessage ?: "Idle.",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
            }
        }

        // 6. Recent Projects List
        if (recentFiles.isNotEmpty()) {
            item {
                Text(
                    text = "RECENT PROJECTS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        for (recent in recentFiles) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = recent.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = "${recent.sizeBytes} B",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TerminalCyan,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun ModuleCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = iconTint.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint)
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
