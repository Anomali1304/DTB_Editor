package com.dtb.editor.dtc

import java.io.File

interface DtcEngine {
    fun decompile(dtbFile: File, outputFile: File): Result<String>
    fun compile(dtsFile: File, outputFile: File): Result<String>
}
