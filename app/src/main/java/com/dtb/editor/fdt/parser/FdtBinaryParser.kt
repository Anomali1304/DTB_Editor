package com.dtb.editor.fdt.parser

import com.dtb.editor.fdt.model.FdtHeader
import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.model.FdtProperty
import com.dtb.editor.fdt.model.FdtReserveEntry
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FdtBinaryParser {

    companion object {
        const val FDT_BEGIN_NODE = 0x00000001
        const val FDT_END_NODE   = 0x00000002
        const val FDT_PROP       = 0x00000003
        const val FDT_NOP        = 0x00000004
        const val FDT_END        = 0x00000009
    }

    data class ParseResult(
        val header: FdtHeader,
        val reservations: List<FdtReserveEntry>,
        val rootNode: FdtNode,
        val warnings: List<String>
    )

    fun parse(bytes: ByteArray): ParseResult {
        require(bytes.size >= FdtHeader.HEADER_SIZE) {
            "File size (${bytes.size} bytes) is smaller than DTB header size (40 bytes)"
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val header = FdtHeader.parse(buffer)

        val warnings = mutableListOf<String>()

        if (header.totalSize > bytes.size) {
            warnings.add("Header totalsize (${header.totalSize}) exceeds actual file size (${bytes.size}). File may be truncated.")
        }

        // 1. Read memory reservation map
        val reservations = mutableListOf<FdtReserveEntry>()
        val rsvOffset = header.offMemRsvmap.toInt()
        if (rsvOffset in 0 until bytes.size) {
            buffer.position(rsvOffset)
            while (buffer.remaining() >= 16) {
                val addr = buffer.long
                val size = buffer.long
                if (addr == 0L && size == 0L) break
                reservations.add(FdtReserveEntry(addr, size))
            }
        }

        // 2. Read strings table
        val strOffset = header.offDtStrings.toInt()
        val strSize = header.sizeDtStrings.toInt().coerceAtMost(bytes.size - strOffset)
        val stringsBlock = if (strOffset in 0 until bytes.size && strSize > 0) {
            bytes.copyOfRange(strOffset, (strOffset + strSize).coerceAtMost(bytes.size))
        } else {
            ByteArray(0)
        }

        fun getStringAt(offset: Int): String {
            if (offset < 0 || offset >= stringsBlock.size) return "unknown_prop_$offset"
            var end = offset
            while (end < stringsBlock.size && stringsBlock[end] != 0.toByte()) {
                end++
            }
            return String(stringsBlock, offset, end - offset, Charsets.US_ASCII)
        }

        // 3. Read structure block
        val structOffset = header.offDtStruct.toInt()
        require(structOffset in 0 until bytes.size) {
            "Invalid struct offset: $structOffset"
        }

        buffer.position(structOffset)
        val nodeStack = mutableListOf<FdtNode>()
        var rootNode: FdtNode? = null

        while (buffer.remaining() >= 4) {
            val token = buffer.int
            when (token) {
                FDT_BEGIN_NODE -> {
                    // Node name null-terminated string, 4-byte aligned
                    val startPos = buffer.position()
                    var nullPos = startPos
                    while (nullPos < bytes.size && bytes[nullPos] != 0.toByte()) {
                        nullPos++
                    }
                    val nameBytes = bytes.copyOfRange(startPos, nullPos)
                    val rawName = if (nameBytes.isEmpty()) "/" else String(nameBytes, Charsets.US_ASCII)

                    // Advance buffer to 4-byte boundary past null terminator
                    val nameLength = nullPos - startPos + 1
                    val alignedLength = (nameLength + 3) and 3.inv()
                    buffer.position(startPos + alignedLength)

                    val parent = nodeStack.lastOrNull()
                    val fullPath = if (parent == null) {
                        "/"
                    } else if (parent.fullPath == "/") {
                        "/${rawName}"
                    } else {
                        "${parent.fullPath}/${rawName}"
                    }

                    val newNode = FdtNode(name = rawName, fullPath = fullPath)
                    if (rootNode == null) {
                        rootNode = newNode
                    } else {
                        parent?.children?.add(newNode)
                    }
                    nodeStack.add(newNode)
                }

                FDT_PROP -> {
                    val propLen = buffer.int
                    val nameOff = buffer.int
                    val propName = getStringAt(nameOff)

                    val propData = if (propLen > 0) {
                        val data = ByteArray(propLen)
                        buffer.get(data)
                        // Align position to 4 bytes
                        val alignedLen = (propLen + 3) and 3.inv()
                        val padding = alignedLen - propLen
                        if (padding > 0 && buffer.remaining() >= padding) {
                            buffer.position(buffer.position() + padding)
                        }
                        data
                    } else {
                        ByteArray(0)
                    }

                    val currentNode = nodeStack.lastOrNull()
                    if (currentNode != null) {
                        currentNode.properties.add(FdtProperty(propName, propData))
                    } else {
                        warnings.add("FDT_PROP found without an enclosing node: $propName")
                    }
                }

                FDT_END_NODE -> {
                    if (nodeStack.isNotEmpty()) {
                        nodeStack.removeAt(nodeStack.lastIndex)
                    }
                }

                FDT_NOP -> {
                    // No operation, skip
                }

                FDT_END -> {
                    break
                }

                else -> {
                    warnings.add("Unknown FDT token 0x${token.toString(16)} at offset ${buffer.position() - 4}")
                    break
                }
            }
        }

        val resolvedRoot = rootNode ?: FdtNode(name = "/", fullPath = "/")
        return ParseResult(
            header = header,
            reservations = reservations,
            rootNode = resolvedRoot,
            warnings = warnings
        )
    }
}
