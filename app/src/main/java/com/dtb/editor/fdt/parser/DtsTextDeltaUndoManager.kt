package com.dtb.editor.fdt.parser

import android.text.Editable

data class DtsTextDelta(
    val start: Int,
    val oldText: String,
    val newText: String
)

class DtsTextDeltaUndoManager(
    private val maxStackSize: Int = 150,
    private val coalesceWindowMs: Long = 700L
) {
    private val undoStack = mutableListOf<DtsTextDelta>()
    private val redoStack = mutableListOf<DtsTextDelta>()
    private var lastEditTimeMs: Long = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun recordChange(
        start: Int,
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
            start == last.start + last.newText.length

        val canCoalesceBackspace = last != null &&
            (timestampMs - lastEditTimeMs) <= coalesceWindowMs &&
            last.newText.isEmpty() &&
            last.oldText.length in 1..64 &&
            newText.isEmpty() &&
            oldText.length == 1 &&
            !oldText[0].isWhitespace() &&
            start + 1 == last.start

        if (canCoalesce) {
            undoStack[undoStack.lastIndex] = last!!.copy(newText = last.newText + newText)
        } else if (canCoalesceBackspace) {
            undoStack[undoStack.lastIndex] = last!!.copy(
                start = start,
                oldText = oldText + last.oldText
            )
        } else {
            undoStack.add(DtsTextDelta(start, oldText, newText))
            if (undoStack.size > maxStackSize) {
                undoStack.removeAt(0)
            }
        }
        lastEditTimeMs = timestampMs
        redoStack.clear()
    }

    /**
     * Applies one undo operation to the given Editable and returns the restored cursor offset,
     * or null if undo stack is empty.
     */
    fun undo(editable: Editable): Int? {
        if (!canUndo) return null
        val delta = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(delta)
        val safeStart = delta.start.coerceIn(0, editable.length)
        val safeEnd = (delta.start + delta.newText.length).coerceIn(safeStart, editable.length)
        editable.replace(safeStart, safeEnd, delta.oldText)
        lastEditTimeMs = 0L
        return (safeStart + delta.oldText.length).coerceIn(0, editable.length)
    }

    /**
     * Applies one redo operation to the given Editable and returns the restored cursor offset,
     * or null if redo stack is empty.
     */
    fun redo(editable: Editable): Int? {
        if (!canRedo) return null
        val delta = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(delta)
        val safeStart = delta.start.coerceIn(0, editable.length)
        val safeEnd = (delta.start + delta.oldText.length).coerceIn(safeStart, editable.length)
        editable.replace(safeStart, safeEnd, delta.newText)
        lastEditTimeMs = 0L
        return (safeStart + delta.newText.length).coerceIn(0, editable.length)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastEditTimeMs = 0L
    }
}
