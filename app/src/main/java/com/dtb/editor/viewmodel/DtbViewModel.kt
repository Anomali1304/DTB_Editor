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
import com.dtb.editor.fdt.model.DtbProject
import com.dtb.editor.fdt.parser.DtsFormatter
import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.fdt.parser.FdtBinaryCompiler
import com.dtb.editor.fdt.parser.FdtBinaryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class RecentFileInfo(
    val name: String,
    val sizeBytes: Long,
    val type: DtbFileType,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ExportType {
    ORIGINAL_DTB,
    MODIFIED_DTB,
    ORIGINAL_DTS,
    MODIFIED_DTS,
    DIFF_REPORT
}

class DtbViewModel(
    private val dtcEngine: DtcEngine = DtcEngineImpl(),
    private val topologyManager: CpuTopologyManager = CpuTopologyManager(),
    private val diffEngine: DtsDiffEngine = DtsDiffEngine()
) : ViewModel() {

    private val _project = MutableStateFlow<DtbProject?>(null)
    val project: StateFlow<DtbProject?> = _project.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>("Ready. Import a DTB/DTBO file to begin.")
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _compilerOutput = MutableStateFlow<CompilationOutput?>(null)
    val compilerOutput: StateFlow<CompilationOutput?> = _compilerOutput.asStateFlow()

    private val _cpuTopology = MutableStateFlow<CpuTopologyState?>(null)
    val cpuTopology: StateFlow<CpuTopologyState?> = _cpuTopology.asStateFlow()

    private val _diffSummary = MutableStateFlow<DiffSummary?>(null)
    val diffSummary: StateFlow<DiffSummary?> = _diffSummary.asStateFlow()

    private val _recentFiles = MutableStateFlow<List<RecentFileInfo>>(emptyList())
    val recentFiles: StateFlow<List<RecentFileInfo>> = _recentFiles.asStateFlow()

    // Undo / Redo history for editor
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    fun clearError() {
        _errorMessage.value = null
    }

    fun loadSample() {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            _statusMessage.value = "Loading sample Qualcomm SM8450 DTB..."

            withContext(Dispatchers.IO) {
                try {
                    val sampleBytes = SampleDtbProvider.getSampleDtbBytes()
                    val decompileResult = dtcEngine.decompileBytes(sampleBytes).getOrThrow()

                    val proj = DtbProject(
                        fileName = "sample_sm8450.dtb",
                        originalSizeBytes = sampleBytes.size.toLong(),
                        fileType = DtbFileType.DTB,
                        originalDts = decompileResult.dtsText,
                        currentDts = decompileResult.dtsText,
                        rootNode = decompileResult.rootNode,
                        memReservations = decompileResult.reservations,
                        warnings = decompileResult.warnings
                    )

                    _project.value = proj
                    _cpuTopology.value = topologyManager.extractTopology(proj.rootNode)
                    _diffSummary.value = diffEngine.compareNodes(proj.rootNode, proj.rootNode)
                    _statusMessage.value = "Sample DTB loaded: ${proj.rootNode.totalNodesCount} nodes parsed."
                    addRecent("sample_sm8450.dtb", sampleBytes.size.toLong(), DtbFileType.DTB)
                } catch (e: Exception) {
                    _errorMessage.value = "Failed to load sample: ${e.message}"
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun importUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            _errorMessage.value = null
            _statusMessage.value = "Reading imported file..."

            withContext(Dispatchers.IO) {
                try {
                    val (fileName, size) = queryFileInfo(context, uri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Cannot open stream for: $uri")

                    val bytes = inputStream.use { it.readBytes() }

                    // Determine file type
                    val fileType = when {
                        fileName.endsWith(".dtb", ignoreCase = true) -> DtbFileType.DTB
                        fileName.endsWith(".dtbo", ignoreCase = true) -> DtbFileType.DTBO
                        fileName.endsWith(".dts", ignoreCase = true) -> DtbFileType.DTS
                        bytes.size >= 4 && isDtbMagic(bytes) -> DtbFileType.DTB
                        else -> DtbFileType.DTS
                    }

                    val proj = if (fileType == DtbFileType.DTS) {
                        val dtsText = String(bytes, Charsets.UTF_8)
                        val parseResult = DtsParser().parse(dtsText)
                        DtbProject(
                            fileName = fileName,
                            originalSizeBytes = bytes.size.toLong(),
                            fileType = DtbFileType.DTS,
                            originalDts = dtsText,
                            currentDts = dtsText,
                            rootNode = parseResult.rootNode,
                            memReservations = parseResult.reservations,
                            warnings = parseResult.warnings
                        )
                    } else {
                        // Binary DTB or DTBO
                        val decompileRes = dtcEngine.decompileBytes(bytes).getOrThrow()
                        DtbProject(
                            fileName = fileName,
                            originalSizeBytes = bytes.size.toLong(),
                            fileType = fileType,
                            originalDts = decompileRes.dtsText,
                            currentDts = decompileRes.dtsText,
                            rootNode = decompileRes.rootNode,
                            memReservations = decompileRes.reservations,
                            warnings = decompileRes.warnings
                        )
                    }

                    _project.value = proj
                    _cpuTopology.value = topologyManager.extractTopology(proj.rootNode)
                    _diffSummary.value = diffEngine.compareNodes(proj.rootNode, proj.rootNode)
                    _statusMessage.value = "Imported $fileName (${bytes.size} bytes, ${proj.rootNode.totalNodesCount} nodes)"
                    addRecent(fileName, bytes.size.toLong(), fileType)
                } catch (e: Exception) {
                    _errorMessage.value = "Import error: ${e.message}"
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun updateDtsText(newText: String) {
        val current = _project.value ?: return
        if (current.currentDts != newText) {
            undoStack.add(current.currentDts)
            redoStack.clear()
            _project.value = current.copy(currentDts = newText)
            refreshDiffAndTopology(newText)
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _project.value ?: return
        redoStack.add(current.currentDts)
        val prev = undoStack.removeAt(undoStack.lastIndex)
        _project.value = current.copy(currentDts = prev)
        refreshDiffAndTopology(prev)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _project.value ?: return
        undoStack.add(current.currentDts)
        val next = redoStack.removeAt(redoStack.lastIndex)
        _project.value = current.copy(currentDts = next)
        refreshDiffAndTopology(next)
    }

    private fun refreshDiffAndTopology(currentDtsText: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val parsed = DtsParser().parse(currentDtsText)
                val origNode = _project.value?.rootNode ?: return@launch
                _diffSummary.value = diffEngine.compareNodes(origNode, parsed.rootNode)
                _cpuTopology.value = topologyManager.extractTopology(parsed.rootNode)
            } catch (_: Exception) {
            }
        }
    }

    fun compile() {
        val current = _project.value ?: run {
            _errorMessage.value = "No project open to compile"
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            _statusMessage.value = "Compiling DTS to DTB..."

            withContext(Dispatchers.Default) {
                val result = dtcEngine.compileDtsText(current.currentDts)
                _compilerOutput.value = result
                _isProcessing.value = false

                if (result.success) {
                    _statusMessage.value = "Compilation succeeded: ${result.outputSizeBytes} bytes generated."
                } else {
                    _errorMessage.value = "Compilation failed: ${result.errors.firstOrNull() ?: "Syntax error"}"
                }
            }
        }
    }

    fun applyCpuPreset(preset: List<Int>) {
        val current = _project.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.Default) {
                // Parse current DTS to AST
                val parsed = DtsParser().parse(current.currentDts)
                val (success, msg) = topologyManager.applyPreset(parsed.rootNode, preset)
                if (success) {
                    val updatedDts = DtsFormatter().format(parsed.rootNode, parsed.reservations)
                    _project.value = current.copy(currentDts = updatedDts)
                    _cpuTopology.value = topologyManager.extractTopology(parsed.rootNode)
                    val orig = DtsParser().parse(current.originalDts).rootNode
                    _diffSummary.value = diffEngine.compareNodes(orig, parsed.rootNode)
                    _statusMessage.value = msg
                } else {
                    _errorMessage.value = msg
                }
                _isProcessing.value = false
            }
        }
    }

    fun resetToOriginal() {
        val current = _project.value ?: return
        _project.value = current.copy(currentDts = current.originalDts)
        refreshDiffAndTopology(current.originalDts)
        _statusMessage.value = "Reset DTS to original state."
    }

    fun exportToUri(context: Context, targetUri: Uri, exportType: ExportType) {
        val current = _project.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(targetUri)
                        ?: throw Exception("Cannot open output stream for: $targetUri")

                    stream.use { out ->
                        when (exportType) {
                            ExportType.ORIGINAL_DTB -> {
                                val origParsed = DtsParser().parse(current.originalDts)
                                val bytes = FdtBinaryCompiler().compile(origParsed.rootNode, origParsed.reservations)
                                out.write(bytes)
                            }
                            ExportType.MODIFIED_DTB -> {
                                val modParsed = DtsParser().parse(current.currentDts)
                                val bytes = FdtBinaryCompiler().compile(modParsed.rootNode, modParsed.reservations)
                                out.write(bytes)
                            }
                            ExportType.ORIGINAL_DTS -> {
                                out.write(current.originalDts.toByteArray(Charsets.UTF_8))
                            }
                            ExportType.MODIFIED_DTS -> {
                                out.write(current.currentDts.toByteArray(Charsets.UTF_8))
                            }
                            ExportType.DIFF_REPORT -> {
                                val report = buildDiffReport(current)
                                out.write(report.toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                    _statusMessage.value = "Export successful: ${exportType.name}"
                } catch (e: Exception) {
                    _errorMessage.value = "Export failed: ${e.message}"
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    private fun buildDiffReport(proj: DtbProject): String {
        val diff = _diffSummary.value ?: return "No diff available."
        return buildString {
            appendLine("=== DTB Studio Comparison Report ===")
            appendLine("File: ${proj.fileName}")
            appendLine("Date: ${java.util.Date()}")
            appendLine("Summary: +${diff.addedCount} added, -${diff.removedCount} removed, ~${diff.modifiedCount} modified\n")
            appendLine("--- Changes List ---")
            for (item in diff.items) {
                appendLine("[${item.type}] ${item.path}")
                appendLine("  ${item.description}")
                if (item.details.isNotEmpty()) {
                    appendLine("  ${item.details}")
                }
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
        var name = "device_tree.dtb"
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

    private fun addRecent(name: String, size: Long, type: DtbFileType) {
        val list = _recentFiles.value.toMutableList()
        list.removeAll { it.name == name }
        list.add(0, RecentFileInfo(name, size, type))
        _recentFiles.value = list.take(5)
    }
}
