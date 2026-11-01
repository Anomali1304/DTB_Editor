package com.dtb.editor.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.fdt.cpu.ClusterConfig
import com.dtb.editor.fdt.cpu.CpuCoreDetail
import com.dtb.editor.fdt.cpu.CpuTopologyState
import com.dtb.editor.fdt.cpu.TopologyPreviewResult
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

    fun autoTuneCapacities(currentConfigs: List<ClusterConfig>, currentPreview: TopologyPreviewResult?): Map<Int, Long> {
        val assignedSlots = currentPreview?.assignedSlots ?: emptyMap()
        return allCores.associate { core ->
            val cluster = currentConfigs.firstOrNull { it.cpuIndices.contains(core.index) }
            val slot = cluster?.selectedDomainSlot ?: (cluster?.let { assignedSlots[it.clusterIndex] })
            val slotHex = slot?.let { "0x" + it.toString(16).lowercase() } ?: ""

            val isBig = core.microarch.contains("a76", ignoreCase = true) || core.nodeName.contains("100") || core.nodeName.contains("101")

            val maxFreqMhz = when {
                slotHex.endsWith("bd30") -> 2200.0
                slotHex.endsWith("bc10") -> 2000.0
                slotHex.endsWith("be50") -> 1540.0
                isBig -> 2200.0
                else -> 2000.0
            }

            val dmipsPerMhz = if (isBig) 7.37 else 3.36
            val maxSystemDmips = 2200.0 * 7.37 // 16214.0
            val rawCap = ((maxFreqMhz * dmipsPerMhz) / maxSystemDmips) * 1024.0

            val finalCap = if (!isBig && slotHex.endsWith("bc10")) {
                383L
            } else {
                kotlin.math.round(rawCap).toLong().coerceIn(100L, 1024L)
            }
            core.index to finalCap
        }
    }

    val initialCapacities = remember(fileState.currentDtsText) {
        allCores.associate { core ->
            val isBig = core.microarch.contains("a76", ignoreCase = true) || core.nodeName.contains("100") || core.nodeName.contains("101")
            core.index to (core.capacity ?: if (isBig) 1024L else 383L)
        }
    }
    var customCapacities by remember(fileState.currentDtsText) {
        mutableStateOf(initialCapacities)
    }
    
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
                                val newConfigs = createConfigsFromSizes(preset, totalCores)
                                draftConfigs = newConfigs
                                selectedPreset = preset
                                val newPreview = viewModel.previewPlan(newConfigs, syncCpufreq)
                                customCapacities = autoTuneCapacities(newConfigs, newPreview)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.core_to_cluster_assignment),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.capacity_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    TextButton(
                        onClick = {
                            customCapacities = autoTuneCapacities(draftConfigs, preview)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.auto_calculate_capacities),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Baris per CPU
                for (cpu in allCores) {
                    val assignedCluster = draftConfigs.firstOrNull { it.cpuIndices.contains(cpu.index) }?.clusterIndex ?: 0
                    val capVal = customCapacities[cpu.index] ?: cpu.capacity ?: (if (cpu.microarch.contains("a76", ignoreCase = true)) 1024L else 383L)
                    CpuAssignmentRow(
                        core = cpu,
                        currentCluster = assignedCluster,
                        capacity = capVal,
                        onCapacityChange = { newCap ->
                            customCapacities = customCapacities + (cpu.index to newCap)
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
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = stringResource(R.string.slot_freq_info_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.slot_freq_info),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                            val assigned = cluster.cpuIndices.mapNotNull { allCores.getOrNull(it) }
                            val coresText = if (assigned.isNotEmpty()) {
                                assigned.joinToString(", ") { core ->
                                    val cap = customCapacities[core.index] ?: core.capacity
                                    "${core.nodeName}${if (cap != null) " (cap $cap)" else ""}"
                                }
                            } else stringResource(R.string.empty_cluster_label)
                            val slot = preview.assignedSlots[cluster.clusterIndex]
                            val slotText = if (slot != null) " • slot ${formatSlotLabel(slot)}" else ""
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
                    viewModel.applyCustomPlan(draftConfigs, syncCpufreq, customCapacities)
                },
                enabled = isEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isEnabled) stringResource(R.string.apply_to_dts) else stringResource(R.string.apply_fix_errors))
            }
        }
    }
}

@Composable
private fun CpuAssignmentRow(
    core: CpuCoreDetail,
    currentCluster: Int,
    capacity: Long,
    onCapacityChange: (Long) -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = core.nodeName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "Cluster $currentCluster",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                val arch = if (core.microarch.isNotBlank()) core.microarch.replace("arm,", "") else ""
                if (arch.isNotBlank()) {
                    Text(
                        text = arch,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Capacity Stepper with Down arrow, capacity number, and Up arrow
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = { onCapacityChange((capacity - 25L).coerceAtLeast(100L)) },
                        modifier = Modifier.size(32.dp),
                        enabled = capacity > 100L
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Decrease capacity",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.widthIn(min = 44.dp)
                    ) {
                        Text(
                            text = "$capacity",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "cap",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { onCapacityChange((capacity + 25L).coerceAtMost(1024L)) },
                        modifier = Modifier.size(32.dp),
                        enabled = capacity < 1024L
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Increase capacity",
                            modifier = Modifier.size(20.dp)
                        )
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
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                stringResource(R.string.cluster_index_label, clusterIndex),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            val currentSlot = selectedSlot ?: autoSlot
            if (currentSlot != null) {
                val freq = getSlotFrequencyRange(currentSlot)
                if (freq.isNotEmpty()) {
                    Text(
                        freq,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                val label = if (selectedSlot != null) {
                    formatSlotLabel(selectedSlot)
                } else if (autoSlot != null) {
                    stringResource(R.string.automatic_slot, formatSlotLabel(autoSlot))
                } else {
                    stringResource(R.string.automatic)
                }
                Text(label, style = MaterialTheme.typography.bodySmall)
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
                        text = { Text(formatSlotLabel(slot)) },
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

private fun getSlotFrequencyRange(slot: Long): String {
    val hex = slot.toString(16).lowercase()
    return when {
        hex.endsWith("bc10") -> "500–2000 MHz"
        hex.endsWith("bd30") -> "725–2200 MHz"
        hex.endsWith("be50") -> "450–1540 MHz"
        else -> ""
    }
}

private fun formatSlotLabel(slot: Long): String {
    val hex = "0x" + slot.toString(16)
    val freq = getSlotFrequencyRange(slot)
    return if (freq.isNotEmpty()) "$hex ($freq)" else hex
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
