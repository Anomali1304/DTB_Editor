package com.dtb.editor.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

data class DtsSearchMatch(
    val lineIndex: Int,
    val startCol: Int,
    val endCol: Int
)

object DtsSearchEngine {
    fun findAllMatches(
        lines: List<String>,
        query: String,
        matchCase: Boolean
    ): List<DtsSearchMatch> {
        if (query.isEmpty() || lines.isEmpty()) return emptyList()
        val matches = ArrayList<DtsSearchMatch>(64)
        val ignoreCase = !matchCase
        val qLen = query.length

        for (lineIdx in lines.indices) {
            val line = lines[lineIdx]
            if (line.length < qLen) continue
            var start = 0
            while (start <= line.length - qLen) {
                val found = line.indexOf(query, startIndex = start, ignoreCase = ignoreCase)
                if (found < 0) break
                matches.add(DtsSearchMatch(lineIdx, found, found + qLen))
                start = found + qLen
            }
        }
        return matches
    }
}

data class DtsSyntaxPalette(
    val text: Color,
    val comment: Color,
    val stringLiteral: Color,
    val propertyKey: Color,
    val nodeTitle: Color,
    val hexOrNumber: Color,
    val directive: Color,
    val punctuation: Color,
    val matchHighlightBg: Color,
    val activeMatchHighlightBg: Color,
    val activeMatchHighlightText: Color
)

object DtsSyntaxHighlighter {

    fun buildHighlightedLine(
        line: String,
        palette: DtsSyntaxPalette,
        lineMatches: List<DtsSearchMatch>?,
        activeMatch: DtsSearchMatch?
    ): AnnotatedString {
        if (line.isEmpty()) return AnnotatedString("")
        return buildAnnotatedString {
            append(line)
            applySyntaxSpans(line, palette)
            if (!lineMatches.isNullOrEmpty()) {
                for (match in lineMatches) {
                    val safeStart = match.startCol.coerceIn(0, line.length)
                    val safeEnd = match.endCol.coerceIn(safeStart, line.length)
                    if (safeStart < safeEnd) {
                        val isActive = activeMatch != null &&
                            activeMatch.lineIndex == match.lineIndex &&
                            activeMatch.startCol == match.startCol &&
                            activeMatch.endCol == match.endCol
                        if (isActive) {
                            addStyle(
                                style = SpanStyle(
                                    background = palette.activeMatchHighlightBg,
                                    color = palette.activeMatchHighlightText,
                                    fontWeight = FontWeight.Bold
                                ),
                                start = safeStart,
                                end = safeEnd
                            )
                        } else {
                            addStyle(
                                style = SpanStyle(background = palette.matchHighlightBg),
                                start = safeStart,
                                end = safeEnd
                            )
                        }
                    }
                }
            }
        }
    }

    private fun AnnotatedString.Builder.applySyntaxSpans(line: String, palette: DtsSyntaxPalette) {
        val len = line.length
        var i = 0
        while (i < len && line[i].isWhitespace()) i++
        if (i >= len) return

        // Line comment: // ...
        if (i + 1 < len && line[i] == '/' && line[i + 1] == '/') {
            addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), i, len)
            return
        }

        // Block comment starting at line: /* ... */
        if (i + 1 < len && line[i] == '/' && line[i + 1] == '*') {
            val closeIdx = line.indexOf("*/", i + 2)
            val end = if (closeIdx >= 0) closeIdx + 2 else len
            addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), i, end)
            if (end >= len) return
            i = end
        }

        // DTS directives: /dts-v1/;, /memreserve/, #include
        if (line.startsWith("/dts-v1/", i) || line.startsWith("/memreserve/", i) || line[i] == '#') {
            addStyle(SpanStyle(color = palette.directive, fontWeight = FontWeight.SemiBold), i, len)
            return
        }

        // Check for '=' on this line outside quotes/comments
        var equalsIdx = -1
        var openBraceIdx = -1
        var inStr = false
        var idx = i
        while (idx < len) {
            val c = line[idx]
            if (c == '"' && (idx == 0 || line[idx - 1] != '\\')) {
                inStr = !inStr
            } else if (!inStr) {
                if (c == '/' && idx + 1 < len && line[idx + 1] == '/') break
                if (c == '=' && equalsIdx < 0) equalsIdx = idx
                if (c == '{' && openBraceIdx < 0) openBraceIdx = idx
            }
            idx++
        }

        if (openBraceIdx >= 0 && (equalsIdx < 0 || openBraceIdx < equalsIdx)) {
            // Node header e.g. `cpu@0 {` or `/ {`
            if (i < openBraceIdx) {
                addStyle(SpanStyle(color = palette.nodeTitle, fontWeight = FontWeight.SemiBold), i, openBraceIdx)
            }
            addStyle(SpanStyle(color = palette.punctuation, fontWeight = FontWeight.Bold), openBraceIdx, openBraceIdx + 1)
            i = openBraceIdx + 1
        } else if (equalsIdx > i) {
            // Property name before '='
            addStyle(SpanStyle(color = palette.propertyKey, fontWeight = FontWeight.Medium), i, equalsIdx)
            addStyle(SpanStyle(color = palette.punctuation), equalsIdx, equalsIdx + 1)
            i = equalsIdx + 1
        }

        // Scan remaining tokens on line: strings, comments, hex cells, references, braces
        while (i < len) {
            val c = line[i]
            when {
                c == '/' && i + 1 < len && line[i + 1] == '/' -> {
                    addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), i, len)
                    return
                }
                c == '/' && i + 1 < len && line[i + 1] == '*' -> {
                    val close = line.indexOf("*/", i + 2)
                    val end = if (close >= 0) close + 2 else len
                    addStyle(SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic), i, end)
                    i = end
                }
                c == '"' -> {
                    var end = i + 1
                    while (end < len) {
                        if (line[end] == '"' && line[end - 1] != '\\') {
                            end++
                            break
                        }
                        end++
                    }
                    addStyle(SpanStyle(color = palette.stringLiteral), i, end)
                    i = end
                }
                c == '0' && i + 1 < len && (line[i + 1] == 'x' || line[i + 1] == 'X') -> {
                    var end = i + 2
                    while (end < len && isHexChar(line[end])) end++
                    addStyle(SpanStyle(color = palette.hexOrNumber), i, end)
                    i = end
                }
                c.isDigit() && (i == 0 || !isIdentChar(line[i - 1])) -> {
                    var end = i + 1
                    while (end < len && line[end].isDigit()) end++
                    if (end == len || !isIdentChar(line[end])) {
                        addStyle(SpanStyle(color = palette.hexOrNumber), i, end)
                    }
                    i = end
                }
                c == '&' -> {
                    var end = i + 1
                    while (end < len && isIdentChar(line[end])) end++
                    addStyle(SpanStyle(color = palette.directive), i, end)
                    i = end
                }
                c == '{' || c == '}' || c == ';' || c == '<' || c == '>' -> {
                    addStyle(SpanStyle(color = palette.punctuation), i, i + 1)
                    i++
                }
                else -> i++
            }
        }
    }

    private fun isHexChar(c: Char): Boolean {
        return c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    }

    private fun isIdentChar(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '_' || c == '-'
    }
}
