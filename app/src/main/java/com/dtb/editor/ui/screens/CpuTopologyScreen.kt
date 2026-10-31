package com.dtb.editor.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dtb.editor.fdt.cpu.ClusterConfig
import com.dtb.editor.fdt.cpu.CpuCoreDetail
import com.dtb.editor.fdt.cpu.CpuTopologyState
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.OpenFileState
import androidx.compose.ui.res.stringResource
import com.dtb.editor.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CpuTopologyScreen(
    fileState: OpenFileState,
    viewModel: DtbViewModel
) {
    val top = fileState.cpuTopology
    val allCores = top.cores
    val totalCores = fileState.cpuTopology.totalCores

    if (totalCores == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_cpu_nodes_detected))
        }
        return
    }

    // Initializing draft topology from current state
    var draftConfigs by remember(fileState.currentDtsText) {
        val initial = if (top.clusters.isNotEmpty()) {
            top.clusters.map { cluster ->
                val cpusInCluster = cluster.cores.map { it.coreIndex }
                ClusterConfig(
                    clusterIndex = cluster.index,
                    cpuIndices = cpusInCluster,
                    selectedDomainSlot = null
                )
            }
        } else {
            listOf(ClusterConfig(0, (0 until totalCores).toList()))
        }
        mutableStateOf(initial)
    }

    var syncCpufreq by remember(fileState.currentDtsText) {
        mutableStateOf(top.syncCpufreqSupported)
    }

    val preview = viewModel.previewPlan(draftConfigs, syncCpufreq)
    
    // Auto-detect initial preset from DTB clusters
    val initialPreset = top.clusters.map { it.cores.size }
    var selectedPreset by remember(fileState.currentDtsText) { mutableStateOf<List<Int>?>(initialPreset) }
    
    // MT6789 restriction
    if (top.model != "MT6789" && top.model != "mt6789") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This app is exclusively for MT6789 devices.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // 1. Ringkasan Susunan Sekarang
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.cpu_topology_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val clusterSummary = top.clusters.joinToString(" + ") { "${it.cores.size}" }
                    Text(
                        text = "$totalCores Core • ${top.clusters.size} Cluster ($clusterSummary)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (top.controllerInfo != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.cpu_controller_label, top.controllerInfo.nodeName, top.controllerInfo.availableSlots.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 2. Quick Presets
        item {
            Column {
                Text(
                    text = stringResource(R.string.quick_presets),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = getApplicablePresets(totalCores)
                    for (preset in presets) {
                        val isSelected = preset == selectedPreset
                        val containerColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            animationSpec = spring(stiffness = 300f, dampingRatio = 0.6f)
                        )
                        SuggestionChip(
                            onClick = {
                                draftConfigs = createConfigsFromSizes(preset, totalCores)
                                selectedPreset = preset
                            },
                            label = { Text(preset.joinToString("+")) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = containerColor
                            )
                        )
                    }
                }
            }
        }

        // 3. Matriks Penugasan CPU
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.core_to_cluster_assignment),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Cluster customization disabled for MT6789
                    }
                }

                // Baris per CPU
                for (cpu in allCores) {
                    val assignedCluster = draftConfigs.firstOrNull { it.cpuIndices.contains(cpu.index) }?.clusterIndex ?: 0
                    CpuAssignmentRow(
                        core = cpu,
                        currentCluster = assignedCluster,
                        clusterCount = draftConfigs.size,
                        onSelectCluster = { targetCluster ->
                            draftConfigs = draftConfigs.map { config ->
                                if (config.clusterIndex == targetCluster) {
                                    if (!config.cpuIndices.contains(cpu.index)) {
                                        config.copy(cpuIndices = (config.cpuIndices + cpu.index).sorted())
                                    } else config
                                } else {
                                    config.copy(cpuIndices = config.cpuIndices.filter { it != cpu.index })
                                }
                            }
                        }
                    )
                }
            }
        }

        // 4. Pengaturan Slot CSRAM per Cluster
        if (syncCpufreq && top.controllerInfo != null) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.cluster_slot_selector_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    for (cluster in draftConfigs) {
                        val autoSlot = preview?.assignedSlots?.get(cluster.clusterIndex)
                        ClusterSlotSelector(
                            clusterIndex = cluster.clusterIndex,
                            selectedSlot = cluster.selectedDomainSlot,
                            autoSlot = autoSlot,
                            availableSlots = top.controllerInfo.availableSlots,
                            onSelectSlot = { newSlot ->
                                draftConfigs = draftConfigs.map {
                                    if (it.clusterIndex == cluster.clusterIndex) it.copy(selectedDomainSlot = newSlot) else it
                                }
                            }
                        )
                    }
                }
            }
        }

        // 5. Pratinjau Hasil
        if (preview != null && preview.plan.clusters.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.preview_topology_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        for (cluster in preview.plan.clusters) {
                            val assigned = cluster.cpuIndices.mapNotNull { allCores.getOrNull(it)?.nodeName }
                            val coresText = if (assigned.isNotEmpty()) assigned.joinToString(", ") else stringResource(R.string.empty_cluster_label)
                            val slot = preview.assignedSlots[cluster.clusterIndex]
                            val slotText = if (slot != null) " • slot 0x${slot.toString(16)}" else ""
                            Text(
                                text = "Cluster ${cluster.clusterIndex}: $coresText$slotText",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // 6. Inline Errors & Warnings
        if (preview != null) {
            if (preview.errors.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (err in preview.errors) {
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            if (preview.warnings.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (w in preview.warnings) {
                                Text(
                                    text = w,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. Apply Button
        item {
            val isEnabled = preview != null && preview.isValid && draftConfigs.isNotEmpty()
            Button(
                onClick = {
                    viewModel.applyCustomPlan(draftConfigs, syncCpufreq)
                },
                enabled = isEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isEnabled) stringResource(R.string.apply_to_dts) else stringResource(R.string.apply_fix_errors))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CpuAssignmentRow(
    core: CpuCoreDetail,
    currentCluster: Int,
    clusterCount: Int,
    onSelectCluster: (Int) -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = core.nodeName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                val subText = buildString {
                    if (core.microarch.isNotBlank()) append(core.microarch.replace("arm,", ""))
                    if (core.capacity != null) append(" • cap ${core.capacity}")
                }
                if (subText.isNotBlank()) {
                    Text(
                        text = subText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SingleChoiceSegmentedButtonRow {
                for (cIdx in 0 until clusterCount) {
                    SegmentedButton(
                        selected = currentCluster == cIdx,
                        onClick = { onSelectCluster(cIdx) },
                        shape = SegmentedButtonDefaults.itemShape(index = cIdx, count = clusterCount)
                    ) {
                        Text("C$cIdx", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClusterSlotSelector(
    clusterIndex: Int,
    selectedSlot: Long?,
    autoSlot: Long?,
    availableSlots: List<Long>,
    onSelectSlot: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.cluster_index_label, clusterIndex), style = MaterialTheme.typography.bodyMedium)

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                val label = if (selectedSlot != null) {
                    "0x${selectedSlot.toString(16)}"
                } else if (autoSlot != null) {
                    stringResource(R.string.automatic_slot, "0x${autoSlot.toString(16)}")
                } else {
                    stringResource(R.string.automatic)
                }
                Text(label)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.automatic)) },
                    onClick = {
                        onSelectSlot(null)
                        expanded = false
                    }
                )
                for (slot in availableSlots) {
                    DropdownMenuItem(
                        text = { Text("0x${slot.toString(16)}") },
                        onClick = {
                            onSelectSlot(slot)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private fun getApplicablePresets(totalCores: Int): List<List<Int>> {
    return when (totalCores) {
        8 -> listOf(listOf(4, 4), listOf(4, 2, 2), listOf(2, 3, 3), listOf(6, 2), listOf(4, 3, 1))
        6 -> listOf(listOf(4, 2), listOf(2, 2, 2), listOf(3, 3))
        4 -> listOf(listOf(2, 2), listOf(4), listOf(1, 1, 1, 1))
        else -> listOf(listOf(totalCores))
    }
}

private fun createConfigsFromSizes(sizes: List<Int>, totalCores: Int): List<ClusterConfig> {
    val configs = mutableListOf<ClusterConfig>()
    var currentCpu = 0
    for ((cIdx, size) in sizes.withIndex()) {
        val cpus = (currentCpu until (currentCpu + size)).filter { it < totalCores }
        configs.add(ClusterConfig(cIdx, cpus))
        currentCpu += size
    }
    return configs
}
