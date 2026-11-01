package com.dtb.editor.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.R
import com.dtb.editor.fdt.cpu.ClusterConfig
import com.dtb.editor.fdt.cpu.CpuCoreDetail
import com.dtb.editor.fdt.cpu.TopologyPreviewResult
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.OpenFileState
import kotlinx.coroutines.delay

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

    var draftConfigs by remember(fileState.currentDtsText) {
        val initial = if (top.clusters.isNotEmpty()) {
            top.clusters.map { cluster ->
                ClusterConfig(
                    clusterIndex = cluster.index,
                    cpuIndices = cluster.cores.map { it.coreIndex },
                    selectedDomainSlot = null
                )
            }
        } else {
            listOf(ClusterConfig(0, (0 until totalCores).toList()))
        }
        mutableStateOf(initial)
    }

    var syncCpufreq by remember(fileState.currentDtsText) { mutableStateOf(top.syncCpufreqSupported) }

    val preview = viewModel.previewPlan(draftConfigs, syncCpufreq)
    
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
            val maxSystemDmips = 2200.0 * 7.37
            val rawCap = ((maxFreqMhz * dmipsPerMhz) / maxSystemDmips) * 1024.0
            val finalCap = if (!isBig && slotHex.endsWith("bc10")) 383L else kotlin.math.round(rawCap).toLong().coerceIn(100L, 1024L)
            core.index to finalCap
        }
    }

    val initialCapacities = remember(fileState.currentDtsText) {
        allCores.associate { core ->
            val isBig = core.microarch.contains("a76", ignoreCase = true) || core.nodeName.contains("100") || core.nodeName.contains("101")
            core.index to (core.capacity ?: if (isBig) 1024L else 383L)
        }
    }
    var customCapacities by remember(fileState.currentDtsText) { mutableStateOf(initialCapacities) }
    
    if (top.model != "MT6789" && top.model != "mt6789") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("This app is exclusively for MT6789 devices.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Text("MediaTek MT6789", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                            Text("${draftConfigs.size} Cluster (${draftConfigs.joinToString(" + ") { it.cpuIndices.size.toString() }})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (top.controllerInfo != null) {
                        Text("Controller: ${top.controllerInfo.nodeName} • 3 Performance Domains", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.quick_presets), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { customCapacities = autoTuneCapacities(draftConfigs, preview) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.auto_calculate_capacities), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    getApplicablePresets(totalCores).forEach { preset ->
                        val isSelected = preset == selectedPreset
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                val newConfigs = createConfigsFromSizes(preset, totalCores)
                                draftConfigs = newConfigs
                                selectedPreset = preset
                                val newPreview = viewModel.previewPlan(newConfigs, syncCpufreq)
                                customCapacities = autoTuneCapacities(newConfigs, newPreview)
                            },
                            label = { Text(when(preset){ listOf(6,2)->"6+2 (Stock)"; listOf(4,4)->"4+4 (Bal)"; listOf(4,2,2)->"4+2+2 (Tri)"; listOf(2,6)->"2+6 (Inv)"; listOf(2,2,4)->"2+2+4 (Tri-Perf)"; else->preset.joinToString("+") }, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }

        items(draftConfigs, key = { it.clusterIndex }) { cluster ->
            val clusterCores = cluster.cpuIndices.mapNotNull { allCores.getOrNull(it) }
            val autoSlot = preview?.assignedSlots?.get(cluster.clusterIndex)
            val archSummary = clusterCores.map { it.microarch.replace("arm,", "") }.filter { it.isNotBlank() }.distinct().joinToString("/")

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().animateContentSize()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Cluster ${cluster.clusterIndex}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp)) {
                                Text("${clusterCores.size} Core" + (if (archSummary.isNotBlank()) " • $archSummary" else ""), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        if (syncCpufreq && top.controllerInfo != null) {
                            CompactSlotDropdown(
                                selectedSlot = cluster.selectedDomainSlot, autoSlot = autoSlot, availableSlots = top.controllerInfo.availableSlots,
                                onSelectSlot = { newSlot -> draftConfigs = draftConfigs.map { if (it.clusterIndex == cluster.clusterIndex) it.copy(selectedDomainSlot = newSlot) else it } }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        clusterCores.forEach { cpu ->
                            val capVal = customCapacities[cpu.index] ?: cpu.capacity ?: if (cpu.microarch.contains("a76", ignoreCase = true)) 1024L else 383L
                            CoreRowItem(cpu, capVal) { newCap -> customCapacities = customCapacities + (cpu.index to newCap) }
                        }
                    }
                }
            }
        }

        if (preview != null) {
            if (preview.errors.isNotEmpty()) {
                item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { preview.errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer) } } } }
            }
            if (preview.warnings.isNotEmpty()) {
                item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { preview.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
            }
        }

        item {
            val isEnabled = preview != null && preview.isValid && draftConfigs.isNotEmpty()
            Button(
                onClick = { viewModel.applyCustomPlan(draftConfigs, syncCpufreq, customCapacities) },
                enabled = isEnabled,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(text = if (isEnabled) stringResource(R.string.apply_to_dts) else stringResource(R.string.apply_fix_errors), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun CoreRowItem(core: CpuCoreDetail, capacity: Long, onCapacityChange: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(core.nodeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            val arch = core.microarch.replace("arm,", "")
            if (arch.isNotBlank()) Text(arch, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
                RepeatingButton(Icons.Default.KeyboardArrowDown, "Decrease", capacity > 100L) { onCapacityChange((capacity - 25L).coerceAtLeast(100L)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 40.dp)) {
                    Text("$capacity", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                    Text("cap", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RepeatingButton(Icons.Default.KeyboardArrowUp, "Increase", capacity < 1024L) { onCapacityChange((capacity + 25L).coerceAtMost(1024L)) }
            }
        }
    }
}

@Composable
private fun RepeatingButton(imageVector: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) {
            onClick()
            delay(350L)
            while (isPressed && enabled) { onClick(); delay(70L) }
        }
    }
    Box(
        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).clickable(enabled = enabled, onClick = onClick).pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) { awaitFirstDown(false); isPressed = true; waitForUpOrCancellation(); isPressed = false }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector, contentDescription, modifier = Modifier.size(18.dp), tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}

@Composable
private fun CompactSlotDropdown(selectedSlot: Long?, autoSlot: Long?, availableSlots: List<Long>, onSelectSlot: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val slotLabel = if (selectedSlot != null) { val freq = getSlotFrequencyRange(selectedSlot); if (freq.isNotBlank()) freq else "0x${selectedSlot.toString(16)}" } else if (autoSlot != null) { val freq = getSlotFrequencyRange(autoSlot); if (freq.isNotBlank()) freq else "0x${autoSlot.toString(16)}" } else "Auto"
    Box {
        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f), shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { expanded = true }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Speed, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(slotLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Auto") }, onClick = { onSelectSlot(null); expanded = false })
            availableSlots.forEach { slot ->
                val label = "${getSlotFrequencyRange(slot)} (0x${slot.toString(16)})"
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelectSlot(slot); expanded = false })
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

private fun getApplicablePresets(totalCores: Int): List<List<Int>> {
    return when (totalCores) {
        8 -> listOf(listOf(6, 2), listOf(4, 4), listOf(4, 2, 2), listOf(2, 6), listOf(2, 2, 4), listOf(2, 3, 3), listOf(4, 3, 1))
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
