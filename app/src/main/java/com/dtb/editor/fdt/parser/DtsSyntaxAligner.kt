package com.dtb.editor.fdt.parser

/**
 * Non-destructive source-preserving DTS formatter and indentation aligner.
 * Preserves comments, phandle references (&label), directives, and incomplete edits
 * while aligning indentation, braces, property assignment spacing, and blank lines.
 */
object DtsSyntaxAligner {

    fun align(source: String): String {
        if (source.isEmpty()) return source

        val normalized = source.replace("\r\n", "\n").replace("\r", "\n")
        val rawLines = normalized.split("\n")
        val indentUnit = detectIndentUnit(rawLines)

        val outLines = ArrayList<String>(rawLines.size)
        var depth = 0
        var inBlockComment = false
        var consecutiveBlank = 0

        val lastNonTrailingEmpty = rawLines.indexOfLast { it.isNotEmpty() }
        val hadTrailingNewline = normalized.endsWith("\n")

        for (idx in rawLines.indices) {
            if (idx > lastNonTrailingEmpty && hadTrailingNewline) {
                break
            }
            val rawLine = rawLines[idx]
            val trimmed = rawLine.trim()

            if (trimmed.isEmpty()) {
                if (outLines.isNotEmpty() && consecutiveBlank == 0) {
                    outLines.add("")
                    consecutiveBlank = 1
                }
                continue
            }
            consecutiveBlank = 0

            val braceScan = scanBraces(trimmed, inBlockComment)
            val effectiveDepth = (depth - braceScan.leadingCloses).coerceAtLeast(0)

            val formattedContent = if (inBlockComment || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("#") || trimmed.startsWith("/dts-v1/") || trimmed.startsWith("/memreserve/") || trimmed.startsWith("/plugin/")) {
                trimmed
            } else {
                normalizeLineSpacing(trimmed)
            }

            val indentPrefix = indentUnit.repeat(effectiveDepth)
            outLines.add(indentPrefix + formattedContent)

            depth = (depth + braceScan.opens - braceScan.closes).coerceAtLeast(0)
            inBlockComment = braceScan.endsInBlockComment
        }

        val joined = outLines.joinToString("\n")
        return if (hadTrailingNewline && joined.isNotEmpty()) "$joined\n" else joined
    }

    internal fun detectIndentUnit(lines: List<String>): String {
        var tabVotes = 0
        var fourSpaceVotes = 0
        var twoSpaceVotes = 0
        var sampled = 0

        for (line in lines) {
            if (line.isBlank()) continue
            var spaces = 0
            var tabs = 0
            for (c in line) {
                when (c) {
                    '\t' -> tabs++
                    ' ' -> spaces++
                    else -> break
                }
            }
            if (tabs == 0 && spaces == 0) continue

            if (tabs > 0 && spaces == 0) {
                tabVotes++
            } else if (spaces % 4 == 0) {
                fourSpaceVotes++
            } else if (spaces % 2 == 0) {
                twoSpaceVotes++
            }
            sampled++
            if (sampled >= 250) break
        }

        return when {
            tabVotes > fourSpaceVotes && tabVotes > twoSpaceVotes -> "\t"
            twoSpaceVotes > fourSpaceVotes && twoSpaceVotes >= 2 -> "  "
            else -> "    "
        }
    }

    private data class BraceScan(
        val leadingCloses: Int,
        val opens: Int,
        val closes: Int,
        val endsInBlockComment: Boolean
    )

    private fun scanBraces(line: String, startsInBlockComment: Boolean): BraceScan {
        var inBlockComment = startsInBlockComment
        var inString = false
        var leadingCloses = 0
        var seenNonCloseCode = false
        var opens = 0
        var closes = 0
        var i = 0
        val len = line.length

        while (i < len) {
            val c = line[i]
            if (inBlockComment) {
                if (c == '*' && i + 1 < len && line[i + 1] == '/') {
                    inBlockComment = false
                    i += 2
                } else {
                    i++
                }
                continue
            }

            if (inString) {
                if (c == '"' && (i == 0 || line[i - 1] != '\\')) {
                    inString = false
                }
                i++
                continue
            }

            if (c == '/' && i + 1 < len && line[i + 1] == '/') {
                break
            }
            if (c == '/' && i + 1 < len && line[i + 1] == '*') {
                inBlockComment = true
                i += 2
                continue
            }
            if (c == '"') {
                seenNonCloseCode = true
                inString = true
                i++
                continue
            }
            if (c == '{') {
                seenNonCloseCode = true
                opens++
            } else if (c == '}') {
                closes++
                if (!seenNonCloseCode) {
                    leadingCloses++
                }
            } else if (!c.isWhitespace() && c != ';') {
                seenNonCloseCode = true
            }
            i++
        }

        return BraceScan(
            leadingCloses = leadingCloses,
            opens = opens,
            closes = closes,
            endsInBlockComment = inBlockComment
        )
    }

    private fun normalizeLineSpacing(trimmed: String): String {
        val sb = StringBuilder(trimmed.length + 4)
        var inString = false
        var inComment = false
        var inAngle = false
        var inBracket = false
        var eqNormalized = false
        var i = 0
        val len = trimmed.length

        while (i < len) {
            val c = trimmed[i]
            if (inComment) {
                sb.append(c)
                i++
                continue
            }
            if (inString) {
                sb.append(c)
                if (c == '"' && (i == 0 || trimmed[i - 1] != '\\')) {
                    inString = false
                }
                i++
                continue
            }
            if (c == '/' && i + 1 < len && trimmed[i + 1] == '/') {
                if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
                inComment = true
                sb.append("//")
                i += 2
                continue
            }
            if (c == '/' && i + 1 < len && trimmed[i + 1] == '*') {
                val endBlock = trimmed.indexOf("*/", i + 2)
                if (endBlock >= 0) {
                    sb.append(trimmed, i, endBlock + 2)
                    i = endBlock + 2
                } else {
                    sb.append(trimmed, i, len)
                    break
                }
                continue
            }
            if (c == '"') {
                inString = true
                sb.append(c)
                i++
                continue
            }
            if (c == '<') inAngle = true
            else if (c == '>') inAngle = false
            else if (c == '[') inBracket = true
            else if (c == ']') inBracket = false

            if (!inAngle && !inBracket && !eqNormalized && c == '=') {
                while (sb.isNotEmpty() && sb.last() == ' ') {
                    sb.deleteCharAt(sb.length - 1)
                }
                sb.append(" = ")
                eqNormalized = true
                i++
                while (i < len && trimmed[i] == ' ') i++
                continue
            }

            if (!inAngle && !inBracket && c == '{') {
                while (sb.isNotEmpty() && sb.last() == ' ') {
                    sb.deleteCharAt(sb.length - 1)
                }
                if (sb.isNotEmpty()) {
                    sb.append(' ')
                }
                sb.append('{')
                i++
                continue
            }

            sb.append(c)
            i++
        }
        return sb.toString().trimEnd()
    }
}
