package com.dtb.editor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dtb.editor.fdt.cpu.ClusterConfig
import com.dtb.editor.fdt.cpu.CpuCoreDetail
import com.dtb.editor.fdt.cpu.CpuTopologyState
import com.dtb.editor.fdt.cpu.PerformanceControllerInfo
import com.dtb.editor.fdt.cpu.TopologyPreviewResult
import com.dtb.editor.ui.theme.*
import com.dtb.editor.viewmodel.DtbViewModel

@Composable
fun CpuTopologyScreen(
    viewModel: DtbViewModel
) {
    val workspace by viewModel.workspace.collectAsState()
    val topology by viewModel.cpuTopology.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

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

    // Toggle sync cpufreq: initialized once per imported file, never reset by LaunchedEffect during editing
    var currentFileKey by remember { mutableStateOf<String?>(null) }
    var syncCpufreqState by remember { mutableStateOf(true) }
    var draftClusterConfigs by remember { mutableStateOf<List<ClusterConfig>>(emptyList()) }
    var applySuccessSummary by remember { mutableStateOf<String?>(null) }
    var showApplyConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(ws.fileName, ws.originalSizeBytes) {
        val fileKey = "${ws.fileName}:${ws.originalSizeBytes}"
        if (currentFileKey != fileKey) {
            currentFileKey = fileKey
            syncCpufreqState = top.syncCpufreqSupported
            draftClusterConfigs = top.clusters.map { c ->
                ClusterConfig(
                    clusterIndex = c.index,
                    cpuIndices = c.cores.map { coreInfo ->
                        top.cores.firstOrNull { it.phandle != null && it.phandle == coreInfo.phandle }?.index
                            ?: coreInfo.coreIndex
                    },
                    selectedDomainSlot = null // auto by default, never stored from assignedSlots
                )
            }
            applySuccessSummary = null
        }
    }

    // Ensure draftClusterConfigs is populated if not yet initialized
    if (draftClusterConfigs.isEmpty() && top.clusters.isNotEmpty()) {
        draftClusterConfigs = top.clusters.map { c ->
            ClusterConfig(
                clusterIndex = c.index,
                cpuIndices = c.cores.map { coreInfo ->
                    top.cores.firstOrNull { it.phandle != null && it.phandle == coreInfo.phandle }?.index
                        ?: coreInfo.coreIndex
                },
                selectedDomainSlot = null
            )
        }
    }

    val dynamicPresets = remember(top.totalCores) {
        generateDynamicPresets(top.totalCores)
    }

    // Live preview from engine
    val previewResult = remember(draftClusterConfigs, syncCpufreqState, top) {
        viewModel.previewPlan(draftClusterConfigs, syncCpufreqState)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(IdeBackground)
    ) {
        // Top Workstation Bar
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
                        text = "CPU TOPOLOGY WORKSTATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = IdePrimaryCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = IdeSurface
                    ) {
                        Text(
                            text = "${top.totalCores} CORES",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = if (top.syncCpufreqSupported) IdePrimaryCyan.copy(alpha = 0.2f) else IdeBorder
                    ) {
                        Text(
                            text = if (top.syncCpufreqSupported) "CPUFREQ HW" else "NO CPUFREQ HW",
                            color = if (top.syncCpufreqSupported) IdePrimaryCyan else TextMuted,
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Apply Success Banner (if any)
            if (applySuccessSummary != null) {
                item {
                    Surface(
                        color = IdeSecondaryGreen.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IdeSecondaryGreen),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = IdeSecondaryGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Topologi Berhasil Diterapkan ke DTS",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = IdeSecondaryGreen
                                    )
                                }
                                IconButton(
                                    onClick = { applySuccessSummary = null },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextMuted, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = applySuccessSummary ?: "",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Petunjuk: Export untuk menyimpan perubahan ke file DTB / DT Table / DTS.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = IdePrimaryCyan
                            )
                        }
                    }
                }
            }

            // Sync Toggle Bar with 1-line concise help
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
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = if (syncCpufreqState) IdePrimaryCyan else TextMuted, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Sync cpufreq performance domains",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = if (syncCpufreqState) {
                                            "Sinkronisasi performance-domains cpu@N dan slot reg performance-controller CSRAM."
                                        } else {
                                            "Hanya cpu-map: Linux scheduler affinity tanpa mengubah domain cpufreq."
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Switch(
                                checked = syncCpufreqState,
                                onCheckedChange = { syncCpufreqState = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = IdePrimaryCyan,
                                    checkedTrackColor = IdePrimaryCyan.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.testTag("toggle_sync_cpufreq")
                            )
                        }
                    }
                }
            }

            // ==========================================
            // (A) KONDISI SAAT INI (Read-Only)
            // ==========================================
            item {
                CurrentFileStateCard(
                    topology = top,
                    syncCpufreq = syncCpufreqState
                )
            }

            // ==========================================
            // (B) DRAFT (Editor Matrix & Presets)
            // ==========================================
            item {
                DraftEditorCard(
                    totalCores = top.totalCores,
                    allCores = top.cores,
                    dynamicPresets = dynamicPresets,
                    draftConfigs = draftClusterConfigs,
                    onUpdateDraft = { draftClusterConfigs = it },
                    onResetToCurrent = {
                        draftClusterConfigs = top.clusters.map { c ->
                            ClusterConfig(
                                clusterIndex = c.index,
                                cpuIndices = c.cores.map { coreInfo ->
                                    top.cores.firstOrNull { it.phandle != null && it.phandle == coreInfo.phandle }?.index
                                        ?: coreInfo.coreIndex
                                },
                                selectedDomainSlot = null
                            )
                        }
                    }
                )
            }

            // ==========================================
            // (C) HASIL PRATINJAU (Live Engine Preview)
            // ==========================================
            item {
                LivePreviewCard(
                    preview = previewResult,
                    allCores = top.cores,
                    controllerInfo = top.controllerInfo,
                    syncCpufreq = syncCpufreqState,
                    isProcessing = isProcessing,
                    draftConfigs = draftClusterConfigs,
                    onUpdateSlot = { clusterIndex, slot ->
                        draftClusterConfigs = draftClusterConfigs.map { c ->
                            if (c.clusterIndex == clusterIndex) c.copy(selectedDomainSlot = slot) else c
                        }
                    },
                    onApplyClick = { showApplyConfirmDialog = true }
                )
            }
        }
    }

    // Confirmation Dialog
    if (showApplyConfirmDialog) {
        val summaryLine = "Terapkan topologi ${draftClusterConfigs.map { it.cpuIndices.size }.joinToString("+")} (${draftClusterConfigs.size} cluster, ${if (syncCpufreqState) "sync cpufreq" else "hanya cpu-map"}) ke DTS?"

        AlertDialog(
            onDismissRequest = { showApplyConfirmDialog = false },
            title = {
                Text("Konfirmasi Penerapan Topologi", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = summaryLine, fontSize = 11.sp, color = TextPrimary)
                    Text(
                        text = if (syncCpufreqState) {
                            "Proses ini akan merekonstruksi /cpus/cpu-map, memperbarui performance-domains di setiap cpu@N, dan mengalokasikan slot CSRAM di performance-controller."
                        } else {
                            "Mode hanya cpu-map: tidak mengubah performance-domains atau registri controller."
                        },
                        fontSize = 10.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showApplyConfirmDialog = false
                        viewModel.applyCustomTopologyPlan(draftClusterConfigs, syncCpufreqState)
                        // Prepare final summary text
                        val summaryBuilder = StringBuilder()
                        for (c in draftClusterConfigs) {
                            val slotStr = if (syncCpufreqState) {
                                previewResult?.assignedSlots?.get(c.clusterIndex)?.let { "0x" + it.toString(16) } ?: "-"
                            } else "-"
                            for (cpuIdx in c.cpuIndices) {
                                val cName = top.cores.getOrNull(cpuIdx)?.nodeName ?: "cpu@$cpuIdx"
                                if (summaryBuilder.isNotEmpty()) summaryBuilder.append(" | ")
                                summaryBuilder.append("$cName: cluster ${c.clusterIndex}, domain ${if (syncCpufreqState) c.clusterIndex else "-"}, slot $slotStr")
                            }
                        }
                        applySuccessSummary = summaryBuilder.toString()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IdePrimaryCyan),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("confirm_apply_button")
                ) {
                    Text("Apply ke DTS", color = IdeBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirmDialog = false }) {
                    Text("Batal", color = TextSecondary, fontSize = 11.sp)
                }
            },
            containerColor = IdeSurface
        )
    }
}

/**
 * Section A: KONDISI SAAT INI (Read-Only)
 */
@Composable
private fun CurrentFileStateCard(
    topology: CpuTopologyState,
    syncCpufreq: Boolean
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
                    text = "KONDISI SAAT INI DARI FILE (READ-ONLY)",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Text(
                    text = "${topology.clusters.size} CLUSTERS",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = IdeSecondaryGreen
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IdeSurfaceHeader)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cluster", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Text("CPU Cores", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Text("Domain", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                if (syncCpufreq && topology.syncCpufreqSupported) {
                    Text("Slot CSRAM", modifier = Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = IdeBorder)

            // Table Rows
            for (c in topology.clusters) {
                val coreNames = c.cores.joinToString(", ") { it.nodeName }
                val slot = topology.assignedSlots[c.index]
                val slotStr = if (slot != null) "0x${slot.toString(16)}" else "-"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(c.name, modifier = Modifier.width(70.dp), style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = IdePrimaryCyan, fontFamily = FontFamily.Monospace)
                    Text(coreNames, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = TextPrimary, fontFamily = FontFamily.Monospace)
                    Text("dom ${c.index}", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace)
                    if (syncCpufreq && topology.syncCpufreqSupported) {
                        Text(slotStr, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = IdeSecondaryGreen, fontFamily = FontFamily.Monospace)
                    }
                }
                HorizontalDivider(color = IdeBorder.copy(alpha = 0.5f))
            }
        }
    }
}

/**
 * Section B: DRAFT EDITOR (Matrix Table & Dynamic Presets)
 */
@Composable
private fun DraftEditorCard(
    totalCores: Int,
    allCores: List<CpuCoreDetail>,
    dynamicPresets: List<List<Int>>,
    draftConfigs: List<ClusterConfig>,
    onUpdateDraft: (List<ClusterConfig>) -> Unit,
    onResetToCurrent: () -> Unit
) {
    Surface(
        color = IdeSurface,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header with prominent DRAFT label
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "EDITOR DRAFT TOPOLOGI",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = IdeWarningAmber.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "DRAFT - belum diterapkan ke DTS",
                            color = IdeWarningAmber,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                TextButton(
                    onClick = onResetToCurrent,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("Reset ke Asli", fontSize = 10.sp, color = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dynamic Presets Chips
            Text(
                text = "PILIH PRESET CLUSTER ($totalCores CORES):",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (preset in dynamicPresets) {
                    val label = preset.joinToString("+")
                    OutlinedButton(
                        onClick = {
                            val newConfigs = createConfigsFromSizes(preset, totalCores)
                            onUpdateDraft(newConfigs)
                        },
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("preset_chip_$label"),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(label, fontSize = 11.sp, color = IdePrimaryCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cluster Management Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MATRIKS PENUGASAN CPU (1 CPU = 1 CLUSTER):",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = {
                            val nextClusterIdx = draftConfigs.size
                            val newConfig = ClusterConfig(clusterIndex = nextClusterIdx, cpuIndices = emptyList())
                            onUpdateDraft(draftConfigs + newConfig)
                        },
                        modifier = Modifier.height(26.dp).testTag("add_cluster_button"),
                        shape = RoundedCornerShape(3.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = IdePrimaryCyan, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("+ Cluster", fontSize = 10.sp, color = TextPrimary)
                    }

                    if (draftConfigs.size > 1) {
                        OutlinedButton(
                            onClick = {
                                val lastIdx = draftConfigs.size - 1
                                val removedCpus = draftConfigs.last().cpuIndices
                                val updated = draftConfigs.dropLast(1).mapIndexed { idx, c ->
                                    if (idx == 0) c.copy(cpuIndices = (c.cpuIndices + removedCpus).distinct().sorted()) else c
                                }
                                onUpdateDraft(updated)
                            },
                            modifier = Modifier.height(26.dp).testTag("remove_cluster_button"),
                            shape = RoundedCornerShape(3.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = IdeErrorRed, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Hapus C${draftConfigs.size - 1}", fontSize = 10.sp, color = IdeErrorRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Scrollable Matrix Table: Rows = CPU cores, Columns = Cluster 0..N-1
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .border(1.dp, IdeBorder, RoundedCornerShape(2.dp))
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .background(IdeSurfaceHeader)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CPU Node", modifier = Modifier.width(90.dp).padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text("Arch", modifier = Modifier.width(55.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text("Cap", modifier = Modifier.width(50.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)

                    for (c in draftConfigs) {
                        Text(
                            text = "Cluster ${c.clusterIndex}",
                            modifier = Modifier.width(65.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = IdePrimaryCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = IdeBorder)

                // Matrix Rows per CPU
                for (core in allCores) {
                    val assignedCluster = draftConfigs.firstOrNull { it.cpuIndices.contains(core.index) }?.clusterIndex
                    val shortArch = if (core.microarch.contains("a76", ignoreCase = true)) "A76"
                    else if (core.microarch.contains("a55", ignoreCase = true)) "A55"
                    else core.microarch.takeLast(6)

                    Row(
                        modifier = Modifier
                            .background(if (core.index % 2 == 0) IdeSurface else IdeBackground)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = core.nodeName,
                            modifier = Modifier.width(90.dp).padding(start = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = shortArch,
                            modifier = Modifier.width(55.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (shortArch == "A76") IdeWarningAmber else IdePrimaryCyan
                        )
                        Text(
                            text = core.capacity?.toString() ?: "-",
                            modifier = Modifier.width(50.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )

                        for (c in draftConfigs) {
                            Box(
                                modifier = Modifier.width(65.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                RadioButton(
                                    selected = (assignedCluster == c.clusterIndex),
                                    onClick = {
                                        // Assign this core strictly to this cluster, removing from all others
                                        val updatedConfigs = draftConfigs.map { other ->
                                            if (other.clusterIndex == c.clusterIndex) {
                                                other.copy(cpuIndices = (other.cpuIndices + core.index).distinct().sorted())
                                            } else {
                                                other.copy(cpuIndices = other.cpuIndices.filter { it != core.index })
                                            }
                                        }
                                        onUpdateDraft(updatedConfigs)
                                    },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .testTag("radio_cpu_${core.index}_cluster_${c.clusterIndex}"),
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = IdePrimaryCyan,
                                        unselectedColor = TextMuted
                                    )
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = IdeBorder.copy(alpha = 0.3f))
                }
            }
        }
    }
}

/**
 * Section C: HASIL PRATINJAU (Live Engine Preview Table & Apply Button)
 */
@Composable
private fun LivePreviewCard(
    preview: TopologyPreviewResult?,
    allCores: List<CpuCoreDetail>,
    controllerInfo: PerformanceControllerInfo?,
    syncCpufreq: Boolean,
    isProcessing: Boolean,
    draftConfigs: List<ClusterConfig>,
    onUpdateSlot: (Int, Long?) -> Unit,
    onApplyClick: () -> Unit
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
                    text = "HASIL PRATINJAU DARI ENGINE",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = IdePrimaryCyan
                )

                if (preview != null) {
                    Surface(
                        shape = RoundedCornerShape(2.dp),
                        color = if (preview.isValid) IdeSecondaryGreen.copy(alpha = 0.2f) else IdeErrorRed.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (preview.isValid) "VALID" else "INVALID",
                            color = if (preview.isValid) IdeSecondaryGreen else IdeErrorRed,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (preview != null) {
                // Preview Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IdeSurfaceHeader)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cluster", modifier = Modifier.width(70.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text("CPU Cores", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Text("Domain", modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    if (syncCpufreq && controllerInfo != null) {
                        Text("Slot CSRAM", modifier = Modifier.width(130.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = IdeBorder)

                // Preview Table Rows
                for (cluster in preview.plan.clusters) {
                    val assignedCores = cluster.cpuIndices.mapNotNull { allCores.getOrNull(it)?.nodeName }
                    val coresStr = if (assignedCores.isNotEmpty()) assignedCores.joinToString(", ") else "(kosong)"
                    val autoSlot = preview.assignedSlots[cluster.clusterIndex]
                    val currentSelectedSlot = draftConfigs.firstOrNull { it.clusterIndex == cluster.clusterIndex }?.selectedDomainSlot
                    val isDuplicate = currentSelectedSlot != null && preview.duplicateSlots.contains(currentSelectedSlot)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cluster ${cluster.clusterIndex}",
                            modifier = Modifier.width(70.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = IdePrimaryCyan
                        )
                        Text(
                            text = coresStr,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (cluster.cpuIndices.isEmpty()) IdeErrorRed else TextPrimary
                        )
                        Text(
                            text = if (syncCpufreq) "dom ${cluster.clusterIndex}" else "cpu-map",
                            modifier = Modifier.width(60.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )

                        if (syncCpufreq && controllerInfo != null) {
                            Box(modifier = Modifier.width(130.dp)) {
                                SlotDropdown(
                                    clusterIndex = cluster.clusterIndex,
                                    selectedSlot = currentSelectedSlot,
                                    autoSlot = autoSlot,
                                    availableSlots = controllerInfo.availableSlots,
                                    isDuplicate = isDuplicate,
                                    onSelectSlot = { newSlot -> onUpdateSlot(cluster.clusterIndex, newSlot) }
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = IdeBorder.copy(alpha = 0.5f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Errors Display
                if (preview.errors.isNotEmpty()) {
                    Surface(
                        color = IdeErrorRed.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IdeErrorRed),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.fillMaxWidth().testTag("preview_error_box")
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (err in preview.errors) {
                                Row(verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = IdeErrorRed, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = err, color = IdeErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Warnings Display
                if (preview.warnings.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (w in preview.warnings) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = IdeWarningAmber, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = w, color = IdeWarningAmber, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Apply Action Button
                val isApplyEnabled = preview.isValid && !isProcessing && draftConfigs.isNotEmpty()
                Button(
                    onClick = onApplyClick,
                    enabled = isApplyEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .testTag("apply_dts_button"),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IdePrimaryCyan,
                        disabledContainerColor = IdeBorder
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = IdeBackground)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = if (isApplyEnabled) "APPLY KE DTS" else "APPLY NONAKTIF (PERBAIKI ERROR)",
                        color = if (isApplyEnabled) IdeBackground else TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Slot Dropdown: allows selecting "auto: 0x..." or explicit available slots.
 */
@Composable
private fun SlotDropdown(
    clusterIndex: Int,
    selectedSlot: Long?,
    autoSlot: Long?,
    availableSlots: List<Long>,
    isDuplicate: Boolean,
    onSelectSlot: (Long?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val displayText = if (selectedSlot == null) {
        if (autoSlot != null) "auto: 0x${autoSlot.toString(16)}" else "auto: -"
    } else {
        "0x${selectedSlot.toString(16)}"
    }

    Box {
        Surface(
            color = if (isDuplicate) IdeErrorRed.copy(alpha = 0.2f) else IdeSurfaceHeader,
            shape = RoundedCornerShape(3.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDuplicate) IdeErrorRed else IdeBorder),
            modifier = Modifier
                .clickable { expanded = true }
                .testTag("slot_dropdown_cluster_$clusterIndex")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (selectedSlot != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (isDuplicate) IdeErrorRed else if (selectedSlot != null) IdePrimaryCyan else TextPrimary,
                    maxLines = 1
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(IdeSurface)
        ) {
            // Auto Option
            DropdownMenuItem(
                text = {
                    Text(
                        text = "auto (${if (autoSlot != null) "0x" + autoSlot.toString(16) else "otomatis"})",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (selectedSlot == null) IdePrimaryCyan else TextPrimary,
                        fontWeight = if (selectedSlot == null) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onSelectSlot(null) // null = auto
                    expanded = false
                }
            )

            // Available Slots Options
            for (slot in availableSlots) {
                val hex = "0x" + slot.toString(16)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = hex,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (selectedSlot == slot) IdePrimaryCyan else TextPrimary,
                            fontWeight = if (selectedSlot == slot) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelectSlot(slot)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun generateDynamicPresets(totalCores: Int): List<List<Int>> {
    return when (totalCores) {
        8 -> listOf(listOf(6, 2), listOf(4, 4), listOf(2, 3, 3), listOf(4, 2, 2), listOf(8))
        6 -> listOf(listOf(4, 2), listOf(2, 2, 2), listOf(3, 3), listOf(6))
        4 -> listOf(listOf(2, 2), listOf(3, 1), listOf(1, 1, 1, 1), listOf(4))
        else -> listOf(listOf(totalCores / 2, totalCores - totalCores / 2), listOf(totalCores))
    }
}

private fun createConfigsFromSizes(sizes: List<Int>, totalCpus: Int): List<ClusterConfig> {
    val configs = mutableListOf<ClusterConfig>()
    var currentCpu = 0
    for ((cIdx, size) in sizes.withIndex()) {
        val cpus = (currentCpu until (currentCpu + size)).filter { it < totalCpus }
        configs.add(ClusterConfig(cIdx, cpus, selectedDomainSlot = null))
        currentCpu += size
    }
    return configs
}
