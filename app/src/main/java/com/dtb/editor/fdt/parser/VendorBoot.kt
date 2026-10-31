package com.dtb.editor.fdt.parser

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class VendorBootException(message: String) : Exception(message)

data class VendorBootHeader(
    val headerVersion: Int,
    val pageSize: Int,
    val kernelAddr: Long,
    val ramdiskAddr: Long,
    val vendorRamdiskSize: Long,
    val cmdline: ByteArray,
    val tagsAddr: Long,
    val name: ByteArray,
    val headerSize: Int,
    val dtbSize: Long,
    val dtbAddr: Long,
    val vendorRamdiskTableSize: Long = 0,
    val vendorRamdiskTableEntryNum: Long = 0,
    val vendorRamdiskTableEntrySize: Long = 0,
    val bootconfigSize: Long = 0
)

data class VendorBootImage(
    val header: VendorBootHeader,
    val hasAvbFooter: Boolean,
    val originalImageSize: Long?,
    val originalFileSize: Long,
    val headerRawBytes: ByteArray,
    val ramdiskOffset: Long,
    val dtbOffset: Long,
    val ramdiskTableOffset: Long,
    val bootconfigOffset: Long,
    val payloadEndOffset: Long,
    val dtbBytes: ByteArray,
    val ramdiskSha256: ByteArray,
    val ramdiskTableSha256: ByteArray? = null,
    val bootconfigSha256: ByteArray? = null
)

class VendorBoot {

    companion object {
        const val MAGIC = "VNDRBOOT"
        const val AVB_FOOTER_MAGIC = "AVBf"
        const val AVB_FOOTER_SIZE = 64
        const val MIN_PAGE_SIZE = 4096
        const val HDR_SIZE_V3 = 2112
        const val HDR_SIZE_V4 = 2128

        fun isVendorBoot(bytes: ByteArray): Boolean {
            if (bytes.size < 8) return false
            return bytes.sliceArray(0 until 8).toString(Charsets.US_ASCII) == MAGIC
        }

        fun roundUp(size: Long, pageSize: Long): Long {
            require(pageSize > 0) { "Ukuran page harus > 0" }
            val rem = size % pageSize
            return if (rem == 0L) size else size + (pageSize - rem)
        }
    }

    fun parse(bytes: ByteArray): VendorBootImage {
        val totalFileSize = bytes.size.toLong()
        require(bytes.size >= HDR_SIZE_V3) { "File too small for vendor_boot header" }

        val magicStr = bytes.sliceArray(0 until 8).toString(Charsets.US_ASCII)
        if (magicStr != MAGIC) {
            throw VendorBootException("Invalid vendor_boot magic")
        }

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(8)

        val headerVersion = buf.int
        if (headerVersion != 3 && headerVersion != 4) {
            throw VendorBootException("Header version not supported: v$headerVersion")
        }

        val pageSize = buf.int
        if (pageSize < MIN_PAGE_SIZE || (pageSize and (pageSize - 1)) != 0) {
            throw VendorBootException("Invalid page size: $pageSize (must be power of two and >= 4096)")
        }

        val kernelAddr = buf.int.toLong() and 0xFFFFFFFFL
        val ramdiskAddr = buf.int.toLong() and 0xFFFFFFFFL
        val vendorRamdiskSize = buf.int.toLong() and 0xFFFFFFFFL

        val cmdline = ByteArray(2048)
        buf.get(cmdline)

        val tagsAddr = buf.int.toLong() and 0xFFFFFFFFL
        val name = ByteArray(16)
        buf.get(name)

        val headerSize = buf.int
        val minHdrSize = if (headerVersion == 3) HDR_SIZE_V3 else HDR_SIZE_V4
        if (headerSize < minHdrSize) {
            throw VendorBootException("Invalid header size: $headerSize (min $minHdrSize)")
        }

        val dtbSize = buf.int.toLong() and 0xFFFFFFFFL
        val dtbAddr = buf.long

        var vendorRamdiskTableSize = 0L
        var vendorRamdiskTableEntryNum = 0L
        var vendorRamdiskTableEntrySize = 0L
        var bootconfigSize = 0L

        if (headerVersion >= 4) {
            buf.position(2112)
            vendorRamdiskTableSize = buf.int.toLong() and 0xFFFFFFFFL
            vendorRamdiskTableEntryNum = buf.int.toLong() and 0xFFFFFFFFL
            vendorRamdiskTableEntrySize = buf.int.toLong() and 0xFFFFFFFFL
            bootconfigSize = buf.int.toLong() and 0xFFFFFFFFL
        }

        val pSize = pageSize.toLong()
        val ramdiskOffset = roundUp(headerSize.toLong(), pSize)
        val dtbOffset = ramdiskOffset + roundUp(vendorRamdiskSize, pSize)

        val ramdiskTableOffset = if (headerVersion >= 4) {
            dtbOffset + roundUp(dtbSize, pSize)
        } else {
            0L
        }

        val bootconfigOffset = if (headerVersion >= 4) {
            ramdiskTableOffset + roundUp(vendorRamdiskTableSize, pSize)
        } else {
            0L
        }

        val payloadEndOffset = if (headerVersion >= 4) {
            bootconfigOffset + roundUp(bootconfigSize, pSize)
        } else {
            dtbOffset + roundUp(dtbSize, pSize)
        }

        if (payloadEndOffset > totalFileSize) {
            throw VendorBootException("Vendor boot payload exceeds file size ($payloadEndOffset > $totalFileSize)")
        }

        var hasAvbFooter = false
        var originalImageSize: Long? = null
        if (bytes.size >= AVB_FOOTER_SIZE) {
            val footerStart = bytes.size - AVB_FOOTER_SIZE
            val footerMagic = bytes.sliceArray(footerStart until footerStart + 4).toString(Charsets.US_ASCII)
            if (footerMagic == AVB_FOOTER_MAGIC) {
                hasAvbFooter = true
                val footerBuf = ByteBuffer.wrap(bytes, footerStart, AVB_FOOTER_SIZE).order(ByteOrder.BIG_ENDIAN)
                originalImageSize = footerBuf.getLong(12)
            }
        }

        val headerRaw = bytes.sliceArray(0 until headerSize)
        val dtbEnd = (dtbOffset + dtbSize).toInt().coerceAtMost(bytes.size)
        val dtbBytes = bytes.sliceArray(dtbOffset.toInt() until dtbEnd)

        val md = MessageDigest.getInstance("SHA-256")
        val ramdiskEnd = (ramdiskOffset + vendorRamdiskSize).toInt().coerceAtMost(bytes.size)
        val ramdiskSha256 = md.digest(bytes.sliceArray(ramdiskOffset.toInt() until ramdiskEnd))

        val ramdiskTableSha256 = if (headerVersion >= 4 && vendorRamdiskTableSize > 0) {
            val mdTable = MessageDigest.getInstance("SHA-256")
            val end = (ramdiskTableOffset + vendorRamdiskTableSize).toInt().coerceAtMost(bytes.size)
            mdTable.digest(bytes.sliceArray(ramdiskTableOffset.toInt() until end))
        } else null

        val bootconfigSha256 = if (headerVersion >= 4 && bootconfigSize > 0) {
            val mdBoot = MessageDigest.getInstance("SHA-256")
            val end = (bootconfigOffset + bootconfigSize).toInt().coerceAtMost(bytes.size)
            mdBoot.digest(bytes.sliceArray(bootconfigOffset.toInt() until end))
        } else null

        val header = VendorBootHeader(
            headerVersion = headerVersion,
            pageSize = pageSize,
            kernelAddr = kernelAddr,
            ramdiskAddr = ramdiskAddr,
            vendorRamdiskSize = vendorRamdiskSize,
            cmdline = cmdline,
            tagsAddr = tagsAddr,
            name = name,
            headerSize = headerSize,
            dtbSize = dtbSize,
            dtbAddr = dtbAddr,
            vendorRamdiskTableSize = vendorRamdiskTableSize,
            vendorRamdiskTableEntryNum = vendorRamdiskTableEntryNum,
            vendorRamdiskTableEntrySize = vendorRamdiskTableEntrySize,
            bootconfigSize = bootconfigSize
        )

        return VendorBootImage(
            header = header,
            hasAvbFooter = hasAvbFooter,
            originalImageSize = originalImageSize,
            originalFileSize = totalFileSize,
            headerRawBytes = headerRaw,
            ramdiskOffset = ramdiskOffset,
            dtbOffset = dtbOffset,
            ramdiskTableOffset = ramdiskTableOffset,
            bootconfigOffset = bootconfigOffset,
            payloadEndOffset = payloadEndOffset,
            dtbBytes = dtbBytes,
            ramdiskSha256 = ramdiskSha256,
            ramdiskTableSha256 = ramdiskTableSha256,
            bootconfigSha256 = bootconfigSha256
        )
    }

    fun repack(
        originalBytes: ByteArray,
        image: VendorBootImage,
        newDtbBytes: ByteArray,
        out: OutputStream
    ) {
        val pSize = image.header.pageSize.toLong()
        val newDtbSize = newDtbBytes.size.toLong()

        // 1. Header with updated dtb_size
        val newHeader = image.headerRawBytes.clone()
        val hdrBuf = ByteBuffer.wrap(newHeader).order(ByteOrder.LITTLE_ENDIAN)
        hdrBuf.putInt(2100, newDtbSize.toInt())

        out.write(newHeader)
        padZeros(out, newHeader.size.toLong(), pSize)

        // 2. Vendor Ramdisk
        if (image.header.vendorRamdiskSize > 0) {
            val start = image.ramdiskOffset.toInt()
            val len = image.header.vendorRamdiskSize.toInt()
            out.write(originalBytes, start, len)
            padZeros(out, image.header.vendorRamdiskSize, pSize)
        }

        // 3. New DTB
        out.write(newDtbBytes)
        padZeros(out, newDtbSize, pSize)

        // 4. v4 Ramdisk table and bootconfig
        if (image.header.headerVersion >= 4) {
            if (image.header.vendorRamdiskTableSize > 0) {
                val start = image.ramdiskTableOffset.toInt()
                val len = image.header.vendorRamdiskTableSize.toInt()
                out.write(originalBytes, start, len)
                padZeros(out, image.header.vendorRamdiskTableSize, pSize)
            }
            if (image.header.bootconfigSize > 0) {
                val start = image.bootconfigOffset.toInt()
                val len = image.header.bootconfigSize.toInt()
                out.write(originalBytes, start, len)
                padZeros(out, image.header.bootconfigSize, pSize)
            }
        }

        // Calculate new content size
        val newRamdiskOffset = roundUp(image.header.headerSize.toLong(), pSize)
        val newDtbOffset = newRamdiskOffset + roundUp(image.header.vendorRamdiskSize, pSize)
        val newEndOffset = if (image.header.headerVersion >= 4) {
            val newRamdiskTableOffset = newDtbOffset + roundUp(newDtbSize, pSize)
            val newBootconfigOffset = newRamdiskTableOffset + roundUp(image.header.vendorRamdiskTableSize, pSize)
            newBootconfigOffset + roundUp(image.header.bootconfigSize, pSize)
        } else {
            newDtbOffset + roundUp(newDtbSize, pSize)
        }

        if (newEndOffset > image.originalFileSize) {
            throw VendorBootException("Ukuran hasil melebihi file asli ($newEndOffset > ${image.originalFileSize})")
        }

        // Pad to original file size
        if (newEndOffset < image.originalFileSize) {
            val diff = image.originalFileSize - newEndOffset
            val zeroBuf = ByteArray(4096)
            var rem = diff
            while (rem > 0) {
                val toWrite = rem.coerceAtMost(zeroBuf.size.toLong()).toInt()
                out.write(zeroBuf, 0, toWrite)
                rem -= toWrite
            }
        }
    }

    fun repackToBytes(originalBytes: ByteArray, image: VendorBootImage, newDtbBytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        repack(originalBytes, image, newDtbBytes, out)
        return out.toByteArray()
    }

    private fun padZeros(out: OutputStream, size: Long, pageSize: Long) {
        val rem = size % pageSize
        if (rem != 0L) {
            val pad = (pageSize - rem).toInt()
            out.write(ByteArray(pad))
        }
    }
}
