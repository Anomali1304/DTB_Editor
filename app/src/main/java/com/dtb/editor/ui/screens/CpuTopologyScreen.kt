package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.fdt.cpu.CpuCluster
import com.dtb.editor.fdt.cpu.CpuCoreInfo
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.DtbViewModel

@Composable
fun CpuTopologyScreen(
    viewModel: DtbViewModel
) {
    val project by viewModel.project.collectAsState()
    val topology by viewModel.cpuTopology.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    var showApplyDialog by remember { mutableStateOf<List<Int>?>(null) }

    if (project == null || topology == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(56.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No Device Tree Loaded", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Import a DTB to inspect CPU topology & clusters.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        return
    }

    val top = topology!!

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Overview Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CPU Cluster Topology",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TerminalCyan
                            )
                            Text(
                                text = "Discovered ${top.totalCores} physical cores in ${top.clusters.size} clusters",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (top.hasCpuMap) TerminalGreen.copy(alpha = 0.15f) else TerminalOrange.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (top.hasCpuMap) "CPU-MAP ACTIVE" else "NO CPU-MAP",
                                color = if (top.hasCpuMap) TerminalGreen else TerminalOrange,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Hardware / Kernel Safety Notice (MANDATORY per spec)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.WarningAmber,
                        contentDescription = "Safety Warning",
                        tint = TerminalOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "CPU TOPOLOGY vs CPUFREQ DVFS",
                            fontWeight = FontWeight.Bold,
                            color = TerminalOrange,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reconfiguring /cpus/cpu-map updates Linux kernel scheduler load-balancing groups. It does NOT automatically create CPUFreq policies or OPP DVFS clock domains. Hardware frequencies, voltages, and register addresses are strictly preserved from verified DTB nodes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // 3. Topology Presets
        item {
            Text(
                text = "TOPOLOGY PRESETS (8 CORES)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresetChip(label = "4 + 4", description = "Dual cluster", onClick = { showApplyDialog = listOf(4, 4) }, modifier = Modifier.weight(1f))
                PresetChip(label = "4 + 2 + 2", description = "Tri-cluster", onClick = { showApplyDialog = listOf(4, 2, 2) }, modifier = Modifier.weight(1f))
                PresetChip(label = "2 + 3 + 3", description = "Tri-cluster", onClick = { showApplyDialog = listOf(2, 3, 3) }, modifier = Modifier.weight(1f))
                PresetChip(label = "4 + 3 + 1", description = "Prime core", onClick = { showApplyDialog = listOf(4, 3, 1) }, modifier = Modifier.weight(1f))
            }
        }

        // 4. Clusters & Cores Visualization
        items(top.clusters) { cluster ->
            ClusterCard(cluster = cluster)
        }
    }

    // Confirmation Dialog before modifying DTS
    if (showApplyDialog != null) {
        val preset = showApplyDialog!!
        AlertDialog(
            onDismissRequest = { showApplyDialog = null },
            title = { Text("Apply Topology Preset: ${preset.joinToString("+")}?", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This will update the /cpus/cpu-map node with ${preset.size} clusters (${preset.joinToString("+")}) and recalculate core phandles.",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Original phandles and hardware registers are verified and preserved.\n• Unrelated DTS properties remain untouched.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.applyCpuPreset(preset)
                        showApplyDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TerminalCyan)
                ) {
                    Text("Apply Changes", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyDialog = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp),
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TerminalCyan,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ClusterCard(cluster: CpuCluster) {
    val clusterColor = when (cluster.index) {
        0 -> TerminalGreen
        1 -> TerminalCyan
        2 -> TerminalOrange
        else -> TerminalPurple
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = clusterColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${cluster.index}",
                                color = clusterColor,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = cluster.name.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DarkSurfaceVariant
                ) {
                    Text(
                        text = "${cluster.cores.size} Cores",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Cores in this cluster
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (core in cluster.cores) {
                    CoreRow(core = core, accentColor = clusterColor)
                }
            }
        }
    }
}

@Composable
private fun CoreRow(core: CpuCoreInfo, accentColor: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DarkBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = core.nodeName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    if (core.compatible.isNotEmpty()) {
                        Text(
                            text = core.compatible,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "reg: 0x${core.regAddress.toString(16)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TerminalCyan,
                    fontFamily = FontFamily.Monospace
                )
                if (core.phandle != null) {
                    Text(
                        text = "phandle: 0x${core.phandle.toString(16)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
