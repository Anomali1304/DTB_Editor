package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.fdt.cpu.ClusterConfig
import com.dtb.editor.fdt.cpu.CpuCluster
import com.dtb.editor.fdt.cpu.CpuCoreDetail
import com.dtb.editor.fdt.cpu.PerformanceControllerInfo
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.DtbViewModel

@Composable
fun CpuTopologyScreen(
    viewModel: DtbViewModel
) {
    val workspace by viewModel.workspace.collectAsState()
    val topology by viewModel.cpuTopology.collectAsState()

    var syncCpufreqState by remember { mutableStateOf(true) }
    var customClusterConfigs by remember { mutableStateOf<List<ClusterConfig>>(emptyList()) }
    var showApplyConfirmDialog by remember { mutableStateOf(false) }

    val top = topology
    val ws = workspace

    if (ws == null || top == null) {
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

    // Initialize custom cluster configs when topology changes
    LaunchedEffect(top.clusters) {
        if (customClusterConfigs.isEmpty() && top.clusters.isNotEmpty()) {
            customClusterConfigs = top.clusters.map { c ->
                ClusterConfig(
                    clusterIndex = c.index,
                    cpuIndices = c.cores.map { it.nodeName.removePrefix("cpu@").removePrefix("cpu").toIntOrNull() ?: 0 },
                    selectedDomainSlot = top.assignedSlots[c.index]
                )
            }
        }
        syncCpufreqState = top.syncCpufreqSupported
    }

    val dynamicPresets = remember(top.totalCores) {
        generateDynamicPresets(top.totalCores)
    }

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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (top.syncCpufreqSupported) {
                        Surface(
                            shape = RoundedCornerShape(2.dp),
                            color = IdePrimaryCyan.copy(alpha = 0.2f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "CPUFREQ HW SUPPORTED",
                                color = IdePrimaryCyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
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
        }

        HorizontalDivider(color = IdeBorder)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // cpufreq domain sync toggle (shown if supported)
            if (top.syncCpufreqSupported) {
                item {
                    Surface(
                        color = IdeSurface,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = IdeSecondaryGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Sync cpufreq performance domains",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Synchronize performance-controller CSRAM reg slots and cpu@N performance-domains",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Switch(
                                checked = syncCpufreqState,
                                onCheckedChange = { syncCpufreqState = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = IdePrimaryCyan, checkedTrackColor = IdePrimaryCyan.copy(alpha = 0.4f))
                            )
                        }
                    }
                }
            }

            // Note Banner
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
                            text = if (syncCpufreqState && top.syncCpufreqSupported) {
                                "Note: Topology changes will reconstruct /cpus/cpu-map, update cpu@N performance-domains, and re-allocate performance-controller CSRAM reg and reg-names slots."
                            } else {
                                "Note: /cpus/cpu-map updates Linux kernel scheduler CPU affinity. Does not alter hardware clock domains, regulators, or cpufreq tables."
                            },
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
                                color = if (diag.startsWith("Warning") || diag.startsWith("[cpufreq] Requested cluster count")) IdeWarningAmber else IdeSecondaryGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Dynamic Topology Preset Buttons
            item {
                Column {
                    Text(
                        text = "PRESET TOPOLOGY SPLITS (${top.totalCores} CORES):",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (preset in dynamicPresets) {
                            OutlinedButton(
                                onClick = {
                                    customClusterConfigs = createConfigsFromSizes(preset, top.totalCores)
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(2.dp)
                            ) {
                                Text(preset.joinToString("+"), fontSize = 11.sp, color = IdePrimaryCyan, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }

            // Custom Cluster Editor Controls
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CLUSTER BUILDER & SLOT ASSIGNMENT:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Row {
                        IconButton(
                            onClick = {
                                val nextClusterIdx = customClusterConfigs.size
                                customClusterConfigs = customClusterConfigs + ClusterConfig(clusterIndex = nextClusterIdx, cpuIndices = emptyList())
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Cluster", tint = IdePrimaryCyan, modifier = Modifier.size(16.dp))
                        }

                        IconButton(
                            onClick = {
                                customClusterConfigs = top.clusters.map { c ->
                                    ClusterConfig(c.index, c.cores.map { it.nodeName.removePrefix("cpu@").removePrefix("cpu").toIntOrNull() ?: 0 })
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Cluster Config Cards
            itemsIndexed(customClusterConfigs) { cIdx, config ->
                ClusterBuilderCard(
                    config = config,
                    allCores = top.cores,
                    controllerInfo = top.controllerInfo,
                    syncCpufreq = syncCpufreqState,
                    onUpdateConfig = { updated ->
                        customClusterConfigs = customClusterConfigs.mapIndexed { idx, item -> if (idx == cIdx) updated else item }
                    },
                    onRemove = {
                        customClusterConfigs = customClusterConfigs.filterIndexed { idx, _ -> idx != cIdx }
                            .mapIndexed { idx, item -> item.copy(clusterIndex = idx) }
                    }
                )
            }

            // Apply Plan Action Button
            item {
                Button(
                    onClick = { showApplyConfirmDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IdePrimaryCyan)
                ) {
                    Text("APPLY CPU TOPOLOGY & CPUFREQ DOMAINS", color = IdeBackground, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }

    if (showApplyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showApplyConfirmDialog = false },
            title = { Text("Apply CPU Topology Plan?", fontSize = 13.sp, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Clusters: ${customClusterConfigs.joinToString(" | ") { "Cluster${it.clusterIndex}: CPUs ${it.cpuIndices.joinToString(",")}" }}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                    Text(
                        text = if (syncCpufreqState && top.syncCpufreqSupported) {
                            "cpufreq Domain Sync: ENABLED. Re-allocates CSRAM reg slots for performance-controller."
                        } else {
                            "cpufreq Domain Sync: DISABLED. Updates /cpus/cpu-map only."
                        },
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.applyCustomTopologyPlan(customClusterConfigs, syncCpufreqState)
                        showApplyConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IdePrimaryCyan),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("Confirm Apply", color = IdeBackground, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirmDialog = false }) {
                    Text("Cancel", color = TextSecondary, fontSize = 11.sp)
                }
            },
            containerColor = IdeSurface
        )
    }
}

@Composable
private fun ClusterBuilderCard(
    config: ClusterConfig,
    allCores: List<CpuCoreDetail>,
    controllerInfo: PerformanceControllerInfo?,
    syncCpufreq: Boolean,
    onUpdateConfig: (ClusterConfig) -> Unit,
    onRemove: () -> Unit
) {
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
                    text = "CLUSTER ${config.clusterIndex} (${config.cpuIndices.size} CORES)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = IdeSecondaryGreen,
                    fontFamily = FontFamily.Monospace
                )

                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove Cluster", tint = IdeErrorRed, modifier = Modifier.size(14.dp))
                }
            }

            // Domain slot selector dropdown if syncCpufreq is enabled
            if (syncCpufreq && controllerInfo != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CSRAM Domain Slot: ",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )

                    val selectedSlot = config.selectedDomainSlot ?: controllerInfo.availableSlots.getOrNull(config.clusterIndex) ?: 0x11bc10L
                    Text(
                        text = "0x${selectedSlot.toString(16)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = IdePrimaryCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // CPU Core Checkboxes / Selectors
            Text(
                text = "ASSIGNED CPU CORES:",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (core in allCores) {
                    val isAssigned = config.cpuIndices.contains(core.index)
                    Surface(
                        color = if (isAssigned) IdeBackground else IdeSurfaceHeader,
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isAssigned,
                                    onCheckedChange = { checked ->
                                        val newIndices = if (checked) {
                                            (config.cpuIndices + core.index).distinct().sorted()
                                        } else {
                                            config.cpuIndices.filter { it != core.index }
                                        }
                                        onUpdateConfig(config.copy(cpuIndices = newIndices))
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "cpu@${core.index}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = if (isAssigned) TextPrimary else TextMuted
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (core.microarch.isNotEmpty()) {
                                    Text(
                                        text = core.microarch.takeLast(10),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = IdePrimaryCyan
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }

                                if (core.capacity != null) {
                                    Text(
                                        text = "cap=${core.capacity}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = IdeSecondaryGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun generateDynamicPresets(totalCores: Int): List<List<Int>> {
    return when (totalCores) {
        8 -> listOf(listOf(4, 4), listOf(4, 2, 2), listOf(2, 3, 3), listOf(6, 2), listOf(4, 3, 1))
        6 -> listOf(listOf(4, 2), listOf(2, 2, 2), listOf(3, 3), listOf(5, 1))
        4 -> listOf(listOf(2, 2), listOf(3, 1), listOf(1, 1, 1, 1))
        else -> listOf(listOf(totalCores / 2, totalCores - totalCores / 2))
    }
}

private fun createConfigsFromSizes(sizes: List<Int>, totalCpus: Int): List<ClusterConfig> {
    val configs = mutableListOf<ClusterConfig>()
    var currentCpu = 0
    for ((cIdx, size) in sizes.withIndex()) {
        val cpus = (currentCpu until (currentCpu + size)).filter { it < totalCpus }
        configs.add(ClusterConfig(cIdx, cpus))
        currentCpu += size
    }
    return configs
}
