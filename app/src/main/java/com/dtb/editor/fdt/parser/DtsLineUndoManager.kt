package com.dtb.editor.fdt.parser

data class DtsLineEditAction(
    val startLine: Int,
    val oldLines: List<String>,
    val newLines: List<String>,
    val focusLineAfterUndo: Int = startLine,
    val focusLineAfterRedo: Int = (startLine + newLines.size - 1).coerceAtLeast(0)
)

class DtsLineUndoManager(
    private val maxStackSize: Int = 150,
    private val coalesceWindowMs: Long = 750L
) {
    private val undoStack = mutableListOf<DtsLineEditAction>()
    private val redoStack = mutableListOf<DtsLineEditAction>()
    private var lastActionTimeMs: Long = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun recordAction(action: DtsLineEditAction, timestampMs: Long = System.currentTimeMillis()) {
        if (action.oldLines == action.newLines) return

        val last = undoStack.lastOrNull()
        val canCoalesce = last != null &&
            (timestampMs - lastActionTimeMs) <= coalesceWindowMs &&
            last.oldLines.size == 1 &&
            last.newLines.size == 1 &&
            action.oldLines.size == 1 &&
            action.newLines.size == 1 &&
            last.startLine == action.startLine &&
            last.newLines[0] == action.oldLines[0]

        if (canCoalesce) {
            undoStack[undoStack.lastIndex] = last!!.copy(newLines = action.newLines)
        } else {
            undoStack.add(action)
            if (undoStack.size > maxStackSize) {
                undoStack.removeAt(0)
            }
        }
        lastActionTimeMs = timestampMs
        redoStack.clear()
    }

    fun undo(lines: MutableList<String>): DtsLineEditAction? {
        if (!canUndo) return null
        val action = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(action)
        applySlice(lines, action.startLine, action.newLines.size, action.oldLines)
        lastActionTimeMs = 0L
        return action
    }

    fun redo(lines: MutableList<String>): DtsLineEditAction? {
        if (!canRedo) return null
        val action = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(action)
        applySlice(lines, action.startLine, action.oldLines.size, action.newLines)
        lastActionTimeMs = 0L
        return action
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastActionTimeMs = 0L
    }

    private fun applySlice(
        lines: MutableList<String>,
        startLine: Int,
        removeCount: Int,
        insertLines: List<String>
    ) {
        val safeStart = startLine.coerceIn(0, lines.size)
        val safeRemove = removeCount.coerceAtMost((lines.size - safeStart).coerceAtLeast(0))
        repeat(safeRemove) {
            lines.removeAt(safeStart)
        }
        lines.addAll(safeStart, insertLines)
        if (lines.isEmpty()) {
            lines.add("")
        }
    }
}
