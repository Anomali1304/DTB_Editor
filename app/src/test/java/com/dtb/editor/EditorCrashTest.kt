package com.dtb.editor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dtb.editor.fdt.cpu.CpuTopologyManager
import com.dtb.editor.fdt.parser.DtbPayloadStructure
import com.dtb.editor.fdt.parser.DtsChunkUndoManager
import com.dtb.editor.fdt.parser.DtsChunker
import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.ui.screens.EditorScreen
import com.dtb.editor.viewmodel.DtbViewModel
import com.dtb.editor.viewmodel.InputFileType
import com.dtb.editor.viewmodel.OpenFileState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE, sdk = [33])
class EditorCrashTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testEditorWithLargeDtsTextDoesNotCrash() {
        val largeText = StringBuilder()
        for (i in 1..7000) {
            largeText.append("/node_$i { compatible = \"vendor,device-$i\"; reg = <0x0 0x${Integer.toHexString(i * 16)} 0x0 0x100>; };\n")
        }
        val textStr = largeText.toString()
        val parseResult = DtsParser().parse(textStr)
        val topology = CpuTopologyManager().extractTopology(parseResult.rootNode)
        val chunks = DtsChunker.split(textStr)
        val undoManager = DtsChunkUndoManager()

        val state = OpenFileState(
            fileName = "stock_mt6789.dts",
            fileType = InputFileType.RAW_FDT,
            originalBytes = textStr.toByteArray(),
            hasAvbFooter = false,
            vendorBootImage = null,
            dtbPayloadStructure = DtbPayloadStructure.SingleFdt(textStr.toByteArray()),
            dtbEntries = emptyList(),
            selectedEntryIndex = 0,
            originalDtsText = textStr,
            currentDtsText = textStr,
            chunks = chunks,
            undoManager = undoManager,
            isModified = false,
            parsedAst = parseResult.rootNode,
            cpuTopology = topology
        )

        val viewModel = DtbViewModel()

        composeTestRule.setContent {
            EditorScreen(fileState = state, viewModel = viewModel)
        }

        composeTestRule.waitForIdle()
    }
}
