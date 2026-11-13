package com.dtb.editor.ui.screens

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.Layout
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.EditText
import android.widget.OverScroller
import androidx.compose.ui.graphics.toArgb
import com.dtb.editor.fdt.parser.DtsTextDeltaUndoManager
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DtsCodeEditText(context: Context) : EditText(context) {

    var appliedRevision: Int = -1
        private set

    var deltaUndoManager: DtsTextDeltaUndoManager = DtsTextDeltaUndoManager()
    var onDirtyCallback: (() -> Unit)? = null
    var onUndoRedoChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null
    var onCursorPositionChanged: ((currentLineOneBased: Int, totalLines: Int) -> Unit)? = null
    var onTextMutated: (() -> Unit)? = null

    private var syntaxPalette: DtsSyntaxPalette? = null
    private var isWordWrap: Boolean = false
    private var isProgrammaticEdit: Boolean = false

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val overScroller = OverScroller(context)
    private var downScrollX = 0
    private var downScrollY = 0

    private var gutterWidthPx: Int = (46 * density).toInt()
    private val gutterEndPadPx: Float = 8 * density
    private val codeLeftGapPx: Int = (10 * density).toInt()
    private val verticalPadPx: Int = (8 * density).toInt()
    private val bottomPadPx: Int = (80 * density).toInt()

    private val gutterBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gutterDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 1f * density
    }
    private val gutterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, resources.displayMetrics)
        textAlign = Paint.Align.RIGHT
    }
    private val activeGutterTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, resources.displayMetrics)
        textAlign = Paint.Align.RIGHT
    }
    private val activeLinePaint = Paint()
    private val matchHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeMatchHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightRect = RectF()

    private var searchMatches: List<DtsOffsetMatch> = emptyList()
    private var activeMatchIndex: Int = -1

    private var cachedLogicalLineCount: Int = 1
    private var pendingBeforeStart: Int = 0
    private var pendingBeforeOldText: String = ""

    private val syntaxHighlightRunnable = Runnable {
        applyViewportSyntaxHighlightingNow()
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val movedVertically = abs(scrollY - downScrollY) > touchSlop / 2
                val movedHorizontally = abs(scrollX - downScrollX) > touchSlop / 2
                if (!movedVertically && !movedHorizontally) {
                    return false
                }
                val maxY = computeMaxScrollY()
                val maxX = if (isWordWrap) 0 else computeMaxScrollX()
                overScroller.fling(
                    scrollX,
                    scrollY,
                    if (movedHorizontally) (-velocityX).toInt() else 0,
                    if (movedVertically) (-velocityY).toInt() else 0,
                    0,
                    maxX,
                    0,
                    maxY
                )
                postInvalidateOnAnimation()
                return true
            }
        }
    )

    init {
        background = null
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setLineSpacing(0f, 1.25f)
        gravity = Gravity.TOP or Gravity.START
        isSingleLine = false
        maxLines = Int.MAX_VALUE
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setHorizontallyScrolling(true)
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = true
        setTextIsSelectable(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            breakStrategy = Layout.BREAK_STRATEGY_SIMPLE
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        }

        updateGutterPadding()

        filters = arrayOf(
            InputFilter { source, start, end, dest, dstart, _ ->
                if (!isProgrammaticEdit && end - start == 1 && source[start] == '\n') {
                    val lineStart = findLineStartBefore(dest, dstart)
                    val indent = readLeadingWhitespace(dest, lineStart, dstart)
                    val lastNonSpace = findLastNonSpaceCharBefore(dest, lineStart, dstart)
                    val extraIndent = if (lastNonSpace == '{') {
                        if (indent.contains('\t')) "\t" else "    "
                    } else {
                        ""
                    }
                    "\n$indent$extraIndent"
                } else {
                    null
                }
            }
        )

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (isProgrammaticEdit || s == null) return
                pendingBeforeStart = start
                val safeEnd = (start + count).coerceAtMost(s.length)
                pendingBeforeOldText = if (count > 0 && start < safeEnd) {
                    s.subSequence(start, safeEnd).toString()
                } else {
                    ""
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isProgrammaticEdit || s == null) return
                val safeEnd = (start + count).coerceAtMost(s.length)
                val inserted = if (count > 0 && start < safeEnd) {
                    s.subSequence(start, safeEnd).toString()
                } else {
                    ""
                }
                deltaUndoManager.recordChange(pendingBeforeStart, pendingBeforeOldText, inserted)
                onUndoRedoChanged?.invoke(deltaUndoManager.canUndo, deltaUndoManager.canRedo)
                onDirtyCallback?.invoke()
            }

            override fun afterTextChanged(s: Editable?) {
                if (isProgrammaticEdit) return
                recomputeLogicalLineCount(s)
                notifyCursorStats()
                onTextMutated?.invoke()
                scheduleSyntaxHighlight(delayMs = 95L)
            }
        })
    }

    fun applyThemeColors(
        palette: DtsSyntaxPalette,
        gutterBgColor: Int,
        gutterDividerColor: Int,
        gutterTextColor: Int,
        gutterActiveTextColor: Int,
        activeLineHighlightColor: Int
    ) {
        val changed = syntaxPalette != palette ||
            gutterBgPaint.color != gutterBgColor ||
            gutterTextPaint.color != gutterTextColor
        syntaxPalette = palette
        setTextColor(palette.text.toArgb())
        gutterBgPaint.color = gutterBgColor
        gutterDividerPaint.color = gutterDividerColor
        gutterTextPaint.color = gutterTextColor
        activeGutterTextPaint.color = gutterActiveTextColor
        activeLinePaint.color = activeLineHighlightColor
        matchHighlightPaint.color = palette.matchHighlightBg.toArgb()
        activeMatchHighlightPaint.color = palette.activeMatchHighlightBg.toArgb()
        if (changed) {
            scheduleSyntaxHighlight(delayMs = 20L)
            invalidate()
        }
    }

    fun setDocumentText(fullText: String, revision: Int, undoManager: DtsTextDeltaUndoManager) {
        this.deltaUndoManager = undoManager
        this.appliedRevision = revision
        isProgrammaticEdit = true
        try {
            setText(fullText, BufferType.EDITABLE)
            recomputeLogicalLineCount(text)
            onUndoRedoChanged?.invoke(deltaUndoManager.canUndo, deltaUndoManager.canRedo)
            notifyCursorStats()
        } finally {
            isProgrammaticEdit = false
        }
        post {
            applyViewportSyntaxHighlightingNow()
        }
    }

    fun setWordWrapEnabled(enabled: Boolean) {
        if (isWordWrap == enabled) return
        isWordWrap = enabled
        setHorizontallyScrolling(!enabled)
        if (enabled && scrollX != 0) {
            scrollTo(0, scrollY)
        }
        requestLayout()
        scheduleSyntaxHighlight(delayMs = 30L)
    }

    fun performUndo(): Boolean {
        val editable = text ?: return false
        isProgrammaticEdit = true
        return try {
            val newCursor = deltaUndoManager.undo(editable) ?: return false
            setSelection(newCursor.coerceIn(0, editable.length))
            recomputeLogicalLineCount(editable)
            onUndoRedoChanged?.invoke(deltaUndoManager.canUndo, deltaUndoManager.canRedo)
            onDirtyCallback?.invoke()
            notifyCursorStats()
            onTextMutated?.invoke()
            scheduleSyntaxHighlight(delayMs = 30L)
            true
        } finally {
            isProgrammaticEdit = false
        }
    }

    fun performRedo(): Boolean {
        val editable = text ?: return false
        isProgrammaticEdit = true
        return try {
            val newCursor = deltaUndoManager.redo(editable) ?: return false
            setSelection(newCursor.coerceIn(0, editable.length))
            recomputeLogicalLineCount(editable)
            onUndoRedoChanged?.invoke(deltaUndoManager.canUndo, deltaUndoManager.canRedo)
            onDirtyCallback?.invoke()
            notifyCursorStats()
            onTextMutated?.invoke()
            scheduleSyntaxHighlight(delayMs = 30L)
            true
        } finally {
            isProgrammaticEdit = false
        }
    }

    /**
     * Replaces the minimal changed region with [alignedText] and records a single delta on the
     * undo stack so the entire format operation can be undone in one tap.
     */
    fun applyAlignedText(alignedText: String): Boolean {
        val editable = text ?: return false
        val currentText = editable.toString()
        if (currentText == alignedText) return false

        var prefix = 0
        val minLen = min(currentText.length, alignedText.length)
        while (prefix < minLen && currentText[prefix] == alignedText[prefix]) {
            prefix++
        }

        var oldSuffixEnd = currentText.length
        var newSuffixEnd = alignedText.length
        while (oldSuffixEnd > prefix && newSuffixEnd > prefix &&
            currentText[oldSuffixEnd - 1] == alignedText[newSuffixEnd - 1]
        ) {
            oldSuffixEnd--
            newSuffixEnd--
        }

        val oldSlice = currentText.substring(prefix, oldSuffixEnd)
        val newSlice = alignedText.substring(prefix, newSuffixEnd)
        val prevCursorRatio = if (currentText.isNotEmpty()) {
            selectionStart.coerceIn(0, currentText.length).toFloat() / currentText.length.toFloat()
        } else {
            0f
        }

        isProgrammaticEdit = true
        return try {
            deltaUndoManager.recordChange(prefix, oldSlice, newSlice)
            editable.replace(prefix, oldSuffixEnd, newSlice)
            val restoredCursor = (prevCursorRatio * editable.length).toInt().coerceIn(0, editable.length)
            setSelection(restoredCursor)
            recomputeLogicalLineCount(editable)
            onUndoRedoChanged?.invoke(deltaUndoManager.canUndo, deltaUndoManager.canRedo)
            onDirtyCallback?.invoke()
            notifyCursorStats()
            onTextMutated?.invoke()
            scheduleSyntaxHighlight(delayMs = 20L)
            true
        } finally {
            isProgrammaticEdit = false
        }
    }

    fun setSearchHighlights(matches: List<DtsOffsetMatch>, activeIdx: Int) {
        searchMatches = matches
        activeMatchIndex = activeIdx
        invalidate()
    }

    fun scrollToMatch(match: DtsOffsetMatch) {
        val lyt = layout ?: return
        val len = text?.length ?: 0
        val safeStart = match.startOffset.coerceIn(0, len)
        val visualLine = lyt.getLineForOffset(safeStart)
        val targetY = (lyt.getLineTop(visualLine) + totalPaddingTop - height / 3)
            .coerceIn(0, computeMaxScrollY())
        val targetX = if (isWordWrap) {
            0
        } else {
            val charX = (lyt.getPrimaryHorizontal(safeStart) - (width - gutterWidthPx) / 3f).toInt()
            charX.coerceAtLeast(0)
        }
        overScroller.forceFinished(true)
        scrollTo(targetX, targetY)
        scheduleSyntaxHighlight(delayMs = 30L)
        invalidate()
    }

    fun focusMatchInEditor(match: DtsOffsetMatch) {
        val editable = text ?: return
        val safeStart = match.startOffset.coerceIn(0, editable.length)
        val safeEnd = match.endOffset.coerceIn(safeStart, editable.length)
        requestFocus()
        setSelection(safeStart, safeEnd)
        scrollToMatch(match)
    }

    fun jumpToLogicalLine(lineOneBased: Int) {
        val editable = text ?: return
        val targetTarget = lineOneBased.coerceIn(1, cachedLogicalLineCount)
        var offset = 0
        var currentLine = 1
        while (offset < editable.length && currentLine < targetTarget) {
            if (editable[offset] == '\n') {
                currentLine++
            }
            offset++
        }
        requestFocus()
        setSelection(offset.coerceIn(0, editable.length))
        val lyt = layout
        if (lyt != null) {
            val visualLine = lyt.getLineForOffset(offset.coerceIn(0, editable.length))
            val targetY = (lyt.getLineTop(visualLine) + totalPaddingTop - height / 3)
                .coerceIn(0, computeMaxScrollY())
            overScroller.forceFinished(true)
            scrollTo(0, targetY)
            scheduleSyntaxHighlight(delayMs = 30L)
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        notifyCursorStats()
        invalidate()
    }

    override fun onScrollChanged(horiz: Int, vert: Int, oldHoriz: Int, oldVert: Int) {
        super.onScrollChanged(horiz, vert, oldHoriz, oldVert)
        if (abs(vert - oldVert) > 20) {
            scheduleSyntaxHighlight(delayMs = 75L)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!overScroller.isFinished) {
                    overScroller.abortAnimation()
                }
                downScrollX = scrollX
                downScrollY = scrollY
            }
        }
        val handledBySuper = super.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return handledBySuper
    }

    override fun computeScroll() {
        if (overScroller.computeScrollOffset()) {
            scrollTo(overScroller.currX, overScroller.currY)
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(syntaxHighlightRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val lyt = layout
        if (lyt == null || width <= 0 || height <= 0) {
            super.onDraw(canvas)
            return
        }

        val topVisibleY = (scrollY - totalPaddingTop).coerceAtLeast(0)
        val bottomVisibleY = (scrollY + height - totalPaddingTop).coerceAtLeast(topVisibleY)
        val firstVisLine = lyt.getLineForVertical(topVisibleY).coerceIn(0, lyt.lineCount - 1)
        val lastVisLine = lyt.getLineForVertical(bottomVisibleY).coerceIn(firstVisLine, lyt.lineCount - 1)

        val saveClipCount = canvas.save()
        canvas.clipRect(
            (scrollX + gutterWidthPx).toFloat(),
            scrollY.toFloat(),
            (scrollX + width).toFloat(),
            (scrollY + height).toFloat()
        )

        // 1. Subtle active cursor line highlight
        val cursorOff = selectionStart.coerceIn(0, text?.length ?: 0)
        val cursorVisualLine = lyt.getLineForOffset(cursorOff)
        if (selectionStart == selectionEnd && cursorVisualLine in firstVisLine..lastVisLine) {
            canvas.drawRect(
                (scrollX + gutterWidthPx).toFloat(),
                (totalPaddingTop + lyt.getLineTop(cursorVisualLine)).toFloat(),
                (scrollX + width).toFloat(),
                (totalPaddingTop + lyt.getLineBottom(cursorVisualLine)).toFloat(),
                activeLinePaint
            )
        }

        // 2. O(log N) search match highlights drawn directly on canvas (zero span churn)
        if (searchMatches.isNotEmpty()) {
            val windowStartOff = lyt.getLineStart(firstVisLine)
            val windowEndOff = lyt.getLineEnd(lastVisLine)
            var idx = findFirstMatchAtOrAfter(windowStartOff)
            while (idx < searchMatches.size) {
                val match = searchMatches[idx]
                if (match.startOffset >= windowEndOff) break
                val textLen = text?.length ?: 0
                val mStart = match.startOffset.coerceIn(0, textLen)
                val mEnd = match.endOffset.coerceIn(mStart, textLen)
                if (mStart < mEnd) {
                    val mStartLine = lyt.getLineForOffset(mStart)
                    val mEndLine = lyt.getLineForOffset(mEnd)
                    val paint = if (idx == activeMatchIndex) activeMatchHighlightPaint else matchHighlightPaint
                    for (l in max(mStartLine, firstVisLine)..min(mEndLine, lastVisLine)) {
                        val segStart = if (l == mStartLine) mStart else lyt.getLineStart(l)
                        val segEnd = if (l == mEndLine) mEnd else lyt.getLineVisibleEnd(l)
                        val x1 = totalPaddingLeft + lyt.getPrimaryHorizontal(segStart)
                        val x2 = totalPaddingLeft + lyt.getPrimaryHorizontal(segEnd)
                        if (x2 > x1) {
                            highlightRect.set(
                                x1,
                                (totalPaddingTop + lyt.getLineTop(l)).toFloat(),
                                x2,
                                (totalPaddingTop + lyt.getLineBottom(l)).toFloat()
                            )
                            canvas.drawRoundRect(highlightRect, 3f * density, 3f * density, paint)
                        }
                    }
                }
                idx++
            }
        }

        // 3. Draw actual text inside clipped area
        super.onDraw(canvas)
        canvas.restoreToCount(saveClipCount)

        // 4. Draw pinned left line number gutter
        val gutterRight = scrollX.toFloat() + gutterWidthPx
        canvas.drawRect(
            scrollX.toFloat(),
            scrollY.toFloat(),
            gutterRight,
            (scrollY + height).toFloat(),
            gutterBgPaint
        )
        canvas.drawLine(
            gutterRight - (0.5f * density),
            scrollY.toFloat(),
            gutterRight - (0.5f * density),
            (scrollY + height).toFloat(),
            gutterDividerPaint
        )

        val numX = gutterRight - gutterEndPadPx
        val editableText = text
        var logicalLineNum = if (!isWordWrap) {
            firstVisLine + 1
        } else {
            countLogicalLinesBefore(editableText, lyt.getLineStart(firstVisLine)) + 1
        }

        for (line in firstVisLine..lastVisLine) {
            val lineStart = lyt.getLineStart(line)
            val isLogicalStart = line == 0 ||
                (editableText != null && lineStart > 0 && lineStart <= editableText.length && editableText[lineStart - 1] == '\n')
            if (isLogicalStart) {
                if (isWordWrap && line > firstVisLine) {
                    logicalLineNum++
                }
                val numToDraw = if (!isWordWrap) (line + 1) else logicalLineNum
                val baseline = (totalPaddingTop + lyt.getLineBaseline(line)).toFloat()
                val paintToUse = if (line == cursorVisualLine) activeGutterTextPaint else gutterTextPaint
                canvas.drawText(numToDraw.toString(), numX, baseline, paintToUse)
            }
        }
    }

    private fun scheduleSyntaxHighlight(delayMs: Long) {
        removeCallbacks(syntaxHighlightRunnable)
        postDelayed(syntaxHighlightRunnable, delayMs)
    }

    private fun applyViewportSyntaxHighlightingNow() {
        val palette = syntaxPalette ?: return
        val lyt = layout ?: return
        val spannable = text as? Spannable ?: return
        if (lyt.lineCount == 0 || spannable.isEmpty()) return

        val topY = (scrollY - totalPaddingTop).coerceAtLeast(0)
        val botY = (scrollY + height.coerceAtLeast(800) - totalPaddingTop).coerceAtLeast(topY)
        val firstLine = (lyt.getLineForVertical(topY) - 18).coerceIn(0, lyt.lineCount - 1)
        val lastLine = (lyt.getLineForVertical(botY) + 18).coerceIn(firstLine, lyt.lineCount - 1)

        val winStart = lyt.getLineStart(firstLine)
        val winEnd = lyt.getLineEnd(lastLine)
        DtsSyntaxHighlighter.applyViewportSyntaxSpans(spannable, winStart, winEnd, palette)
    }

    private fun findFirstMatchAtOrAfter(offset: Int): Int {
        var lo = 0
        var hi = searchMatches.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (searchMatches[mid].endOffset <= offset) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }
        return lo
    }

    private fun recomputeLogicalLineCount(cs: CharSequence?) {
        val count = if (cs.isNullOrEmpty()) {
            1
        } else {
            var n = 1
            for (i in cs.indices) {
                if (cs[i] == '\n') n++
            }
            n
        }
        if (count != cachedLogicalLineCount) {
            cachedLogicalLineCount = count
            updateGutterPadding()
        }
    }

    private fun updateGutterPadding() {
        val digits = cachedLogicalLineCount.coerceAtLeast(1).toString().length.coerceAtLeast(3)
        val sampleText = "9".repeat(digits)
        val textWidth = gutterTextPaint.measureText(sampleText)
        val newGutterWidth = (textWidth + gutterEndPadPx + (10 * density)).toInt()
        if (newGutterWidth != gutterWidthPx || paddingLeft != newGutterWidth + codeLeftGapPx) {
            gutterWidthPx = newGutterWidth
            setPadding(gutterWidthPx + codeLeftGapPx, verticalPadPx, (24 * density).toInt(), bottomPadPx)
        }
    }

    private fun notifyCursorStats() {
        val editable = text
        val total = cachedLogicalLineCount.coerceAtLeast(1)
        if (editable == null) {
            onCursorPositionChanged?.invoke(1, total)
            return
        }
        val sel = selectionStart.coerceIn(0, editable.length)
        val lyt = layout
        val currentLine = if (!isWordWrap && lyt != null && lyt.lineCount > 0) {
            lyt.getLineForOffset(sel) + 1
        } else {
            countLogicalLinesBefore(editable, sel) + 1
        }
        onCursorPositionChanged?.invoke(currentLine.coerceIn(1, total), total)
    }

    private fun countLogicalLinesBefore(cs: CharSequence?, endOffset: Int): Int {
        if (cs == null || endOffset <= 0) return 0
        val safeEnd = endOffset.coerceAtMost(cs.length)
        var count = 0
        for (i in 0 until safeEnd) {
            if (cs[i] == '\n') count++
        }
        return count
    }

    private fun computeMaxScrollY(): Int {
        val lyt = layout ?: return 0
        return (lyt.height + totalPaddingTop + totalPaddingBottom - height).coerceAtLeast(0)
    }

    private fun computeMaxScrollX(): Int {
        val lyt = layout ?: return 0
        val topY = (scrollY - totalPaddingTop).coerceAtLeast(0)
        val botY = (scrollY + height - totalPaddingTop).coerceAtLeast(topY)
        val firstLine = (lyt.getLineForVertical(topY) - 10).coerceIn(0, lyt.lineCount - 1)
        val lastLine = (lyt.getLineForVertical(botY) + 20).coerceIn(firstLine, lyt.lineCount - 1)
        var maxLineRight = 0f
        for (line in firstLine..lastLine) {
            val w = lyt.getLineMax(line)
            if (w > maxLineRight) maxLineRight = w
        }
        return (totalPaddingLeft + maxLineRight.toInt() + paddingRight - width).coerceAtLeast(0)
    }

    private fun findLineStartBefore(cs: Spanned, pos: Int): Int {
        var i = (pos - 1).coerceAtMost(cs.length - 1)
        while (i >= 0) {
            if (cs[i] == '\n') return i + 1
            i--
        }
        return 0
    }

    private fun readLeadingWhitespace(cs: Spanned, lineStart: Int, limit: Int): String {
        val sb = StringBuilder()
        var i = lineStart
        val safeLimit = limit.coerceAtMost(cs.length)
        while (i < safeLimit) {
            val c = cs[i]
            if (c == ' ' || c == '\t') {
                sb.append(c)
                i++
            } else {
                break
            }
        }
        return sb.toString()
    }

    private fun findLastNonSpaceCharBefore(cs: Spanned, lineStart: Int, limit: Int): Char? {
        var i = (limit - 1).coerceAtMost(cs.length - 1)
        while (i >= lineStart) {
            val c = cs[i]
            if (!c.isWhitespace()) return c
            i--
        }
        return null
    }
}
