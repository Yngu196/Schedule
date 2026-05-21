
package com.cherry.wakeupschedule.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private const val TAG = "CourseAlarmDebug"
    private const val LOG_FILE_NAME = "course_alarm_log.txt"
    private const val MAX_LINES = 500

    private lateinit var appContext: Context
    private var logFile: File? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        logFile = File(appContext.filesDir, LOG_FILE_NAME)
        logInfo("DebugLogger initialized")
    }

    fun logInfo(message: String) {
        Log.i(TAG, message)
        writeLine("[INFO] $message")
    }

    fun logDebug(message: String) {
        Log.d(TAG, message)
        writeLine("[DEBUG] $message")
    }

    fun logWarn(message: String) {
        Log.w(TAG, message)
        writeLine("[WARN] $message")
    }

    fun logError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
        val fullMessage = StringBuilder().apply {
            append("[ERROR] $message")
            throwable?.let {
                append("\n")
                append(Log.getStackTraceString(it))
            }
        }.toString()
        writeLine(fullMessage)
    }

    fun logAlarmSet(courseName: String, time: Date, requestCode: Int) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val msg = "Alarm set: $courseName at ${sdf.format(time)}, requestCode=$requestCode"
        Log.d(TAG, msg)
        writeLine("[ALARM] $msg")
    }

    fun logAlarmReceive(courseName: String) {
        val msg = "Alarm received: $courseName"
        Log.d(TAG, msg)
        writeLine("[ALARM] $msg")
    }

    fun logNotificationShow(courseName: String) {
        val msg = "Notification shown: $courseName"
        Log.d(TAG, msg)
        writeLine("[NOTIFICATION] $msg")
    }

    fun getLogs(): String {
        return try {
            logFile?.let {
                if (it.exists()) {
                    it.readText()
                } else {
                    "No logs yet"
                }
            } ?: "Log file not initialized"
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    fun clearLogs() {
        try {
            logFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }
            logInfo("Logs cleared")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing logs", e)
        }
    }

    private fun writeLine(message: String) {
        try {
            logFile?.let { file ->
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                val line = "$timestamp $message\n"

                val lines = if (file.exists()) {
                    file.readLines().toMutableList()
                } else {
                    mutableListOf()
                }

                lines.add(line.trim())

                while (lines.size > MAX_LINES) {
                    lines.removeAt(0)
                }

                FileWriter(file, false).use { writer ->
                    writer.write(lines.joinToString("\n"))
                    writer.write("\n")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing log", e)
        }
    }
}
