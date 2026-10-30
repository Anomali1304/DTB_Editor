package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val workspace by viewModel.workspace.collectAsState()
    val topology by viewModel.cpuTopology.collectAsState()
    var showPresetConfirm by remember { mutableStateOf<List<Int>?>(null) }

    if (workspace == null || topology == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(IdeBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No Device Tree Open", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            }
        }
        return
    }

    val top = topology!!

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeBackground)
    ) {
        // Topology Header Bar
        Surface(
            color = IdeSurfaceHeader,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = IdePrimaryCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CPU CLUSTER TOPOLOGY (${top.totalCores} CORES, ${top.clusters.size} CLUSTERS)",
                        style = MaterialTheme.typography.labelSmall,
                        color = IdePrimaryCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = if (top.hasCpuMap) IdeSecondaryGreen.copy(alpha = 0.2f) else IdeWarningAmber.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (top.hasCpuMap) "CPU-MAP VALID" else "NO CPU-MAP",
                        color = if (top.hasCpuMap) IdeSecondaryGreen else IdeWarningAmber,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = IdeBorder)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hardware vs Scheduler Notice
            item {
                Surface(
                    color = IdeSurface,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = IdeWarningAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Note: /cpus/cpu-map updates Linux kernel scheduler CPU affinity. It does not alter hardware clock domains, regulators, or CPUFreq OPP tables.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Diagnostics messages
            if (top.diagnostics.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (diag in top.diagnostics) {
                            Text(
                                text = "• $diag",
                                style = MaterialTheme.typography.labelSmall,
                                color = IdeWarningAmber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Topology Preset Actions (If 8 cores detected)
            if (top.totalCores == 8) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetButton("4+4", listOf(4, 4)) { showPresetConfirm = it }
                        PresetButton("4+2+2", listOf(4, 2, 2)) { showPresetConfirm = it }
                        PresetButton("2+3+3", listOf(2, 3, 3)) { showPresetConfirm = it }
                        PresetButton("4+3+1", listOf(4, 3, 1)) { showPresetConfirm = it }
                    }
                }
            }

            // Cluster Cards
            items(top.clusters) { cluster ->
                ClusterBox(cluster = cluster)
            }
        }
    }

    if (showPresetConfirm != null) {
        val preset = showPresetConfirm!!
        AlertDialog(
            onDismissRequest = { showPresetConfirm = null },
            title = { Text("Reconstruct /cpus/cpu-map to ${preset.joinToString("+")}?", fontSize = 13.sp, color = TextPrimary) },
            text = {
                Text(
                    text = "This will update the cpu-map node in DTS to reflect cluster sizes ${preset.joinToString("+")}. Hardware phandles will be verified and resolved.",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.applyCpuPreset(preset)
                        showPresetConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IdePrimaryCyan),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Apply", color = IdeBackground, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPresetConfirm = null }) {
                    Text("Cancel", color = TextSecondary, fontSize = 11.sp)
                }
            },
            containerColor = IdeSurface
        )
    }
}

@Composable
private fun RowScope.PresetButton(label: String, preset: List<Int>, onClick: (List<Int>) -> Unit) {
    OutlinedButton(
        onClick = { onClick(preset) },
        modifier = Modifier.weight(1f).height(32.dp),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(2.dp)
    ) {
        Text(label, fontSize = 11.sp, color = IdePrimaryCyan, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ClusterBox(cluster: CpuCluster) {
    Surface(
        color = IdeSurface,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${cluster.name.uppercase()} (${cluster.cores.size} CORES)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = IdeSecondaryGreen,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (core in cluster.cores) {
                    Surface(
                        color = IdeBackground,
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = core.nodeName,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "reg=0x${core.regAddress.toString(16)}" + if (core.phandle != null) " (phandle=0x${core.phandle.toString(16)})" else "",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
