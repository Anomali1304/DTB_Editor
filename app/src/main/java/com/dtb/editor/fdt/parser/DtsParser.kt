package com.dtb.editor.fdt.parser

import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.model.FdtProperty
import com.dtb.editor.fdt.model.FdtReserveEntry
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DtsParser {

    data class DtsParseResult(
        val rootNode: FdtNode,
        val reservations: List<FdtReserveEntry>,
        val errors: List<String>,
        val warnings: List<String>
    )

    fun parse(dtsText: String): DtsParseResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val reservations = mutableListOf<FdtReserveEntry>()

        // 1. Strip comments (C-style /* */ and C++ style //)
        val cleanText = stripComments(dtsText)

        // 2. Extract /memreserve/
        val rsvRegex = Regex("""/memreserve/\s+(0x[0-9a-fA-F]+|[0-9]+)\s+(0x[0-9a-fA-F]+|[0-9]+)\s*;""")
        for (match in rsvRegex.findAll(cleanText)) {
            val addr = parseLongSafe(match.groupValues[1])
            val size = parseLongSafe(match.groupValues[2])
            reservations.add(FdtReserveEntry(addr, size))
        }

        // 3. Tokenize and parse tree
        val root = FdtNode(name = "/", fullPath = "/")
        val nodeStack = mutableListOf<FdtNode>()

        // Token scanning state machine
        var i = 0
        val len = cleanText.length

        fun skipWhitespace() {
            while (i < len && cleanText[i].isWhitespace()) i++
        }

        // Skip /dts-v1/;
        val dtsV1Idx = cleanText.indexOf("/dts-v1/;")
        if (dtsV1Idx != -1) {
            i = dtsV1Idx + "/dts-v1/;".length
        }

        while (i < len) {
            skipWhitespace()
            if (i >= len) break

            // Check for /memreserve/ line to skip
            if (cleanText.startsWith("/memreserve/", i)) {
                val endSemi = cleanText.indexOf(';', i)
                if (endSemi != -1) {
                    i = endSemi + 1
                    continue
                }
            }

            // Read next statement or block
            val start = i
            var hasBrace = false
            var endStmt = -1

            while (i < len) {
                val c = cleanText[i]
                if (c == '{') {
                    hasBrace = true
                    endStmt = i
                    i++
                    break
                } else if (c == ';') {
                    endStmt = i
                    i++
                    break
                } else if (c == '}') {
                    endStmt = i
                    i++
                    // check trailing semicolon
                    skipWhitespace()
                    if (i < len && cleanText[i] == ';') i++
                    break
                }
                i++
            }

            if (endStmt == -1) break
            val rawToken = cleanText.substring(start, endStmt).trim()

            if (cleanText[endStmt] == '}') {
                if (nodeStack.isNotEmpty()) {
                    nodeStack.removeAt(nodeStack.lastIndex)
                }
            } else if (hasBrace) {
                // Beginning of node
                var nodeName = rawToken
                var label: String? = null

                if (nodeName.contains(':')) {
                    val parts = nodeName.split(':', limit = 2)
                    label = parts[0].trim()
                    nodeName = parts[1].trim()
                }

                if (nodeName == "/" || (nodeName.isEmpty() && nodeStack.isEmpty())) {
                    nodeStack.clear()
                    nodeStack.add(root)
                    root.label = label
                } else {
                    val parent = nodeStack.lastOrNull() ?: root
                    val path = if (parent.fullPath == "/") "/$nodeName" else "${parent.fullPath}/$nodeName"
                    val newNode = FdtNode(name = nodeName, fullPath = path, label = label)
                    parent.children.add(newNode)
                    nodeStack.add(newNode)
                }
            } else {
                // Property statement: name [= value]
                if (rawToken.isNotEmpty()) {
                    val currentNode = nodeStack.lastOrNull() ?: root
                    val prop = parseProperty(rawToken, warnings)
                    if (prop != null) {
                        currentNode.properties.add(prop)
                    }
                }
            }
        }

        return DtsParseResult(
            rootNode = root,
            reservations = reservations,
            errors = errors,
            warnings = warnings
        )
    }

    private fun parseProperty(statement: String, warnings: MutableList<String>): FdtProperty? {
        val eqIdx = statement.indexOf('=')
        if (eqIdx == -1) {
            // Boolean empty property
            val name = statement.trim()
            return if (name.isNotEmpty()) FdtProperty(name, ByteArray(0)) else null
        }

        val propName = statement.substring(0, eqIdx).trim()
        val propVal = statement.substring(eqIdx + 1).trim()

        if (propVal.startsWith('"')) {
            // String or list of strings: "str1", "str2"
            val strings = mutableListOf<String>()
            val regex = Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"""")
            for (m in regex.findAll(propVal)) {
                strings.add(m.groupValues[1])
            }
            val bytesList = mutableListOf<Byte>()
            for (s in strings) {
                for (b in s.toByteArray(Charsets.US_ASCII)) {
                    bytesList.add(b)
                }
                bytesList.add(0) // null terminator
            }
            return FdtProperty(propName, bytesList.toByteArray())
        } else if (propVal.startsWith('<')) {
            // Cell array: <0x1 0x2 ...> or <&phandle ...>
            val inner = propVal.trim('<', '>', ' ')
            val tokens = inner.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val buffer = ByteBuffer.allocate(tokens.size * 4).order(ByteOrder.BIG_ENDIAN)
            for (token in tokens) {
                val num = if (token.startsWith("&")) {
                    // Reference phandle, assign a placeholder integer or parse hash
                    token.hashCode().toLong() and 0xFFFFFFFFL
                } else {
                    parseLongSafe(token)
                }
                buffer.putInt(num.toInt())
            }
            return FdtProperty(propName, buffer.array())
        } else if (propVal.startsWith('[')) {
            // Byte array: [00 11 22]
            val inner = propVal.trim('[', ']', ' ')
            val hexTokens = inner.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val bytes = ByteArray(hexTokens.size)
            for (idx in hexTokens.indices) {
                bytes[idx] = hexTokens[idx].toInt(16).toByte()
            }
            return FdtProperty(propName, bytes)
        } else {
            // Fallback numeric or raw token
            val num = parseLongSafe(propVal)
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
            buffer.putInt(num.toInt())
            return FdtProperty(propName, buffer.array())
        }
    }

    private fun stripComments(source: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = source.length
        while (i < len) {
            if (i + 1 < len && source[i] == '/' && source[i + 1] == '/') {
                // Single line comment
                i += 2
                while (i < len && source[i] != '\n') i++
                if (i < len) {
                    sb.append('\n')
                    i++
                }
            } else if (i + 1 < len && source[i] == '/' && source[i + 1] == '*') {
                // Multi line comment
                i += 2
                while (i + 1 < len && !(source[i] == '*' && source[i + 1] == '/')) {
                    if (source[i] == '\n') sb.append('\n')
                    i++
                }
                i += 2
            } else {
                sb.append(source[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun parseLongSafe(str: String): Long {
        val s = str.trim()
        return try {
            if (s.startsWith("0x", ignoreCase = true)) {
                s.substring(2).toLong(16)
            } else {
                s.toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }
}
