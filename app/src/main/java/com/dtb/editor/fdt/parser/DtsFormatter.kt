package com.dtb.editor.fdt.parser

import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.model.FdtProperty
import com.dtb.editor.fdt.model.FdtReserveEntry

class DtsFormatter {

    fun format(rootNode: FdtNode, reservations: List<FdtReserveEntry> = emptyList()): String {
        val sb = StringBuilder()
        sb.append("/dts-v1/;\n\n")

        for (rsv in reservations) {
            sb.append(String.format("/memreserve/ 0x%016x 0x%016x;\n", rsv.address, rsv.size))
        }
        if (reservations.isNotEmpty()) {
            sb.append("\n")
        }

        formatNode(rootNode, sb, 0)
        return sb.toString()
    }

    private fun formatNode(node: FdtNode, sb: StringBuilder, indentLevel: Int) {
        val indent = "    ".repeat(indentLevel)

        val nodeHeader = if (node.name.isEmpty() || node.name == "/") {
            "/"
        } else {
            val labelPrefix = if (node.label != null) "${node.label}: " else ""
            "$labelPrefix${node.name}"
        }

        sb.append(indent).append(nodeHeader).append(" {\n")

        // 1. Format properties
        for (prop in node.properties) {
            formatProperty(prop, sb, indentLevel + 1)
        }

        // Add a blank line between properties and child nodes if both exist
        if (node.properties.isNotEmpty() && node.children.isNotEmpty()) {
            sb.append("\n")
        }

        // 2. Format children
        for (i in node.children.indices) {
            val child = node.children[i]
            formatNode(child, sb, indentLevel + 1)
            if (i < node.children.size - 1) {
                sb.append("\n")
            }
        }

        sb.append(indent).append("};\n")
    }

    private fun formatProperty(prop: FdtProperty, sb: StringBuilder, indentLevel: Int) {
        val indent = "    ".repeat(indentLevel)
        sb.append(indent).append(prop.name)

        if (prop.data.isEmpty()) {
            sb.append(";\n")
            return
        }

        sb.append(" = ")
        val strings = prop.asStringList()
        if (strings.isNotEmpty()) {
            sb.append(strings.joinToString(", ") { "\"$it\"" })
            sb.append(";\n")
            return
        }

        val u32s = prop.asU32List()
        if (u32s.isNotEmpty() && prop.data.size % 4 == 0) {
            sb.append("<")
            sb.append(u32s.joinToString(" ") { String.format("0x%x", it) })
            sb.append(">;\n")
            return
        }

        // Fallback to byte array syntax [00 11 22 33]
        sb.append("[")
        sb.append(prop.data.joinToString(" ") { String.format("%02x", it) })
        sb.append("];\n")
    }
}
