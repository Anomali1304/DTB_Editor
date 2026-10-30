package com.dtb.editor

import com.dtb.editor.fdt.SampleDtbProvider
import com.dtb.editor.fdt.cpu.CpuTopologyManager
import com.dtb.editor.fdt.diff.DiffType
import com.dtb.editor.fdt.diff.DtsDiffEngine
import com.dtb.editor.fdt.parser.DtsFormatter
import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.fdt.parser.FdtBinaryCompiler
import com.dtb.editor.fdt.parser.FdtBinaryParser
import org.junit.Assert.*
import org.junit.Test

class DtbEngineUnitTest {

    @Test
    fun testSampleDtbDecompileAndCompileRoundtrip() {
        val sampleBytes = SampleDtbProvider.getSampleDtbBytes()
        assertNotNull(sampleBytes)
        assertTrue(sampleBytes.size > 100)

        // 1. Parse binary DTB
        val parser = FdtBinaryParser()
        val parseResult = parser.parse(sampleBytes)
        assertEquals(0xd00dfeedL, parseResult.header.magic)
        assertTrue(parseResult.rootNode.totalNodesCount >= 8)

        // 2. Format to DTS
        val formatter = DtsFormatter()
        val dts = formatter.format(parseResult.rootNode, parseResult.reservations)
        assertTrue(dts.contains("/dts-v1/;"))
        assertTrue(dts.contains("cpus {"))

        // 3. Parse DTS back
        val dtsParser = DtsParser()
        val parsedBack = dtsParser.parse(dts)
        assertTrue(parsedBack.rootNode.totalNodesCount >= 8)

        // 4. Compile back to binary DTB
        val compiler = FdtBinaryCompiler()
        val recompiledBytes = compiler.compile(parsedBack.rootNode, parsedBack.reservations)
        assertTrue(recompiledBytes.size > 100)

        // Verify magic of recompiled binary
        val reParse = parser.parse(recompiledBytes)
        assertEquals(0xd00dfeedL, reParse.header.magic)
    }

    @Test
    fun testCpuTopologyExtractionAndPreset() {
        val parsed = DtsParser().parse(SampleDtbProvider.SAMPLE_DTS)
        val manager = CpuTopologyManager()
        val top = manager.extractTopology(parsed.rootNode)

        assertEquals(8, top.totalCores)
        assertTrue(top.hasCpuMap)
        assertEquals(2, top.clusters.size) // initial 4+4

        // Apply 4+2+2 preset
        val (success, msg) = manager.applyPreset(parsed.rootNode, listOf(4, 2, 2))
        assertTrue(success)

        val updatedTop = manager.extractTopology(parsed.rootNode)
        assertEquals(3, updatedTop.clusters.size)
        assertEquals(4, updatedTop.clusters[0].cores.size)
        assertEquals(2, updatedTop.clusters[1].cores.size)
        assertEquals(2, updatedTop.clusters[2].cores.size)
    }

    @Test
    fun testDiffEngine() {
        val parsed1 = DtsParser().parse(SampleDtbProvider.SAMPLE_DTS)
        val modifiedDts = SampleDtbProvider.SAMPLE_DTS.replace("Qualcomm Technologies, Inc. SM8450 v2", "Modified SM8450 Test")
        val parsed2 = DtsParser().parse(modifiedDts)

        val diffEngine = DtsDiffEngine()
        val diff = diffEngine.compareNodes(parsed1.rootNode, parsed2.rootNode)

        assertTrue(diff.modifiedCount >= 1)
        assertTrue(diff.items.any { it.path.contains("model") && it.type == DiffType.MODIFIED })
    }
}
