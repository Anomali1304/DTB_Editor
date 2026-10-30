package com.dtb.editor.fdt.cpu

import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.model.FdtProperty
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class CpuCoreInfo(
    val nodeName: String,
    val regAddress: Long,
    val phandle: Long?,
    val clusterIndex: Int,
    val compatible: String = ""
)

data class CpuCluster(
    val index: Int,
    val name: String,
    val cores: MutableList<CpuCoreInfo> = mutableListOf()
)

data class CpuTopologyState(
    val clusters: List<CpuCluster>,
    val totalCores: Int,
    val hasCpuMap: Boolean,
    val warnings: List<String>
)

class CpuTopologyManager {

    fun extractTopology(rootNode: FdtNode): CpuTopologyState {
        val warnings = mutableListOf<String>()
        val cpusNode = rootNode.findChild("cpus") ?: rootNode.children.firstOrNull { it.name.startsWith("cpus") }

        if (cpusNode == null) {
            return CpuTopologyState(emptyList(), 0, false, listOf("No /cpus node found in device tree"))
        }

        // 1. Discover all CPU core nodes (cpu@...)
        val cpuNodes = cpusNode.children.filter { it.name.startsWith("cpu@") || (it.name.startsWith("cpu") && it.name != "cpu-map") }
        val coreList = mutableListOf<CpuCoreInfo>()

        for (cpu in cpuNodes) {
            val regProp = cpu.findProperty("reg")
            val regAddr = regProp?.asSingleU32() ?: parseRegFromName(cpu.name)
            val phandle = cpu.getPhandle()
            val comp = cpu.findProperty("compatible")?.asStringList()?.joinToString() ?: ""
            coreList.add(
                CpuCoreInfo(
                    nodeName = cpu.name,
                    regAddress = regAddr,
                    phandle = phandle,
                    clusterIndex = 0,
                    compatible = comp
                )
            )
        }

        // 2. Discover cpu-map node if present
        val cpuMapNode = cpusNode.findChild("cpu-map")
        val clusters = mutableListOf<CpuCluster>()

        if (cpuMapNode != null) {
            val clusterNodes = cpuMapNode.children.filter { it.name.startsWith("cluster") }
            for (cIdx in clusterNodes.indices) {
                val clusterNode = clusterNodes[cIdx]
                val cluster = CpuCluster(index = cIdx, name = clusterNode.name)

                // Search for core nodes inside cluster
                val coreChildNodes = clusterNode.children.filter { it.name.startsWith("core") }
                for (coreChild in coreChildNodes) {
                    val cpuProp = coreChild.findProperty("cpu")
                    val refPhandle = cpuProp?.asSingleU32()
                    val matchedCore = coreList.firstOrNull { it.phandle != null && it.phandle == refPhandle }
                        ?: coreList.getOrNull(cluster.cores.size + clusters.sumOf { it.cores.size })

                    if (matchedCore != null) {
                        cluster.cores.add(matchedCore.copy(clusterIndex = cIdx))
                    }
                }
                clusters.add(cluster)
            }
        }

        // If no cpu-map or cluster assignment was found, default assign based on available cores
        if (clusters.isEmpty() || clusters.sumOf { it.cores.size } < coreList.size) {
            clusters.clear()
            val total = coreList.size
            if (total == 8) {
                // Default 4+4
                val c0 = CpuCluster(0, "cluster0", coreList.take(4).map { it.copy(clusterIndex = 0) }.toMutableList())
                val c1 = CpuCluster(1, "cluster1", coreList.drop(4).map { it.copy(clusterIndex = 1) }.toMutableList())
                clusters.addAll(listOf(c0, c1))
            } else {
                val c0 = CpuCluster(0, "cluster0", coreList.map { it.copy(clusterIndex = 0) }.toMutableList())
                clusters.add(c0)
            }
        }

        return CpuTopologyState(
            clusters = clusters,
            totalCores = coreList.size,
            hasCpuMap = cpuMapNode != null,
            warnings = warnings
        )
    }

    /**
     * Applies preset topology groupings (e.g. 4+4, 4+2+2, 2+3+3, or custom) to the AST rootNode
     */
    fun applyPreset(
        rootNode: FdtNode,
        clusterSizes: List<Int>
    ): Pair<Boolean, String> {
        val cpusNode = rootNode.findChild("cpus") ?: rootNode.children.firstOrNull { it.name.startsWith("cpus") }
            ?: return Pair(false, "Cannot find /cpus node in Device Tree")

        val cpuNodes = cpusNode.children.filter { it.name.startsWith("cpu@") || (it.name.startsWith("cpu") && it.name != "cpu-map") }
            .sortedBy { it.findProperty("reg")?.asSingleU32() ?: parseRegFromName(it.name) }

        val totalCores = cpuNodes.size
        val requestedSum = clusterSizes.sum()

        if (requestedSum != totalCores) {
            return Pair(false, "Preset core count ($requestedSum) does not match actual CPU cores found in DTB ($totalCores)")
        }

        // Ensure every CPU node has a phandle
        var maxPhandle = 0x8000L
        for (node in cpuNodes) {
            val p = node.getPhandle()
            if (p != null && p > maxPhandle) maxPhandle = p
        }

        for (node in cpuNodes) {
            if (node.getPhandle() == null) {
                maxPhandle++
                val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                buf.putInt(maxPhandle.toInt())
                node.properties.add(FdtProperty("phandle", buf.array()))
            }
        }

        // Reconstruct or update cpu-map node
        var cpuMapNode = cpusNode.findChild("cpu-map")
        if (cpuMapNode == null) {
            cpuMapNode = FdtNode(name = "cpu-map", fullPath = "${cpusNode.fullPath}/cpu-map")
            cpusNode.children.add(cpuMapNode)
        } else {
            cpuMapNode.children.clear()
        }

        var coreIdx = 0
        for (cIdx in clusterSizes.indices) {
            val count = clusterSizes[cIdx]
            val clusterNode = FdtNode(
                name = "cluster$cIdx",
                fullPath = "${cpuMapNode.fullPath}/cluster$cIdx"
            )
            cpuMapNode.children.add(clusterNode)

            for (i in 0 until count) {
                val targetCpu = cpuNodes[coreIdx]
                val phandle = targetCpu.getPhandle() ?: 0L

                val coreNode = FdtNode(
                    name = "core$i",
                    fullPath = "${clusterNode.fullPath}/core$i"
                )
                val phandleBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                phandleBuf.putInt(phandle.toInt())
                coreNode.properties.add(FdtProperty("cpu", phandleBuf.array()))

                clusterNode.children.add(coreNode)
                coreIdx++
            }
        }

        val msg = buildString {
            appendLine("Topology successfully updated to ${clusterSizes.joinToString("+")}:")
            appendLine("- /cpus/cpu-map reconstructed with ${clusterSizes.size} clusters")
            appendLine("- Mapped $totalCores cores with valid hardware phandles")
            appendLine("NOTE: This updates scheduler affinity topology in Linux kernel. CPUFreq DVFS policies remain tied to hardware clock/regulator domains.")
        }

        return Pair(true, msg)
    }

    private fun parseRegFromName(name: String): Long {
        val atIdx = name.indexOf('@')
        if (atIdx != -1) {
            val hex = name.substring(atIdx + 1)
            return hex.toLongOrNull(16) ?: 0L
        }
        return 0L
    }
}
