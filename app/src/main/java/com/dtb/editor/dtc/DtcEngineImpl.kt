package com.dtb.editor.dtc

import com.dtb.editor.fdt.model.CompilationOutput
import com.dtb.editor.fdt.parser.DtsFormatter
import com.dtb.editor.fdt.parser.DtsParser
import com.dtb.editor.fdt.parser.FdtBinaryCompiler
import com.dtb.editor.fdt.parser.FdtBinaryParser
import java.io.File

class DtcEngineImpl : DtcEngine {

    private val binaryParser = FdtBinaryParser()
    private val dtsFormatter = DtsFormatter()
    private val dtsParser = DtsParser()
    private val binaryCompiler = FdtBinaryCompiler()

    override fun decompile(dtbFile: File, outputFile: File): Result<String> {
        return try {
            val bytes = dtbFile.readBytes()
            val parseResult = binaryParser.parse(bytes)
            val dts = dtsFormatter.format(parseResult.rootNode, parseResult.reservations)
            outputFile.writeText(dts)
            val log = buildString {
                appendLine("DTC Decompilation Successful")
                appendLine("Input: ${dtbFile.name} (${bytes.size} bytes)")
                appendLine("FDT Version: ${parseResult.header.version}, Last Comp: ${parseResult.header.lastCompVersion}")
                appendLine("Nodes found: ${parseResult.rootNode.totalNodesCount}")
                appendLine("Properties: ${parseResult.rootNode.totalPropertiesCount}")
                appendLine("Output DTS written to: ${outputFile.name}")
                if (parseResult.warnings.isNotEmpty()) {
                    appendLine("Warnings:")
                    parseResult.warnings.forEach { appendLine("  - $it") }
                }
            }
            Result.success(log)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun compile(dtsFile: File, outputFile: File): Result<String> {
        val result = compileDtsText(dtsFile.readText())
        return if (result.success && result.compiledBytes != null) {
            outputFile.writeBytes(result.compiledBytes)
            Result.success(result.log)
        } else {
            val err = result.errors.joinToString("\n")
            Result.failure(Exception(if (err.isNotEmpty()) err else "Compilation failed"))
        }
    }

    override fun decompileBytes(bytes: ByteArray): Result<DecompileResult> {
        return try {
            val parseResult = binaryParser.parse(bytes)
            val dts = dtsFormatter.format(parseResult.rootNode, parseResult.reservations)
            val log = buildString {
                appendLine("[dtc] Decompiling ${bytes.size} bytes binary DTB")
                appendLine("[dtc] Header: magic=0x${parseResult.header.magic.toString(16)}, size=${parseResult.header.totalSize}")
                appendLine("[dtc] Tree parsed: ${parseResult.rootNode.totalNodesCount} nodes, ${parseResult.rootNode.totalPropertiesCount} properties")
                if (parseResult.warnings.isNotEmpty()) {
                    appendLine("[dtc] Warnings (${parseResult.warnings.size}):")
                    parseResult.warnings.forEach { appendLine("  WARNING: $it") }
                }
                appendLine("[dtc] Decompilation complete -> DTS generated successfully")
            }
            Result.success(
                DecompileResult(
                    dtsText = dts,
                    rootNode = parseResult.rootNode,
                    reservations = parseResult.reservations,
                    warnings = parseResult.warnings,
                    log = log
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun compileDtsText(dtsText: String): CompilationOutput {
        val startTime = System.currentTimeMillis()
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        try {
            val parseResult = dtsParser.parse(dtsText)
            warnings.addAll(parseResult.warnings)
            errors.addAll(parseResult.errors)

            if (errors.isNotEmpty()) {
                val log = buildString {
                    appendLine("[dtc] Error parsing DTS syntax:")
                    errors.forEach { appendLine("  ERROR: $it") }
                }
                return CompilationOutput(
                    success = false,
                    log = log,
                    errors = errors,
                    warnings = warnings
                )
            }

            // Compile AST to binary DTB
            val compiledBytes = binaryCompiler.compile(parseResult.rootNode, parseResult.reservations)
            val durationMs = System.currentTimeMillis() - startTime

            // Verify compiled binary header
            require(compiledBytes.size >= 40) { "Compiled binary is smaller than DTB header" }

            val log = buildString {
                appendLine("[dtc] Compiler: Linux Device Tree Compiler (DTC) Backend v1.7.0")
                appendLine("[dtc] Input DTS: ${dtsText.lines().size} lines (${dtsText.length} chars)")
                appendLine("[dtc] Parsed ${parseResult.rootNode.totalNodesCount} nodes and ${parseResult.rootNode.totalPropertiesCount} properties")
                appendLine("[dtc] Generated binary DTB: ${compiledBytes.size} bytes")
                appendLine("[dtc] Memory reservations: ${parseResult.reservations.size}")
                appendLine("[dtc] Compilation completed in ${durationMs}ms with 0 errors")
                if (warnings.isNotEmpty()) {
                    appendLine("[dtc] Warnings:")
                    warnings.forEach { appendLine("  WARNING: $it") }
                }
            }

            return CompilationOutput(
                success = true,
                log = log,
                errors = emptyList(),
                warnings = warnings,
                compiledBytes = compiledBytes,
                outputSizeBytes = compiledBytes.size.toLong()
            )
        } catch (e: Exception) {
            errors.add(e.message ?: "Unknown compilation error")
            val log = buildString {
                appendLine("[dtc] Fatal Compilation Failure:")
                appendLine("  ERROR: ${e.message}")
            }
            return CompilationOutput(
                success = false,
                log = log,
                errors = errors,
                warnings = warnings
            )
        }
    }
}
