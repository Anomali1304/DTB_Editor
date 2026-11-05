package com.dtb.editor.root

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FlashSlotInfo(
    val activeSlotSuffix: String, // e.g. "_a", "_b", or ""
    val defaultPartitionName: String // e.g. "vendor_boot_a", "vendor_boot_b", or "vendor_boot"
)

class RootFlashManager {

    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            exitCode == 0 && (output.contains("uid=0") || output.contains("root"))
        } catch (_: Exception) {
            false
        }
    }

    suspend fun detectSlotInfo(): FlashSlotInfo = withContext(Dispatchers.IO) {
        var suffix = ""
        try {
            val process = Runtime.getRuntime().exec(arrayOf("getprop", "ro.boot.slot_suffix"))
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            if (output.isNotEmpty()) {
                suffix = output
            } else {
                val suProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "getprop ro.boot.slot_suffix"))
                val suOutput = suProcess.inputStream.bufferedReader().use { it.readText().trim() }
                if (suOutput.isNotEmpty()) {
                    suffix = suOutput
                }
            }
        } catch (_: Exception) {
            suffix = ""
        }

        val defaultPart = when (suffix.lowercase()) {
            "_a", "a" -> "vendor_boot_a"
            "_b", "b" -> "vendor_boot_b"
            else -> "vendor_boot"
        }

        FlashSlotInfo(
            activeSlotSuffix = suffix,
            defaultPartitionName = defaultPart
        )
    }

    suspend fun flashVendorBootFromUriWithLogs(
        context: Context,
        imageUri: Uri,
        targetPartitionName: String,
        onLog: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "flash_vendor_boot_temp.img")
        try {
            onLog("- Streaming image payload from storage...")
            var copiedBytes = 0L
            context.contentResolver.openInputStream(imageUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        copiedBytes += bytesRead
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(Exception("Unable to read image from storage"))
            onLog("- Payload prepared: ${tempFile.name} ($copiedBytes bytes)")

            flashPayloadFile(tempFile, targetPartitionName, onLog)
        } catch (e: Exception) {
            onLog("[-] Exception occurred: ${e.message}")
            Result.failure(e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    suspend fun flashVendorBootImageWithLogs(
        context: Context,
        imageBytes: ByteArray,
        targetPartitionName: String,
        onLog: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "flash_vendor_boot_temp.img")
        try {
            onLog("- Preparing image payload (${imageBytes.size} bytes)...")
            tempFile.writeBytes(imageBytes)
            onLog("- Temporary payload saved: ${tempFile.name}")

            flashPayloadFile(tempFile, targetPartitionName, onLog)
        } catch (e: Exception) {
            onLog("[-] Exception occurred: ${e.message}")
            Result.failure(e)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private suspend fun flashPayloadFile(
        tempFile: File,
        targetPartitionName: String,
        onLog: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val partitions = if (targetPartitionName == "both") {
            listOf("vendor_boot_a", "vendor_boot_b")
        } else {
            listOf(targetPartitionName)
        }

        for (part in partitions) {
            onLog("- Target partition: $part")
            onLog("- Locating block device path for $part...")

            val cmd = """
                PART="$part"
                TEMP="${tempFile.absolutePath}"
                TARGET=""
                if [ -b "/dev/block/by-name/${'$'}PART" ]; then
                    TARGET="/dev/block/by-name/${'$'}PART"
                elif [ -b "/dev/block/bootdevice/by-name/${'$'}PART" ]; then
                    TARGET="/dev/block/bootdevice/by-name/${'$'}PART"
                else
                    TARGET=${'$'}(find /dev/block -name "${'$'}PART" 2>/dev/null | head -n 1)
                fi

                if [ -z "${'$'}TARGET" ]; then
                    echo "ERROR: Partition ${'$'}PART block device not found"
                    exit 1
                fi

                echo "DEVICE: ${'$'}TARGET"
                dd if="${'$'}TEMP" of="${'$'}TARGET" bs=4M conv=fsync status=none
                RES=${'$'}?
                sync
                if [ ${'$'}RES -eq 0 ]; then
                    echo "SUCCESS: Flashed ${'$'}PART to ${'$'}TARGET"
                else
                    echo "ERROR: dd failed with code ${'$'}RES"
                    exit ${'$'}RES
                fi
            """.trimIndent()

            onLog("- Writing image payload to $part...")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val outputLines = mutableListOf<String>()

            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    outputLines.add(line)
                    if (line.startsWith("DEVICE:")) {
                        onLog("- Resolved block device: ${line.removePrefix("DEVICE:").trim()}")
                    } else if (line.startsWith("SUCCESS:")) {
                        onLog("[+] ${line.trim()}")
                    } else if (line.startsWith("ERROR:")) {
                        onLog("[-] ${line.trim()}")
                    }
                }
            }

            val errOutput = process.errorStream.bufferedReader().use { it.readText().trim() }
            if (errOutput.isNotEmpty()) {
                onLog("[-] $errOutput")
            }

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                onLog("[-] Command failed with exit code $exitCode")
                return@withContext Result.failure(Exception("Failed to flash $part (exit code $exitCode)"))
            } else {
                onLog("[+] Partition $part flashed successfully!")
            }
        }

        onLog("- Filesystem synced.")
        onLog("[+] All operations completed successfully!")
        Result.success("Flash completed successfully")
    }

    suspend fun flashVendorBootImage(
        context: Context,
        imageBytes: ByteArray,
        targetPartitionName: String
    ): Result<String> {
        return flashVendorBootImageWithLogs(context, imageBytes, targetPartitionName) { _ -> }
    }

    suspend fun rebootDevice(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Graceful Android framework reboot:
            // 1. "svc power reboot" calls Android PowerManagerService ShutdownThread, showing standard restart animation
            // 2. "cmd power reboot" (Android 10+ command line service call)
            // 3. "setprop sys.powerctl reboot" (init-level graceful property trigger)
            val gracefulScript = """
                if which svc >/dev/null 2>&1; then
                    svc power reboot || cmd power reboot || setprop sys.powerctl reboot
                elif which cmd >/dev/null 2>&1; then
                    cmd power reboot || setprop sys.powerctl reboot
                else
                    setprop sys.powerctl reboot || reboot
                fi
            """.trimIndent()

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", gracefulScript))
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                Result.success(true)
            } else {
                // Secondary fallback
                val fb = Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop sys.powerctl reboot"))
                fb.waitFor()
                Result.success(true)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
