package com.dtb.editor.fdt.parser

import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.model.FdtProperty
import com.dtb.editor.fdt.model.FdtReserveEntry
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Robust Device Tree Source (DTS) parser and scanner.
 * Parses DTS text into a hierarchical FdtNode AST without altering or stripping source text.
 */
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

        val rootNode = FdtNode(name = "/", fullPath = "/")
        val nodeStack = mutableListOf<FdtNode>()

        val cleanText = stripComments(dtsText)

        // Parse memreserve
        val rsvRegex = Regex("""/memreserve/\s+(0x[0-9a-fA-F]+|[0-9]+)\s+(0x[0-9a-fA-F]+|[0-9]+)\s*;""")
        for (match in rsvRegex.findAll(cleanText)) {
            val addr = parseLongSafe(match.groupValues[1])
            val size = parseLongSafe(match.groupValues[2])
            reservations.add(FdtReserveEntry(addr, size))
        }

        // Tokenize into node blocks and property statements
        var pos = 0
        val len = cleanText.length

        fun skipWhitespaceAndDirectives() {
            while (pos < len) {
                val c = cleanText[pos]
                if (c.isWhitespace()) {
                    pos++
                } else if (c == '#' || (c == '/' && pos + 1 < len && cleanText[pos + 1] != '{' && !cleanText.startsWith("/dts-v1/", pos) && !cleanText.startsWith("/plugin/", pos) && !cleanText.startsWith("/memreserve/", pos) && !cleanText.startsWith("/delete-", pos))) {
                    // Skip preprocessor directive line e.g. #include, #define, /dts-v1/; /plugin/;
                    val lineEnd = cleanText.indexOf('\n', pos)
                    pos = if (lineEnd != -1) lineEnd + 1 else len
                } else if (cleanText.startsWith("/dts-v1/;", pos)) {
                    pos += "/dts-v1/;".length
                } else if (cleanText.startsWith("/plugin/;", pos)) {
                    pos += "/plugin/;".length
                } else if (cleanText.startsWith("/memreserve/", pos)) {
                    val semi = cleanText.indexOf(';', pos)
                    pos = if (semi != -1) semi + 1 else len
                } else {
                    break
                }
            }
        }

        while (pos < len) {
            skipWhitespaceAndDirectives()
            if (pos >= len) break

            // Find next statement or brace
            val startPos = pos
            var braceOrSemiPos = -1
            var tokenChar = ' '

            var inString = false
            var inAngle = false
            var inBracket = false

            while (pos < len) {
                val c = cleanText[pos]
                if (c == '"' && (pos == 0 || cleanText[pos - 1] != '\\')) {
                    inString = !inString
                } else if (!inString) {
                    if (c == '<') inAngle = true
                    else if (c == '>') inAngle = false
                    else if (c == '[') inBracket = true
                    else if (c == ']') inBracket = false
                    else if (!inAngle && !inBracket) {
                        if (c == '{' || c == '}' || c == ';') {
                            braceOrSemiPos = pos
                            tokenChar = c
                            pos++
                            break
                        }
                    }
                }
                pos++
            }

            if (braceOrSemiPos == -1) break

            val rawToken = cleanText.substring(startPos, braceOrSemiPos).trim()

            when (tokenChar) {
                '{' -> {
                    // Node declaration start
                    var nodeHeader = rawToken
                    var label: String? = null

                    if (nodeHeader.contains(':')) {
                        val parts = nodeHeader.split(':', limit = 2)
                        label = parts[0].trim()
                        nodeHeader = parts[1].trim()
                    }

                    // Handle label reference e.g. &cpus { ... } or &{/path} { ... }
                    if (nodeHeader.startsWith("&")) {
                        val refTarget = nodeHeader.substring(1).trim('{', '}', ' ')
                        val existingNode = rootNode.findNodeByPath(refTarget) ?: rootNode.children.firstOrNull { it.name == refTarget || it.label == refTarget }
                        if (existingNode != null) {
                            nodeStack.add(existingNode)
                        } else {
                            val refNode = FdtNode(name = refTarget, fullPath = "/$refTarget", label = label)
                            rootNode.children.add(refNode)
                            nodeStack.add(refNode)
                        }
                    } else {
                        val nodeName = if (nodeHeader == "/" || nodeHeader.isEmpty()) "/" else nodeHeader

                        if (nodeName == "/" && nodeStack.isEmpty()) {
                            rootNode.label = label
                            nodeStack.add(rootNode)
                        } else {
                            val parent = nodeStack.lastOrNull() ?: rootNode
                            val fullPath = if (parent.fullPath == "/") "/$nodeName" else "${parent.fullPath}/$nodeName"
                            val newNode = FdtNode(name = nodeName, fullPath = fullPath, label = label)
                            parent.children.add(newNode)
                            nodeStack.add(newNode)
                        }
                    }
                }

                '}' -> {
                    // Node end
                    if (nodeStack.isNotEmpty()) {
                        nodeStack.removeAt(nodeStack.lastIndex)
                    }
                    // Skip optional trailing semicolon after }
                    skipWhitespaceAndDirectives()
                    if (pos < len && cleanText[pos] == ';') {
                        pos++
                    }
                }

                ';' -> {
                    // Property statement
                    if (rawToken.isNotEmpty() && !rawToken.startsWith("/")) {
                        val currentNode = nodeStack.lastOrNull() ?: rootNode
                        val prop = parsePropertyStatement(rawToken, warnings)
                        if (prop != null) {
                            currentNode.properties.add(prop)
                        }
                    }
                }
            }
        }

        return DtsParseResult(
            rootNode = rootNode,
            reservations = reservations,
            errors = errors,
            warnings = warnings
        )
    }

    private fun parsePropertyStatement(statement: String, warnings: MutableList<String>): FdtProperty? {
        val eqIdx = statement.indexOf('=')
        if (eqIdx == -1) {
            val name = statement.trim()
            return if (name.isNotEmpty() && isValidPropName(name)) FdtProperty(name, ByteArray(0)) else null
        }

        val propName = statement.substring(0, eqIdx).trim()
        val propVal = statement.substring(eqIdx + 1).trim()

        if (!isValidPropName(propName)) return null

        return when {
            propVal.startsWith('"') -> {
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
                    bytesList.add(0)
                }
                FdtProperty(propName, bytesList.toByteArray())
            }

            propVal.startsWith('<') -> {
                val inner = propVal.trim('<', '>', ' ')
                val tokens = inner.split(Regex("""\s+""")).filter { it.isNotEmpty() }
                val buffer = ByteBuffer.allocate(tokens.size * 4).order(ByteOrder.BIG_ENDIAN)
                for (token in tokens) {
                    val num = if (token.startsWith("&")) {
                        token.hashCode().toLong() and 0xFFFFFFFFL
                    } else {
                        parseLongSafe(token)
                    }
                    buffer.putInt(num.toInt())
                }
                FdtProperty(propName, buffer.array())
            }

            propVal.startsWith('[') -> {
                val inner = propVal.trim('[', ']', ' ')
                val hexTokens = inner.split(Regex("""\s+""")).filter { it.isNotEmpty() }
                val bytes = ByteArray(hexTokens.size)
                for (idx in hexTokens.indices) {
                    bytes[idx] = hexTokens[idx].toInt(16).toByte()
                }
                FdtProperty(propName, bytes)
            }

            else -> {
                val num = parseLongSafe(propVal)
                val buffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
                buffer.putInt(num.toInt())
                FdtProperty(propName, buffer.array())
            }
        }
    }

    private fun isValidPropName(name: String): Boolean {
        if (name.isEmpty()) return false
        val first = name[0]
        return (first.isLetter() || first == '#' || first == '_' || first == '$' || first == '/')
    }

    private fun stripComments(source: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = source.length
        while (i < len) {
            if (i + 1 < len && source[i] == '/' && source[i + 1] == '/') {
                i += 2
                while (i < len && source[i] != '\n') i++
                if (i < len) {
                    sb.append('\n')
                    i++
                }
            } else if (i + 1 < len && source[i] == '/' && source[i + 1] == '*') {
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
