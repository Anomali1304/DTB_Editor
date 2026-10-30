package com.dtb.editor.fdt.parser

import com.dtb.editor.fdt.model.FdtHeader
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class DtTableHeader(
    val magic: Long = DT_TABLE_MAGIC,
    val totalSize: Long,
    val headerSize: Int,
    val dtEntrySize: Int,
    val dtEntryCount: Int,
    val dtEntriesOffset: Long,
    val pageSize: Long,
    val version: Long
) {
    companion object {
        const val DT_TABLE_MAGIC = 0xd7b7ab1eL
        const val MIN_HEADER_SIZE = 32
        const val MIN_ENTRY_SIZE = 32
    }
}

data class DtTableEntry(
    val index: Int,
    val dtSize: Long,
    val dtOffset: Long,
    val id: Long,
    val rev: Long,
    val custom: List<Long>, // 4 uint32 custom fields
    val fdtBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DtTableEntry
        if (index != other.index) return false
        if (dtSize != other.dtSize) return false
        if (dtOffset != other.dtOffset) return false
        if (id != other.id) return false
        if (rev != other.rev) return false
        if (custom != other.custom) return false
        if (!fdtBytes.contentEquals(other.fdtBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + dtSize.hashCode()
        result = 31 * result + dtOffset.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + rev.hashCode()
        result = 31 * result + custom.hashCode()
        result = 31 * result + fdtBytes.contentHashCode()
        return result
    }
}

data class DtTableContainer(
    val header: DtTableHeader,
    val entries: List<DtTableEntry>,
    val rawBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DtTableContainer
        if (header != other.header) return false
        if (entries != other.entries) return false
        if (!rawBytes.contentEquals(other.rawBytes)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + entries.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

class DtTableParser {

    fun isDtTable(bytes: ByteArray): Boolean {
        if (bytes.size < DtTableHeader.MIN_HEADER_SIZE) return false
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magic = buffer.int.toLong() and 0xFFFFFFFFL
        return magic == DtTableHeader.DT_TABLE_MAGIC
    }

    fun parse(bytes: ByteArray): DtTableContainer {
        require(bytes.size >= DtTableHeader.MIN_HEADER_SIZE) {
            "Buffer size (${bytes.size} B) is smaller than DT table header size (32 B)"
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magic = buffer.int.toLong() and 0xFFFFFFFFL
        require(magic == DtTableHeader.DT_TABLE_MAGIC) {
            "Invalid DT table magic: 0x${magic.toString(16)}. Expected 0xd7b7ab1e."
        }

        val totalSize = buffer.int.toLong() and 0xFFFFFFFFL
        val headerSize = buffer.int
        val dtEntrySize = buffer.int
        val dtEntryCount = buffer.int
        val dtEntriesOffset = buffer.int.toLong() and 0xFFFFFFFFL
        val pageSize = buffer.int.toLong() and 0xFFFFFFFFL
        val version = buffer.int.toLong() and 0xFFFFFFFFL

        require(headerSize >= DtTableHeader.MIN_HEADER_SIZE) {
            "Invalid header_size: $headerSize (expected >= 32)"
        }
        require(dtEntrySize >= DtTableHeader.MIN_ENTRY_SIZE) {
            "Invalid dt_entry_size: $dtEntrySize (expected >= 32)"
        }
        require(dtEntryCount in 1..1024) {
            "Invalid dt_entry_count: $dtEntryCount"
        }
        require(dtEntriesOffset >= headerSize && dtEntriesOffset <= bytes.size) {
            "Invalid dt_entries_offset: $dtEntriesOffset"
        }

        val header = DtTableHeader(
            magic = magic,
            totalSize = totalSize,
            headerSize = headerSize,
            dtEntrySize = dtEntrySize,
            dtEntryCount = dtEntryCount,
            dtEntriesOffset = dtEntriesOffset,
            pageSize = pageSize,
            version = version
        )

        val entries = mutableListOf<DtTableEntry>()

        for (i in 0 until dtEntryCount) {
            val entryOffset = (dtEntriesOffset + i * dtEntrySize).toInt()
            require(entryOffset + dtEntrySize <= bytes.size) {
                "DT entry $i offset ($entryOffset) exceeds buffer size (${bytes.size})"
            }

            buffer.position(entryOffset)
            val dtSize = buffer.int.toLong() and 0xFFFFFFFFL
            val dtOffset = buffer.int.toLong() and 0xFFFFFFFFL
            val id = buffer.int.toLong() and 0xFFFFFFFFL
            val rev = buffer.int.toLong() and 0xFFFFFFFFL

            val custom = mutableListOf<Long>()
            for (c in 0 until 4) {
                custom.add(buffer.int.toLong() and 0xFFFFFFFFL)
            }

            require(dtOffset in 0 until bytes.size) {
                "Entry $i dt_offset ($dtOffset) is out of buffer bounds (${bytes.size})"
            }
            require(dtOffset + dtSize <= bytes.size) {
                "Entry $i payload (offset $dtOffset, size $dtSize) exceeds buffer size (${bytes.size})"
            }

            val fdtBytes = bytes.copyOfRange(dtOffset.toInt(), (dtOffset + dtSize).toInt())

            // Verify embedded FDT header magic
            require(fdtBytes.size >= FdtHeader.HEADER_SIZE) {
                "Entry $i embedded FDT payload size (${fdtBytes.size}) is smaller than FDT header (40 B)"
            }
            val fdtBuf = ByteBuffer.wrap(fdtBytes).order(ByteOrder.BIG_ENDIAN)
            val fdtMagic = fdtBuf.int.toLong() and 0xFFFFFFFFL
            require(fdtMagic == FdtHeader.FDT_MAGIC) {
                "Entry $i embedded FDT magic is 0x${fdtMagic.toString(16)}. Expected 0xd00dfeed."
            }

            entries.add(
                DtTableEntry(
                    index = i,
                    dtSize = dtSize,
                    dtOffset = dtOffset,
                    id = id,
                    rev = rev,
                    custom = custom,
                    fdtBytes = fdtBytes
                )
            )
        }

        return DtTableContainer(
            header = header,
            entries = entries,
            rawBytes = bytes
        )
    }

    /**
     * Rebuilds the complete Android DT Table container image when an embedded FDT payload is updated.
     */
    fun rebuild(
        container: DtTableContainer,
        updatedEntryIndex: Int,
        newFdtBytes: ByteArray
    ): ByteArray {
        require(updatedEntryIndex in container.entries.indices) {
            "Invalid entry index: $updatedEntryIndex"
        }

        val updatedEntries = container.entries.map { entry ->
            if (entry.index == updatedEntryIndex) {
                entry.copy(dtSize = newFdtBytes.size.toLong(), fdtBytes = newFdtBytes)
            } else {
                entry
            }
        }

        // Calculate layout
        val headerSize = container.header.headerSize
        val dtEntrySize = container.header.dtEntrySize
        val dtEntryCount = updatedEntries.size
        val dtEntriesOffset = container.header.dtEntriesOffset.toInt()

        val entryTableEnd = dtEntriesOffset + (dtEntryCount * dtEntrySize)

        // Calculate payload start offset (aligned to 64 bytes or page size)
        var currentOffset = alignOffset(entryTableEnd, 64)

        val finalEntries = mutableListOf<DtTableEntry>()
        val payloadMap = mutableListOf<Pair<Long, ByteArray>>()

        for (entry in updatedEntries) {
            val alignedOff = alignOffset(currentOffset, 64)
            val updatedEntry = entry.copy(dtOffset = alignedOff.toLong(), dtSize = entry.fdtBytes.size.toLong())
            finalEntries.add(updatedEntry)
            payloadMap.add(Pair(alignedOff.toLong(), entry.fdtBytes))
            currentOffset = alignedOff + entry.fdtBytes.size
        }

        val finalTotalSize = currentOffset.toLong()

        // Allocate buffer
        val buffer = ByteBuffer.allocate(finalTotalSize.toInt()).order(ByteOrder.BIG_ENDIAN)

        // Write Header
        buffer.putInt(DtTableHeader.DT_TABLE_MAGIC.toInt())
        buffer.putInt(finalTotalSize.toInt())
        buffer.putInt(headerSize)
        buffer.putInt(dtEntrySize)
        buffer.putInt(dtEntryCount)
        buffer.putInt(dtEntriesOffset)
        buffer.putInt(container.header.pageSize.toInt())
        buffer.putInt(container.header.version.toInt())

        // Write Entries Table
        for (i in finalEntries.indices) {
            val entry = finalEntries[i]
            val off = dtEntriesOffset + i * dtEntrySize
            buffer.position(off)
            buffer.putInt(entry.dtSize.toInt())
            buffer.putInt(entry.dtOffset.toInt())
            buffer.putInt(entry.id.toInt())
            buffer.putInt(entry.rev.toInt())
            for (c in 0 until 4) {
                val customVal = entry.custom.getOrElse(c) { 0L }
                buffer.putInt(customVal.toInt())
            }
        }

        // Write FDT Payloads
        for ((off, fdtBytes) in payloadMap) {
            buffer.position(off.toInt())
            buffer.put(fdtBytes)
        }

        return buffer.array()
    }

    private fun alignOffset(offset: Int, align: Int): Int {
        val rem = offset % align
        return if (rem == 0) offset else offset + (align - rem)
    }
}
