package com.dtb.editor.fdt.cpu

import com.dtb.editor.fdt.model.FdtNode

data class CpuCoreInfo(
    val nodeName: String,
    val regAddress: Long,
    val phandle: Long?,
    val clusterIndex: Int,
    val compatible: String = "",
    val coreIndex: Int = 0
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
    val cores: List<CpuCoreDetail> = emptyList(),
    val controllerInfo: PerformanceControllerInfo? = null,
    val assignedSlots: Map<Int, Long> = emptyMap(),
    val diagnostics: List<String> = emptyList(),
    val syncCpufreqSupported: Boolean = false,
    val syncCpufreqEnabled: Boolean = true
)

class CpuTopologyManager(
    val engine: TopologyEngine = TopologyEngine()
) {

    fun extractTopology(rootNode: FdtNode): CpuTopologyState {
        val diagnostics = mutableListOf<String>()
        val cores = engine.extractCpuCores(rootNode)

        if (cores.isEmpty()) {
            diagnostics.add("No /cpus core nodes detected in imported Device Tree.")
            return CpuTopologyState(emptyList(), 0, false, emptyList(), null, emptyMap(), diagnostics, false, false)
        }

        val controllerInfo = engine.discoverPerformanceController(rootNode, cores)
        val syncSupported = controllerInfo != null

        if (syncSupported) {
            diagnostics.add("[cpufreq] Discovered performance controller '${controllerInfo!!.nodeName}' at ${controllerInfo.fullPath} (phandle 0x${controllerInfo.phandle.toString(16)})")
            diagnostics.add("[cpufreq] Available CSRAM slots (${controllerInfo.availableSlots.size}): " +
                    controllerInfo.availableSlots.joinToString { "0x${it.toString(16)}" })
            if (controllerInfo.cpuhvfsFound) {
                diagnostics.add("[cpufreq] Discovered cpuhvfs node. Max domain limit: ${controllerInfo.maxDomainsAllowed}")
            }
            diagnostics.addAll(controllerInfo.diagnostics)
        } else {
            diagnostics.add("[cpufreq] No performance-controller detected via performance-domains phandle.")
        }

        // Build clusters representation
        val cpusNode = rootNode.findChild("cpus")
            ?: rootNode.children.firstOrNull { it.name.startsWith("cpus") || it.label == "cpus" }
        val cpuMapNode = cpusNode?.findChild("cpu-map")
            ?: cpusNode?.children?.firstOrNull { it.name == "cpu-map" }

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

                    val matchedCore = if (refPhandle != null) {
                        cores.firstOrNull { it.phandle != null && it.phandle == refPhandle }
                    } else null
                        ?: cores.getOrNull(cluster.cores.size + clusters.sumOf { it.cores.size })

                    if (matchedCore != null) {
                        cluster.cores.add(
                            CpuCoreInfo(
                                nodeName = matchedCore.nodeName,
                                regAddress = matchedCore.regAddress,
                                phandle = matchedCore.phandle,
                                clusterIndex = cIdx,
                                compatible = matchedCore.microarch,
                                coreIndex = matchedCore.index
                            )
                        )
                    } else {
                        diagnostics.add("Warning: core node ${coreChild.name} in cluster$cIdx has unresolvable cpu phandle 0x${refPhandle?.toString(16)}")
                    }
                }
                clusters.add(cluster)
            }
        }

        if (clusters.isEmpty()) {
            val defaultCluster = CpuCluster(
                index = 0,
                name = "cluster0",
                cores = cores.map {
                    CpuCoreInfo(
                        nodeName = it.nodeName,
                        regAddress = it.regAddress,
                        phandle = it.phandle,
                        clusterIndex = 0,
                        compatible = it.microarch,
                        coreIndex = it.index
                    )
                }.toMutableList()
            )
            clusters.add(defaultCluster)
            diagnostics.add("No /cpus/cpu-map topology node found. Cores grouped in single flat layout.")
        }

        val clusterConfigs = clusters.map { c ->
            val cpuIndices = c.cores.map { coreInfo ->
                val matched = if (coreInfo.phandle != null) {
                    cores.firstOrNull { it.phandle == coreInfo.phandle }
                } else {
                    cores.firstOrNull { it.nodeName == coreInfo.nodeName }
                }
                matched?.index ?: coreInfo.coreIndex
            }
            ClusterConfig(c.index, cpuIndices)
        }

        val assignedSlots = if (controllerInfo != null) {
            try {
                engine.assignDomainSlots(clusterConfigs, cores, controllerInfo)
            } catch (e: Exception) {
                diagnostics.add("Domain slot assignment warning: ${e.message}")
                emptyMap()
            }
        } else {
            emptyMap()
        }

        val warnings = engine.generateWarnings(clusterConfigs, cores, controllerInfo)
        diagnostics.addAll(warnings)

        return CpuTopologyState(
            clusters = clusters,
            totalCores = cores.size,
            hasCpuMap = (cpuMapNode != null),
            cores = cores,
            controllerInfo = controllerInfo,
            assignedSlots = assignedSlots,
            diagnostics = diagnostics,
            syncCpufreqSupported = syncSupported,
            syncCpufreqEnabled = syncSupported
        )
    }

    fun applyPreset(
        rootNode: FdtNode,
        clusterSizes: List<Int>,
        syncCpufreq: Boolean = true
    ): Pair<Boolean, String> {
        val cores = engine.extractCpuCores(rootNode)
        if (cores.isEmpty()) return Pair(false, "No CPU cores found in /cpus node")

        val controllerInfo = if (syncCpufreq) engine.discoverPerformanceController(rootNode, cores) else null
        val maxClusters = controllerInfo?.availableSlots?.size

        val plan = engine.createPlanFromSizes(clusterSizes, cores.size, maxClusters).copy(syncCpufreq = syncCpufreq)
        if (!plan.isValid) return Pair(false, plan.errors.joinToString("; "))

        val warnings = engine.generateWarnings(plan.clusters, cores, controllerInfo)
        val planWithWarnings = plan.copy(warnings = warnings)

        val slots = if (controllerInfo != null) engine.assignDomainSlots(plan.clusters, cores, controllerInfo) else emptyMap()

        return engine.applyPlanToAst(rootNode, planWithWarnings, cores, controllerInfo, slots)
    }

    fun applyCustomPlan(
        rootNode: FdtNode,
        clusterConfigs: List<ClusterConfig>,
        syncCpufreq: Boolean = true
    ): Pair<Boolean, String> {
        val cores = engine.extractCpuCores(rootNode)
        if (cores.isEmpty()) return Pair(false, "No CPU cores found in /cpus node")

        val controllerInfo = if (syncCpufreq) engine.discoverPerformanceController(rootNode, cores) else null
        val maxClusters = controllerInfo?.availableSlots?.size

        val plan = engine.validatePlan(clusterConfigs, cores.size, maxClusters).copy(syncCpufreq = syncCpufreq)
        if (!plan.isValid) return Pair(false, plan.errors.joinToString("; "))

        val warnings = engine.generateWarnings(plan.clusters, cores, controllerInfo)
        val planWithWarnings = plan.copy(warnings = warnings)

        val slots = if (controllerInfo != null) engine.assignDomainSlots(plan.clusters, cores, controllerInfo) else emptyMap()

        return engine.applyPlanToAst(rootNode, planWithWarnings, cores, controllerInfo, slots)
    }

    fun previewPlan(
        clusterConfigs: List<ClusterConfig>,
        cores: List<CpuCoreDetail>,
        controllerInfo: PerformanceControllerInfo?,
        syncCpufreq: Boolean
    ): TopologyPreviewResult {
        return engine.previewPlan(clusterConfigs, cores, controllerInfo, syncCpufreq)
    }
}
