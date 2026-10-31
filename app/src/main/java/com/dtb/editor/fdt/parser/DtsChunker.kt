package com.dtb.editor.fdt.parser

data class ChunkEditAction(
    val chunkIndex: Int,
    val oldText: String,
    val newText: String
)

class DtsChunkUndoManager(
    private val maxStackSize: Int = 100
) {
    private val undoStack = mutableListOf<ChunkEditAction>()
    private val redoStack = mutableListOf<ChunkEditAction>()

    fun recordAction(action: ChunkEditAction) {
        if (action.oldText == action.newText) return
        undoStack.add(action)
        if (undoStack.size > maxStackSize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun undo(currentChunks: MutableList<String>): ChunkEditAction? {
        if (!canUndo) return null
        val action = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(action)
        if (action.chunkIndex in currentChunks.indices) {
            currentChunks[action.chunkIndex] = action.oldText
        }
        return action
    }

    fun redo(currentChunks: MutableList<String>): ChunkEditAction? {
        if (!canRedo) return null
        val action = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(action)
        if (action.chunkIndex in currentChunks.indices) {
            currentChunks[action.chunkIndex] = action.newText
        }
        return action
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

object DtsChunker {
    const val LINES_PER_CHUNK = 100

    fun split(text: String): List<String> {
        if (text.isEmpty()) return listOf("")
        val lineEnding = if (text.contains("\r\n")) "\r\n" else "\n"
        val rawLines = text.split(lineEnding)
        val chunks = mutableListOf<String>()
        for (i in rawLines.indices step LINES_PER_CHUNK) {
            val end = (i + LINES_PER_CHUNK).coerceAtMost(rawLines.size)
            val chunkLines = rawLines.subList(i, end)
            chunks.add(chunkLines.joinToString(lineEnding))
        }
        if (chunks.isEmpty()) chunks.add("")
        return chunks
    }

    fun join(chunks: List<String>, lineEnding: String = "\n"): String {
        return chunks.joinToString(lineEnding)
    }
}
