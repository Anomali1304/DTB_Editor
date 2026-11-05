package com.dtb.editor

import com.dtb.editor.fdt.parser.ChunkEditAction
import com.dtb.editor.fdt.parser.DtsChunkUndoManager
import com.dtb.editor.fdt.parser.DtsChunker
import org.junit.Assert.*
import org.junit.Test

class DtsChunkerTest {

    @Test
    fun testLosslessSplitAndJoin() {
        val original = "line1\nline2\r\n\nline4\n"
        val chunks = DtsChunker.split(original)
        val joined = DtsChunker.join(chunks)
        assertEquals(original, joined)
    }

    @Test
    fun testChunkUndoRedoAndLimit() {
        val undoManager = DtsChunkUndoManager(maxStackSize = 5)
        val chunks = mutableListOf("chunk0_v0", "chunk1_v0")

        // Record 6 actions to test max stack size 5
        for (i in 1..6) {
            val old = chunks[0]
            val nw = "chunk0_v$i"
            undoManager.recordAction(ChunkEditAction(0, old, nw))
            chunks[0] = nw
        }

        assertEquals("chunk0_v6", chunks[0])
        assertTrue(undoManager.canUndo)

        // Undo 5 times (since max stack is 5, oldest action v0->v1 was dropped)
        var undoneCount = 0
        while (undoManager.canUndo) {
            undoManager.undo(chunks)
            undoneCount++
        }
        assertEquals(5, undoneCount)
        assertEquals("chunk0_v1", chunks[0])

        // Redo once
        undoManager.redo(chunks)
        assertEquals("chunk0_v2", chunks[0])
    }
}
