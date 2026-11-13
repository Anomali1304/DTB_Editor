package com.dtb.editor.ui.screens

import android.text.Spannable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

data class DtsOffsetMatch(
    val lineIndex: Int,
    val startOffset: Int,
    val endOffset: Int
)

class DtsSyntaxSpan(color: Int) : ForegroundColorSpan(color)

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

object DtsOffsetSearchEngine {
    fun findAllMatches(
        text: String,
        query: String,
        matchCase: Boolean
    ): List<DtsOffsetMatch> {
        if (query.isEmpty() || text.length < query.length) return emptyList()
        val matches = ArrayList<DtsOffsetMatch>(64)
        val ignoreCase = !matchCase
        val qLen = query.length
        var searchPos = 0
        var lineCursor = 0
        var currentLine = 0

        while (searchPos <= text.length - qLen) {
            val found = text.indexOf(query, startIndex = searchPos, ignoreCase = ignoreCase)
            if (found < 0) break
            while (lineCursor < found) {
                if (text[lineCursor] == '\n') currentLine++
                lineCursor++
            }
            matches.add(DtsOffsetMatch(currentLine, found, found + qLen))
            searchPos = found + qLen
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
            tokenizeLine(line, 0, line.length) { start, end, tokenKind ->
                val style = when (tokenKind) {
                    TokenKind.COMMENT -> SpanStyle(color = palette.comment, fontStyle = FontStyle.Italic)
                    TokenKind.DIRECTIVE -> SpanStyle(color = palette.directive, fontWeight = FontWeight.SemiBold)
                    TokenKind.NODE_TITLE -> SpanStyle(color = palette.nodeTitle, fontWeight = FontWeight.SemiBold)
                    TokenKind.PROPERTY_KEY -> SpanStyle(color = palette.propertyKey, fontWeight = FontWeight.Medium)
                    TokenKind.STRING -> SpanStyle(color = palette.stringLiteral)
                    TokenKind.NUMBER -> SpanStyle(color = palette.hexOrNumber)
                    TokenKind.PUNCTUATION -> SpanStyle(color = palette.punctuation)
                }
                addStyle(style, start, end)
            }
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

    fun applyViewportSyntaxSpans(
        spannable: Spannable,
        windowStart: Int,
        windowEnd: Int,
        palette: DtsSyntaxPalette
    ) {
        val len = spannable.length
        val safeStart = windowStart.coerceIn(0, len)
        val safeEnd = windowEnd.coerceIn(safeStart, len)

        // Remove only our previous DtsSyntaxSpan markers (never touches IME/selection spans)
        val existing = spannable.getSpans(0, len, DtsSyntaxSpan::class.java)
        for (span in existing) {
            spannable.removeSpan(span)
        }
        if (safeStart >= safeEnd) return

        val commentArgb = palette.comment.toArgb()
        val directiveArgb = palette.directive.toArgb()
        val nodeArgb = palette.nodeTitle.toArgb()
        val propArgb = palette.propertyKey.toArgb()
        val strArgb = palette.stringLiteral.toArgb()
        val numArgb = palette.hexOrNumber.toArgb()
        val punctArgb = palette.punctuation.toArgb()

        var lineStart = safeStart
        while (lineStart < safeEnd) {
            var lineEnd = lineStart
            while (lineEnd < safeEnd && spannable[lineEnd] != '\n') {
                lineEnd++
            }
            if (lineEnd > lineStart) {
                tokenizeLine(spannable, lineStart, lineEnd) { s, e, kind ->
                    val argb = when (kind) {
                        TokenKind.COMMENT -> commentArgb
                        TokenKind.DIRECTIVE -> directiveArgb
                        TokenKind.NODE_TITLE -> nodeArgb
                        TokenKind.PROPERTY_KEY -> propArgb
                        TokenKind.STRING -> strArgb
                        TokenKind.NUMBER -> numArgb
                        TokenKind.PUNCTUATION -> punctArgb
                    }
                    spannable.setSpan(DtsSyntaxSpan(argb), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            lineStart = lineEnd + 1
        }
    }

    private enum class TokenKind {
        COMMENT,
        DIRECTIVE,
        NODE_TITLE,
        PROPERTY_KEY,
        STRING,
        NUMBER,
        PUNCTUATION
    }

    private inline fun tokenizeLine(
        text: CharSequence,
        lineStart: Int,
        lineEnd: Int,
        emit: (start: Int, end: Int, kind: TokenKind) -> Unit
    ) {
        var i = lineStart
        while (i < lineEnd && text[i].isWhitespace()) i++
        if (i >= lineEnd) return

        // Line comment: // ...
        if (i + 1 < lineEnd && text[i] == '/' && text[i + 1] == '/') {
            emit(i, lineEnd, TokenKind.COMMENT)
            return
        }

        // Block comment starting at line: /* ... */
        if (i + 1 < lineEnd && text[i] == '/' && text[i + 1] == '*') {
            val closeIdx = indexOfSubseq(text, "*/", i + 2, lineEnd)
            val end = if (closeIdx >= 0) closeIdx + 2 else lineEnd
            emit(i, end, TokenKind.COMMENT)
            if (end >= lineEnd) return
            i = end
        }

        // DTS directives: /dts-v1/;, /memreserve/, #include
        if (startsWithAt(text, "/dts-v1/", i, lineEnd) ||
            startsWithAt(text, "/memreserve/", i, lineEnd) ||
            text[i] == '#'
        ) {
            emit(i, lineEnd, TokenKind.DIRECTIVE)
            return
        }

        var equalsIdx = -1
        var openBraceIdx = -1
        var inStr = false
        var idx = i
        while (idx < lineEnd) {
            val c = text[idx]
            if (c == '"' && (idx == lineStart || text[idx - 1] != '\\')) {
                inStr = !inStr
            } else if (!inStr) {
                if (c == '/' && idx + 1 < lineEnd && text[idx + 1] == '/') break
                if (c == '=' && equalsIdx < 0) equalsIdx = idx
                if (c == '{' && openBraceIdx < 0) openBraceIdx = idx
            }
            idx++
        }

        if (openBraceIdx >= 0 && (equalsIdx < 0 || openBraceIdx < equalsIdx)) {
            if (i < openBraceIdx) {
                emit(i, openBraceIdx, TokenKind.NODE_TITLE)
            }
            emit(openBraceIdx, openBraceIdx + 1, TokenKind.PUNCTUATION)
            i = openBraceIdx + 1
        } else if (equalsIdx > i) {
            emit(i, equalsIdx, TokenKind.PROPERTY_KEY)
            emit(equalsIdx, equalsIdx + 1, TokenKind.PUNCTUATION)
            i = equalsIdx + 1
        }

        while (i < lineEnd) {
            val c = text[i]
            when {
                c == '/' && i + 1 < lineEnd && text[i + 1] == '/' -> {
                    emit(i, lineEnd, TokenKind.COMMENT)
                    return
                }
                c == '/' && i + 1 < lineEnd && text[i + 1] == '*' -> {
                    val close = indexOfSubseq(text, "*/", i + 2, lineEnd)
                    val end = if (close >= 0) close + 2 else lineEnd
                    emit(i, end, TokenKind.COMMENT)
                    i = end
                }
                c == '"' -> {
                    var end = i + 1
                    while (end < lineEnd) {
                        if (text[end] == '"' && text[end - 1] != '\\') {
                            end++
                            break
                        }
                        end++
                    }
                    emit(i, end, TokenKind.STRING)
                    i = end
                }
                c == '0' && i + 1 < lineEnd && (text[i + 1] == 'x' || text[i + 1] == 'X') -> {
                    var end = i + 2
                    while (end < lineEnd && isHexChar(text[end])) end++
                    emit(i, end, TokenKind.NUMBER)
                    i = end
                }
                c.isDigit() && (i == lineStart || !isIdentChar(text[i - 1])) -> {
                    var end = i + 1
                    while (end < lineEnd && text[end].isDigit()) end++
                    if (end == lineEnd || !isIdentChar(text[end])) {
                        emit(i, end, TokenKind.NUMBER)
                    }
                    i = end
                }
                c == '&' -> {
                    var end = i + 1
                    while (end < lineEnd && isIdentChar(text[end])) end++
                    emit(i, end, TokenKind.DIRECTIVE)
                    i = end
                }
                c == '{' || c == '}' || c == ';' || c == '<' || c == '>' -> {
                    emit(i, i + 1, TokenKind.PUNCTUATION)
                    i++
                }
                else -> i++
            }
        }
    }

    private fun indexOfSubseq(text: CharSequence, target: String, start: Int, end: Int): Int {
        if (target.length == 2 && end - start >= 2) {
            val c0 = target[0]
            val c1 = target[1]
            for (i in start until end - 1) {
                if (text[i] == c0 && text[i + 1] == c1) return i
            }
        }
        return -1
    }

    private fun startsWithAt(text: CharSequence, prefix: String, start: Int, end: Int): Boolean {
        if (end - start < prefix.length) return false
        for (i in prefix.indices) {
            if (text[start + i] != prefix[i]) return false
        }
        return true
    }

    private fun isHexChar(c: Char): Boolean {
        return c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    }

    private fun isIdentChar(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '_' || c == '-'
    }
}
