package com.dtb.editor.ui.editor

import com.dtb.editor.ui.screens.DtsOffsetMatch

data class DtsLineCol(
    val line: Int,
    val col: Int
)

data class DtsBufferDelta(
    val startOffset: Int,
    val oldText: String,
    val newText: String
)

class DtsBufferUndoManager(
    private val maxStackSize: Int = 150,
    private val coalesceWindowMs: Long = 700L
) {
    private val undoStack = mutableListOf<DtsBufferDelta>()
    private val redoStack = mutableListOf<DtsBufferDelta>()
    private var lastEditTimeMs: Long = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun recordChange(
        startOffset: Int,
        oldText: String,
        newText: String,
        timestampMs: Long = System.currentTimeMillis()
    ) {
        if (oldText == newText) return

        val last = undoStack.lastOrNull()
        val canCoalesce = last != null &&
            (timestampMs - lastEditTimeMs) <= coalesceWindowMs &&
            last.oldText.isEmpty() &&
            last.newText.length in 1..64 &&
            oldText.isEmpty() &&
            newText.length == 1 &&
            !newText[0].isWhitespace() &&
            startOffset == last.startOffset + last.newText.length

        val canCoalesceBackspace = last != null &&
            (timestampMs - lastEditTimeMs) <= coalesceWindowMs &&
            last.newText.isEmpty() &&
            last.oldText.length in 1..64 &&
            newText.isEmpty() &&
            oldText.length == 1 &&
            !oldText[0].isWhitespace() &&
            startOffset + 1 == last.startOffset

        if (canCoalesce) {
            undoStack[undoStack.lastIndex] = last!!.copy(newText = last.newText + newText)
        } else if (canCoalesceBackspace) {
            undoStack[undoStack.lastIndex] = last!!.copy(
                startOffset = startOffset,
                oldText = oldText + last.oldText
            )
        } else {
            undoStack.add(DtsBufferDelta(startOffset, oldText, newText))
            if (undoStack.size > maxStackSize) {
                undoStack.removeAt(0)
            }
        }
        lastEditTimeMs = timestampMs
        redoStack.clear()
    }

    fun undo(buffer: DtsDocumentBuffer): Int? {
        if (!canUndo) return null
        val delta = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(delta)
        val safeStart = delta.startOffset.coerceIn(0, buffer.totalLength)
        val safeEnd = (delta.startOffset + delta.newText.length).coerceIn(safeStart, buffer.totalLength)
        buffer.replaceRangeOffsets(safeStart, safeEnd, delta.oldText)
        lastEditTimeMs = 0L
        return (safeStart + delta.oldText.length).coerceIn(0, buffer.totalLength)
    }

    fun redo(buffer: DtsDocumentBuffer): Int? {
        if (!canRedo) return null
        val delta = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(delta)
        val safeStart = delta.startOffset.coerceIn(0, buffer.totalLength)
        val safeEnd = (delta.startOffset + delta.oldText.length).coerceIn(safeStart, buffer.totalLength)
        buffer.replaceRangeOffsets(safeStart, safeEnd, delta.newText)
        lastEditTimeMs = 0L
        return (safeStart + delta.newText.length).coerceIn(0, buffer.totalLength)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastEditTimeMs = 0L
    }
}

/**
 * Virtualized line-backed mutable text buffer for DTS files.
 * Splitting, typing, deletion, and search operate directly on per-line StringBuilders without
 * copying the entire document or triggering Android DynamicLayout reflows.
 */
class DtsDocumentBuffer(initialText: String = "") {
    private val lines = ArrayList<StringBuilder>(256)
    private var lineStartOffsets = IntArray(256)
    private var offsetsDirtyFrom = 0
    private var cachedTotalLength = 0
    private var cachedMaxLineChars = 0

    val lineCount: Int get() = lines.size
    val totalLength: Int
        get() {
            ensureOffsetsValid()
            return cachedTotalLength
        }
    val maxLineCharCount: Int get() = cachedMaxLineChars

    init {
        setText(initialText)
    }

    fun setText(text: String) {
        lines.clear()
        if (text.isEmpty()) {
            lines.add(StringBuilder())
            cachedMaxLineChars = 0
            cachedTotalLength = 0
            lineStartOffsets = intArrayOf(0)
            offsetsDirtyFrom = Int.MAX_VALUE
            return
        }

        val estimatedLines = (text.length / 32).coerceAtLeast(64)
        lines.ensureCapacity(estimatedLines)

        var start = 0
        var maxLen = 0
        val len = text.length
        while (start <= len) {
            val nl = text.indexOf('\n', start)
            val end = if (nl >= 0) nl else len
            val effectiveEnd = if (end > start && text[end - 1] == '\r') end - 1 else end
            val sb = StringBuilder(effectiveEnd - start + 8)
            sb.append(text, start, effectiveEnd)
            lines.add(sb)
            if (sb.length > maxLen) maxLen = sb.length
            if (nl < 0) break
            start = nl + 1
        }
        if (lines.isEmpty()) lines.add(StringBuilder())
        cachedMaxLineChars = maxLen
        offsetsDirtyFrom = 0
        ensureOffsetsValid()
    }

    fun getLine(index: Int): StringBuilder = lines[index]

    fun getLineLength(index: Int): Int = lines[index].length

    fun getLineStartOffset(index: Int): Int {
        ensureOffsetsValid()
        return lineStartOffsets[index.coerceIn(0, lines.lastIndex)]
    }

    fun getFullText(): String {
        ensureOffsetsValid()
        val sb = StringBuilder(cachedTotalLength + 16)
        for (i in lines.indices) {
            if (i > 0) sb.append('\n')
            sb.append(lines[i])
        }
        return sb.toString()
    }

    fun lineColToOffset(line: Int, col: Int): Int {
        ensureOffsetsValid()
        val safeLine = line.coerceIn(0, lines.lastIndex)
        val safeCol = col.coerceIn(0, lines[safeLine].length)
        return lineStartOffsets[safeLine] + safeCol
    }

    fun offsetToLineCol(offset: Int): DtsLineCol {
        ensureOffsetsValid()
        val clamped = offset.coerceIn(0, cachedTotalLength)
        var lo = 0
        var hi = lines.lastIndex
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lineStartOffsets[mid] <= clamped) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        val col = (clamped - lineStartOffsets[ans]).coerceIn(0, lines[ans].length)
        return DtsLineCol(ans, col)
    }

    fun extractRange(startOffset: Int, endOffset: Int): String {
        ensureOffsetsValid()
        val safeStart = startOffset.coerceIn(0, cachedTotalLength)
        val safeEnd = endOffset.coerceIn(safeStart, cachedTotalLength)
        if (safeStart == safeEnd) return ""
        val p1 = offsetToLineCol(safeStart)
        val p2 = offsetToLineCol(safeEnd)
        if (p1.line == p2.line) {
            return lines[p1.line].substring(p1.col, p2.col)
        }
        val sb = StringBuilder(safeEnd - safeStart + 8)
        sb.append(lines[p1.line], p1.col, lines[p1.line].length)
        for (l in (p1.line + 1) until p2.line) {
            sb.append('\n').append(lines[l])
        }
        sb.append('\n').append(lines[p2.line], 0, p2.col)
        return sb.toString()
    }

    fun replaceRangeOffsets(startOffset: Int, endOffset: Int, replacement: String) {
        ensureOffsetsValid()
        val safeStart = startOffset.coerceIn(0, cachedTotalLength)
        val safeEnd = endOffset.coerceIn(safeStart, cachedTotalLength)
        val p1 = offsetToLineCol(safeStart)
        val p2 = offsetToLineCol(safeEnd)

        val normalizedRep = if (replacement.indexOf('\r') >= 0) {
            replacement.replace("\r\n", "\n").replace('\r', '\n')
        } else {
            replacement
        }

        val headLine = lines[p1.line]
        val tailLine = lines[p2.line]
        val tailSuffix = tailLine.substring(p2.col)

        // Remove lines after p1.line up to p2.line
        if (p2.line > p1.line) {
            lines.subList(p1.line + 1, p2.line + 1).clear()
        }
        headLine.setLength(p1.col)

        val firstNl = normalizedRep.indexOf('\n')
        if (firstNl < 0) {
            headLine.append(normalizedRep)
            headLine.append(tailSuffix)
            if (headLine.length > cachedMaxLineChars) {
                cachedMaxLineChars = headLine.length
            }
        } else {
            headLine.append(normalizedRep, 0, firstNl)
            if (headLine.length > cachedMaxLineChars) {
                cachedMaxLineChars = headLine.length
            }
            val insertedLines = ArrayList<StringBuilder>()
            var pos = firstNl + 1
            while (pos <= normalizedRep.length) {
                val nextNl = normalizedRep.indexOf('\n', pos)
                val segEnd = if (nextNl >= 0) nextNl else normalizedRep.length
                val sb = StringBuilder(segEnd - pos + if (nextNl < 0) tailSuffix.length + 8 else 8)
                sb.append(normalizedRep, pos, segEnd)
                if (nextNl < 0) {
                    sb.append(tailSuffix)
                }
                if (sb.length > cachedMaxLineChars) {
                    cachedMaxLineChars = sb.length
                }
                insertedLines.add(sb)
                if (nextNl < 0) break
                pos = nextNl + 1
            }
            lines.addAll(p1.line + 1, insertedLines)
        }

        offsetsDirtyFrom = minOf(offsetsDirtyFrom, p1.line)
        ensureOffsetsValid()
    }

    fun findAllMatches(query: String, matchCase: Boolean): List<DtsOffsetMatch> {
        if (query.isEmpty()) return emptyList()
        ensureOffsetsValid()
        val qLen = query.length
        val ignoreCase = !matchCase
        val results = ArrayList<DtsOffsetMatch>(64)

        for (lineIdx in lines.indices) {
            val line = lines[lineIdx]
            if (line.length < qLen) continue
            val lineStartOff = lineStartOffsets[lineIdx]
            var searchFrom = 0
            while (searchFrom <= line.length - qLen) {
                val found = indexOfIgnoreCase(line, query, searchFrom, ignoreCase)
                if (found < 0) break
                results.add(
                    DtsOffsetMatch(
                        lineIndex = lineIdx,
                        startOffset = lineStartOff + found,
                        endOffset = lineStartOff + found + qLen
                    )
                )
                searchFrom = found + qLen
            }
        }
        return results
    }

    private fun indexOfIgnoreCase(
        haystack: CharSequence,
        needle: String,
        startIndex: Int,
        ignoreCase: Boolean
    ): Int {
        val hLen = haystack.length
        val nLen = needle.length
        val maxStart = hLen - nLen
        if (maxStart < startIndex) return -1
        val firstChar = needle[0]
        for (i in startIndex..maxStart) {
            if (!charsEqual(haystack[i], firstChar, ignoreCase)) continue
            var matched = true
            for (j in 1 until nLen) {
                if (!charsEqual(haystack[i + j], needle[j], ignoreCase)) {
                    matched = false
                    break
                }
            }
            if (matched) return i
        }
        return -1
    }

    private fun charsEqual(a: Char, b: Char, ignoreCase: Boolean): Boolean {
        if (a == b) return true
        if (!ignoreCase) return false
        return a.lowercaseChar() == b.lowercaseChar()
    }

    private fun ensureOffsetsValid() {
        if (offsetsDirtyFrom >= lines.size && lineStartOffsets.size >= lines.size) return
        if (lineStartOffsets.size < lines.size) {
            val newCap = maxOf(lines.size, lineStartOffsets.size * 2)
            val grown = IntArray(newCap)
            val copyCount = minOf(offsetsDirtyFrom, lineStartOffsets.size)
            System.arraycopy(lineStartOffsets, 0, grown, 0, copyCount)
            lineStartOffsets = grown
            offsetsDirtyFrom = copyCount
        }
        val startIdx = offsetsDirtyFrom.coerceIn(0, lines.lastIndex)
        var acc = if (startIdx == 0) 0 else lineStartOffsets[startIdx - 1] + lines[startIdx - 1].length + 1
        for (i in startIdx until lines.size) {
            lineStartOffsets[i] = acc
            acc += lines[i].length + 1
        }
        cachedTotalLength = (acc - 1).coerceAtLeast(0)
        offsetsDirtyFrom = Int.MAX_VALUE
    }
}
