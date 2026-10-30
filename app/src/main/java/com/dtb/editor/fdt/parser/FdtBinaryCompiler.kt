package com.dtb.editor.fdt.parser

import com.dtb.editor.fdt.model.FdtHeader
import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.model.FdtReserveEntry
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FdtBinaryCompiler {

    fun compile(rootNode: FdtNode, reservations: List<FdtReserveEntry> = emptyList()): ByteArray {
        val stringsStream = ByteArrayOutputStream()
        val stringOffsets = mutableMapOf<String, Int>()

        fun getStringOffset(name: String): Int {
            return stringOffsets.getOrPut(name) {
                val offset = stringsStream.size()
                val bytes = name.toByteArray(Charsets.US_ASCII)
                stringsStream.write(bytes)
                stringsStream.write(0) // null terminator
                offset
            }
        }

        // Pre-collect property names to build strings block
        fun collectPropertyNames(node: FdtNode) {
            for (p in node.properties) {
                getStringOffset(p.name)
            }
            for (child in node.children) {
                collectPropertyNames(child)
            }
        }
        collectPropertyNames(rootNode)

        // Generate dt_struct block
        val structStream = ByteArrayOutputStream()

        fun writeInt(out: ByteArrayOutputStream, value: Int) {
            out.write((value ushr 24) and 0xFF)
            out.write((value ushr 16) and 0xFF)
            out.write((value ushr 8) and 0xFF)
            out.write(value and 0xFF)
        }

        fun writeNode(node: FdtNode) {
            writeInt(structStream, FdtBinaryParser.FDT_BEGIN_NODE)

            // Node name (root node is empty string null-terminated)
            val nameBytes = if (node.name == "/") {
                ByteArray(0)
            } else {
                node.name.toByteArray(Charsets.US_ASCII)
            }
            structStream.write(nameBytes)
            structStream.write(0) // null terminator

            // 4-byte align name
            val nameLen = nameBytes.size + 1
            val pad = ((nameLen + 3) and 3.inv()) - nameLen
            for (i in 0 until pad) {
                structStream.write(0)
            }

            // Write properties
            for (prop in node.properties) {
                writeInt(structStream, FdtBinaryParser.FDT_PROP)
                writeInt(structStream, prop.data.size)
                writeInt(structStream, getStringOffset(prop.name))
                if (prop.data.isNotEmpty()) {
                    structStream.write(prop.data)
                    val propPad = ((prop.data.size + 3) and 3.inv()) - prop.data.size
                    for (i in 0 until propPad) {
                        structStream.write(0)
                    }
                }
            }

            // Write children
            for (child in node.children) {
                writeNode(child)
            }

            writeInt(structStream, FdtBinaryParser.FDT_END_NODE)
        }

        writeNode(rootNode)
        writeInt(structStream, FdtBinaryParser.FDT_END)

        val structBytes = structStream.toByteArray()
        val stringsBytes = stringsStream.toByteArray()

        // Generate memory reservation block: 16 bytes per entry + 16 bytes terminating (0, 0)
        val rsvStream = ByteArrayOutputStream()
        for (rsv in reservations) {
            val buf = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            buf.putLong(rsv.address)
            buf.putLong(rsv.size)
            rsvStream.write(buf.array())
        }
        val termBuf = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
        termBuf.putLong(0L)
        termBuf.putLong(0L)
        rsvStream.write(termBuf.array())
        val rsvBytes = rsvStream.toByteArray()

        // Calculate layout with 8-byte alignments
        val headerSize = FdtHeader.HEADER_SIZE
        val offMemRsvmap = headerSize
        val offDtStruct = (offMemRsvmap + rsvBytes.size + 7) and 7.inv()
        val offDtStrings = offDtStruct + structBytes.size
        val totalSize = offDtStrings + stringsBytes.size

        // Build header
        val headerBuf = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN)
        headerBuf.putInt(FdtHeader.FDT_MAGIC.toInt())
        headerBuf.putInt(totalSize)
        headerBuf.putInt(offDtStruct)
        headerBuf.putInt(offDtStrings)
        headerBuf.putInt(offMemRsvmap)
        headerBuf.putInt(17) // version
        headerBuf.putInt(16) // last_comp_version
        headerBuf.putInt(0)  // boot_cpuid_phys
        headerBuf.putInt(stringsBytes.size)
        headerBuf.putInt(structBytes.size)

        // Assemble final binary
        val finalBuffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        finalBuffer.put(headerBuf.array())

        // Mem rsv map
        finalBuffer.position(offMemRsvmap)
        finalBuffer.put(rsvBytes)

        // Struct block
        finalBuffer.position(offDtStruct)
        finalBuffer.put(structBytes)

        // Strings block
        finalBuffer.position(offDtStrings)
        finalBuffer.put(stringsBytes)

        return finalBuffer.array()
    }
}
