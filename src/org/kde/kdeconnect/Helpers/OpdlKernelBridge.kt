/*
 * SPDX-FileCopyrightText: 2026 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.kde.kdeconnect.Helpers

import android.util.Log
import org.kde.kdeconnect.KdeConnect
import org.kde.kdeconnect.DeviceInfo
import org.kde.kdeconnect.NetworkPacket
import java.io.FileInputStream
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
 * - Keeps transfer fallback to KDE Connect LAN sockets.
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

    /**
     * Placeholder hook for OPDL kernel-level payload send.
     * Return true once JNI/native send path is available.
     */
    @JvmStatic
    fun trySendPayloadViaKernel(deviceId: String, payload: NetworkPacket.Payload?): Boolean {
        if (payload == null) return false
        if (!shouldAdvertiseFastPath()) return false

        val context = KdeConnect.getInstance().applicationContext
        val tempFile = File.createTempFile("opdl_send_", ".bin", context.cacheDir)

        return try {
            payload.inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    copyStream(input, output)
                }
            } ?: return false

            val expectedSize = payload.payloadSize
            val actualSize = tempFile.length()
            if (expectedSize > 0 && actualSize != expectedSize) {
                Log.w(LOG_TAG, "Payload size mismatch before OPDL send: expected=$expectedSize, actual=$actualSize")
                return false
            }

            nativeSendPayloadFile(deviceId, tempFile.absolutePath, actualSize)
        } catch (e: IOException) {
            Log.w(LOG_TAG, "OPDL fast-path send failed, using fallback", e)
            false
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Placeholder hook for OPDL kernel-level payload receive.
     * Return non-null payload once JNI/native receive path is available.
     */
    @JvmStatic
    fun tryReceivePayloadViaKernel(deviceId: String, payloadSize: Long): NetworkPacket.Payload? {
        if (!shouldAdvertiseFastPath()) return null
        if (payloadSize <= 0) return null

        val context = KdeConnect.getInstance().applicationContext
        val tempFile = File.createTempFile("opdl_recv_", ".bin", context.cacheDir)

        return try {
            val receivedBytes = nativeReceivePayloadFile(deviceId, tempFile.absolutePath, payloadSize)
            if (receivedBytes <= 0) {
                tempFile.delete()
                return null
            }

            val input = AutoDeleteFileInputStream(tempFile)
            NetworkPacket.Payload(input, receivedBytes)
        } catch (e: IOException) {
            Log.w(LOG_TAG, "OPDL fast-path receive failed, using fallback", e)
            tempFile.delete()
            null
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
    private external fun nativeReceivePayloadFile(deviceId: String, destinationPath: String, expectedSize: Long): Long
}
