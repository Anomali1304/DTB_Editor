package com.dtb.editor.dtc

import com.dtb.editor.fdt.model.CompilationOutput
import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.model.FdtReserveEntry
import com.dtb.editor.fdt.parser.DtsFormatter
import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.fdt.parser.FdtBinaryCompiler
import com.dtb.editor.fdt.parser.FdtBinaryParser
import java.io.File

interface DtcEngine {
    fun decompile(dtbFile: File, outputFile: File): Result<String>
    fun compile(dtsFile: File, outputFile: File): Result<String>
    fun decompileBytes(bytes: ByteArray): Result<DecompileResult>
    fun compileDtsText(dtsText: String): CompilationOutput
}

data class DecompileResult(
    val dtsText: String,
    val rootNode: FdtNode,
    val reservations: List<FdtReserveEntry>,
    val warnings: List<String>,
    val log: String
)
