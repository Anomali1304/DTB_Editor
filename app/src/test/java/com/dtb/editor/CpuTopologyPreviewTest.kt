package com.dtb.editor

import com.dtb.editor.fdt.cpu.ClusterConfig
import com.dtb.editor.fdt.cpu.CpuTopologyManager
import com.dtb.editor.fdt.cpu.TopologyEngine
import com.dtb.editor.fdt.parser.DtsFormatter
import com.dtb.editor.fdt.parser.DtsParser
import org.junit.Assert.*
import org.junit.Test

class CpuTopologyPreviewTest {

    private val engine = TopologyEngine()
    private val topologyManager = CpuTopologyManager(engine)
    private val dtsParser = DtsParser()
    private val dtsFormatter = DtsFormatter()

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
    fun testStockMT6789Preset233PreviewExactSlots() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        assertEquals(8, cores.size)

        val controller = engine.discoverPerformanceController(root, cores)
        assertNotNull(controller)
        assertEquals(listOf(0x11bc10L, 0x11bd30L, 0x11be50L), controller!!.availableSlots)

        // 2+3+3 split:
        // Cluster 0: cpu@0, cpu@100 (indices 0, 1)
        // Cluster 1: cpu@200, cpu@300, cpu@400 (indices 2, 3, 4)
        // Cluster 2: cpu@500, cpu@600, cpu@700 (indices 5, 6, 7)
        val configs233 = listOf(
            ClusterConfig(clusterIndex = 0, cpuIndices = listOf(0, 1)),
            ClusterConfig(clusterIndex = 1, cpuIndices = listOf(2, 3, 4)),
            ClusterConfig(clusterIndex = 2, cpuIndices = listOf(5, 6, 7))
        )

        val preview = engine.previewPlan(configs233, cores, controller, syncCpufreq = true)
        assertTrue("Preview 2+3+3 must be valid", preview.isValid)
        assertTrue("Errors must be empty", preview.errors.isEmpty())

        assertEquals(3, preview.assignedSlots.size)
        assertEquals("Cluster 0 must get 0x11bc10", 0x11bc10L, preview.assignedSlots[0])
        assertEquals("Cluster 1 must get 0x11be50", 0x11be50L, preview.assignedSlots[1])
        assertEquals("Cluster 2 must get 0x11bd30", 0x11bd30L, preview.assignedSlots[2])
    }

    @Test
    fun testStockMT6789ApplyExportDecompile233() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val configs233 = listOf(
            ClusterConfig(clusterIndex = 0, cpuIndices = listOf(0, 1)),
            ClusterConfig(clusterIndex = 1, cpuIndices = listOf(2, 3, 4)),
            ClusterConfig(clusterIndex = 2, cpuIndices = listOf(5, 6, 7))
        )

        val (success, msg) = topologyManager.applyCustomPlan(root, configs233, syncCpufreq = true)
        assertTrue("Apply must succeed: $msg", success)

        // Format to DTS and re-parse (simulating export and decompile/re-open)
        val formattedDts = dtsFormatter.format(root, emptyList())
        val reparsedRoot = dtsParser.parse(formattedDts).rootNode

        // Verify cpu-map 2/3/3
        val cpusNode = reparsedRoot.findChild("cpus")!!
        val cpuMapNode = cpusNode.findChild("cpu-map")!!
        assertEquals(3, cpuMapNode.children.size)
        assertEquals(2, cpuMapNode.children[0].children.size)
        assertEquals(3, cpuMapNode.children[1].children.size)
        assertEquals(3, cpuMapNode.children[2].children.size)

        // Verify performance-domains per CPU: cluster0(slot 0x11bc10->0), cluster1(slot 0x11be50->2), cluster2(slot 0x11bd30->1)
        val updatedCores = engine.extractCpuCores(reparsedRoot)
        val domains = updatedCores.map { it.originalPerfDomainIndex }
        assertEquals(listOf(0, 0, 2, 2, 2, 1, 1, 1), domains)

        // Verify reg controller = 0x11bc10 (domain 0), 0x11bd30 (domain 1), 0x11be50 (domain 2)
        val perfController = reparsedRoot.findNodeByPath("/soc/performance-controller@11bc00")!!
        val regVal = perfController.findProperty("reg")!!.asU32List()
        assertEquals(12, regVal.size)
        assertEquals(0x11bc10L, regVal[1]) // domain 0
        assertEquals(0x11bd30L, regVal[5]) // domain 1
        assertEquals(0x11be50L, regVal[9]) // domain 2
    }

    @Test
    fun testFourClustersPreviewShowsSlotHanya3Error() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        val configs4 = listOf(
            ClusterConfig(clusterIndex = 0, cpuIndices = listOf(0, 1)),
            ClusterConfig(clusterIndex = 1, cpuIndices = listOf(2, 3)),
            ClusterConfig(clusterIndex = 2, cpuIndices = listOf(4, 5)),
            ClusterConfig(clusterIndex = 3, cpuIndices = listOf(6, 7))
        )

        val preview = engine.previewPlan(configs4, cores, controller, syncCpufreq = true)
        assertFalse("4 clusters on 3-slot hardware must be invalid", preview.isValid)
        assertTrue("Error must mention slot limit", preview.errors.any { it.contains("maximum 3 slots") })
    }

    @Test
    fun testDuplicateManualSlotSelection() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        // Both clusters manually pick 0x11bc10
        val configsDuplicate = listOf(
            ClusterConfig(clusterIndex = 0, cpuIndices = listOf(0, 1, 2, 3), selectedDomainSlot = 0x11bc10L),
            ClusterConfig(clusterIndex = 1, cpuIndices = listOf(4, 5, 6, 7), selectedDomainSlot = 0x11bc10L)
        )

        val preview = engine.previewPlan(configsDuplicate, cores, controller, syncCpufreq = true)
        assertFalse("Duplicate slot selection must be invalid", preview.isValid)
        assertTrue("Duplicate slot must be recorded", preview.duplicateSlots.contains(0x11bc10L))
        assertTrue("Error message must explain duplicate slot", preview.errors.any { it.contains("selected by more than one cluster") })
    }

    @Test
    fun testEmptyClusterRejected() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        val configsEmpty = listOf(
            ClusterConfig(clusterIndex = 0, cpuIndices = emptyList()),
            ClusterConfig(clusterIndex = 1, cpuIndices = listOf(0, 1, 2, 3, 4, 5, 6, 7))
        )

        val preview = engine.previewPlan(configsEmpty, cores, controller, syncCpufreq = true)
        assertFalse("Empty cluster plan must be invalid", preview.isValid)
        assertTrue("Error must mention empty cluster", preview.errors.any { it.contains("cannot be empty") })
    }

    @Test
    fun testSyncCpufreqDisabledPreview() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        val configs = listOf(
            ClusterConfig(clusterIndex = 0, cpuIndices = listOf(0, 1, 2, 3)),
            ClusterConfig(clusterIndex = 1, cpuIndices = listOf(4, 5, 6, 7))
        )

        val preview = engine.previewPlan(configs, cores, controller, syncCpufreq = false)
        assertTrue("Preview with syncCpufreq=false must be valid", preview.isValid)
        assertTrue("Assigned slots must be empty when sync is off", preview.assignedSlots.isEmpty())
        assertTrue("Summary must note cpu-map only mode", preview.summary.contains("cpu-map only mode"))
    }

    @Test
    fun testFourClustersSharedPolicyPreviewAndApply() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        val configs4Shared = listOf(
            ClusterConfig(clusterIndex = 0, cpuIndices = listOf(0, 1), selectedDomainSlot = 0x11bc10L),
            ClusterConfig(clusterIndex = 1, cpuIndices = listOf(2, 3), selectedDomainSlot = 0x11be50L),
            ClusterConfig(clusterIndex = 2, cpuIndices = listOf(4, 5), selectedDomainSlot = 0x11bd30L),
            ClusterConfig(clusterIndex = 3, cpuIndices = listOf(6, 7), selectedDomainSlot = 0x11bd30L)
        )

        val preview = engine.previewPlan(configs4Shared, cores, controller, syncCpufreq = true)
        assertTrue("4 clusters with 3 unique shared slots must be valid", preview.isValid)
        assertEquals(4, preview.assignedSlots.size)
        assertEquals(3, preview.assignedSlots.values.toSet().size)
        assertTrue("Summary must mention 3 cpufreq domains", preview.summary.contains("3 cpufreq domains"))

        val (success, report) = engine.applyPlanToAst(root, preview.plan, cores, controller, preview.assignedSlots)
        assertTrue("Applying 4 clusters with shared policy must succeed", success)
        assertTrue(report.contains("Reconstructed /cpus/cpu-map with 4 clusters"))
    }

    @Test
    fun testMonoClusterBigDomainPreviewAndApply() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        val monoBigConfig = listOf(
            ClusterConfig(clusterIndex = 0, cpuIndices = (0 until 8).toList(), selectedDomainSlot = 0x11bd30L)
        )

        val preview = engine.previewPlan(monoBigConfig, cores, controller, syncCpufreq = true)
        assertTrue("Mono-cluster plan must be valid", preview.isValid)
        assertEquals(1, preview.assignedSlots.size)
        assertEquals(0x11bd30L, preview.assignedSlots[0])
        assertTrue("Summary must mention 1 cpufreq domain", preview.summary.contains("1 cpufreq domain"))
        assertTrue("Warnings must include A55 running in Big domain", preview.warnings.any { it.contains("0x11bd30") })

        val (success, report) = engine.applyPlanToAst(root, preview.plan, cores, controller, preview.assignedSlots)
        assertTrue("Applying mono-cluster big domain must succeed", success)
        assertTrue(report.contains("Reconstructed /cpus/cpu-map with 1 clusters"))
        assertTrue(report.contains("Synchronized cpufreq performance-controller reg/reg-names"))
    }

    @Test
    fun testNewRequestedPresets() {
        val root = dtsParser.parse(STOCK_MT6789_DTS).rootNode
        val cores = engine.extractCpuCores(root)
        val controller = engine.discoverPerformanceController(root, cores)!!

        val requestedPresets = listOf(
            listOf(2, 3, 3),
            listOf(1, 3, 4),
            listOf(1, 5, 2),
            listOf(4, 3)
        )

        for (preset in requestedPresets) {
            val configs = mutableListOf<ClusterConfig>()
            var currentCpu = 0
            for ((cIdx, size) in preset.withIndex()) {
                val cpus = (currentCpu until (currentCpu + size)).filter { it < 8 }
                configs.add(ClusterConfig(cIdx, cpus))
                currentCpu += size
            }

            val finalConfigs = when {
                preset == listOf(4, 3) -> listOf(
                    ClusterConfig(0, listOf(0, 1, 2, 3)),
                    ClusterConfig(1, listOf(4, 5, 6)),
                    ClusterConfig(2, listOf(7))
                )
                preset.size > 3 -> configs.mapIndexed { idx, config ->
                    when (idx) {
                        0 -> config.copy(selectedDomainSlot = 0x11bc10L)
                        1 -> config.copy(selectedDomainSlot = 0x11be50L)
                        else -> config.copy(selectedDomainSlot = 0x11bd30L)
                    }
                }
                else -> configs
            }

            val preview = engine.previewPlan(finalConfigs, cores, controller, syncCpufreq = true)
            assertTrue("Preset $preset preview must be valid", preview.isValid)
            
            val (success, report) = engine.applyPlanToAst(root, preview.plan, cores, controller, preview.assignedSlots)
            assertTrue("Preset $preset apply failed: $report", success)
        }
    }
}
