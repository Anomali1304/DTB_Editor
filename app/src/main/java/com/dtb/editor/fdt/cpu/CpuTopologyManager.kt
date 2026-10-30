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
    val diagnostics: List<String>
)

class CpuTopologyManager {

    /**
     * Locate the /cpus node anywhere in the tree (handles root /cpus, labeled cpus:, and paths)
     */
    fun findCpusNode(rootNode: FdtNode): FdtNode? {
        // Direct child
        val direct = rootNode.findChild("cpus")
            ?: rootNode.children.firstOrNull { it.name.startsWith("cpus") || it.label == "cpus" }
        if (direct != null) return direct

        // Recursive search for any node named cpus or label cpus
        fun search(node: FdtNode): FdtNode? {
            if (node.name == "cpus" || node.name.startsWith("cpus@") || node.label == "cpus") {
                return node
            }
            for (child in node.children) {
                val found = search(child)
                if (found != null) return found
            }
            return null
        }
        return search(rootNode)
    }

    fun extractTopology(rootNode: FdtNode): CpuTopologyState {
        val diagnostics = mutableListOf<String>()
        val cpusNode = findCpusNode(rootNode)

        if (cpusNode == null) {
            diagnostics.add("No /cpus node found in imported Device Tree.")
            return CpuTopologyState(emptyList(), 0, false, diagnostics)
        }

        // Discover CPU core nodes (cpu@... or cpu...)
        val cpuNodes = cpusNode.children.filter {
            (it.name.startsWith("cpu@") || (it.name.startsWith("cpu") && it.name != "cpu-map"))
        }

        if (cpuNodes.isEmpty()) {
            diagnostics.add("Found /cpus container, but no child cpu@ core nodes were detected.")
            return CpuTopologyState(emptyList(), 0, false, diagnostics)
        }

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

        // Discover cpu-map
        val cpuMapNode = cpusNode.findChild("cpu-map")
            ?: cpusNode.children.firstOrNull { it.name == "cpu-map" }
        val clusters = mutableListOf<CpuCluster>()

        if (cpuMapNode != null) {
            val clusterNodes = cpuMapNode.children.filter { it.name.startsWith("cluster") }
            for (cIdx in clusterNodes.indices) {
                val clusterNode = clusterNodes[cIdx]
                val cluster = CpuCluster(index = cIdx, name = clusterNode.name)

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

        if (clusters.isEmpty()) {
            // Assign all cores to cluster0 if no explicit cpu-map node is present
            val defaultCluster = CpuCluster(0, "cluster0", coreList.map { it.copy(clusterIndex = 0) }.toMutableList())
            clusters.add(defaultCluster)
            diagnostics.add("No /cpus/cpu-map topology node found. Cores displayed in flat layout.")
        }

        return CpuTopologyState(
            clusters = clusters,
            totalCores = coreList.size,
            hasCpuMap = cpuMapNode != null,
            diagnostics = diagnostics
        )
    }

    /**
     * Applies preset topology groupings (e.g. 4+4, 4+2+2, 2+3+3, or custom) to the AST rootNode
     */
    fun applyPreset(
        rootNode: FdtNode,
        clusterSizes: List<Int>
    ): Pair<Boolean, String> {
        val cpusNode = findCpusNode(rootNode)
            ?: return Pair(false, "Cannot find /cpus node in Device Tree")

        val cpuNodes = cpusNode.children.filter { it.name.startsWith("cpu@") || (it.name.startsWith("cpu") && it.name != "cpu-map") }
            .sortedBy { it.findProperty("reg")?.asSingleU32() ?: parseRegFromName(it.name) }

        val totalCores = cpuNodes.size
        val requestedSum = clusterSizes.sum()

        if (requestedSum != totalCores) {
            return Pair(false, "Preset core count ($requestedSum) does not match actual CPU cores found ($totalCores)")
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

        // Reconstruct cpu-map node
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
            appendLine("Topology updated to ${clusterSizes.joinToString("+")}:")
            appendLine("- Reconstructed /cpus/cpu-map with ${clusterSizes.size} clusters")
            appendLine("- Mapped $totalCores physical cores with valid phandles")
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
