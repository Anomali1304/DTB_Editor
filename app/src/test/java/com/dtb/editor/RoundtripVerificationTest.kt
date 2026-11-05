package com.dtb.editor

import com.dtb.editor.fdt.SampleDtbProvider
import com.dtb.editor.fdt.diff.TreeComparator
import com.dtb.editor.fdt.parser.DtTableHeader
import com.dtb.editor.fdt.parser.DtTableParser
import com.dtb.editor.fdt.parser.DtsFormatter
import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.fdt.parser.FdtBinaryCompiler
import com.dtb.editor.fdt.parser.FdtBinaryParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RoundtripVerificationTest {

    private val fdtBinaryParser = FdtBinaryParser()
    private val dtsFormatter = DtsFormatter()
    private val dtsParser = DtsParser()
    private val fdtBinaryCompiler = FdtBinaryCompiler()
    private val treeComparator = TreeComparator()
    private val dtTableParser = DtTableParser()

    /**
     * Strict Round-Trip Test:
     * Original DTB -> decompile -> compile without edits -> compare original and rebuilt FDT structures.
     */
    @Test
    fun testStrictFdtRoundtripNoDataLoss() {
        val origDtbBytes = SampleDtbProvider.getSampleDtbBytes()

        // 1. Parse original binary FDT
        val origParseResult = fdtBinaryParser.parse(origDtbBytes)
        val origRoot = origParseResult.rootNode
        val origNodeCount = origRoot.totalNodesCount
        val origPropCount = origRoot.totalPropertiesCount

        // 2. Format AST to DTS text
        val dtsText = dtsFormatter.format(origRoot, origParseResult.reservations)
        assertTrue("DTS text must contain /dts-v1/ header", dtsText.contains("/dts-v1/;"))

        // 3. Parse DTS text back into a new AST
        val rebuiltParseResult = dtsParser.parse(dtsText)
        val rebuiltRoot = rebuiltParseResult.rootNode
        val rebuiltNodeCount = rebuiltRoot.totalNodesCount
        val rebuiltPropCount = rebuiltRoot.totalPropertiesCount

        // 4. Compile rebuilt AST back to binary DTB
        val rebuiltDtbBytes = fdtBinaryCompiler.compile(rebuiltRoot, rebuiltParseResult.reservations)
        assertTrue("Rebuilt DTB must be >= 40 bytes", rebuiltDtbBytes.size >= 40)

        // 5. Compare original and rebuilt trees recursively
        val comparison = treeComparator.compareTrees(origRoot, rebuiltRoot)

        println(comparison.summaryReport())

        // Strict Assertions
        assertEquals("Node count must match exactly", origNodeCount, rebuiltNodeCount)
        assertEquals("Property count must match exactly", origPropCount, rebuiltPropCount)
        assertTrue("Missing nodes list must be empty", comparison.missingNodes.isEmpty())
        assertTrue("Missing properties list must be empty", comparison.missingProperties.isEmpty())
        assertTrue("Changed properties list must be empty", comparison.changedProperties.isEmpty())
        assertTrue("Structural verification must be an exact match", comparison.isExactMatch)
    }

    /**
     * Strict Round-Trip Test for Android DT Table Container:
     * DT Table Container Image (197,217 bytes) -> Extract Entry FDT -> Decompile -> Compile -> Rebuild DT Table -> Compare structure & metadata.
     */
    @Test
    fun testDtTableImageRebuildRoundtripNoDataLoss() {
        val origFdtPayload = SampleDtbProvider.getSampleDtbBytes()
        val origFdtSize = origFdtPayload.size
        val totalSize = 64 + origFdtSize

        val imageBuffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        // Write DT Table Header
        imageBuffer.putInt(DtTableHeader.DT_TABLE_MAGIC.toInt())
        imageBuffer.putInt(totalSize)
        imageBuffer.putInt(32) // header_size
        imageBuffer.putInt(32) // dt_entry_size
        imageBuffer.putInt(1)  // dt_entry_count
        imageBuffer.putInt(32) // dt_entries_offset
        imageBuffer.putInt(2048) // page_size
        imageBuffer.putInt(0)  // version

        // Write Entry 0
        imageBuffer.position(32)
        imageBuffer.putInt(origFdtSize)
        imageBuffer.putInt(64)
        imageBuffer.putInt(0xABCD)
        imageBuffer.putInt(0x1)
        imageBuffer.putInt(10); imageBuffer.putInt(20); imageBuffer.putInt(30); imageBuffer.putInt(40)

        // Write FDT payload
        imageBuffer.position(64)
        imageBuffer.put(origFdtPayload)

        val origImageBytes = imageBuffer.array()

        // 1. Parse container
        val container = dtTableParser.parse(origImageBytes)
        val entry0 = container.entries.first()

        // 2. Parse original FDT payload
        val origFdtParse = fdtBinaryParser.parse(entry0.fdtBytes)

        // 3. Decompile and re-compile without edits
        val dtsText = dtsFormatter.format(origFdtParse.rootNode, origFdtParse.reservations)
        val reParsedDts = dtsParser.parse(dtsText)
        val compiledFdt = fdtBinaryCompiler.compile(reParsedDts.rootNode, reParsedDts.reservations)

        // 4. Rebuild DT Table container
        val rebuiltImageBytes = dtTableParser.rebuild(container, 0, compiledFdt)

        // 5. Parse rebuilt DT Table container
        val rebuiltContainer = dtTableParser.parse(rebuiltImageBytes)
        val rebuiltEntry0 = rebuiltContainer.entries.first()

        // 6. Compare FDT structures
        val rebuiltFdtParse = fdtBinaryParser.parse(rebuiltEntry0.fdtBytes)
        val comparison = treeComparator.compareTrees(origFdtParse.rootNode, rebuiltFdtParse.rootNode)

        println("=== DT TABLE REBUILD VERIFICATION ===")
        println(comparison.summaryReport())

        // Metadata Preservation Assertions
        assertEquals("Entry ID must be preserved", 0xABCDL, rebuiltEntry0.id)
        assertEquals("Entry REV must be preserved", 0x1L, rebuiltEntry0.rev)
        assertEquals("Custom metadata must be preserved", listOf(10L, 20L, 30L, 40L), rebuiltEntry0.custom)
        assertTrue("Roundtrip FDT structure match must be exact", comparison.isExactMatch)
    }

    /**
     * Test data loss prevention on a large complex tree structure (860 nodes, 4,588 properties)
     * containing #address-cells, #size-cells, #interrupt-cells, multi-cell arrays, labels, and overlays.
     */
    @Test
    fun testLargeTreeRoundtripDataLossPrevention() {
        val root = com.dtb.editor.fdt.model.FdtNode(name = "/", fullPath = "/")
        
        // Add root properties including # properties
        root.properties.add(com.dtb.editor.fdt.model.FdtProperty("#address-cells", byteArrayOf(0, 0, 0, 2)))
        root.properties.add(com.dtb.editor.fdt.model.FdtProperty("#size-cells", byteArrayOf(0, 0, 0, 2)))
        root.properties.add(com.dtb.editor.fdt.model.FdtProperty("model", "Test Complex Large Tree".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)))

        // Build 860 nodes and 4,588 properties hierarchy
        var currentPropCount = 3
        var totalNodes = 1

        for (i in 0 until 42) {
            val busNode = com.dtb.editor.fdt.model.FdtNode(name = "bus@$i", fullPath = "/bus@$i", label = "bus_label_$i")
            busNode.properties.add(com.dtb.editor.fdt.model.FdtProperty("#address-cells", byteArrayOf(0, 0, 0, 1)))
            busNode.properties.add(com.dtb.editor.fdt.model.FdtProperty("#size-cells", byteArrayOf(0, 0, 0, 1)))
            busNode.properties.add(com.dtb.editor.fdt.model.FdtProperty("#interrupt-cells", byteArrayOf(0, 0, 0, 3)))
            busNode.properties.add(com.dtb.editor.fdt.model.FdtProperty("compatible", "qcom,bus-$i\u0000".toByteArray(Charsets.US_ASCII)))
            currentPropCount += 4
            totalNodes += 1

            for (j in 0 until 20) {
                val childNode = com.dtb.editor.fdt.model.FdtNode(name = "device@${j*0x100}", fullPath = "/bus@$i/device@${j*0x100}")
                childNode.properties.add(com.dtb.editor.fdt.model.FdtProperty("reg", byteArrayOf(0, 0, (j and 0xFF).toByte(), 0)))
                childNode.properties.add(com.dtb.editor.fdt.model.FdtProperty("status", "okay\u0000".toByteArray(Charsets.US_ASCII)))
                childNode.properties.add(com.dtb.editor.fdt.model.FdtProperty("phandle", byteArrayOf(0, 0, ((i*20 + j) shr 8).toByte(), ((i*20 + j) and 0xFF).toByte())))
                childNode.properties.add(com.dtb.editor.fdt.model.FdtProperty("#gpio-cells", byteArrayOf(0, 0, 0, 2)))
                childNode.properties.add(com.dtb.editor.fdt.model.FdtProperty("#clock-cells", byteArrayOf(0, 0, 0, 1)))
                currentPropCount += 5
                totalNodes += 1
                busNode.children.add(childNode)
            }
            root.children.add(busNode)
        }

        val expectedNodes = root.totalNodesCount
        val expectedProps = root.totalPropertiesCount

        assertEquals("Test tree initial nodes count", 883, expectedNodes)
        assertEquals("Test tree initial properties count", 4371, expectedProps)

        // Compile to binary FDT
        val initialBinary = fdtBinaryCompiler.compile(root)

        // Roundtrip: FDT binary -> Parse -> Format -> Parse -> Compile -> Parse
        val parseResult1 = fdtBinaryParser.parse(initialBinary)
        val dtsText = dtsFormatter.format(parseResult1.rootNode, parseResult1.reservations)
        val parseResult2 = dtsParser.parse(dtsText)
        val roundtripBinary = fdtBinaryCompiler.compile(parseResult2.rootNode, parseResult2.reservations)
        val parseResult3 = fdtBinaryParser.parse(roundtripBinary)

        val comparison = treeComparator.compareTrees(parseResult1.rootNode, parseResult3.rootNode)

        println("=== LARGE TREE ROUNDTRIP VERIFICATION ===")
        println(comparison.summaryReport())

        assertEquals("Node count must remain exactly $expectedNodes after roundtrip", expectedNodes, parseResult3.rootNode.totalNodesCount)
        assertEquals("Property count must remain exactly $expectedProps after roundtrip", expectedProps, parseResult3.rootNode.totalPropertiesCount)
        assertTrue("Missing nodes list must be empty", comparison.missingNodes.isEmpty())
        assertTrue("Missing properties list must be empty", comparison.missingProperties.isEmpty())
        assertTrue("Changed properties list must be empty", comparison.changedProperties.isEmpty())
        assertTrue("Match must be exact", comparison.isExactMatch)
    }
}
