package com.dtb.editor

import com.dtb.editor.fdt.cpu.TopologyEngine
import com.dtb.editor.fdt.diff.TreeComparator
import com.dtb.editor.fdt.model.FdtProperty
import com.dtb.editor.fdt.parser.DtsFormatter
import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.fdt.parser.FdtBinaryCompiler
import com.dtb.editor.fdt.parser.FdtBinaryParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DtsParserDigitPropertyTest {

    private val dtsParser = DtsParser()
    private val dtsFormatter = DtsFormatter()
    private val fdtCompiler = FdtBinaryCompiler()
    private val fdtParser = FdtBinaryParser()
    private val treeComparator = TreeComparator()
    private val engine = TopologyEngine()

    private val STOCK_MT6789_WITH_DISPLAYPORT = """/dts-v1/;

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

        displayport {
            compatible = "mediatek,dp-tx";
            1st_connection = "dfp_d";
            2nd_connection = "dfp_d";
        };
    };
};
"""

    @Test
    fun testPropertiesStartingWithDigits() {
        val dts = """/dts-v1/;
/ {
    displayport {
        1st_connection = "dfp_d";
        2nd_connection = "dfp_d";
    };
};
"""
        val result = dtsParser.parse(dts)
        assertTrue("Errors must be empty", result.errors.isEmpty())
        assertTrue("Warnings must be empty", result.warnings.isEmpty())

        val dpNode = result.rootNode.findNodeByPath("/displayport")
            ?: result.rootNode.children.firstOrNull { it.name == "displayport" }
        assertNotNull("displayport node must be parsed", dpNode)

        val prop1 = dpNode!!.findProperty("1st_connection")
        assertNotNull("1st_connection property must not be dropped", prop1)
        assertEquals("dfp_d", prop1!!.asStringList().firstOrNull())

        val prop2 = dpNode.findProperty("2nd_connection")
        assertNotNull("2nd_connection property must not be dropped", prop2)
        assertEquals("dfp_d", prop2!!.asStringList().firstOrNull())

        // Verify formatting preserves properties as-is without escaping
        val formatted = dtsFormatter.format(result.rootNode)
        assertTrue("Formatted DTS must contain 1st_connection", formatted.contains("1st_connection = \"dfp_d\";"))
        assertTrue("Formatted DTS must contain 2nd_connection", formatted.contains("2nd_connection = \"dfp_d\";"))

        // Re-parse and verify exact match
        val reparsed = dtsParser.parse(formatted)
        val reparsedDp = reparsed.rootNode.findNodeByPath("/displayport")
            ?: reparsed.rootNode.children.firstOrNull { it.name == "displayport" }
        assertNotNull(reparsedDp)
        assertEquals("dfp_d", reparsedDp!!.findProperty("1st_connection")?.asStringList()?.firstOrNull())
        assertEquals("dfp_d", reparsedDp.findProperty("2nd_connection")?.asStringList()?.firstOrNull())
    }

    @Test
    fun testNodeNamesStartingWithDigits() {
        val dts = """/dts-v1/;
/ {
    1st_phy@1000 {
        reg = <0x1000 0x100>;
    };
    2nd_controller {
        status = "okay";
    };
};
"""
        val result = dtsParser.parse(dts)
        assertTrue("Parse errors must be empty", result.errors.isEmpty())
        assertTrue("Warnings must not flag valid node names", result.warnings.isEmpty())

        val node1 = result.rootNode.children.firstOrNull { it.name.startsWith("1st_phy") }
        assertNotNull("1st_phy node must be parsed", node1)

        val node2 = result.rootNode.children.firstOrNull { it.name == "2nd_controller" }
        assertNotNull("2nd_controller node must be parsed", node2)

        val formatted = dtsFormatter.format(result.rootNode)
        assertTrue("Formatted must preserve 1st_phy@1000", formatted.contains("1st_phy@1000 {"))
        assertTrue("Formatted must preserve 2nd_controller", formatted.contains("2nd_controller {"))
    }

    @Test
    fun testBooleanPropertyStartingWithDigits() {
        val dts = """/dts-v1/;
/ {
    node {
        1st_flag;
        2nd_flag;
    };
};
"""
        val result = dtsParser.parse(dts)
        val node = result.rootNode.children.firstOrNull { it.name == "node" }
        assertNotNull(node)

        val flag1 = node!!.findProperty("1st_flag")
        assertNotNull("1st_flag boolean property must exist", flag1)
        assertEquals(0, flag1!!.data.size)

        val flag2 = node.findProperty("2nd_flag")
        assertNotNull("2nd_flag boolean property must exist", flag2)
        assertEquals(0, flag2!!.data.size)

        val formatted = dtsFormatter.format(result.rootNode)
        assertTrue(formatted.contains("1st_flag;"))
        assertTrue(formatted.contains("2nd_flag;"))
    }

    @Test
    fun testInvalidStatementProducesWarning() {
        val dts = """/dts-v1/;
/ {
    broken_node {
        !invalid#syntax% = 123;
    };
};
"""
        val result = dtsParser.parse(dts)
        assertTrue("Must produce at least one warning for invalid statement", result.warnings.isNotEmpty())
        assertTrue(
            "Warning must describe unrecognized statement or node",
            result.warnings.any { it.contains("broken_node") || it.contains("invalid") }
        )
    }

    @Test
    fun testStockMt6789RoundtripPreservesDigitProperties() {
        val parseResult = dtsParser.parse(STOCK_MT6789_WITH_DISPLAYPORT)
        val origRoot = parseResult.rootNode

        // Verify displayport properties in original AST
        val dp = origRoot.findNodeByPath("/soc/displayport")
            ?: origRoot.findChild("soc")?.findChild("displayport")
        assertNotNull("displayport node must exist in original AST", dp)
        assertNotNull("1st_connection must exist", dp!!.findProperty("1st_connection"))
        assertNotNull("2nd_connection must exist", dp.findProperty("2nd_connection"))

        // Roundtrip to DTS text
        val formattedDts = dtsFormatter.format(origRoot, parseResult.reservations)

        // Compile to DTB binary
        val dtbBytes = fdtCompiler.compile(origRoot, parseResult.reservations)
        assertTrue(dtbBytes.size > 100)

        // Parse back from DTB binary
        val reparsedResult = fdtParser.parse(dtbBytes)
        val reparsedRoot = reparsedResult.rootNode

        // Verify structure comparison
        val comparison = treeComparator.compareTrees(origRoot, reparsedRoot)
        println(comparison.summaryReport())

        assertTrue("Missing properties must be empty", comparison.missingProperties.isEmpty())
        assertTrue("Changed properties must be empty", comparison.changedProperties.isEmpty())
        assertTrue("Missing nodes must be empty", comparison.missingNodes.isEmpty())
        assertTrue("Structural match must be exact", comparison.isExactMatch)

        val roundtripDp = reparsedRoot.findNodeByPath("/soc/displayport")
            ?: reparsedRoot.findChild("soc")?.findChild("displayport")
        assertNotNull("displayport node must exist in roundtrip", roundtripDp)
        assertEquals("dfp_d", roundtripDp!!.findProperty("1st_connection")?.asStringList()?.firstOrNull())
        assertEquals("dfp_d", roundtripDp.findProperty("2nd_connection")?.asStringList()?.firstOrNull())
    }

    @Test
    fun testPreset422StockTopologyTransformationAndIsolation() {
        val stockAst = dtsParser.parse(STOCK_MT6789_WITH_DISPLAYPORT).rootNode
        val cores = engine.extractCpuCores(stockAst)
        assertEquals(8, cores.size)

        val controllerInfo = engine.discoverPerformanceController(stockAst, cores)
        assertNotNull(controllerInfo)
        assertEquals(listOf(0x11bc10L, 0x11bd30L, 0x11be50L), controllerInfo!!.availableSlots)

        // Apply preset [4, 2, 2]
        val modifiedAst = dtsParser.parse(STOCK_MT6789_WITH_DISPLAYPORT).rootNode
        val modifiedCores = engine.extractCpuCores(modifiedAst)
        val modifiedControllerInfo = engine.discoverPerformanceController(modifiedAst, modifiedCores)
        assertNotNull(modifiedControllerInfo)

        val plan = engine.createPlanFromSizes(listOf(4, 2, 2), 8, modifiedControllerInfo!!.availableSlots.size)
            .copy(syncCpufreq = true)
        assertTrue("Plan must be valid", plan.isValid)

        val slots = engine.assignDomainSlots(plan.clusters, modifiedCores, modifiedControllerInfo)
        assertEquals(3, slots.size)
        // Verify slot mapping: cluster0=0x11bc10, cluster1=0x11be50, cluster2=0x11bd30
        assertEquals(0x11bc10L, slots[0])
        assertEquals(0x11be50L, slots[1])
        assertEquals(0x11bd30L, slots[2])

        val (success, report) = engine.applyPlanToAst(modifiedAst, plan, modifiedCores, modifiedControllerInfo, slots)
        assertTrue("Apply plan must succeed: $report", success)

        // Verify Tree Isolation
        val isolation = engine.verifyTopologyIsolation(stockAst, modifiedAst, controllerInfo.fullPath)
        assertTrue(
            "Topology changes must be strictly isolated. Unauthorized: ${isolation.unauthorizedPaths}",
            isolation.isValid
        )
        assertTrue("Unauthorized paths must be empty", isolation.unauthorizedPaths.isEmpty())
        assertEquals("Only topology changes detected", isolation.message)

        // Verify /soc/displayport is 100% intact
        val dpBefore = stockAst.findNodeByPath("/soc/displayport")
            ?: stockAst.findChild("soc")?.findChild("displayport")
        val dpAfter = modifiedAst.findNodeByPath("/soc/displayport")
            ?: modifiedAst.findChild("soc")?.findChild("displayport")
        assertNotNull(dpBefore)
        assertNotNull(dpAfter)
        assertEquals(dpBefore!!.properties.size, dpAfter!!.properties.size)
        assertEquals("dfp_d", dpAfter.findProperty("1st_connection")?.asStringList()?.firstOrNull())
        assertEquals("dfp_d", dpAfter.findProperty("2nd_connection")?.asStringList()?.firstOrNull())

        // Verify clusters in /cpus/cpu-map
        val cpus = modifiedAst.findChild("cpus")!!
        val cpuMap = cpus.findChild("cpu-map")!!
        assertEquals(3, cpuMap.children.size)

        val c0 = cpuMap.findChild("cluster0")!!
        assertEquals(4, c0.children.size)
        val c1 = cpuMap.findChild("cluster1")!!
        assertEquals(2, c1.children.size)
        val c2 = cpuMap.findChild("cluster2")!!
        assertEquals(2, c2.children.size)

        // Verify performance-domains on all 8 CPUs: [0, 0, 0, 0, 1, 1, 2, 2]
        val expectedDomains = listOf(0, 0, 0, 0, 1, 1, 2, 2)
        val cpuNodes = listOf("cpu@0", "cpu@100", "cpu@200", "cpu@300", "cpu@400", "cpu@500", "cpu@600", "cpu@700")
            .map { cpus.findChild(it)!! }

        for (i in 0 until 8) {
            val perfProp = cpuNodes[i].findProperty("performance-domains")
            assertNotNull("cpu $i must have performance-domains", perfProp)
            val buf = ByteBuffer.wrap(perfProp!!.data).order(ByteOrder.BIG_ENDIAN)
            val ctrlPhandle = buf.int
            val domainIdx = buf.int
            assertEquals(controllerInfo.phandle.toInt(), ctrlPhandle)
            assertEquals("CPU $i domain index", expectedDomains[i], domainIdx)
        }

        // Verify performance-controller reg and reg-names
        val ctrlNode = modifiedAst.findNodeByPath(controllerInfo.fullPath)
            ?: modifiedAst.findChild("soc")?.findChild("performance-controller@11bc00")!!
        val regProp = ctrlNode.findProperty("reg")
        assertNotNull(regProp)
        val regNamesProp = ctrlNode.findProperty("reg-names")
        assertNotNull(regNamesProp)

        assertEquals(
            listOf("performance-domain0", "performance-domain1", "performance-domain2"),
            regNamesProp!!.asStringList()
        )
    }

    @Test
    fun testIsolationRejectsUnauthorizedExternalMutations() {
        val stockAst = dtsParser.parse(STOCK_MT6789_WITH_DISPLAYPORT).rootNode
        val cores = engine.extractCpuCores(stockAst)
        val controllerInfo = engine.discoverPerformanceController(stockAst, cores)!!

        val tamperedAst = dtsParser.parse(STOCK_MT6789_WITH_DISPLAYPORT).rootNode
        // Deliberately tamper with displayport node
        val dp = tamperedAst.findChild("soc")?.findChild("displayport")!!
        dp.properties.removeIf { it.name == "1st_connection" }

        val isolation = engine.verifyTopologyIsolation(stockAst, tamperedAst, controllerInfo.fullPath)
        assertFalse("Must reject when external property is removed", isolation.isValid)
        assertTrue(
            "Must report unauthorized path for displayport",
            isolation.unauthorizedPaths.any { it.contains("displayport") && it.contains("1st_connection") }
        )
    }
}
