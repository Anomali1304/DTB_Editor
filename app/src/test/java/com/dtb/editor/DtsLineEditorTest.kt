package com.dtb.editor

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dtb.editor.fdt.cpu.CpuTopologyManager
import com.dtb.editor.fdt.parser.DtbPayloadStructure
import com.dtb.editor.fdt.parser.DtsChunkUndoManager
import com.dtb.editor.fdt.parser.DtsChunker
import com.dtb.editor.fdt.parser.DtsLineEditAction
import com.dtb.editor.fdt.parser.DtsLineUndoManager
import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.fdt.parser.DtsSyntaxAligner
import com.dtb.editor.fdt.parser.DtsTextDeltaUndoManager
import com.dtb.editor.ui.screens.DtsOffsetSearchEngine
import com.dtb.editor.ui.screens.DtsSearchEngine
import com.dtb.editor.ui.screens.DtsSyntaxHighlighter
import com.dtb.editor.ui.screens.DtsSyntaxPalette
import com.dtb.editor.ui.screens.DtsSyntaxSpan
import com.dtb.editor.ui.screens.EditorScreen
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.InputFileType
import com.dtb.editor.viewmodel.OpenFileState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33])
class DtsLineEditorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLineUndoManagerSplitDeleteMergeAndCoalesce() {
        val manager = DtsLineUndoManager(maxStackSize = 50, coalesceWindowMs = 500L)
        val lines = mutableListOf("/ {", "    status = \"okay\";", "};")

        manager.recordAction(DtsLineEditAction(1, listOf("    status = \"okay\";"), listOf("    status = \"o\";")), timestampMs = 1000L)
        lines[1] = "    status = \"o\";"
        manager.recordAction(DtsLineEditAction(1, listOf("    status = \"o\";"), listOf("    status = \"ok\";")), timestampMs = 1200L)
        lines[1] = "    status = \"ok\";"

        val undo1 = manager.undo(lines)
        assertNotNull(undo1)
        assertEquals("    status = \"okay\";", lines[1])
        assertTrue(manager.canRedo)

        manager.redo(lines)
        assertEquals("    status = \"ok\";", lines[1])

        manager.recordAction(
            DtsLineEditAction(1, listOf("    status = \"ok\";"), listOf("    status = \"ok\";", "    new-prop = <0x1>;")),
            timestampMs = 3000L
        )
        lines[1] = "    status = \"ok\";"
        lines.add(2, "    new-prop = <0x1>;")
        assertEquals(4, lines.size)

        manager.undo(lines)
        assertEquals(3, lines.size)
        assertEquals("    status = \"ok\";", lines[1])

        manager.redo(lines)
        assertEquals(4, lines.size)
        assertEquals("    new-prop = <0x1>;", lines[2])
    }

    @Test
    fun testTextDeltaUndoManagerCoalesceUndoAndRedo() {
        val manager = DtsTextDeltaUndoManager(maxStackSize = 50, coalesceWindowMs = 700L)
        val editable = SpannableStringBuilder("/ {\n    status = \"\";\n};")

        // Coalesce typing "okay" inside quotes at offset 18
        val insertAt = editable.indexOf("\"\"") + 1
        editable.insert(insertAt, "o")
        manager.recordChange(insertAt, "", "o", timestampMs = 1000L)
        editable.insert(insertAt + 1, "k")
        manager.recordChange(insertAt + 1, "", "k", timestampMs = 1100L)
        editable.insert(insertAt + 2, "a")
        manager.recordChange(insertAt + 2, "", "a", timestampMs = 1200L)
        editable.insert(insertAt + 3, "y")
        manager.recordChange(insertAt + 3, "", "y", timestampMs = 1300L)

        assertTrue(editable.toString().contains("\"okay\""))

        // Single undo removes entire coalesced typing burst
        val cursorAfterUndo = manager.undo(editable)
        assertEquals(insertAt, cursorAfterUndo)
        assertTrue(editable.toString().contains("\"\""))
        assertTrue(manager.canRedo)

        // Single redo restores "okay"
        val cursorAfterRedo = manager.redo(editable)
        assertEquals(insertAt + 4, cursorAfterRedo)
        assertTrue(editable.toString().contains("\"okay\""))

        // Multi-line paste beyond coalesce window
        val pastePos = editable.indexOf("};")
        val pasted = "    cpu@0 { status = \"okay\"; };\n"
        editable.insert(pastePos, pasted)
        manager.recordChange(pastePos, "", pasted, timestampMs = 5000L)
        assertTrue(editable.toString().contains("cpu@0"))

        val undoPasteCursor = manager.undo(editable)
        assertEquals(pastePos, undoPasteCursor)
        assertFalse(editable.toString().contains("cpu@0"))
    }

    @Test
    fun testOffsetSearchEngineCaseSensitivityAndPerformance() {
        val builder = StringBuilder(400_000)
        for (i in 0 until 10000) {
            when (i) {
                12 -> builder.append("    cpu@0 { compatible = \"arm,cortex-a76\"; };\n")
                5432 -> builder.append("    CPU_CLUSTER_1: cpu-map { core0 { cpu = <&CPU0>; }; };\n")
                9990 -> builder.append("    capacity-dmips-mhz = <1024>;\n")
                else -> builder.append("    dummy-node-$i { reg = <0x0 0x${Integer.toHexString(i)}>; };\n")
            }
        }
        val fullText = builder.toString()

        val insensitive = DtsOffsetSearchEngine.findAllMatches(fullText, "cpu", matchCase = false)
        assertEquals(5, insensitive.size)
        assertEquals(12, insensitive[0].lineIndex)
        assertEquals("cpu", fullText.substring(insensitive[0].startOffset, insensitive[0].endOffset))
        assertEquals(5432, insensitive[1].lineIndex)

        val sensitive = DtsOffsetSearchEngine.findAllMatches(fullText, "CPU", matchCase = true)
        assertEquals(2, sensitive.size)
        assertEquals(5432, sensitive[0].lineIndex)
        assertEquals("CPU", fullText.substring(sensitive[0].startOffset, sensitive[0].endOffset))
    }

    @Test
    fun testSearchEngineCaseSensitivityAndPerformance() {
        val lines = ArrayList<String>(10000)
        for (i in 0 until 10000) {
            lines.add(
                when (i) {
                    12 -> "    cpu@0 { compatible = \"arm,cortex-a76\"; };"
                    5432 -> "    CPU_CLUSTER_1: cpu-map { core0 { cpu = <&CPU0>; }; };"
                    9990 -> "    capacity-dmips-mhz = <1024>;"
                    else -> "    dummy-node-$i { reg = <0x0 0x${Integer.toHexString(i)}>; };"
                }
            )
        }

        val insensitive = DtsSearchEngine.findAllMatches(lines, "cpu", matchCase = false)
        assertEquals(5, insensitive.size)
        assertEquals(12, insensitive[0].lineIndex)
        assertEquals(5432, insensitive[1].lineIndex)

        val sensitive = DtsSearchEngine.findAllMatches(lines, "CPU", matchCase = true)
        assertEquals(2, sensitive.size)
        assertEquals(5432, sensitive[0].lineIndex)
    }

    @Test
    fun testSyntaxHighlighterViewportSpansPreservesNonSyntaxSpans() {
        val palette = DtsSyntaxPalette(
            text = Color.White,
            comment = Color.Gray,
            stringLiteral = Color.Green,
            propertyKey = Color.Cyan,
            nodeTitle = Color.Yellow,
            hexOrNumber = Color.Magenta,
            directive = Color.Blue,
            punctuation = Color.LightGray,
            matchHighlightBg = Color.DarkGray,
            activeMatchHighlightBg = Color.Red,
            activeMatchHighlightText = Color.White
        )

        val text = "/dts-v1/;\n// full line comment\ncompatible = \"arm,cortex-a76\";\nreg = <0x0 0x11bc10>;"
        val spannable = SpannableStringBuilder(text)
        val imeSpan = UnderlineSpan()
        spannable.setSpan(imeSpan, 0, 5, Spanned.SPAN_COMPOSING)

        DtsSyntaxHighlighter.applyViewportSyntaxSpans(spannable, 0, spannable.length, palette)

        assertTrue(spannable.getSpans(0, spannable.length, DtsSyntaxSpan::class.java).isNotEmpty())
        assertEquals(1, spannable.getSpans(0, spannable.length, UnderlineSpan::class.java).size)
    }

    @Test
    fun testEditorScreenShowsFullLinesWithoutPartsAndSearchFindsMatches() {
        val builder = StringBuilder()
        builder.append("/dts-v1/;\n/ {\n")
        for (i in 1..2500) {
            if (i == 1800) {
                builder.append("    target_marker_node { status = \"okay\"; };\n")
            } else {
                builder.append("    node_$i { reg = <0x0 0x${Integer.toHexString(i)}>; };\n")
            }
        }
        builder.append("};\n")

        val textStr = builder.toString()
        val parseResult = DtsParser().parse(textStr)
        val topology = CpuTopologyManager().extractTopology(parseResult.rootNode)

        val state = OpenFileState(
            fileName = "mt6789.dts",
            fileType = InputFileType.RAW_FDT,
            originalBytes = textStr.toByteArray(),
            hasAvbFooter = false,
            vendorBootImage = null,
            dtbPayloadStructure = DtbPayloadStructure.SingleFdt(textStr.toByteArray()),
            dtbEntries = emptyList(),
            selectedEntryIndex = 0,
            originalDtsText = textStr,
            currentDtsText = textStr,
            chunks = DtsChunker.split(textStr),
            undoManager = DtsChunkUndoManager(),
            isModified = false,
            parsedAst = parseResult.rootNode,
            cpuTopology = topology
        )

        val viewModel = DtbViewModel()

        composeTestRule.setContent {
            EditorScreen(fileState = state, viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // Shows native full-document editor and line count without parts
        composeTestRule.onNodeWithTag("dts_native_editor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dts_line_count_chip").assertIsDisplayed()
        composeTestRule.onNodeWithText("${state.dtsLines.size} lines").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dts_wrap_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dts_format_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dts_undo_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dts_redo_button").assertIsDisplayed()

        // Open search bar and search for the marker on line 1802
        composeTestRule.onNodeWithTag("dts_search_toggle").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dts_search_input").performTextInput("target_marker_node")
        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("1 / 1").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("dts_search_counter").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 / 1").assertIsDisplayed()
    }

    @Test
    fun testDtsSyntaxAlignerPreservesCommentsAndAlignsIndentation() {
        val messy = """
/dts-v1/;

/ {
cpu@0{
compatible="arm,cortex-a76"; // keep comment { untouched }
  reg = <0x0 0x100>;
opp-table {
opp-1000 {
opp-hz = /bits/ 64 <1000000000>;
};
};
};
};
""".trimIndent() + "\n"

        val aligned = DtsSyntaxAligner.align(messy)
        val expected = """
/dts-v1/;

/ {
    cpu@0 {
        compatible = "arm,cortex-a76"; // keep comment { untouched }
        reg = <0x0 0x100>;
        opp-table {
            opp-1000 {
                opp-hz = /bits/ 64 <1000000000>;
            };
        };
    };
};
""".trimIndent() + "\n"

        assertEquals(expected, aligned)
    }
}
