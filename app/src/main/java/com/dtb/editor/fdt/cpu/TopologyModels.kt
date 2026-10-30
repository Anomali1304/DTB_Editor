package com.dtb.editor.fdt.cpu

import com.dtb.editor.fdt.model.FdtNode

/**
 * Cluster configuration in a topology plan.
 */
data class ClusterConfig(
    val clusterIndex: Int,
    val cpuIndices: List<Int>,
    val selectedDomainSlot: Long? = null
)

/**
 * Validated topology plan representing desired CPU cluster layout and optional cpufreq sync settings.
 */
data class TopologyPlan(
    val clusters: List<ClusterConfig>,
    val syncCpufreq: Boolean = true,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Detailed information for an individual CPU core extracted from the DT AST.
 */
data class CpuCoreDetail(
    val index: Int,
    val nodeName: String,
    val regAddress: Long,
    val phandle: Long?,
    val microarch: String = "",
    val capacity: Long? = null,
    val originalPerfDomainPhandle: Long? = null,
    val originalPerfDomainIndex: Int? = null,
    val originalSlotAddress: Long? = null,
    val supplies: List<String> = emptyList(),
    val astNode: FdtNode? = null
)

/**
 * Discovered cpufreq performance controller details.
 */
data class PerformanceControllerInfo(
    val phandle: Long,
    val nodeName: String,
    val fullPath: String,
    val addressCells: Int = 2,
    val sizeCells: Int = 2,
    val existingSlots: List<Pair<Long, Long>>, // (slotAddress, slotSize)
    val stride: Long = 0x120L,
    val availableSlots: List<Long>,
    val maxDomainsAllowed: Int = 16,
    val cpuhvfsFound: Boolean = false,
    val astNode: FdtNode
)
