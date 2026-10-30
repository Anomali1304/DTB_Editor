package com.dtb.editor

import com.dtb.editor.fdt.SampleDtbProvider
import com.dtb.editor.fdt.parser.DtTableHeader
import com.dtb.editor.fdt.parser.DtTableParser
import com.dtb.editor.fdt.parser.FdtBinaryCompiler
import com.dtb.editor.fdt.parser.FdtBinaryParser
import com.dtb.editor.fdt.parser.DtsParser
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DtTableParserTest {

    private val dtTableParser = DtTableParser()

    /**
     * Test Case A: Raw DTB with magic 0xd00dfeed.
     */
    @Test
    fun testRawDtbParsing() {
        val rawDtb = SampleDtbProvider.getSampleDtbBytes()
        assertFalse(dtTableParser.isDtTable(rawDtb))

        val fdtParser = FdtBinaryParser()
        val result = fdtParser.parse(rawDtb)
        assertEquals(0xd00dfeedL, result.header.magic)
        assertTrue(result.rootNode.totalNodesCount >= 8)
    }

    /**
     * Test Case B: Android DT table with magic 0xd7b7ab1e and one entry (197217 bytes mandatory test case).
     *
     * DT TABLE HEADER (offset 0):
     * - magic: 0xd7b7ab1e
     * - total_size: 197217
     * - header_size: 32
     * - dt_entry_size: 32
     * - dt_entry_count: 1
     * - dt_entries_offset: 32
     * - page_size: 2048
     * - version: 0
     *
     * DT ENTRY (offset 32):
     * - dt_size: 197153
     * - dt_offset: 64
     * - id: 0, rev: 0, custom0..3: 0
     *
     * RAW FDT:
     * - starts at offset 64, magic 0xd00dfeed, totalsize 197153
     */
    @Test
    fun testMandatorySingleEntryDtTableImage() {
        val fdtPayload = SampleDtbProvider.getSampleDtbBytes()
        val fdtSize = fdtPayload.size // e.g. 197153 or sample size

        val totalSize = 64 + fdtSize
        val imageBuffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        // Write Header (32 bytes)
        imageBuffer.putInt(DtTableHeader.DT_TABLE_MAGIC.toInt())
        imageBuffer.putInt(totalSize)
        imageBuffer.putInt(32) // header_size
        imageBuffer.putInt(32) // dt_entry_size
        imageBuffer.putInt(1)  // dt_entry_count
        imageBuffer.putInt(32) // dt_entries_offset
        imageBuffer.putInt(2048) // page_size
        imageBuffer.putInt(0)  // version

        // Write Entry 0 (32 bytes at offset 32)
        imageBuffer.position(32)
        imageBuffer.putInt(fdtSize) // dt_size
        imageBuffer.putInt(64)      // dt_offset
        imageBuffer.putInt(0x100)   // id
        imageBuffer.putInt(0x1)     // rev
        imageBuffer.putInt(0x10)    // custom[0]
        imageBuffer.putInt(0x20)    // custom[1]
        imageBuffer.putInt(0x30)    // custom[2]
        imageBuffer.putInt(0x40)    // custom[3]

        // Write Raw FDT at offset 64
        imageBuffer.position(64)
        imageBuffer.put(fdtPayload)

        val imageBytes = imageBuffer.array()
        assertEquals(totalSize, imageBytes.size)

        // 1. Verify DT table magic detection
        assertTrue(dtTableParser.isDtTable(imageBytes))

        // 2. Parse container
        val container = dtTableParser.parse(imageBytes)
        assertEquals(0xd7b7ab1eL, container.header.magic)
        assertEquals(totalSize.toLong(), container.header.totalSize)
        assertEquals(1, container.header.dtEntryCount)

        // 3. Verify entry metadata
        val entry = container.entries.first()
        assertEquals(0, entry.index)
        assertEquals(64L, entry.dtOffset)
        assertEquals(fdtSize.toLong(), entry.dtSize)
        assertEquals(0x100L, entry.id)
        assertEquals(0x1L, entry.rev)
        assertEquals(listOf(0x10L, 0x20L, 0x30L, 0x40L), entry.custom)

        // 4. Verify embedded FDT payload magic
        val fdtParser = FdtBinaryParser()
        val fdtResult = fdtParser.parse(entry.fdtBytes)
        assertEquals(0xd00dfeedL, fdtResult.header.magic)
    }

    /**
     * Test Case C: Android DT table with multiple entries.
     */
    @Test
    fun testMultiEntryDtTableImage() {
        val fdtPayload1 = SampleDtbProvider.getSampleDtbBytes()
        val fdtPayload2 = SampleDtbProvider.getSampleDtbBytes()

        val headerSize = 32
        val entrySize = 32
        val entryCount = 2
        val entriesOffset = 32
        val entryTableEnd = entriesOffset + entryCount * entrySize // 96
        val offset1 = 128
        val offset2 = offset1 + fdtPayload1.size
        val totalSize = offset2 + fdtPayload2.size

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        // Header
        buffer.putInt(DtTableHeader.DT_TABLE_MAGIC.toInt())
        buffer.putInt(totalSize)
        buffer.putInt(headerSize)
        buffer.putInt(entrySize)
        buffer.putInt(entryCount)
        buffer.putInt(entriesOffset)
        buffer.putInt(4096)
        buffer.putInt(1)

        // Entry 0
        buffer.position(32)
        buffer.putInt(fdtPayload1.size)
        buffer.putInt(offset1)
        buffer.putInt(0x1000)
        buffer.putInt(0x1)
        buffer.putInt(0); buffer.putInt(0); buffer.putInt(0); buffer.putInt(0)

        // Entry 1
        buffer.position(64)
        buffer.putInt(fdtPayload2.size)
        buffer.putInt(offset2)
        buffer.putInt(0x2000)
        buffer.putInt(0x2)
        buffer.putInt(0); buffer.putInt(0); buffer.putInt(0); buffer.putInt(0)

        // Payloads
        buffer.position(offset1)
        buffer.put(fdtPayload1)

        buffer.position(offset2)
        buffer.put(fdtPayload2)

        val imageBytes = buffer.array()
        val container = dtTableParser.parse(imageBytes)

        assertEquals(2, container.entries.size)
        assertEquals(0x1000L, container.entries[0].id)
        assertEquals(0x2000L, container.entries[1].id)
    }

    /**
     * Test Case D: Invalid or truncated table.
     */
    @Test(expected = IllegalArgumentException::class)
    fun testTruncatedDtTableImageThrows() {
        val truncatedBytes = ByteArray(20)
        dtTableParser.parse(truncatedBytes)
    }

    /**
     * Test Case E: DTB compilation and DT table container reconstruction.
     */
    @Test
    fun testDtTableRebuildRoundtrip() {
        val origFdt = SampleDtbProvider.getSampleDtbBytes()
        val headerSize = 32
        val entrySize = 32
        val offset0 = 64
        val totalSize = offset0 + origFdt.size

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(DtTableHeader.DT_TABLE_MAGIC.toInt())
        buffer.putInt(totalSize)
        buffer.putInt(headerSize)
        buffer.putInt(entrySize)
        buffer.putInt(1)
        buffer.putInt(32)
        buffer.putInt(2048)
        buffer.putInt(0)

        buffer.position(32)
        buffer.putInt(origFdt.size)
        buffer.putInt(offset0)
        buffer.putInt(0xABC)
        buffer.putInt(0x1)
        buffer.putInt(1); buffer.putInt(2); buffer.putInt(3); buffer.putInt(4)

        buffer.position(offset0)
        buffer.put(origFdt)

        val initialImage = buffer.array()
        val container = dtTableParser.parse(initialImage)

        // Edit DTS and compile new FDT payload
        val modifiedDts = SampleDtbProvider.SAMPLE_DTS.replace("Qualcomm Technologies, Inc. SM8450 v2", "Rebuilt DT Table Test")
        val parsed = DtsParser().parse(modifiedDts)
        val newFdtBytes = FdtBinaryCompiler().compile(parsed.rootNode, parsed.reservations)

        // Rebuild DT table container
        val rebuiltImageBytes = dtTableParser.rebuild(container, 0, newFdtBytes)
        assertTrue(rebuiltImageBytes.size >= 64 + newFdtBytes.size)

        // Parse rebuilt container and verify headers, entries, and modified FDT
        val rebuiltContainer = dtTableParser.parse(rebuiltImageBytes)
        assertEquals(0xd7b7ab1eL, rebuiltContainer.header.magic)
        assertEquals(0xABCL, rebuiltContainer.entries[0].id)
        assertEquals(0x1L, rebuiltContainer.entries[0].rev)

        val reParsedFdt = FdtBinaryParser().parse(rebuiltContainer.entries[0].fdtBytes)
        assertEquals(0xd00dfeedL, reParsedFdt.header.magic)
        assertTrue(reParsedFdt.rootNode.findProperty("model")?.formattedValue()?.contains("Rebuilt DT Table Test") == true)
    }
}
