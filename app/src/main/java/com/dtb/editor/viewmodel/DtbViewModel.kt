package com.dtb.editor.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtb.editor.dtc.DtcEngine
import com.dtb.editor.dtc.DtcEngineImpl
import com.dtb.editor.fdt.SampleDtbProvider
import com.dtb.editor.fdt.cpu.CpuTopologyManager
import com.dtb.editor.fdt.cpu.CpuTopologyState
import com.dtb.editor.fdt.diff.DiffSummary
import com.dtb.editor.fdt.diff.DtsDiffEngine
import com.dtb.editor.fdt.model.CompilationOutput
import com.dtb.editor.fdt.model.DtbFileType
import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.parser.DtsFormatter
import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.fdt.parser.FdtBinaryCompiler
import com.dtb.editor.fdt.parser.FdtBinaryParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ActiveWorkspace(
    val fileName: String,
    val fileType: DtbFileType,
    val originalSizeBytes: Long,
    val originalSourceText: String,
    val sourceText: String,
    val originalBytes: ByteArray? = null,
    val isDirty: Boolean = false,
    val parsedAst: FdtNode? = null
)

enum class WorkspaceTab {
    SOURCE,
    TREE,
    BINARY,
    TOPOLOGY,
    COMPARE,
    DIAGNOSTICS
}

enum class ExportTargetType {
    EDITED_DTS,
    COMPILED_DTB,
    ORIGINAL_DTB,
    DIFF_REPORT
}

class DtbViewModel(
    private val dtcEngine: DtcEngine = DtcEngineImpl(),
    private val topologyManager: CpuTopologyManager = CpuTopologyManager(),
    private val diffEngine: DtsDiffEngine = DtsDiffEngine()
) : ViewModel() {

    private val _workspace = MutableStateFlow<ActiveWorkspace?>(null)
    val workspace: StateFlow<ActiveWorkspace?> = _workspace.asStateFlow()

    private val _activeTab = MutableStateFlow(WorkspaceTab.SOURCE)
    val activeTab: StateFlow<WorkspaceTab> = _activeTab.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow("Ready. Import a DTS or DTB file.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _compilerOutput = MutableStateFlow<CompilationOutput?>(null)
    val compilerOutput: StateFlow<CompilationOutput?> = _compilerOutput.asStateFlow()

    private val _cpuTopology = MutableStateFlow<CpuTopologyState?>(null)
    val cpuTopology: StateFlow<CpuTopologyState?> = _cpuTopology.asStateFlow()

    private val _diffSummary = MutableStateFlow<DiffSummary?>(null)
    val diffSummary: StateFlow<DiffSummary?> = _diffSummary.asStateFlow()

    private val _diagnosticsLog = MutableStateFlow<List<String>>(emptyList())
    val diagnosticsLog: StateFlow<List<String>> = _diagnosticsLog.asStateFlow()

    // Undo / Redo history
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    private var debounceParseJob: Job? = null

    fun selectTab(tab: WorkspaceTab) {
        _activeTab.value = tab
    }

    fun loadSampleFile() {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Decompiling sample Qualcomm SM8450 DTB..."

            withContext(Dispatchers.IO) {
                try {
                    val sampleBytes = SampleDtbProvider.getSampleDtbBytes()
                    val decompileRes = dtcEngine.decompileBytes(sampleBytes).getOrThrow()

                    val ast = DtsParser().parse(decompileRes.dtsText).rootNode

                    val ws = ActiveWorkspace(
                        fileName = "sample_sm8450.dtb",
                        fileType = DtbFileType.DTB,
                        originalSizeBytes = sampleBytes.size.toLong(),
                        originalSourceText = decompileRes.dtsText,
                        sourceText = decompileRes.dtsText,
                        originalBytes = sampleBytes,
                        isDirty = false,
                        parsedAst = ast
                    )

                    _workspace.value = ws
                    _cpuTopology.value = topologyManager.extractTopology(ast)
                    _diffSummary.value = diffEngine.compareNodes(ast, ast)
                    _activeTab.value = WorkspaceTab.SOURCE
                    _statusMessage.value = "Loaded sample_sm8450.dtb (decompiled to DTS)"
                    _diagnosticsLog.value = listOf(decompileRes.log)
                } catch (e: Exception) {
                    _statusMessage.value = "Sample load error: ${e.message}"
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun importUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Reading file..."

            withContext(Dispatchers.IO) {
                try {
                    val (fileName, size) = queryFileInfo(context, uri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open file stream for: $uri")

                    val bytes = inputStream.use { it.readBytes() }

                    val isBinary = bytes.size >= 4 && isDtbMagic(bytes)
                    val isDtsFile = fileName.endsWith(".dts", ignoreCase = true) && !isBinary

                    val logs = mutableListOf<String>()

                    val ws = if (isDtsFile) {
                        // Import raw DTS text directly
                        val dtsText = String(bytes, Charsets.UTF_8)
                        val parseResult = DtsParser().parse(dtsText)
                        logs.add("[DTS Import] Loaded $fileName (${bytes.size} bytes)")
                        if (parseResult.warnings.isNotEmpty()) {
                            logs.addAll(parseResult.warnings.map { "WARNING: $it" })
                        }

                        ActiveWorkspace(
                            fileName = fileName,
                            fileType = DtbFileType.DTS,
                            originalSizeBytes = bytes.size.toLong(),
                            originalSourceText = dtsText,
                            sourceText = dtsText,
                            originalBytes = null,
                            isDirty = false,
                            parsedAst = parseResult.rootNode
                        )
                    } else {
                        // Decompile binary DTB/DTBO
                        val fileType = if (fileName.endsWith(".dtbo", ignoreCase = true)) DtbFileType.DTBO else DtbFileType.DTB
                        val decompileRes = dtcEngine.decompileBytes(bytes).getOrThrow()
                        logs.add(decompileRes.log)

                        ActiveWorkspace(
                            fileName = fileName,
                            fileType = fileType,
                            originalSizeBytes = bytes.size.toLong(),
                            originalSourceText = decompileRes.dtsText,
                            sourceText = decompileRes.dtsText,
                            originalBytes = bytes,
                            isDirty = false,
                            parsedAst = decompileRes.rootNode
                        )
                    }

                    _workspace.value = ws
                    _cpuTopology.value = topologyManager.extractTopology(ws.parsedAst ?: FdtNode("/", "/"))
                    _diffSummary.value = diffEngine.compareNodes(ws.parsedAst ?: FdtNode("/", "/"), ws.parsedAst ?: FdtNode("/", "/"))
                    _diagnosticsLog.value = logs
                    _activeTab.value = WorkspaceTab.SOURCE
                    _statusMessage.value = "Opened $fileName (${ws.sourceText.lines().size} lines)"
                } catch (e: Exception) {
                    _statusMessage.value = "Import error: ${e.message}"
                    _diagnosticsLog.value = listOf("ERROR: ${e.message}")
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun updateSourceText(newText: String) {
        val current = _workspace.value ?: return
        if (current.sourceText != newText) {
            undoStack.add(current.sourceText)
            redoStack.clear()
            val isDirty = newText != current.originalSourceText
            _workspace.value = current.copy(sourceText = newText, isDirty = isDirty)

            // Trigger debounced background parsing (300ms delay)
            debounceParseJob?.cancel()
            debounceParseJob = viewModelScope.launch(Dispatchers.Default) {
                delay(300)
                try {
                    val parsed = DtsParser().parse(newText)
                    val origAst = DtsParser().parse(current.originalSourceText).rootNode
                    _workspace.value = _workspace.value?.copy(parsedAst = parsed.rootNode)
                    _cpuTopology.value = topologyManager.extractTopology(parsed.rootNode)
                    _diffSummary.value = diffEngine.compareNodes(origAst, parsed.rootNode)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _workspace.value ?: return
        redoStack.add(current.sourceText)
        val prev = undoStack.removeAt(undoStack.lastIndex)
        _workspace.value = current.copy(sourceText = prev, isDirty = (prev != current.originalSourceText))
        updateSourceText(prev)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _workspace.value ?: return
        undoStack.add(current.sourceText)
        val next = redoStack.removeAt(redoStack.lastIndex)
        _workspace.value = current.copy(sourceText = next, isDirty = (next != current.originalSourceText))
        updateSourceText(next)
    }

    fun compile() {
        val current = _workspace.value ?: run {
            _statusMessage.value = "No file open to compile"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Compiling DTS with Device Tree Compiler..."

            withContext(Dispatchers.Default) {
                val output = dtcEngine.compileDtsText(current.sourceText)
                _compilerOutput.value = output
                _diagnosticsLog.value = listOf(output.log)
                _isProcessing.value = false

                if (output.success) {
                    _statusMessage.value = "Compilation Succeeded: ${output.outputSizeBytes} bytes"
                } else {
                    _statusMessage.value = "Compilation Failed. See Diagnostics tab."
                    _activeTab.value = WorkspaceTab.DIAGNOSTICS
                }
            }
        }
    }

    fun applyCpuPreset(preset: List<Int>) {
        val current = _workspace.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.Default) {
                val parsed = DtsParser().parse(current.sourceText)
                val (success, msg) = topologyManager.applyPreset(parsed.rootNode, preset)
                if (success) {
                    val formattedDts = DtsFormatter().format(parsed.rootNode, parsed.reservations)
                    updateSourceText(formattedDts)
                    _statusMessage.value = msg
                } else {
                    _statusMessage.value = "Topology Error: $msg"
                }
                _isProcessing.value = false
            }
        }
    }

    fun saveWorkspace() {
        val current = _workspace.value ?: return
        _workspace.value = current.copy(originalSourceText = current.sourceText, isDirty = false)
        _statusMessage.value = "Saved workspace changes."
    }

    fun exportFile(context: Context, targetUri: Uri, exportType: ExportTargetType) {
        val current = _workspace.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(targetUri)
                        ?: throw Exception("Cannot open stream for: $targetUri")

                    stream.use { out ->
                        when (exportType) {
                            ExportTargetType.EDITED_DTS -> {
                                out.write(current.sourceText.toByteArray(Charsets.UTF_8))
                            }
                            ExportTargetType.COMPILED_DTB -> {
                                val output = dtcEngine.compileDtsText(current.sourceText)
                                if (!output.success || output.compiledBytes == null) {
                                    throw Exception("Cannot export corrupt DTB: Compilation failed")
                                }
                                out.write(output.compiledBytes)
                            }
                            ExportTargetType.ORIGINAL_DTB -> {
                                val bytes = current.originalBytes
                                    ?: FdtBinaryCompiler().compile(
                                        DtsParser().parse(current.originalSourceText).rootNode
                                    )
                                out.write(bytes)
                            }
                            ExportTargetType.DIFF_REPORT -> {
                                val report = buildDiffReport(current)
                                out.write(report.toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                    _statusMessage.value = "Exported file to $targetUri"
                } catch (e: Exception) {
                    _statusMessage.value = "Export failed: ${e.message}"
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    private fun buildDiffReport(ws: ActiveWorkspace): String {
        val diff = _diffSummary.value ?: return "No diff summary."
        return buildString {
            appendLine("=== DTB Studio Workspace Diff Report ===")
            appendLine("File: ${ws.fileName}")
            appendLine("Summary: +${diff.addedCount} added, -${diff.removedCount} removed, ~${diff.modifiedCount} modified\n")
            for (item in diff.items) {
                appendLine("[${item.type}] ${item.path}")
                appendLine("  ${item.description}")
                if (item.details.isNotEmpty()) appendLine("  ${item.details}")
                appendLine()
            }
        }
    }

    private fun isDtbMagic(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        val magic = ((bytes[0].toInt() and 0xFF) shl 24) or
                ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or
                (bytes[3].toInt() and 0xFF)
        return magic == 0xd00dfeed.toInt()
    }

    private fun queryFileInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = "device_tree.dts"
        var size = 0L
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx != -1) name = it.getString(nameIdx) ?: name
                if (sizeIdx != -1) size = it.getLong(sizeIdx)
            }
        }
        return Pair(name, size)
    }
}
