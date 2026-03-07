/*
 * SPDX-FileCopyrightText: 2026 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.opdl.transfer.Helpers

import android.util.Log
import org.opdl.transfer.OpdlTransfer
import org.opdl.transfer.DeviceInfo
import org.opdl.transfer.NetworkPacket
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileDescriptor
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.File

/**
 * Bridge point for OPDL kernel/userspace fast-path integration.
 *
 * Current behavior:
 * - Negotiates capability via identity packets.
 * - Keeps transfer fallback to OPDL Transfer LAN sockets.
 * - Exposes hooks so JNI/native OPDL transport can be enabled incrementally.
 */
object OpdlKernelBridge {
    private const val LOG_TAG = "KDE/OPDLBridge"
    const val IDENTITY_KEY_FAST_PATH = "opdlFastPathV1"
    const val PAYLOAD_TRANSFER_MODE_KEY = "mode"
    const val PAYLOAD_TRANSFER_MODE_FAST_PATH = "opdl-fastpath"
    private const val OPDL_JNI_LIB = "opdl_jni"
    private const val COPY_BUFFER_SIZE = 64 * 1024

    private val nativeAvailable: Boolean by lazy {
        runCatching {
            System.loadLibrary(OPDL_JNI_LIB)
            true
        }.getOrElse {
            Log.i(LOG_TAG, "OPDL JNI library unavailable, using socket fallback: ${it.message}")
            false
        }
    }

    /**
     * Decide if this device should advertise OPDL fast-path support.
     * Keep this conservative until JNI/native path is fully wired.
     */
    @JvmStatic
    fun shouldAdvertiseFastPath(): Boolean {
        return nativeAvailable && runCatching { nativeHasKernelDevice() }.getOrDefault(false)
    }

    /**
     * Check if fast-path can be attempted for a given peer.
     */
    @JvmStatic
    fun canAttemptFastPath(peerInfo: DeviceInfo): Boolean {
        return shouldAdvertiseFastPath() && peerInfo.supportsOpdlFastPath
    }

    @JvmStatic
    fun trySendPayloadViaKernel(deviceId: String, payload: NetworkPacket.Payload?): Boolean {
        if (payload == null) return false
        if (!shouldAdvertiseFastPath()) return false

        val input = payload.inputStream ?: return false
        val fd = getFd(input)

        if (fd != -1) {
            // Optimization: Direct stream from existing FD
            Log.d(LOG_TAG, "Using direct FD streaming for OPDL send")
            return nativeSendPayloadFD(deviceId, fd, payload.payloadSize)
        }

        // Fallback: Copy to temp file to get an FD
        Log.d(LOG_TAG, "Input stream has no FD, using temp file fallback for OPDL")
        val context = OpdlTransfer.getInstance().applicationContext
        val tempFile = File.createTempFile("opdl_send_", ".bin", context.cacheDir)

        return try {
            tempFile.outputStream().use { output ->
                copyStream(input, output)
            }

            val actualSize = tempFile.length()
            val tempFd = tempFile.inputStream().use { getFd(it) }
            
            if (tempFd != -1) {
                nativeSendPayloadFD(deviceId, tempFd, actualSize)
            } else {
                nativeSendPayloadFile(deviceId, tempFile.absolutePath, actualSize)
            }
        } catch (e: IOException) {
            Log.w(LOG_TAG, "OPDL fast-path send failed, using fallback", e)
            false
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Optimized receive path: Returns an InputStream that reads directly from the kernel device.
     */
    @JvmStatic
    fun tryReceivePayloadViaKernel(deviceId: String, payloadSize: Long): NetworkPacket.Payload? {
        if (!shouldAdvertiseFastPath()) return null
        if (payloadSize <= 0) return null

        return try {
            val opdlStream = DirectOpdlInputStream(deviceId, payloadSize)
            NetworkPacket.Payload(opdlStream, payloadSize)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "OPDL fast-path receive setup failed, using fallback", e)
            null
        }
    }

    private class DirectOpdlInputStream(val deviceId: String, val expectedSize: Long) : InputStream() {
        private var totalRead = 0L
        private var closed = false

        override fun read(): Int {
            val b = ByteArray(1)
            val n = read(b, 0, 1)
            return if (n <= 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (closed || totalRead >= expectedSize) return -1
            
            val remaining = expectedSize - totalRead
            val toRead = Math.min(len.toLong(), remaining).toInt()
            
            val readCount = nativeRead(b, off, toRead)
            if (readCount > 0) {
                totalRead += readCount
            }
            return readCount
        }

        override fun close() {
            closed = true
            super.close()
        }
    }

    @Throws(IOException::class)
    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
        }
        output.flush()
    }

    private fun getFd(stream: Any?): Int {
        return try {
            val fd = when (stream) {
                is FileInputStream -> stream.fd
                is FileOutputStream -> stream.fd
                else -> return -1
            }
            val descriptorField = FileDescriptor::class.java.getDeclaredField("descriptor")
            descriptorField.isAccessible = true
            descriptorField.getInt(fd)
        } catch (e: Exception) {
            -1
        }
    }

    private class AutoDeleteFileInputStream(private val backingFile: File) :
        FilterInputStream(FileInputStream(backingFile)) {
        override fun close() {
            try {
                super.close()
            } finally {
                backingFile.delete()
            }
        }
    }

    @JvmStatic
    private external fun nativeHasKernelDevice(): Boolean

    @JvmStatic
    private external fun nativeSendPayloadFile(deviceId: String, payloadPath: String, payloadSize: Long): Boolean

    @JvmStatic
    private external fun nativeSendPayloadFD(deviceId: String, fd: Int, payloadSize: Long): Boolean

    @JvmStatic
    private external fun nativeReceivePayloadFile(deviceId: String, destinationPath: String, expectedSize: Long): Long

    @JvmStatic
    private external fun nativeReceivePayloadFD(deviceId: String, fd: Int, expectedSize: Long): Long

    @JvmStatic
    private external fun nativeRead(buffer: ByteArray, offset: Int, len: Int): Int
}
