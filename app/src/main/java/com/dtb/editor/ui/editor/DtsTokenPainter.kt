package com.dtb.editor.ui.editor

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.dtb.editor.ui.screens.DtsSyntaxPalette

/**
 * Zero-allocation direct-canvas DTS syntax renderer for visible viewport lines.
 * Eliminates ForegroundColorSpan/DynamicLayout churn completely.
 */
class DtsTokenPainter(baseTextSizePx: Float) {

    private val defaultPaint = createPaint(baseTextSizePx, Typeface.MONOSPACE)
    private val commentPaint = createPaint(baseTextSizePx, Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC))
    private val directivePaint = createPaint(baseTextSizePx, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD))
    private val nodePaint = createPaint(baseTextSizePx, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD))
    private val propPaint = createPaint(baseTextSizePx, Typeface.MONOSPACE)
    private val stringPaint = createPaint(baseTextSizePx, Typeface.MONOSPACE)
    private val numberPaint = createPaint(baseTextSizePx, Typeface.MONOSPACE)
    private val punctPaint = createPaint(baseTextSizePx, Typeface.MONOSPACE)

    val charWidth: Float
        get() = defaultPaint.measureText("MMMMMMMMMM") / 10f

    val fontMetrics: Paint.FontMetrics
        get() = defaultPaint.fontMetrics

    fun measureSlice(text: CharSequence, start: Int, end: Int): Float {
        if (start >= end) return 0f
        return defaultPaint.measureText(text, start, end)
    }

    fun applyPalette(palette: DtsSyntaxPalette) {
        defaultPaint.color = palette.text.toArgb()
        commentPaint.color = palette.comment.toArgb()
        directivePaint.color = palette.directive.toArgb()
        nodePaint.color = palette.nodeTitle.toArgb()
        propPaint.color = palette.propertyKey.toArgb()
        stringPaint.color = palette.stringLiteral.toArgb()
        numberPaint.color = palette.hexOrNumber.toArgb()
        punctPaint.color = palette.punctuation.toArgb()
    }

    /**
     * Draws [text] slice `[sliceStart, sliceEnd)` at `(startX, baseline)` with inline DTS syntax colors.
     */
    fun drawHighlightedSlice(
        canvas: Canvas,
        text: CharSequence,
        sliceStart: Int,
        sliceEnd: Int,
        startX: Float,
        baseline: Float,
        fastCharWidth: Float,
        isPureAscii: Boolean
    ) {
        if (sliceStart >= sliceEnd) return

        var cursor = sliceStart
        var x = startX

        fun drawSegment(segStart: Int, segEnd: Int, paint: Paint) {
            if (segStart >= segEnd) return
            if (segStart > cursor) {
                val gapW = advanceWidth(text, cursor, segStart, fastCharWidth, isPureAscii)
                canvas.drawText(text, cursor, segStart, x, baseline, defaultPaint)
                x += gapW
            }
            val segW = advanceWidth(text, segStart, segEnd, fastCharWidth, isPureAscii)
            canvas.drawText(text, segStart, segEnd, x, baseline, paint)
            x += segW
            cursor = segEnd
        }

        var i = sliceStart
        while (i < sliceEnd && text[i].isWhitespace()) i++
        if (i >= sliceEnd) {
            canvas.drawText(text, sliceStart, sliceEnd, startX, baseline, defaultPaint)
            return
        }

        // Comment-only line
        if (i + 1 < sliceEnd && text[i] == '/' && text[i + 1] == '/') {
            drawSegment(i, sliceEnd, commentPaint)
            return
        }
        if (i + 1 < sliceEnd && text[i] == '/' && text[i + 1] == '*') {
            val closeIdx = indexOfTwoChars(text, '*', '/', i + 2, sliceEnd)
            val end = if (closeIdx >= 0) closeIdx + 2 else sliceEnd
            drawSegment(i, end, commentPaint)
            if (end >= sliceEnd) return
            i = end
        }

        // Directives: /dts-v1/;, /memreserve/, #include
        if (startsWithAt(text, "/dts-v1/", i, sliceEnd) ||
            startsWithAt(text, "/memreserve/", i, sliceEnd) ||
            text[i] == '#'
        ) {
            drawSegment(i, sliceEnd, directivePaint)
            return
        }

        var equalsIdx = -1
        var openBraceIdx = -1
        var inStr = false
        var idx = i
        while (idx < sliceEnd) {
            val c = text[idx]
            if (c == '"' && (idx == sliceStart || text[idx - 1] != '\\')) {
                inStr = !inStr
            } else if (!inStr) {
                if (c == '/' && idx + 1 < sliceEnd && text[idx + 1] == '/') break
                if (c == '=' && equalsIdx < 0) equalsIdx = idx
                if (c == '{' && openBraceIdx < 0) openBraceIdx = idx
            }
            idx++
        }

        if (openBraceIdx >= 0 && (equalsIdx < 0 || openBraceIdx < equalsIdx)) {
            if (i < openBraceIdx) {
                drawSegment(i, openBraceIdx, nodePaint)
            }
            drawSegment(openBraceIdx, openBraceIdx + 1, punctPaint)
            i = openBraceIdx + 1
        } else if (equalsIdx > i) {
            drawSegment(i, equalsIdx, propPaint)
            drawSegment(equalsIdx, equalsIdx + 1, punctPaint)
            i = equalsIdx + 1
        }

        while (i < sliceEnd) {
            val c = text[i]
            when {
                c == '/' && i + 1 < sliceEnd && text[i + 1] == '/' -> {
                    drawSegment(i, sliceEnd, commentPaint)
                    return
                }
                c == '/' && i + 1 < sliceEnd && text[i + 1] == '*' -> {
                    val close = indexOfTwoChars(text, '*', '/', i + 2, sliceEnd)
                    val end = if (close >= 0) close + 2 else sliceEnd
                    drawSegment(i, end, commentPaint)
                    i = end
                }
                c == '"' -> {
                    var end = i + 1
                    while (end < sliceEnd) {
                        if (text[end] == '"' && text[end - 1] != '\\') {
                            end++
                            break
                        }
                        end++
                    }
                    drawSegment(i, end, stringPaint)
                    i = end
                }
                c == '0' && i + 1 < sliceEnd && (text[i + 1] == 'x' || text[i + 1] == 'X') -> {
                    var end = i + 2
                    while (end < sliceEnd && isHexChar(text[end])) end++
                    drawSegment(i, end, numberPaint)
                    i = end
                }
                c.isDigit() && (i == sliceStart || !isIdentChar(text[i - 1])) -> {
                    var end = i + 1
                    while (end < sliceEnd && text[end].isDigit()) end++
                    if (end == sliceEnd || !isIdentChar(text[end])) {
                        drawSegment(i, end, numberPaint)
                    }
                    i = end
                }
                c == '&' -> {
                    var end = i + 1
                    while (end < sliceEnd && isIdentChar(text[end])) end++
                    drawSegment(i, end, directivePaint)
                    i = end
                }
                c == '{' || c == '}' || c == ';' || c == '<' || c == '>' -> {
                    drawSegment(i, i + 1, punctPaint)
                    i++
                }
                else -> i++
            }
        }

        if (cursor < sliceEnd) {
            canvas.drawText(text, cursor, sliceEnd, x, baseline, defaultPaint)
        }
    }

    private fun advanceWidth(
        text: CharSequence,
        start: Int,
        end: Int,
        fastCharWidth: Float,
        isPureAscii: Boolean
    ): Float {
        return if (isPureAscii) {
            (end - start) * fastCharWidth
        } else {
            defaultPaint.measureText(text, start, end)
        }
    }

    private fun indexOfTwoChars(text: CharSequence, c0: Char, c1: Char, start: Int, end: Int): Int {
        for (i in start until end - 1) {
            if (text[i] == c0 && text[i + 1] == c1) return i
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

    private fun isHexChar(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
    private fun isIdentChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '-'

    private fun createPaint(sizePx: Float, tf: Typeface): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx
        typeface = tf
    }
}
