package com.dtb.editor.fdt.parser

import com.dtb.editor.fdt.model.FdtHeader
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DtbEntryItem(
    val index: Int,
    val model: String?,
    val compatible: String?,
    val sizeBytes: Int,
    val fdtBytes: ByteArray
) {
    val displayLabel: String
        get() = buildString {
            append("Entri $index: ")
            if (!model.isNullOrBlank()) {
                append(model)
            } else if (!compatible.isNullOrBlank()) {
                append(compatible)
            } else {
                append("FDT")
            }
            append(" (${sizeBytes / 1024} KB)")
        }
}

sealed class DtbPayloadStructure {
    data class SingleFdt(val fdtBytes: ByteArray) : DtbPayloadStructure()
    data class ConcatenatedFdts(val fdtList: List<ByteArray>) : DtbPayloadStructure()
    data class AndroidDtTable(val container: DtTableContainer) : DtbPayloadStructure()
}

class DtbPayloadHelper {

    private val dtTableParser = DtTableParser()
    private val fdtParser = FdtBinaryParser()

    fun parseStructure(bytes: ByteArray): Pair<DtbPayloadStructure, List<DtbEntryItem>> {
        require(bytes.size >= 4) { "Data DTB terlalu kecil" }

        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        val magicBE = (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        val magicLE = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0

        if (magicBE == DtTableHeader.DT_TABLE_MAGIC.toInt() || magicLE == DtTableHeader.DT_TABLE_MAGIC.toInt()) {
            val container = dtTableParser.parse(bytes)
            val items = container.entries.map { entry ->
                val meta = extractMetadata(entry.fdtBytes)
                DtbEntryItem(
                    index = entry.index,
                    model = meta.first,
                    compatible = meta.second,
                    sizeBytes = entry.fdtBytes.size,
                    fdtBytes = entry.fdtBytes
                )
            }
            return Pair(DtbPayloadStructure.AndroidDtTable(container), items)
        }

        if (magicBE == FdtHeader.FDT_MAGIC.toInt()) {
            val fdtBlobs = splitConcatenatedFdts(bytes)
            val items = fdtBlobs.mapIndexed { idx, blob ->
                val meta = extractMetadata(blob)
                DtbEntryItem(
                    index = idx,
                    model = meta.first,
                    compatible = meta.second,
                    sizeBytes = blob.size,
                    fdtBytes = blob
                )
            }
            return if (fdtBlobs.size == 1) {
                Pair(DtbPayloadStructure.SingleFdt(fdtBlobs[0]), items)
            } else {
                Pair(DtbPayloadStructure.ConcatenatedFdts(fdtBlobs), items)
            }
        }

        throw IllegalArgumentException("Format DTB tidak dikenali (magic: 0x${Integer.toHexString(magicBE)})")
    }

    fun rebuild(structure: DtbPayloadStructure, updatedIndex: Int, newFdtBytes: ByteArray): ByteArray {
        return when (structure) {
            is DtbPayloadStructure.SingleFdt -> {
                newFdtBytes
            }
            is DtbPayloadStructure.ConcatenatedFdts -> {
                val out = ByteArrayOutputStream()
                for (i in structure.fdtList.indices) {
                    val blob = if (i == updatedIndex) newFdtBytes else structure.fdtList[i]
                    out.write(blob)
                    while (out.size() % 8 != 0) {
                        out.write(0)
                    }
                }
                out.toByteArray()
            }
            is DtbPayloadStructure.AndroidDtTable -> {
                dtTableParser.rebuild(structure.container, updatedIndex, newFdtBytes)
            }
        }
    }

    private fun splitConcatenatedFdts(bytes: ByteArray): List<ByteArray> {
        val list = mutableListOf<ByteArray>()
        var offset = 0
        while (offset + 8 <= bytes.size) {
            val buf = ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN)
            val magic = buf.int.toLong() and 0xFFFFFFFFL
            val totalsize = buf.int.toLong() and 0xFFFFFFFFL

            if (magic == FdtHeader.FDT_MAGIC) {
                require(totalsize >= 40 && offset + totalsize <= bytes.size) {
                    "Ukuran totalsize FDT tidak valid ($totalsize) pada offset $offset"
                }
                val slice = bytes.copyOfRange(offset, (offset + totalsize).toInt())
                list.add(slice)
                offset += totalsize.toInt()
                while (offset < bytes.size && bytes[offset] == 0.toByte()) {
                    offset++
                }
            } else {
                if (bytes.slice(offset until bytes.size).all { it == 0.toByte() }) {
                    break
                }
                throw IllegalArgumentException("Magic FDT tidak valid pada offset $offset: 0x${magic.toString(16)}")
            }
        }
        require(list.isNotEmpty()) { "Tidak ada blob FDT yang valid ditemukan" }
        return list
    }

    private fun extractMetadata(fdtBytes: ByteArray): Pair<String?, String?> {
        return try {
            val ast = fdtParser.parse(fdtBytes).rootNode
            val model = ast.findProperty("model")?.asStringList()?.firstOrNull()
            val compatible = ast.findProperty("compatible")?.asStringList()?.joinToString(", ")
            Pair(model, compatible)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }
}
