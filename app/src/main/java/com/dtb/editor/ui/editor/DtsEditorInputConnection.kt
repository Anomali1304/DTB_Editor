package com.dtb.editor.ui.editor

import android.text.Editable
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest

/**
 * High-performance InputConnection backed directly by [DtsDocumentBuffer].
 * Avoids Editable / DynamicLayout overhead while supporting Gboard/IME composing text,
 * swipe typing, backspace/delete across lines, Enter auto-indent, and selection sync.
 */
class DtsEditorInputConnection(
    private val editorView: DtsCodeEditorView
) : BaseInputConnection(editorView, true) {

    private val dummyEditable: Editable by lazy { Editable.Factory.getInstance().newEditable("") }

    override fun getEditable(): Editable = dummyEditable

    override fun beginBatchEdit(): Boolean {
        editorView.beginImeBatchEdit()
        return true
    }

    override fun endBatchEdit(): Boolean {
        editorView.endImeBatchEdit()
        return true
    }

    override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
        if (n <= 0) return ""
        val selStart = editorView.getSelectionStartOffset()
        val from = (selStart - n).coerceAtLeast(0)
        return editorView.buffer.extractRange(from, selStart)
    }

    override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
        if (n <= 0) return ""
        val selEnd = editorView.getSelectionEndOffset()
        val to = (selEnd + n).coerceAtMost(editorView.buffer.totalLength)
        return editorView.buffer.extractRange(selEnd, to)
    }

    override fun getSelectedText(flags: Int): CharSequence? {
        val start = editorView.getSelectionStartOffset()
        val end = editorView.getSelectionEndOffset()
        if (start >= end) return null
        return editorView.buffer.extractRange(start, end)
    }

    override fun getCursorCapsMode(reqModes: Int): Int = 0

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
        val total = editorView.buffer.totalLength
        val selStart = editorView.getSelectionStartOffset()
        val selEnd = editorView.getSelectionEndOffset()
        val winStart = (selStart - 1024).coerceAtLeast(0)
        val winEnd = (selEnd + 1024).coerceAtMost(total)

        return ExtractedText().apply {
            text = editorView.buffer.extractRange(winStart, winEnd)
            startOffset = winStart
            selectionStart = selStart - winStart
            selectionEnd = selEnd - winStart
            partialStartOffset = -1
            partialEndOffset = -1
        }
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        editorView.imeCommitText(text?.toString() ?: "", newCursorPosition)
        return true
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        editorView.imeSetComposingText(text?.toString() ?: "", newCursorPosition)
        return true
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        editorView.imeSetComposingRegion(start, end)
        return true
    }

    override fun finishComposingText(): Boolean {
        editorView.imeFinishComposingText()
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        editorView.imeDeleteSurroundingText(beforeLength, afterLength)
        return true
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        val selStart = editorView.getSelectionStartOffset()
        val selEnd = editorView.getSelectionEndOffset()

        var charsBefore = 0
        if (beforeLength > 0 && selStart > 0) {
            val window = editorView.buffer.extractRange((selStart - beforeLength * 2 - 4).coerceAtLeast(0), selStart)
            var idx = window.length
            var cpCount = 0
            while (idx > 0 && cpCount < beforeLength) {
                val c = window[idx - 1]
                idx--
                if (Character.isLowSurrogate(c) && idx > 0 && Character.isHighSurrogate(window[idx - 1])) {
                    idx--
                }
                cpCount++
            }
            charsBefore = window.length - idx
        }

        var charsAfter = 0
        val totalLen = editorView.buffer.totalLength
        if (afterLength > 0 && selEnd < totalLen) {
            val window = editorView.buffer.extractRange(selEnd, (selEnd + afterLength * 2 + 4).coerceAtMost(totalLen))
            var idx = 0
            var cpCount = 0
            while (idx < window.length && cpCount < afterLength) {
                val c = window[idx]
                idx++
                if (Character.isHighSurrogate(c) && idx < window.length && Character.isLowSurrogate(window[idx])) {
                    idx++
                }
                cpCount++
            }
            charsAfter = idx
        }

        editorView.imeDeleteSurroundingText(charsBefore, charsAfter)
        return true
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        editorView.setSelectionOffsets(start, end, showHandles = start != end)
        return true
    }

    override fun performContextMenuAction(id: Int): Boolean {
        return when (id) {
            android.R.id.selectAll -> {
                editorView.selectAllText()
                true
            }
            android.R.id.cut -> {
                editorView.cutSelection()
                true
            }
            android.R.id.copy -> {
                editorView.copySelection()
                true
            }
            android.R.id.paste, android.R.id.pasteAsPlainText -> {
                editorView.pasteFromClipboard()
                true
            }
            android.R.id.undo -> {
                editorView.performUndo()
            }
            android.R.id.redo -> {
                editorView.performRedo()
            }
            else -> super.performContextMenuAction(id)
        }
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        if (editorView.handleEditorKeyEvent(event)) {
            return true
        }
        return super.sendKeyEvent(event)
    }
}
