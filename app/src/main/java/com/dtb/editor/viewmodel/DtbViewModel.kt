package com.dtb.editor.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dtb.editor.dtc.DtcEngine
import com.dtb.editor.dtc.DtcEngineImpl
import com.dtb.editor.fdt.cpu.ClusterConfig
import com.dtb.editor.fdt.cpu.CpuTopologyManager
import com.dtb.editor.fdt.cpu.CpuTopologyState
import com.dtb.editor.fdt.cpu.TopologyPlan
import com.dtb.editor.fdt.cpu.TopologyPreviewResult
import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.parser.DtbEntryItem
import com.dtb.editor.fdt.parser.DtbPayloadHelper
import com.dtb.editor.fdt.parser.DtbPayloadStructure
import com.dtb.editor.fdt.parser.DtsFormatter
import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.fdt.parser.VendorBoot
import com.dtb.editor.fdt.parser.VendorBootException
import com.dtb.editor.fdt.parser.VendorBootImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

enum class InputFileType(val defaultExtension: String) {
    VENDOR_BOOT("img"),
    DT_TABLE("dtb"),
    RAW_FDT("dtb")
}

enum class AppTab {
    CPU,
    DTS
}

data class OpenFileState(
    val fileName: String,
    val fileType: InputFileType,
    val originalBytes: ByteArray,
    val hasAvbFooter: Boolean,
    val vendorBootImage: VendorBootImage? = null,
    val dtbPayloadStructure: DtbPayloadStructure,
    val dtbEntries: List<DtbEntryItem>,
    val selectedEntryIndex: Int,
    val originalDtsText: String,
    val currentDtsText: String,
    val parsedAst: FdtNode,
    val cpuTopology: CpuTopologyState
)

data class PendingMultiDtb(
    val fileName: String,
    val fileType: InputFileType,
    val originalBytes: ByteArray,
    val hasAvbFooter: Boolean,
    val vendorBootImage: VendorBootImage? = null,
    val dtbPayloadStructure: DtbPayloadStructure,
    val dtbEntries: List<DtbEntryItem>
)

class DtbViewModel(
    private val dtcEngine: DtcEngine = DtcEngineImpl(),
    private val topologyManager: CpuTopologyManager = CpuTopologyManager(),
    private val vendorBoot: VendorBoot = VendorBoot(),
    private val dtbPayloadHelper: DtbPayloadHelper = DtbPayloadHelper()
) : ViewModel() {

    private val _openFileState = MutableStateFlow<OpenFileState?>(null)
    val openFileState: StateFlow<OpenFileState?> = _openFileState.asStateFlow()

    private val _pendingMultiDtb = MutableStateFlow<PendingMultiDtb?>(null)
    val pendingMultiDtb: StateFlow<PendingMultiDtb?> = _pendingMultiDtb.asStateFlow()

    private val _activeTab = MutableStateFlow(AppTab.CPU)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    fun selectTab(tab: AppTab) {
        _activeTab.value = tab
    }

    fun openUri(uri: Uri, context: Context) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val fileName = queryFileName(context, uri)
                    val inputStream = context.contentResolver.openInputStream(uri)
                        ?: throw Exception("Gagal membuka stream file")
                    val bytes = inputStream.use { it.readBytes() }
                    if (bytes.size < 4) {
                        _snackbarMessage.emit("Format file tidak dikenali")
                        return@withContext
                    }

                    if (VendorBoot.isVendorBoot(bytes)) {
                        val image = vendorBoot.parse(bytes)
                        val (structure, entries) = dtbPayloadHelper.parseStructure(image.dtbBytes)
                        if (entries.size > 1) {
                            _pendingMultiDtb.value = PendingMultiDtb(
                                fileName = fileName,
                                fileType = InputFileType.VENDOR_BOOT,
                                originalBytes = bytes,
                                hasAvbFooter = image.hasAvbFooter,
                                vendorBootImage = image,
                                dtbPayloadStructure = structure,
                                dtbEntries = entries
                            )
                        } else {
                            loadEntryDirect(fileName, InputFileType.VENDOR_BOOT, bytes, image.hasAvbFooter, image, structure, entries, 0)
                        }
                        return@withContext
                    }

                    // Check DT table or FDT magic
                    try {
                        val (structure, entries) = dtbPayloadHelper.parseStructure(bytes)
                        val fileType = if (structure is DtbPayloadStructure.AndroidDtTable) {
                            InputFileType.DT_TABLE
                        } else {
                            InputFileType.RAW_FDT
                        }

                        if (entries.size > 1) {
                            _pendingMultiDtb.value = PendingMultiDtb(
                                fileName = fileName,
                                fileType = fileType,
                                originalBytes = bytes,
                                hasAvbFooter = false,
                                vendorBootImage = null,
                                dtbPayloadStructure = structure,
                                dtbEntries = entries
                            )
                        } else {
                            loadEntryDirect(fileName, fileType, bytes, false, null, structure, entries, 0)
                        }
                    } catch (_: Exception) {
                        _snackbarMessage.emit("Format file tidak dikenali")
                    }
                } catch (e: Exception) {
                    _snackbarMessage.emit(e.message ?: "Gagal membuka file")
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun selectDtbEntry(index: Int) {
        val pending = _pendingMultiDtb.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    loadEntryDirect(
                        pending.fileName,
                        pending.fileType,
                        pending.originalBytes,
                        pending.hasAvbFooter,
                        pending.vendorBootImage,
                        pending.dtbPayloadStructure,
                        pending.dtbEntries,
                        index
                    )
                    _pendingMultiDtb.value = null
                } catch (e: Exception) {
                    _snackbarMessage.emit(e.message ?: "Gagal memuat entri DTB")
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun cancelMultiDtbSelection() {
        _pendingMultiDtb.value = null
    }

    private fun loadEntryDirect(
        fileName: String,
        fileType: InputFileType,
        originalBytes: ByteArray,
        hasAvbFooter: Boolean,
        vendorBootImage: VendorBootImage?,
        structure: DtbPayloadStructure,
        entries: List<DtbEntryItem>,
        selectedIndex: Int
    ) {
        val selectedFdt = entries[selectedIndex].fdtBytes
        val decompileRes = dtcEngine.decompileBytes(selectedFdt).getOrThrow()
        val ast = decompileRes.rootNode
        val topology = topologyManager.extractTopology(ast)

        _openFileState.value = OpenFileState(
            fileName = fileName,
            fileType = fileType,
            originalBytes = originalBytes,
            hasAvbFooter = hasAvbFooter,
            vendorBootImage = vendorBootImage,
            dtbPayloadStructure = structure,
            dtbEntries = entries,
            selectedEntryIndex = selectedIndex,
            originalDtsText = decompileRes.dtsText,
            currentDtsText = decompileRes.dtsText,
            parsedAst = ast,
            cpuTopology = topology
        )
        _activeTab.value = AppTab.CPU
    }

    fun updateDtsText(newText: String) {
        val current = _openFileState.value ?: return
        if (current.currentDtsText == newText) return
        val parsed = DtsParser().parse(newText)
        val topology = topologyManager.extractTopology(parsed.rootNode)
        _openFileState.value = current.copy(
            currentDtsText = newText,
            parsedAst = parsed.rootNode,
            cpuTopology = topology
        )
    }

    fun applyCustomPlan(clusterConfigs: List<ClusterConfig>, syncCpufreq: Boolean) {
        val current = _openFileState.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.Default) {
                try {
                    val beforeAst = DtsParser().parse(current.currentDtsText).rootNode
                    val parsed = DtsParser().parse(current.currentDtsText)
                    val (success, msg) = topologyManager.applyCustomPlan(parsed.rootNode, clusterConfigs, syncCpufreq)
                    if (!success) {
                        _snackbarMessage.emit("Gagal menerapkan topologi: $msg")
                        return@withContext
                    }

                    val formattedDts = DtsFormatter().format(parsed.rootNode, parsed.reservations)
                    val reparsedAfterAst = DtsParser().parse(formattedDts).rootNode

                    val cores = topologyManager.engine.extractCpuCores(beforeAst)
                    val controller = topologyManager.engine.discoverPerformanceController(beforeAst, cores)
                    val isolation = topologyManager.engine.verifyTopologyIsolation(beforeAst, reparsedAfterAst, controller?.fullPath)

                    if (!isolation.isValid) {
                        _snackbarMessage.emit(isolation.message)
                        return@withContext
                    }

                    updateDtsText(formattedDts)
                    _snackbarMessage.emit("Topologi diterapkan ke DTS")
                } catch (e: Exception) {
                    _snackbarMessage.emit(e.message ?: "Gagal menerapkan topologi")
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun previewPlan(clusterConfigs: List<ClusterConfig>, syncCpufreq: Boolean): TopologyPreviewResult? {
        val current = _openFileState.value ?: return null
        return topologyManager.previewPlan(clusterConfigs, current.cpuTopology.cores, current.cpuTopology.controllerInfo, syncCpufreq)
    }

    fun prepareSaveBytes(): ByteArray? {
        val current = _openFileState.value ?: return null
        return try {
            if (current.currentDtsText == current.originalDtsText) {
                return current.originalBytes
            }

            // 1. Compile modified DTS to FDT binary
            val compileResult = dtcEngine.compileDtsText(current.currentDtsText)
            if (!compileResult.success || compileResult.compiledBytes == null) {
                throw Exception("Kompilasi DTS gagal: ${compileResult.log}")
            }
            val newFdtBytes = compileResult.compiledBytes

            // 2. Verify compiled FDT can be decompiled
            val decompileCheck = dtcEngine.decompileBytes(newFdtBytes)
            if (decompileCheck.isFailure) {
                throw Exception("FDT hasil kompilasi tidak dapat di-decompile ulang")
            }

            // 3. Rebuild DTB block
            val newDtbBytes = dtbPayloadHelper.rebuild(
                current.dtbPayloadStructure,
                current.selectedEntryIndex,
                newFdtBytes
            )

            // 4. Output format handling
            when (current.fileType) {
                InputFileType.VENDOR_BOOT -> {
                    val vbImage = current.vendorBootImage
                        ?: throw Exception("Data header vendor_boot tidak tersedia")
                    val repacked = vendorBoot.repackToBytes(current.originalBytes, vbImage, newDtbBytes)

                    // In-memory repack verification
                    val verifyParsed = vendorBoot.parse(repacked)
                    val md = MessageDigest.getInstance("SHA-256")
                    val origRamdiskSha = vbImage.ramdiskSha256
                    val newRamdiskSha = verifyParsed.ramdiskSha256
                    if (!origRamdiskSha.contentEquals(newRamdiskSha)) {
                        throw Exception("Verifikasi integritas ramdisk gagal")
                    }

                    if (vbImage.header.headerVersion >= 4) {
                        if (vbImage.ramdiskTableSha256 != null && !vbImage.ramdiskTableSha256.contentEquals(verifyParsed.ramdiskTableSha256)) {
                            throw Exception("Verifikasi ramdisk table gagal")
                        }
                        if (vbImage.bootconfigSha256 != null && !vbImage.bootconfigSha256.contentEquals(verifyParsed.bootconfigSha256)) {
                            throw Exception("Verifikasi bootconfig gagal")
                        }
                    }

                    // Verify unedited DTB entries in repacked image
                    val (_, repackedEntries) = dtbPayloadHelper.parseStructure(verifyParsed.dtbBytes)
                    for (i in current.dtbEntries.indices) {
                        if (i != current.selectedEntryIndex) {
                            val origEntryBytes = current.dtbEntries[i].fdtBytes
                            val repackedEntryBytes = repackedEntries.getOrNull(i)?.fdtBytes
                            if (repackedEntryBytes == null || !origEntryBytes.contentEquals(repackedEntryBytes)) {
                                throw Exception("Entri DTB lain berubah saat repack")
                            }
                        }
                    }

                    repacked
                }
                InputFileType.DT_TABLE -> {
                    // Verify unedited entries in DT table
                    val (_, rebuiltEntries) = dtbPayloadHelper.parseStructure(newDtbBytes)
                    for (i in current.dtbEntries.indices) {
                        if (i != current.selectedEntryIndex) {
                            val origEntryBytes = current.dtbEntries[i].fdtBytes
                            val rebuiltEntryBytes = rebuiltEntries.getOrNull(i)?.fdtBytes
                            if (rebuiltEntryBytes == null || !origEntryBytes.contentEquals(rebuiltEntryBytes)) {
                                throw Exception("Entri DTB lain berubah saat rekonstruksi tabel DT")
                            }
                        }
                    }
                    newDtbBytes
                }
                InputFileType.RAW_FDT -> {
                    if (current.dtbEntries.size > 1) {
                        val (_, rebuiltEntries) = dtbPayloadHelper.parseStructure(newDtbBytes)
                        for (i in current.dtbEntries.indices) {
                            if (i != current.selectedEntryIndex) {
                                val origEntryBytes = current.dtbEntries[i].fdtBytes
                                val rebuiltEntryBytes = rebuiltEntries.getOrNull(i)?.fdtBytes
                                if (rebuiltEntryBytes == null || !origEntryBytes.contentEquals(rebuiltEntryBytes)) {
                                    throw Exception("Entri FDT lain berubah saat rekonstruksi")
                                }
                            }
                        }
                    }
                    newDtbBytes
                }
            }
        } catch (e: Exception) {
            viewModelScope.launch {
                _snackbarMessage.emit(e.message ?: "Verifikasi sebelum simpan gagal")
            }
            null
        }
    }

    fun writeSavedFile(uri: Uri, context: Context, bytes: ByteArray) {
        viewModelScope.launch {
            _isProcessing.value = true
            withContext(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: throw Exception("Gagal membuka file tujuan simpan")
                    stream.use { it.write(bytes) }

                    // Mark as clean
                    val current = _openFileState.value
                    if (current != null) {
                        _openFileState.value = current.copy(originalDtsText = current.currentDtsText)
                    }
                    _snackbarMessage.emit("File berhasil disimpan")
                } catch (e: Exception) {
                    _snackbarMessage.emit(e.message ?: "Gagal menyimpan file")
                } finally {
                    _isProcessing.value = false
                }
            }
        }
    }

    fun closeFile() {
        _openFileState.value = null
        _pendingMultiDtb.value = null
    }

    fun getSuggestedFileName(): String {
        val current = _openFileState.value ?: return "output.dtb"
        val rawName = current.fileName
        val dotIdx = rawName.lastIndexOf('.')
        return if (dotIdx > 0) {
            val base = rawName.substring(0, dotIdx)
            val ext = rawName.substring(dotIdx + 1)
            "${base}_mod.$ext"
        } else {
            "${rawName}_mod.${current.fileType.defaultExtension}"
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String {
        var name = "file"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) name = it.getString(idx) ?: name
            }
        }
        return name
    }
}
