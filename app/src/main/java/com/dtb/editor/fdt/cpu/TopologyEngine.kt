package com.dtb.editor.fdt.cpu

import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.model.FdtProperty
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Pure Kotlin Topology Engine for CPU topology, cluster planning, and cpufreq performance-domain synchronization.
 * Zero Android dependencies. Highly testable.
 */
class TopologyEngine {

    /**
     * Validates a cluster plan against system total CPU count and available domain slots.
     */
    fun validatePlan(
        clusterConfigs: List<ClusterConfig>,
        totalCpus: Int,
        maxClusters: Int? = null
    ): TopologyPlan {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (totalCpus <= 0) {
            errors.add("Invalid total CPU count: $totalCpus")
            return TopologyPlan(clusterConfigs, errors = errors)
        }

        if (clusterConfigs.isEmpty()) {
            errors.add("Plan must contain at least one cluster")
            return TopologyPlan(clusterConfigs, errors = errors)
        }

        if (maxClusters != null && clusterConfigs.size > maxClusters) {
            errors.add("Requested cluster count (${clusterConfigs.size}) exceeds available cpufreq performance domain slots (slot hanya $maxClusters)")
        }

        // Sort clusters by clusterIndex and renumber to 0..n-1
        val sortedConfigs = clusterConfigs.sortedBy { it.clusterIndex }
        val normalizedConfigs = sortedConfigs.mapIndexed { newIdx, config ->
            config.copy(clusterIndex = newIdx)
        }

        val assignedCpuSet = mutableSetOf<Int>()

        for ((idx, cluster) in normalizedConfigs.withIndex()) {
            if (cluster.cpuIndices.isEmpty()) {
                errors.add("Cluster $idx (${cluster.clusterIndex}) cannot be empty")
            }
            for (cpu in cluster.cpuIndices) {
                if (cpu < 0 || cpu >= totalCpus) {
                    errors.add("CPU index $cpu in cluster $idx is out of range (0 until $totalCpus)")
                }
                if (assignedCpuSet.contains(cpu)) {
                    errors.add("CPU $cpu is assigned to multiple clusters")
                }
                assignedCpuSet.add(cpu)
            }
        }

        val unassignedCpus = (0 until totalCpus).filterNot { assignedCpuSet.contains(it) }
        if (unassignedCpus.isNotEmpty()) {
            errors.add("Unassigned CPUs detected: ${unassignedCpus.joinToString()}")
        }

        return TopologyPlan(normalizedConfigs, warnings = warnings, errors = errors)
    }

    fun validatePlan(
        clusterConfigs: List<ClusterConfig>,
        totalCpus: Int,
        controllerInfo: PerformanceControllerInfo?
    ): TopologyPlan {
        return validatePlan(clusterConfigs, totalCpus, controllerInfo?.availableSlots?.size)
    }

    /**
     * Creates a default cluster plan given cluster sizes (e.g. [2, 3, 3] for 8 CPUs).
     */
    fun createPlanFromSizes(
        sizes: List<Int>,
        totalCpus: Int,
        maxClusters: Int? = null
    ): TopologyPlan {
        val configs = mutableListOf<ClusterConfig>()
        var currentCpu = 0
        for ((cIdx, size) in sizes.withIndex()) {
            val cpus = (currentCpu until (currentCpu + size)).filter { it < totalCpus }
            configs.add(ClusterConfig(cIdx, cpus))
            currentCpu += size
        }
        return validatePlan(configs, totalCpus, maxClusters)
    }

    fun createPlanFromSizes(
        sizes: List<Int>,
        totalCpus: Int,
        controllerInfo: PerformanceControllerInfo?
    ): TopologyPlan {
        return createPlanFromSizes(sizes, totalCpus, controllerInfo?.availableSlots?.size)
    }

    /**
     * Calculates a live preview of the topology plan with assigned slots, validation errors, and warnings.
     */
    fun previewPlan(
        clusterConfigs: List<ClusterConfig>,
        cpuCores: List<CpuCoreDetail>,
        controllerInfo: PerformanceControllerInfo?,
        syncCpufreq: Boolean
    ): TopologyPreviewResult {
        val maxClusters = if (syncCpufreq) controllerInfo?.availableSlots?.size else null
        val plan = validatePlan(clusterConfigs, cpuCores.size, maxClusters).copy(syncCpufreq = syncCpufreq)
        val errors = plan.errors.toMutableList()
        val warnings = mutableListOf<String>()
        var assignedSlots = emptyMap<Int, Long>()
        val duplicateSlots = mutableSetOf<Long>()

        if (syncCpufreq && controllerInfo != null) {
            // Check for duplicate manual slot selections
            val manualSelections = clusterConfigs.mapNotNull { it.selectedDomainSlot }
            val dupes = manualSelections.groupingBy { it }.eachCount().filter { it.value > 1 }.keys
            if (dupes.isNotEmpty()) {
                duplicateSlots.addAll(dupes)
                errors.add("Slots (${dupes.joinToString { "0x" + it.toString(16) }}) selected by more than one cluster")
            }

            if (clusterConfigs.size > controllerInfo.availableSlots.size) {
                val limit = controllerInfo.availableSlots.size
                if (!errors.any { it.contains("slot hanya $limit") }) {
                    errors.add("Requested cluster count (${clusterConfigs.size}) exceeds available cpufreq performance domain slots (slot hanya $limit)")
                }
            }

            if (errors.isEmpty()) {
                try {
                    assignedSlots = assignDomainSlots(plan.clusters, cpuCores, controllerInfo)
                } catch (e: Exception) {
                    errors.add(e.message ?: "Failed to determine slot domain")
                }
            }
        }

        warnings.addAll(generateWarnings(plan.clusters, cpuCores, controllerInfo))

        val summary = buildString {
            append("Topologi: ${plan.clusters.size} cluster (${plan.clusters.map { it.cpuIndices.size }.joinToString("+")})")
            if (syncCpufreq && controllerInfo != null) {
                append(" | ${assignedSlots.size} domain cpufreq")
            } else {
                append(" | mode cpu-map saja")
            }
        }

        return TopologyPreviewResult(
            plan = plan.copy(errors = errors, warnings = warnings),
            assignedSlots = assignedSlots,
            duplicateSlots = duplicateSlots,
            warnings = warnings,
            errors = errors,
            summary = summary
        )
    }

    /**
     * Discovers CPU cores and performance controller from the DT AST root node.
     */
    fun extractCpuCores(rootNode: FdtNode): List<CpuCoreDetail> {
        val cpusNode = findCpusNode(rootNode) ?: return emptyList()
        val cpuNodes = cpusNode.children.filter {
            (it.name.startsWith("cpu@") || (it.name.startsWith("cpu") && it.name != "cpu-map"))
        }.sortedBy { it.findProperty("reg")?.asSingleU32() ?: parseRegFromName(it.name) }

        val cores = mutableListOf<CpuCoreDetail>()

        for ((index, cpu) in cpuNodes.withIndex()) {
            val regProp = cpu.findProperty("reg")
            val regAddr = regProp?.asSingleU32() ?: parseRegFromName(cpu.name)
            val phandle = cpu.getPhandle()
            val comp = cpu.findProperty("compatible")?.asStringList()?.joinToString() ?: ""
            val cap = cpu.findProperty("capacity-dmips-mhz")?.asSingleU32()

            var perfPhandle: Long? = null
            var perfIdx: Int? = null
            val perfProp = cpu.findProperty("performance-domains")
            if (perfProp != null && perfProp.data.size >= 8) {
                val u32s = perfProp.asU32List()
                if (u32s.size >= 2) {
                    perfPhandle = u32s[0]
                    perfIdx = u32s[1].toInt()
                }
            }

            // Extract supply regulators
            val supplies = cpu.properties
                .filter { it.name.endsWith("-supply") }
                .map { it.name }

            cores.add(
                CpuCoreDetail(
                    index = index,
                    nodeName = cpu.name,
                    regAddress = regAddr,
                    phandle = phandle,
                    microarch = comp,
                    capacity = cap,
                    originalPerfDomainPhandle = perfPhandle,
                    originalPerfDomainIndex = perfIdx,
                    supplies = supplies,
                    astNode = cpu
                )
            )
        }

        // Resolve originalSlotAddress for cores if controller reg is available
        val firstPhandle = cores.firstNotNullOfOrNull { it.originalPerfDomainPhandle }
        if (firstPhandle != null) {
            val controllerNode = findNodeByPhandle(rootNode, firstPhandle)
            if (controllerNode != null) {
                val parentNode = findParentNode(rootNode, controllerNode)
                val addrCells = parentNode?.findProperty("#address-cells")?.asSingleU32()?.toInt() ?: 2
                val sizeCells = parentNode?.findProperty("#size-cells")?.asSingleU32()?.toInt() ?: 2
                val regProp = controllerNode.findProperty("reg")
                if (regProp != null) {
                    val regCellWidth = addrCells + sizeCells
                    val u32s = regProp.asU32List()
                    val existingSlots = mutableListOf<Pair<Long, Long>>()
                    var i = 0
                    while (i + regCellWidth <= u32s.size) {
                        val addr = parseCellsValue(u32s, i, addrCells)
                        val size = parseCellsValue(u32s, i + addrCells, sizeCells)
                        existingSlots.add(Pair(addr, size))
                        i += regCellWidth
                    }
                    if (existingSlots.isNotEmpty()) {
                        return cores.map { core ->
                            val slot = if (core.originalPerfDomainIndex != null && core.originalPerfDomainIndex in existingSlots.indices) {
                                existingSlots[core.originalPerfDomainIndex].first
                            } else {
                                null
                            }
                            core.copy(originalSlotAddress = slot)
                        }
                    }
                }
            }
        }

        return cores
    }

    /**
     * Discovers cpufreq performance controller node dynamically via performance-domains phandle.
     */
    fun discoverPerformanceController(
        rootNode: FdtNode,
        cpuCores: List<CpuCoreDetail>
    ): PerformanceControllerInfo? {
        val firstPerfPhandle = cpuCores.firstNotNullOfOrNull { it.originalPerfDomainPhandle } ?: return null
        val controllerNode = findNodeByPhandle(rootNode, firstPerfPhandle) ?: return null

        val parentNode = findParentNode(rootNode, controllerNode)
        val addrCells = parentNode?.findProperty("#address-cells")?.asSingleU32()?.toInt() ?: 2
        val sizeCells = parentNode?.findProperty("#size-cells")?.asSingleU32()?.toInt() ?: 2

        val regProp = controllerNode.findProperty("reg") ?: return null
        val regCellWidth = addrCells + sizeCells
        val regU32s = regProp.asU32List()

        if (regU32s.size < regCellWidth) return null

        val existingSlots = mutableListOf<Pair<Long, Long>>()
        var idx = 0
        while (idx + regCellWidth <= regU32s.size) {
            val addr = parseCellsValue(regU32s, idx, addrCells)
            val size = parseCellsValue(regU32s, idx + addrCells, sizeCells)
            existingSlots.add(Pair(addr, size))
            idx += regCellWidth
        }

        if (existingSlots.isEmpty()) return null

        // Determine stride
        val stride = if (existingSlots.size >= 2) {
            (existingSlots[1].first - existingSlots[0].first).coerceAtLeast(0x40L)
        } else if (existingSlots[0].second > 0) {
            existingSlots[0].second
        } else {
            0x120L
        }

        val firstSlotAddr = existingSlots[0].first
        val slotSize = existingSlots[0].second.coerceAtLeast(0x120L)

        // Check cpuhvfs node
        val cpuhvfsNode = findCpuhvfsNode(rootNode)
        var maxDomainsAllowed = 16
        var csramBase: Long? = null
        var csramSize: Long? = null
        val diagnostics = mutableListOf<String>()

        if (cpuhvfsNode != null) {
            val tblOffProp = cpuhvfsNode.findProperty("tbl-off")
            val pllConProp = cpuhvfsNode.findProperty("pll-con")
            val clkDivProp = cpuhvfsNode.findProperty("clk-div")

            val tblCount = tblOffProp?.asU32List()?.size ?: 16
            val pllCount = pllConProp?.asU32List()?.size ?: 16
            val clkCount = clkDivProp?.asU32List()?.size ?: 16

            maxDomainsAllowed = minOf(tblCount, pllCount, clkCount).coerceAtLeast(1)

            val csramReg = cpuhvfsNode.findProperty("reg")
            if (csramReg != null) {
                val cpuhvfsParent = findParentNode(rootNode, cpuhvfsNode)
                val cpuhvfsAddrCells = cpuhvfsParent?.findProperty("#address-cells")?.asSingleU32()?.toInt() ?: addrCells
                val cpuhvfsSizeCells = cpuhvfsParent?.findProperty("#size-cells")?.asSingleU32()?.toInt() ?: sizeCells
                val cpuhvfsCellWidth = cpuhvfsAddrCells + cpuhvfsSizeCells

                val csramU32s = csramReg.asU32List()
                var offset = 0
                while (offset + cpuhvfsCellWidth <= csramU32s.size) {
                    val base = parseCellsValue(csramU32s, offset, cpuhvfsAddrCells)
                    val size = parseCellsValue(csramU32s, offset + cpuhvfsAddrCells, cpuhvfsSizeCells)
                    if (firstSlotAddr in base until (base + size)) {
                        csramBase = base
                        csramSize = size
                        break
                    }
                    offset += cpuhvfsCellWidth
                }
            }
        }

        // Extrapolate available slots or limit to existing
        val availableSlots: List<Long>
        if (csramBase != null && csramSize != null) {
            val slots = existingSlots.map { it.first }.toMutableList()
            var nextSlot = slots.last() + stride
            val csramEnd = csramBase + csramSize
            while (slots.size < maxDomainsAllowed && (nextSlot + slotSize) <= csramEnd) {
                if (!slots.contains(nextSlot)) {
                    slots.add(nextSlot)
                }
                nextSlot += stride
            }
            availableSlots = slots
        } else {
            availableSlots = existingSlots.map { it.first }
            if (cpuhvfsNode != null) {
                diagnostics.add("[cpufreq] No cpuhvfs reg entry contains first controller slot 0x${firstSlotAddr.toString(16)}. Limiting available slots to existing slots (${availableSlots.size}).")
            }
        }

        // Map original slot address per core
        val updatedCores = cpuCores.map { core ->
            val slot = if (core.originalPerfDomainIndex != null && core.originalPerfDomainIndex in existingSlots.indices) {
                existingSlots[core.originalPerfDomainIndex].first
            } else {
                null
            }
            core.copy(originalSlotAddress = slot)
        }

        return PerformanceControllerInfo(
            phandle = firstPerfPhandle,
            nodeName = controllerNode.name,
            fullPath = controllerNode.fullPath,
            addressCells = addrCells,
            sizeCells = sizeCells,
            existingSlots = existingSlots,
            stride = stride,
            availableSlots = availableSlots,
            maxDomainsAllowed = maxDomainsAllowed,
            cpuhvfsFound = (cpuhvfsNode != null),
            csramBase = csramBase,
            csramSize = csramSize,
            diagnostics = diagnostics,
            astNode = controllerNode
        )
    }

    /**
     * Executes automatic domain slot assignment for clusters based on highest-capacity CPU core matching.
     * Enforces strict slot availability without silent fallbacks.
     */
    fun assignDomainSlots(
        clusters: List<ClusterConfig>,
        cpuCores: List<CpuCoreDetail>,
        controllerInfo: PerformanceControllerInfo
    ): Map<Int, Long> {
        if (clusters.size > controllerInfo.availableSlots.size) {
            throw IllegalArgumentException("Requested cluster count (${clusters.size}) exceeds available cpufreq performance domain slots (${controllerInfo.availableSlots.size})")
        }

        val assignment = mutableMapOf<Int, Long>()
        val availablePool = controllerInfo.availableSlots.toMutableList()

        // 1. Check manual overrides
        val unassignedClusters = mutableListOf<ClusterConfig>()
        for (cluster in clusters) {
            if (cluster.selectedDomainSlot != null && cluster.selectedDomainSlot in availablePool) {
                assignment[cluster.clusterIndex] = cluster.selectedDomainSlot
                availablePool.remove(cluster.selectedDomainSlot)
            } else {
                unassignedClusters.add(cluster)
            }
        }

        // 2. Sort unassigned clusters by highest CPU capacity descending, then lowest min CPU index ascending
        val sortedClusters = unassignedClusters.sortedWith(
            compareByDescending<ClusterConfig> { cluster ->
                val clusterCores = cluster.cpuIndices.mapNotNull { idx -> cpuCores.getOrNull(idx) }
                clusterCores.mapNotNull { it.capacity }.maxOrNull() ?: 0L
            }.thenBy { cluster ->
                cluster.cpuIndices.minOrNull() ?: cluster.clusterIndex
            }
        )

        // 3. Assign slots strictly without silent fallback
        for (cluster in sortedClusters) {
            val clusterCores = cluster.cpuIndices.mapNotNull { idx -> cpuCores.getOrNull(idx) }
            val highestCapCore = clusterCores.maxByOrNull { (it.capacity ?: 0L) * 10000L + it.regAddress }

            val origSlot = highestCapCore?.originalSlotAddress
            if (origSlot != null && origSlot in availablePool) {
                assignment[cluster.clusterIndex] = origSlot
                availablePool.remove(origSlot)
            } else if (availablePool.isNotEmpty()) {
                val nextSlot = availablePool.removeAt(0)
                assignment[cluster.clusterIndex] = nextSlot
            } else {
                throw IllegalStateException("Insufficient available performance domain slots for cluster ${cluster.clusterIndex}")
            }
        }

        return assignment
    }

    /**
     * Inspects plan for topology/hardware warnings.
     */
    fun generateWarnings(
        clusters: List<ClusterConfig>,
        cpuCores: List<CpuCoreDetail>,
        controllerInfo: PerformanceControllerInfo?
    ): List<String> {
        val warnings = mutableListOf<String>()

        for (cluster in clusters) {
            val clusterCores = cluster.cpuIndices.mapNotNull { idx -> cpuCores.getOrNull(idx) }
            val microarchs = clusterCores.map { it.microarch }.filter { it.isNotEmpty() }.toSet()
            if (microarchs.size > 1) {
                warnings.add("Cluster ${cluster.clusterIndex} contains mixed CPU microarchitectures (${microarchs.joinToString()}). Weaker cores will share the clock/voltage domain.")
            }
        }

        return warnings
    }

    /**
     * Applies plan to AST: reconstructs /cpus/cpu-map, sets performance-domains per cpu, and rewrites performance-controller reg and reg-names.
     */
    fun applyPlanToAst(
        rootNode: FdtNode,
        plan: TopologyPlan,
        cpuCores: List<CpuCoreDetail>,
        controllerInfo: PerformanceControllerInfo?,
        assignedSlots: Map<Int, Long>
    ): Pair<Boolean, String> {
        if (!plan.isValid) {
            return Pair(false, "Cannot apply invalid topology plan: ${plan.errors.joinToString("; ")}")
        }

        if (plan.syncCpufreq && controllerInfo != null) {
            if (plan.clusters.size > controllerInfo.availableSlots.size) {
                return Pair(false, "Requested cluster count (${plan.clusters.size}) exceeds available cpufreq performance domain slots (${controllerInfo.availableSlots.size})")
            }
        }

        val cpusNode = findCpusNode(rootNode)
            ?: return Pair(false, "Cannot locate /cpus node in Device Tree")

        val logs = mutableListOf<String>()

        // 1. Calculate max phandle across entire AST tree
        var maxPhandle = calculateMaxPhandle(rootNode)

        // Ensure all cpu nodes have valid phandles
        val cpuNodes = cpusNode.children.filter { it.name.startsWith("cpu@") || (it.name.startsWith("cpu") && it.name != "cpu-map") }
            .sortedBy { it.findProperty("reg")?.asSingleU32() ?: parseRegFromName(it.name) }

        for (cpuNode in cpuNodes) {
            if (cpuNode.getPhandle() == null) {
                maxPhandle++
                val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                buf.putInt(maxPhandle.toInt())
                cpuNode.properties.add(FdtProperty("phandle", buf.array()))
                logs.add("Generated new phandle 0x${maxPhandle.toString(16)} for ${cpuNode.name}")
            }
        }

        // 2. Reconstruct /cpus/cpu-map
        var cpuMapNode = cpusNode.findChild("cpu-map")
        if (cpuMapNode == null) {
            cpuMapNode = FdtNode(name = "cpu-map", fullPath = "${cpusNode.fullPath}/cpu-map")
            cpusNode.children.add(cpuMapNode)
        } else {
            cpuMapNode.children.clear()
        }

        for (clusterConfig in plan.clusters) {
            val cIdx = clusterConfig.clusterIndex
            val clusterNode = FdtNode(name = "cluster$cIdx", fullPath = "${cpuMapNode.fullPath}/cluster$cIdx")
            cpuMapNode.children.add(clusterNode)

            for ((coreIdx, cpuIdx) in clusterConfig.cpuIndices.withIndex()) {
                val targetCpuNode = cpuNodes.getOrNull(cpuIdx)
                val targetPhandle = targetCpuNode?.getPhandle() ?: 0L

                val coreNode = FdtNode(name = "core$coreIdx", fullPath = "${clusterNode.fullPath}/core$coreIdx")
                val phandleBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                phandleBuf.putInt(targetPhandle.toInt())
                coreNode.properties.add(FdtProperty("cpu", phandleBuf.array()))

                clusterNode.children.add(coreNode)
            }
        }
        logs.add("Reconstructed /cpus/cpu-map with ${plan.clusters.size} clusters")

        // 3. Synchronize performance domains if enabled and controller detected
        if (plan.syncCpufreq && controllerInfo != null) {
            val slotSize = controllerInfo.existingSlots.firstOrNull()?.second ?: 0x120L
            val activeSlots = mutableListOf<Pair<Long, Long>>()
            val usedSlotAddrs = mutableSetOf<Long>()

            for (clusterConfig in plan.clusters) {
                val slotAddr = assignedSlots[clusterConfig.clusterIndex]
                    ?: return Pair(false, "Missing domain slot assignment for cluster ${clusterConfig.clusterIndex}")

                if (usedSlotAddrs.contains(slotAddr)) {
                    return Pair(false, "Duplicate domain slot 0x${slotAddr.toString(16)} detected for cluster ${clusterConfig.clusterIndex}")
                }
                usedSlotAddrs.add(slotAddr)
                activeSlots.add(Pair(slotAddr, slotSize))

                val domainIdx = clusterConfig.clusterIndex
                for (cpuIdx in clusterConfig.cpuIndices) {
                    val targetCpuNode = cpuNodes.getOrNull(cpuIdx) ?: continue
                    val perfPropBuf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                    perfPropBuf.putInt(controllerInfo.phandle.toInt())
                    perfPropBuf.putInt(domainIdx)

                    val existingIndex = targetCpuNode.properties.indexOfFirst { it.name == "performance-domains" }
                    val newProp = FdtProperty("performance-domains", perfPropBuf.array())
                    if (existingIndex != -1) {
                        targetCpuNode.properties[existingIndex] = newProp
                    } else {
                        targetCpuNode.properties.add(newProp)
                    }
                }
            }

            // Verify no duplicate slots in activeSlots
            if (activeSlots.map { it.first }.toSet().size != activeSlots.size) {
                return Pair(false, "Duplicate slots detected in performance-controller reg: ${activeSlots.map { "0x" + it.first.toString(16) }}")
            }

            // Rewrite controller reg property
            val addrCells = controllerInfo.addressCells
            val sizeCells = controllerInfo.sizeCells
            val regCellWidth = addrCells + sizeCells
            val regBuffer = ByteBuffer.allocate(activeSlots.size * regCellWidth * 4).order(ByteOrder.BIG_ENDIAN)

            for ((slotAddr, sSize) in activeSlots) {
                writeCellsValue(regBuffer, slotAddr, addrCells)
                writeCellsValue(regBuffer, sSize, sizeCells)
            }

            val controllerNode = rootNode.findNodeByPath(controllerInfo.fullPath) ?: controllerInfo.astNode
            val regPropIndex = controllerNode.properties.indexOfFirst { it.name == "reg" }
            val newRegProp = FdtProperty("reg", regBuffer.array())
            if (regPropIndex != -1) {
                controllerNode.properties[regPropIndex] = newRegProp
            } else {
                controllerNode.properties.add(newRegProp)
            }

            // Rewrite reg-names property with null terminators
            val regNamesStream = java.io.ByteArrayOutputStream()
            for (i in activeSlots.indices) {
                val strName = "performance-domain$i"
                regNamesStream.write(strName.toByteArray(Charsets.US_ASCII))
                regNamesStream.write(0) // null terminator
            }

            val regNamesPropIndex = controllerNode.properties.indexOfFirst { it.name == "reg-names" }
            val newRegNamesProp = FdtProperty("reg-names", regNamesStream.toByteArray())
            if (regNamesPropIndex != -1) {
                controllerNode.properties[regNamesPropIndex] = newRegNamesProp
            } else {
                controllerNode.properties.add(newRegNamesProp)
            }

            logs.add("Synchronized cpufreq performance-controller reg/reg-names for ${activeSlots.size} domains:")
            for ((cIdx, slotAddr) in assignedSlots) {
                logs.add("  - Cluster $cIdx -> domain$cIdx at CSRAM slot 0x${slotAddr.toString(16)}")
            }
        }

        val report = buildString {
            appendLine("Topology Plan Applied Successfully (${plan.clusters.map { it.cpuIndices.size }.joinToString("+")}):")
            logs.forEach { appendLine("- $it") }
            if (plan.warnings.isNotEmpty()) {
                appendLine("Warnings:")
                plan.warnings.forEach { appendLine("  * $it") }
            }
        }

        return Pair(true, report)
    }

    private fun findCpusNode(rootNode: FdtNode): FdtNode? {
        val direct = rootNode.findChild("cpus")
            ?: rootNode.children.firstOrNull { it.name.startsWith("cpus") || it.label == "cpus" }
        if (direct != null) return direct

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

    private fun findCpuhvfsNode(node: FdtNode): FdtNode? {
        if (node.name.startsWith("cpuhvfs") || node.findProperty("compatible")?.asStringList()?.any { it.contains("cpufreq-hybrid") } == true) {
            return node
        }
        for (child in node.children) {
            val found = findCpuhvfsNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun findNodeByPhandle(node: FdtNode, phandle: Long): FdtNode? {
        if (node.getPhandle() == phandle) return node
        for (child in node.children) {
            val found = findNodeByPhandle(child, phandle)
            if (found != null) return found
        }
        return null
    }

    private fun findParentNode(root: FdtNode, target: FdtNode): FdtNode? {
        if (root.children.contains(target)) return root
        for (child in root.children) {
            val found = findParentNode(child, target)
            if (found != null) return found
        }
        return null
    }

    private fun calculateMaxPhandle(node: FdtNode): Long {
        var max = 0L
        val p = node.getPhandle()
        if (p != null && p > max) max = p

        val linuxP = node.findProperty("linux,phandle")?.asSingleU32()
        if (linuxP != null && linuxP > max) max = linuxP

        for (child in node.children) {
            val childMax = calculateMaxPhandle(child)
            if (childMax > max) max = childMax
        }
        return max.coerceAtLeast(0x8000L)
    }

    private fun parseRegFromName(name: String): Long {
        val atIdx = name.indexOf('@')
        if (atIdx != -1) {
            val hex = name.substring(atIdx + 1)
            return hex.toLongOrNull(16) ?: 0L
        }
        return 0L
    }

    private fun parseCellsValue(u32s: List<Long>, startIdx: Int, cellCount: Int): Long {
        if (cellCount == 1) {
            return u32s.getOrElse(startIdx) { 0L }
        }
        if (cellCount == 2) {
            val high = u32s.getOrElse(startIdx) { 0L }
            val low = u32s.getOrElse(startIdx + 1) { 0L }
            return (high shl 32) or (low and 0xFFFFFFFFL)
        }
        return u32s.getOrElse(startIdx) { 0L }
    }

    private fun writeCellsValue(buffer: ByteBuffer, value: Long, cellCount: Int) {
        if (cellCount == 1) {
            buffer.putInt(value.toInt())
        } else if (cellCount >= 2) {
            val high = (value ushr 32).toInt()
            val low = value.toInt()
            buffer.putInt(high)
            buffer.putInt(low)
            for (i in 2 until cellCount) {
                buffer.putInt(0)
            }
        }
    }

    /**
     * Strictly verifies that AST differences between before and after are strictly isolated to:
     * 1. /cpus/cpu-map (all child nodes and properties)
     * 2. performance-domains (and generated phandle) in /cpus/cpu@*
     * 3. reg and reg-names in performance-controller node referenced by phandle
     *
     * Rejects any external or accidental modifications.
     */
    fun verifyTopologyIsolation(
        beforeAst: FdtNode,
        afterAst: FdtNode,
        controllerFullPath: String? = null
    ): TopologyIsolationResult {
        val unauthorizedPaths = mutableListOf<String>()
        val authorizedChanges = mutableListOf<String>()

        fun isPathAuthorized(path: String): Boolean {
            // 1. Inside /cpus/cpu-map
            if (path == "/cpus/cpu-map" || path.startsWith("/cpus/cpu-map/") || path.contains("/cpus/cpu-map")) {
                return true
            }
            // 2. performance-domains or phandle on a cpu node
            if (path.matches(Regex(""".*/cpu(@[^/]+)?/performance-domains"""))) {
                return true
            }
            if (path.matches(Regex(""".*/cpu(@[^/]+)?/phandle"""))) {
                return true
            }
            // 3. reg and reg-names in performance-controller
            if (controllerFullPath != null) {
                if (path == "$controllerFullPath/reg" || path == "$controllerFullPath/reg-names") {
                    return true
                }
            }
            if (path.contains("performance-controller") && (path.endsWith("/reg") || path.endsWith("/reg-names"))) {
                return true
            }
            return false
        }

        fun diffNodes(before: FdtNode, after: FdtNode) {
            val beforeProps = before.properties.associateBy { it.name }
            val afterProps = after.properties.associateBy { it.name }

            for ((name, bProp) in beforeProps) {
                val propPath = "${before.fullPath}/$name"
                val aProp = afterProps[name]
                if (aProp == null) {
                    if (isPathAuthorized(propPath)) {
                        authorizedChanges.add("Removed prop: $propPath")
                    } else {
                        unauthorizedPaths.add(propPath)
                    }
                } else if (!bProp.data.contentEquals(aProp.data)) {
                    if (isPathAuthorized(propPath)) {
                        authorizedChanges.add("Modified prop: $propPath")
                    } else {
                        unauthorizedPaths.add(propPath)
                    }
                }
            }

            for ((name, _) in afterProps) {
                if (!beforeProps.containsKey(name)) {
                    val propPath = "${after.fullPath}/$name"
                    if (isPathAuthorized(propPath)) {
                        authorizedChanges.add("Added prop: $propPath")
                    } else {
                        unauthorizedPaths.add(propPath)
                    }
                }
            }

            val beforeChildren = before.children.associateBy { it.name }
            val afterChildren = after.children.associateBy { it.name }

            for ((cName, bChild) in beforeChildren) {
                val aChild = afterChildren[cName]
                if (aChild == null) {
                    if (isPathAuthorized(bChild.fullPath)) {
                        authorizedChanges.add("Removed node: ${bChild.fullPath}")
                    } else {
                        unauthorizedPaths.add(bChild.fullPath)
                    }
                } else {
                    diffNodes(bChild, aChild)
                }
            }

            for ((cName, aChild) in afterChildren) {
                if (!beforeChildren.containsKey(cName)) {
                    if (isPathAuthorized(aChild.fullPath)) {
                        authorizedChanges.add("Added node: ${aChild.fullPath}")
                    } else {
                        unauthorizedPaths.add(aChild.fullPath)
                    }
                }
            }
        }

        diffNodes(beforeAst, afterAst)

        return if (unauthorizedPaths.isNotEmpty()) {
            TopologyIsolationResult(
                isValid = false,
                unauthorizedPaths = unauthorizedPaths,
                authorizedChanges = authorizedChanges,
                message = "Apply rejected: Changes outside CPU topology detected on path: ${unauthorizedPaths.joinToString(", ")}"
            )
        } else {
            TopologyIsolationResult(
                isValid = true,
                unauthorizedPaths = emptyList(),
                authorizedChanges = authorizedChanges,
                message = "Only topology changes detected"
            )
        }
    }
}

data class TopologyIsolationResult(
    val isValid: Boolean,
    val unauthorizedPaths: List<String>,
    val authorizedChanges: List<String>,
    val message: String
)
