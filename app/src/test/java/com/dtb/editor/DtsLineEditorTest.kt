package com.dtb.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.dtb.editor.ui.screens.DtsSearchEngine
import com.dtb.editor.ui.screens.DtsSyntaxHighlighter
import com.dtb.editor.ui.screens.DtsSyntaxPalette
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

        // Coalesced typing on line 1 within 500ms
        manager.recordAction(DtsLineEditAction(1, listOf("    status = \"okay\";"), listOf("    status = \"o\";")), timestampMs = 1000L)
        lines[1] = "    status = \"o\";"
        manager.recordAction(DtsLineEditAction(1, listOf("    status = \"o\";"), listOf("    status = \"ok\";")), timestampMs = 1200L)
        lines[1] = "    status = \"ok\";"

        // Single undo reverts entire coalesced burst back to "okay"
        val undo1 = manager.undo(lines)
        assertNotNull(undo1)
        assertEquals("    status = \"okay\";", lines[1])
        assertTrue(manager.canRedo)

        // Redo restores "ok"
        manager.redo(lines)
        assertEquals("    status = \"ok\";", lines[1])

        // Split line (Enter) at line 1 -> becomes 2 lines
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
        // Line 12: "cpu@0" -> 1 match; Line 5432: "CPU_CLUSTER_1", "cpu-map", "cpu", "CPU0" -> 4 matches
        assertEquals(5, insensitive.size)
        assertEquals(12, insensitive[0].lineIndex)
        assertEquals(5432, insensitive[1].lineIndex)

        val sensitive = DtsSearchEngine.findAllMatches(lines, "CPU", matchCase = true)
        assertEquals(2, sensitive.size)
        assertEquals(5432, sensitive[0].lineIndex)
    }

    @Test
    fun testSyntaxHighlighterHandlesEdgeCases() {
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

        val samples = listOf(
            "",
            "/dts-v1/;",
            "// full line comment",
            "/* unclosed block comment",
            "compatible = \"unclosed string",
            "reg = <0x0 0x11bc10 0x0 0x120>;",
            "performance-controller@11bc00 {"
        )

        for ((idx, line) in samples.withIndex()) {
            val match = if (line.length >= 3) DtsSearchEngine.findAllMatches(listOf(line), line.take(3), false).firstOrNull()?.copy(lineIndex = idx) else null
            val annotated = DtsSyntaxHighlighter.buildHighlightedLine(
                line = line,
                palette = palette,
                lineMatches = listOfNotNull(match),
                activeMatch = match
            )
            assertEquals(line, annotated.text)
        }
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

        // Shows total lines directly without dividing into parts
        composeTestRule.onNodeWithTag("dts_line_count_chip").assertIsDisplayed()
        composeTestRule.onNodeWithText("${state.dtsLines.size} lines").assertIsDisplayed()

        // Open search bar and search for the marker on line 1802
        composeTestRule.onNodeWithTag("dts_search_toggle").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dts_search_input").performTextInput("target_marker_node")
        composeTestRule.mainClock.advanceTimeBy(150)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("dts_search_counter").assertIsDisplayed()
    }
}
