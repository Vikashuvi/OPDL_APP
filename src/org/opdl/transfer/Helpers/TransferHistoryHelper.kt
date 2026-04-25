/*
 * SPDX-FileCopyrightText: 2025 OPDL Project Team
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */

package org.opdl.transfer.Helpers

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class TransferRecord(
    val fileName: String,
    val fileType: String,
    val fileSize: String,
    val duration: String,
    val source: String,
    val isUpload: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class TransferHistoryHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    companion object {
        private const val PREFS_NAME = "transfer_history"
        private const val KEY_HISTORY = "history"
        private const val MAX_HISTORY_SIZE = 50
    }

    fun addRecord(record: TransferRecord) {
        val history = getHistory()
        history.add(0, record) // Add to beginning
        
        // Limit history size
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }
        
        saveHistory(history)
    }

    fun getHistory(): MutableList<TransferRecord> {
        val historyJson = prefs.getString(KEY_HISTORY, null) ?: return mutableListOf()
        val history = mutableListOf<TransferRecord>()
        
        try {
            val jsonArray = JSONArray(historyJson)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val record = TransferRecord(
                    fileName = jsonObject.getString("fileName"),
                    fileType = jsonObject.getString("fileType"),
                    fileSize = jsonObject.getString("fileSize"),
                    duration = jsonObject.getString("duration"),
                    source = jsonObject.getString("source"),
                    isUpload = jsonObject.getBoolean("isUpload"),
                    timestamp = jsonObject.getLong("timestamp")
                )
                history.add(record)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return history
    }

    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(history: List<TransferRecord>) {
        val jsonArray = JSONArray()
        
        for (record in history) {
            val jsonObject = JSONObject().apply {
                put("fileName", record.fileName)
                put("fileType", record.fileType)
                put("fileSize", record.fileSize)
                put("duration", record.duration)
                put("source", record.source)
                put("isUpload", record.isUpload)
                put("timestamp", record.timestamp)
            }
            jsonArray.put(jsonObject)
        }
        
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.getDefault(), "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatDuration(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60)
            else -> String.format(Locale.getDefault(), "%d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
        }
    }

    fun getFileType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> "Image"
            "mp4", "avi", "mkv", "mov", "webm" -> "Video"
            "mp3", "wav", "ogg", "flac", "m4a" -> "Audio"
            "pdf" -> "PDF"
            "doc", "docx" -> "Document"
            "xls", "xlsx" -> "Spreadsheet"
            "ppt", "pptx" -> "Presentation"
            "zip", "rar", "7z", "tar" -> "Archive"
            "apk" -> "APK"
            else -> if (extension.isEmpty()) "File" else extension.uppercase()
        }
    }
}
