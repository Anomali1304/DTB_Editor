package com.dtb.editor.fdt.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Standard Flattened Device Tree Header (Device Tree Specification v0.4)
 */
data class FdtHeader(
    val magic: Long = 0xd00dfeed,
    val totalSize: Long = 0,
    val offDtStruct: Long = 0,
    val offDtStrings: Long = 0,
    val offMemRsvmap: Long = 0,
    val version: Long = 17,
    val lastCompVersion: Long = 16,
    val bootCpuidPhys: Long = 0,
    val sizeDtStrings: Long = 0,
    val sizeDtStruct: Long = 0
) {
    companion object {
        const val FDT_MAGIC = 0xd00dfeedL
        const val HEADER_SIZE = 40

        fun parse(buffer: ByteBuffer): FdtHeader {
            buffer.order(ByteOrder.BIG_ENDIAN)
            val magic = buffer.int.toLong() and 0xFFFFFFFFL
            require(magic == FDT_MAGIC) {
                "Invalid DTB magic: 0x${magic.toString(16)}. Expected 0xd00dfeed."
            }
            val totalSize = buffer.int.toLong() and 0xFFFFFFFFL
            val offDtStruct = buffer.int.toLong() and 0xFFFFFFFFL
            val offDtStrings = buffer.int.toLong() and 0xFFFFFFFFL
            val offMemRsvmap = buffer.int.toLong() and 0xFFFFFFFFL
            val version = buffer.int.toLong() and 0xFFFFFFFFL
            val lastCompVersion = buffer.int.toLong() and 0xFFFFFFFFL
            val bootCpuidPhys = buffer.int.toLong() and 0xFFFFFFFFL
            val sizeDtStrings = buffer.int.toLong() and 0xFFFFFFFFL
            val sizeDtStruct = buffer.int.toLong() and 0xFFFFFFFFL

            return FdtHeader(
                magic = magic,
                totalSize = totalSize,
                offDtStruct = offDtStruct,
                offDtStrings = offDtStrings,
                offMemRsvmap = offMemRsvmap,
                version = version,
                lastCompVersion = lastCompVersion,
                bootCpuidPhys = bootCpuidPhys,
                sizeDtStrings = sizeDtStrings,
                sizeDtStruct = sizeDtStruct
            )
        }
    }
}

data class FdtReserveEntry(
    val address: Long,
    val size: Long
)

/**
 * Represents a Device Tree Property
 */
data class FdtProperty(
    val name: String,
    val data: ByteArray
) {
    val size: Int get() = data.size

    fun asU32List(): List<Long> {
        if (data.size % 4 != 0) return emptyList()
        val list = mutableListOf<Long>()
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        while (buffer.remaining() >= 4) {
            list.add(buffer.int.toLong() and 0xFFFFFFFFL)
        }
        return list
    }

    fun asSingleU32(): Long? {
        if (data.size != 4) return null
        return ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
    }

    fun isPrintableString(): Boolean {
        if (data.isEmpty()) return false
        if (data.last() != 0.toByte()) return false
        for (i in 0 until data.size - 1) {
            val b = data[i].toInt() and 0xFF
            if (b == 0) continue // multiple strings in array
            if (b < 32 || b > 126) return false
        }
        return true
    }

    fun asStringList(): List<String> {
        if (!isPrintableString()) return emptyList()
        val str = String(data, Charsets.US_ASCII)
        return str.split('\u0000').filter { it.isNotEmpty() }
    }

    fun asHexDump(): String {
        return data.joinToString(" ") { String.format("%02x", it) }
    }

    fun formattedValue(): String {
        if (data.isEmpty()) return "(empty boolean)"
        val strings = asStringList()
        if (strings.isNotEmpty()) {
            return strings.joinToString(", ") { "\"$it\"" }
        }
        val u32s = asU32List()
        if (u32s.isNotEmpty() && data.size % 4 == 0) {
            return "<" + u32s.joinToString(" ") { "0x" + it.toString(16) } + ">"
        }
        return "[ " + asHexDump() + " ]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FdtProperty
        if (name != other.name) return false
        if (!data.contentEquals(other.data)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/**
 * Represents a Device Tree Node in the hierarchy
 */
data class FdtNode(
    var name: String,
    var fullPath: String = "",
    val properties: MutableList<FdtProperty> = mutableListOf(),
    val children: MutableList<FdtNode> = mutableListOf(),
    var label: String? = null
) {
    fun findProperty(propName: String): FdtProperty? {
        return properties.firstOrNull { it.name == propName }
    }

    fun findChild(childName: String): FdtNode? {
        return children.firstOrNull { it.name == childName }
    }

    fun findNodeByPath(path: String): FdtNode? {
        val cleanPath = path.trim('/')
        if (cleanPath.isEmpty()) return this
        val segments = cleanPath.split('/')
        var current: FdtNode? = this
        for (segment in segments) {
            current = current?.children?.firstOrNull {
                it.name == segment || it.name.startsWith("$segment@")
            }
            if (current == null) return null
        }
        return current
    }

    fun getPhandle(): Long? {
        return findProperty("phandle")?.asSingleU32()
            ?: findProperty("linux,phandle")?.asSingleU32()
    }

    val totalNodesCount: Int get() = 1 + children.sumOf { it.totalNodesCount }
    val totalPropertiesCount: Int get() = properties.size + children.sumOf { it.totalPropertiesCount }
}

data class DtbProject(
    val fileName: String,
    val originalSizeBytes: Long,
    val fileType: DtbFileType,
    val originalDts: String,
    var currentDts: String,
    val rootNode: FdtNode,
    val memReservations: List<FdtReserveEntry> = emptyList(),
    val warnings: List<String> = emptyList()
)

enum class DtbFileType(val extension: String, val displayName: String) {
    DTB("dtb", "Device Tree Blob (.dtb)"),
    DTBO("dtbo", "Device Tree Overlay (.dtbo)"),
    DTS("dts", "Device Tree Source (.dts)"),
    UNKNOWN("bin", "Binary / Unknown")
}

data class CompilationOutput(
    val success: Boolean,
    val log: String,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val compiledBytes: ByteArray? = null,
    val outputSizeBytes: Long = 0
)
