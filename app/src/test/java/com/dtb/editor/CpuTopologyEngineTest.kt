package com.dtb.editor

import com.dtb.editor.fdt.cpu.ClusterConfig
import com.dtb.editor.fdt.cpu.TopologyEngine
import com.dtb.editor.fdt.parser.DtsFormatter
import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.fdt.parser.FdtBinaryCompiler
import com.dtb.editor.fdt.parser.FdtBinaryParser
import org.junit.Assert.*
import org.junit.Test

class CpuTopologyEngineTest {

    private val engine = TopologyEngine()
    private val dtsParser = DtsParser()
    private val dtsFormatter = DtsFormatter()
    private val fdtCompiler = FdtBinaryCompiler()
    private val fdtParser = FdtBinaryParser()

    private val STOCK_MT6789_DTS = """/dts-v1/;

/ {
    #address-cells = <0x2>;
    #size-cells = <0x2>;
    compatible = "mediatek,mt6789";

    cpus {
        #address-cells = <0x1>;
        #size-cells = <0x0>;

        cpu-map {
            cluster0 {
                core0 { cpu = <0x10>; };
                core1 { cpu = <0x11>; };
                core2 { cpu = <0x12>; };
                core3 { cpu = <0x13>; };
                core4 { cpu = <0x14>; };
                core5 { cpu = <0x15>; };
            };
            cluster1 {
                core0 { cpu = <0x16>; };
                core1 { cpu = <0x17>; };
            };
        };

        cpu@0 {
            compatible = "arm,cortex-a55";
            device_type = "cpu";
            reg = <0x0>;
            phandle = <0x10>;
            capacity-dmips-mhz = <0x1a6>;
            performance-domains = <0x2 0x0>;
        };
        cpu@100 {
            compatible = "arm,cortex-a55";
            device_type = "cpu";
            reg = <0x100>;
            phandle = <0x11>;
            capacity-dmips-mhz = <0x1a6>;
            performance-domains = <0x2 0x0>;
        };
        cpu@200 {
            compatible = "arm,cortex-a55";
            device_type = "cpu";
            reg = <0x200>;
            phandle = <0x12>;
            capacity-dmips-mhz = <0x1a6>;
            performance-domains = <0x2 0x0>;
        };
        cpu@300 {
            compatible = "arm,cortex-a55";
            device_type = "cpu";
            reg = <0x300>;
            phandle = <0x13>;
            capacity-dmips-mhz = <0x1a6>;
            performance-domains = <0x2 0x0>;
        };
        cpu@400 {
            compatible = "arm,cortex-a55";
            device_type = "cpu";
            reg = <0x400>;
            phandle = <0x14>;
            capacity-dmips-mhz = <0x1a6>;
            performance-domains = <0x2 0x0>;
        };
        cpu@500 {
            compatible = "arm,cortex-a55";
            device_type = "cpu";
            reg = <0x500>;
            phandle = <0x15>;
            capacity-dmips-mhz = <0x1a6>;
            performance-domains = <0x2 0x0>;
        };
        cpu@600 {
            compatible = "arm,cortex-a76";
            device_type = "cpu";
            reg = <0x600>;
            phandle = <0x16>;
            capacity-dmips-mhz = <0x400>;
            performance-domains = <0x2 0x1>;
        };
        cpu@700 {
            compatible = "arm,cortex-a76";
            device_type = "cpu";
            reg = <0x700>;
            phandle = <0x17>;
            capacity-dmips-mhz = <0x400>;
            performance-domains = <0x2 0x1>;
        };
    };

    soc {
        #address-cells = <0x2>;
        #size-cells = <0x2>;

        performance-controller@11bc00 {
            compatible = "mediatek,cpufreq-hw";
            reg = <0x0 0x11bc10 0x0 0x120 0x0 0x11bd30 0x0 0x120>;
            reg-names = "performance-domain0", "performance-domain1";
            phandle = <0x2>;
        };

        cpuhvfs@114400 {
            compatible = "mediatek,cpufreq-hybrid";
            reg = <0x0 0x114400 0x0 0xc00 0x0 0x11bc00 0x0 0x1400 0x0 0x112800 0x0 0x1800 0x0 0x114f40 0x0 0xc0>;
            reg-names = "USRAM", "CSRAM", "ESRAM";
            tbl-off = <0x0 0x100 0x200>;
            pll-con = <0x0 0x10 0x20>;
            clk-div = <0x0 0x1 0x2>;
        };
    };
};
"""

    @Test
    fun testStockTo233Transformation() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        assertEquals(8, cores.size)

        val controller = engine.discoverPerformanceController(root, cores)
        assertNotNull(controller)
        assertEquals(2, controller!!.existingSlots.size)
        assertEquals(0x11bc10L, controller.existingSlots[0].first)
        assertEquals(0x11bd30L, controller.existingSlots[1].first)
        assertEquals(3, controller.maxDomainsAllowed)
        assertEquals(0x11bc00L, controller.csramBase)
        assertEquals(0x1400L, controller.csramSize)
        assertEquals(listOf(0x11bc10L, 0x11bd30L, 0x11be50L), controller.availableSlots)

        // Apply 2+3+3
        val plan233 = engine.createPlanFromSizes(listOf(2, 3, 3), 8)
        assertTrue(plan233.isValid)

        val slots233 = engine.assignDomainSlots(plan233.clusters, cores, controller)
        assertEquals(3, slots233.size)
        assertEquals("Cluster 0 must use CSRAM slot 0x11bc10", 0x11bc10L, slots233[0])
        assertEquals("Cluster 1 must use CSRAM slot 0x11be50", 0x11be50L, slots233[1])
        assertEquals("Cluster 2 must use A76 CSRAM slot 0x11bd30", 0x11bd30L, slots233[2])

        val (success, log) = engine.applyPlanToAst(root, plan233, cores, controller, slots233)
        assertTrue(success)

        // Verify cpu-map updated
        val cpus = root.findChild("cpus")!!
        val cpuMap = cpus.findChild("cpu-map")!!
        assertEquals(3, cpuMap.children.size) // cluster0, cluster1, cluster2
        assertEquals(2, cpuMap.children[0].children.size) // core0, core1
        assertEquals(3, cpuMap.children[1].children.size) // core0, core1, core2
        assertEquals(3, cpuMap.children[2].children.size) // core0, core1, core2

        // Verify performance-domains per cpu: [0,0,1,1,1,2,2,2]
        val updatedCores = engine.extractCpuCores(root)
        val domainPerCpu = updatedCores.map { it.originalPerfDomainIndex }
        assertEquals(listOf(0, 0, 1, 1, 1, 2, 2, 2), domainPerCpu)

        // Verify performance-controller reg and reg-names
        val perfController = root.findNodeByPath("/soc/performance-controller@11bc00")!!
        val regVal = perfController.findProperty("reg")!!.asU32List()
        assertEquals(12, regVal.size) // 3 entries * 4 cells = 12 cells
        assertEquals(0L, regVal[0])
        assertEquals(0x11bc10L, regVal[1]) // domain 0 = 0x11bc10
        assertEquals(0L, regVal[2])
        assertEquals(0x120L, regVal[3])

        assertEquals(0L, regVal[4])
        assertEquals(0x11be50L, regVal[5]) // domain 1 = 0x11be50
        assertEquals(0L, regVal[6])
        assertEquals(0x120L, regVal[7])

        assertEquals(0L, regVal[8])
        assertEquals(0x11bd30L, regVal[9]) // domain 2 = 0x11bd30
        assertEquals(0L, regVal[10])
        assertEquals(0x120L, regVal[11])

        val regNamesList = perfController.findProperty("reg-names")!!.asStringList()
        assertEquals(3, regNamesList.size)
        assertEquals("performance-domain0", regNamesList[0])
        assertEquals("performance-domain1", regNamesList[1])
        assertEquals("performance-domain2", regNamesList[2])
    }

    @Test
    fun testRoundtrip233ToStock62() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        // Step 1: Transform to 2+3+3
        val plan233 = engine.createPlanFromSizes(listOf(2, 3, 3), 8)
        val slots233 = engine.assignDomainSlots(plan233.clusters, cores, controller)
        engine.applyPlanToAst(root, plan233, cores, controller, slots233)

        // Step 2: Transform 2+3+3 back to 6+2 (Stock)
        val cores233 = engine.extractCpuCores(root)
        val controller233 = engine.discoverPerformanceController(root, cores233)!!
        val plan62 = engine.createPlanFromSizes(listOf(6, 2), 8)
        val slots62 = engine.assignDomainSlots(plan62.clusters, cores233, controller233)

        val (success, log) = engine.applyPlanToAst(root, plan62, cores233, controller233, slots62)
        assertTrue(success)

        // Verify exact restored reg and slots
        val perfController = root.findNodeByPath("/soc/performance-controller@11bc00")!!
        val regVal = perfController.findProperty("reg")!!.asU32List()
        assertEquals(8, regVal.size) // 2 entries * 4 cells
        assertEquals(0x11bc10L, regVal[1])
        assertEquals(0x11bd30L, regVal[5])
        assertEquals(0x11bc10L, slots62[0])
        assertEquals(0x11bd30L, slots62[1])
    }

    @Test
    fun testValidationAndCapacityWarnings() {
        // Test 8 clusters (1,1,1,1,1,1,1,1) when max domains allowed = 3
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        val plan1x8 = engine.createPlanFromSizes(listOf(1, 1, 1, 1, 1, 1, 1, 1), 8, controller.availableSlots.size)
        assertFalse(plan1x8.isValid)
        assertTrue(plan1x8.errors.any { it.contains("exceeds available cpufreq performance domain slots") })

        // Test invalid CPU assignment (out of bounds or duplicate CPU)
        val invalidConfig = listOf(
            ClusterConfig(0, listOf(0, 1, 2)),
            ClusterConfig(1, listOf(2, 3, 4, 5, 6, 7)) // Duplicate CPU 2
        )
        val invalidPlan = engine.validatePlan(invalidConfig, 8)
        assertFalse(invalidPlan.isValid)
        assertTrue(invalidPlan.errors.any { it.contains("assigned to multiple clusters") })

        // Test empty cluster
        val emptyConfig = listOf(
            ClusterConfig(0, emptyList()),
            ClusterConfig(1, listOf(0, 1, 2, 3, 4, 5, 6, 7))
        )
        val emptyPlan = engine.validatePlan(emptyConfig, 8)
        assertFalse(emptyPlan.isValid)
        assertTrue(emptyPlan.errors.any { it.contains("cannot be empty") })
    }

    @Test
    fun testNonSequentialCPUAssignments() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)

        // Non-sequential: cluster 0 has CPUs 0,2,4,6; cluster 1 has CPUs 1,3,5,7
        val nonSeqConfig = listOf(
            ClusterConfig(0, listOf(0, 2, 4, 6)),
            ClusterConfig(1, listOf(1, 3, 5, 7))
        )
        val plan = engine.validatePlan(nonSeqConfig, 8)
        assertTrue(plan.isValid)

        val controller = engine.discoverPerformanceController(root, cores)!!
        val slots = engine.assignDomainSlots(plan.clusters, cores, controller)
        val (success, log) = engine.applyPlanToAst(root, plan, cores, controller, slots)
        assertTrue(success)

        val cpus = root.findChild("cpus")!!
        val cpuMap = cpus.findChild("cpu-map")!!
        assertEquals(2, cpuMap.children.size)
        assertEquals(4, cpuMap.children[0].children.size)
        assertEquals(4, cpuMap.children[1].children.size)
    }

    @Test
    fun testPreset44And422And8() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        // [4, 2, 2] -> slot c0=0x11bc10, c1=0x11be50, c2=0x11bd30
        val plan422 = engine.createPlanFromSizes(listOf(4, 2, 2), 8)
        val slots422 = engine.assignDomainSlots(plan422.clusters, cores, controller)
        assertEquals(0x11bc10L, slots422[0])
        assertEquals(0x11be50L, slots422[1])
        assertEquals(0x11bd30L, slots422[2])
        val (succ422, _) = engine.applyPlanToAst(root, plan422, cores, controller, slots422)
        assertTrue(succ422)

        // [4, 4] -> 0x11bc10 and 0x11bd30
        val plan44 = engine.createPlanFromSizes(listOf(4, 4), 8)
        val slots44 = engine.assignDomainSlots(plan44.clusters, cores, controller)
        assertEquals(0x11bc10L, slots44[0])
        assertEquals(0x11bd30L, slots44[1])
        val (succ44, _) = engine.applyPlanToAst(root, plan44, cores, controller, slots44)
        assertTrue(succ44)

        // Single cluster [8]
        val plan8 = engine.createPlanFromSizes(listOf(8), 8)
        val slots8 = engine.assignDomainSlots(plan8.clusters, cores, controller)
        assertEquals(1, slots8.size)
        val (succ8, _) = engine.applyPlanToAst(root, plan8, cores, controller, slots8)
        assertTrue(succ8)
    }

    @Test
    fun testFourClustersReturnsError() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!
        assertEquals(3, controller.availableSlots.size)

        // 4 clusters exceeds 3 available slots
        val plan4 = engine.createPlanFromSizes(listOf(2, 2, 2, 2), 8, controller.availableSlots.size)
        assertFalse("4 clusters plan must be invalid", plan4.isValid)
        assertTrue(plan4.errors.any { it.contains("exceeds available cpufreq performance domain slots") })

        val (success, err) = engine.applyPlanToAst(root, plan4, cores, controller, emptyMap())
        assertFalse("applyPlanToAst must reject plan exceeding available slots", success)
        assertTrue(err.contains("invalid topology plan") || err.contains("exceeds available"))
    }

    @Test
    fun testNoDuplicateSlotsForAnyPreset() {
        val presets = listOf(
            listOf(6, 2),
            listOf(2, 3, 3),
            listOf(4, 2, 2),
            listOf(4, 4),
            listOf(8)
        )

        for (preset in presets) {
            val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
            val cores = engine.extractCpuCores(root)
            val controller = engine.discoverPerformanceController(root, cores)!!
            val plan = engine.createPlanFromSizes(preset, 8, controller.availableSlots.size)
            assertTrue("Plan for preset $preset must be valid", plan.isValid)

            val slots = engine.assignDomainSlots(plan.clusters, cores, controller)
            assertEquals("Assigned slots must not contain duplicates for preset $preset", slots.values.toSet().size, slots.values.size)

            val (success, log) = engine.applyPlanToAst(root, plan, cores, controller, slots)
            assertTrue("applyPlanToAst for preset $preset must succeed: $log", success)

            val perfController = root.findNodeByPath("/soc/performance-controller@11bc00")!!
            val regU32s = perfController.findProperty("reg")!!.asU32List()
            val regSlotAddrs = mutableListOf<Long>()
            var i = 0
            while (i + 4 <= regU32s.size) {
                regSlotAddrs.add(regU32s[i + 1])
                i += 4
            }
            assertEquals("Reg slots must not contain duplicates for preset $preset", regSlotAddrs.toSet().size, regSlotAddrs.size)
        }
    }

    @Test
    fun testCsramNoMatchingEntryFallback() {
        val dtsNoMatch = STOCK_MT6789_DTS.replace(
            "reg = <0x0 0x114400 0x0 0xc00 0x0 0x11bc00 0x0 0x1400 0x0 0x112800 0x0 0x1800 0x0 0x114f40 0x0 0xc0>;",
            "reg = <0x0 0x114400 0x0 0xc00 0x0 0x112800 0x0 0x1800>;"
        )
        val root = dtsParser.parse(dtsNoMatch).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        assertEquals(2, controller.availableSlots.size)
        assertEquals(listOf(0x11bc10L, 0x11bd30L), controller.availableSlots)
        assertNull(controller.csramBase)
        assertTrue(controller.diagnostics.any { it.contains("Limiting available slots to existing slots") })
    }

    @Test
    fun testExtractTopologyCpuIndexMapping() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val manager = com.dtb.editor.fdt.cpu.CpuTopologyManager(engine)
        val state = manager.extractTopology(root)

        assertEquals(2, state.clusters.size)
        assertEquals(6, state.clusters[0].cores.size)
        assertEquals(2, state.clusters[1].cores.size)

        // Cluster 0 cores must have coreIndex 0..5
        val c0Indices = state.clusters[0].cores.map { it.coreIndex }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), c0Indices)

        // Cluster 1 cores must have coreIndex 6..7
        val c1Indices = state.clusters[1].cores.map { it.coreIndex }
        assertEquals(listOf(6, 7), c1Indices)
    }

    @Test
    fun testRegNamesNulTerminators() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!
        val plan233 = engine.createPlanFromSizes(listOf(2, 3, 3), 8)
        val slots233 = engine.assignDomainSlots(plan233.clusters, cores, controller)
        engine.applyPlanToAst(root, plan233, cores, controller, slots233)

        val perfController = root.findNodeByPath("/soc/performance-controller@11bc00")!!
        val regNamesProp = perfController.findProperty("reg-names")!!
        val data = regNamesProp.data
        assertTrue(data.contains(0.toByte()))
    }
}
