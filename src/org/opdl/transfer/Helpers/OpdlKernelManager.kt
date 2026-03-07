package org.opdl.transfer.Helpers

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Manages the OPDL kernel module lifecycle and device node access.
 */
object OpdlKernelManager {
    private const val LOG_TAG = "KDE/OPDLManager"
    private const val MODULE_PATH = "/data/local/tmp/modules/opdl.ko"
    private const val DEVICE_NODE = "/dev/opdl0"

    /**
     * Check if the opdl kernel module is currently loaded.
     */
    @JvmStatic
    fun isModuleLoaded(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("lsmod")
            process.inputStream.bufferedReader().useLines { lines ->
                lines.any { it.startsWith("opdl") || it.contains(" opdl ") }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to check if module is loaded", e)
            false
        }
    }

    /**
     * Check if the device node exists and is accessible.
     */
    @JvmStatic
    fun isDeviceNodeAvailable(): Boolean {
        val file = File(DEVICE_NODE)
        return file.exists() && file.canRead() && file.canWrite()
    }

    /**
     * Check if the device has root access available via 'su'.
     */
    @JvmStatic
    fun hasRootAccess(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readLine()
            output?.contains("uid=0") ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Attempt to load the OPDL kernel module using 'su insmod'.
     * Also ensures the device node has correct permissions.
     */
    @JvmStatic
    fun loadModule(): Boolean {
        if (isModuleLoaded() && isDeviceNodeAvailable()) return true
        if (!hasRootAccess()) {
            Log.e(LOG_TAG, "Cannot load module: Root access required")
            return false
        }

        return try {
            Log.i(LOG_TAG, "Attempting to load OPDL module from $MODULE_PATH")
            
            // 1. Load the module
            val insmodProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "insmod $MODULE_PATH"))
            insmodProcess.waitFor()

            if (!isModuleLoaded()) {
                Log.e(LOG_TAG, "Module load failed (insmod returned ${insmodProcess.exitValue()})")
                return false
            }

            // 2. Ensure device node permissions (some Androids need manual chmod)
            val chmodProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 666 $DEVICE_NODE"))
            chmodProcess.waitFor()

            val success = isDeviceNodeAvailable()
            if (success) {
                Log.i(LOG_TAG, "OPDL module loaded and device node is ready")
            } else {
                Log.w(LOG_TAG, "Module loaded but device node $DEVICE_NODE is not accessible")
            }
            success
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Exception during module load", e)
            false
        }
    }

    /**
     * Attempt to unload the OPDL kernel module.
     */
    @JvmStatic
    fun unloadModule(): Boolean {
        if (!isModuleLoaded()) return true
        if (!hasRootAccess()) return false

        return try {
            Log.i(LOG_TAG, "Unloading OPDL module")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "rmmod opdl"))
            process.waitFor()
            
            val success = !isModuleLoaded()
            if (success) {
                Log.i(LOG_TAG, "OPDL module unloaded")
            }
            success
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Exception during module unload", e)
            false
        }
    }
}
