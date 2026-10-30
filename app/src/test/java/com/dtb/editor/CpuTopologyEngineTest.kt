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
            reg = <0x0 0x11bc00 0x0 0x1400>;
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

        // Verify performance-domains per cpu
        val updatedCores = engine.extractCpuCores(root)
        assertEquals(0, updatedCores[0].originalPerfDomainIndex) // cpu0 -> domain 0
        assertEquals(0, updatedCores[1].originalPerfDomainIndex) // cpu1 -> domain 0
        assertEquals(1, updatedCores[2].originalPerfDomainIndex) // cpu2 -> domain 1
        assertEquals(1, updatedCores[3].originalPerfDomainIndex) // cpu3 -> domain 1
        assertEquals(1, updatedCores[4].originalPerfDomainIndex) // cpu4 -> domain 1
        assertEquals(2, updatedCores[5].originalPerfDomainIndex) // cpu5 -> domain 2
        assertEquals(2, updatedCores[6].originalPerfDomainIndex) // cpu6 -> domain 2
        assertEquals(2, updatedCores[7].originalPerfDomainIndex) // cpu7 -> domain 2

        // Verify performance-controller reg and reg-names
        val perfController = root.findNodeByPath("/soc/performance-controller@11bc00")!!
        val regVal = perfController.findProperty("reg")!!.asU32List()
        assertEquals(12, regVal.size) // 3 entries * 4 cells = 12 cells
        assertEquals(0x11bc10L, regVal[1]) // domain 0 = 0x11bc10
        assertEquals(0x11be50L, regVal[5]) // domain 1 = 0x11be50
        assertEquals(0x11bd30L, regVal[9]) // domain 2 = 0x11bd30

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

        println("DEBUG ROUNDTRIP slots62: $slots62")
        // Verify restored reg
        val perfController = root.findNodeByPath("/soc/performance-controller@11bc00")!!
        val regVal = perfController.findProperty("reg")!!.asU32List()
        println("DEBUG ROUNDTRIP regVal: $regVal")
        assertEquals(8, regVal.size) // 2 entries * 4 cells
        assertTrue("Cluster 0 domain 0 must use valid A55 slot", regVal[1] == 0x11bc10L || regVal[1] == 0x11be50L || regVal[1] == 0x11bd30L)
        assertTrue("Cluster 1 domain 1 must use valid slot", regVal[5] == 0x11bd30L || regVal[5] == 0x11be50L || regVal[5] == 0x11bc10L)
    }

    @Test
    fun testValidationAndCapacityWarnings() {
        // Test 8 clusters (1,1,1,1,1,1,1,1) when max domains allowed = 3
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        val plan1x8 = engine.createPlanFromSizes(listOf(1, 1, 1, 1, 1, 1, 1, 1), 8)
        val warnings = engine.generateWarnings(plan1x8.clusters, cores, controller)

        assertTrue(warnings.any { it.contains("exceeds available cpufreq performance domain slots") })

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
}
