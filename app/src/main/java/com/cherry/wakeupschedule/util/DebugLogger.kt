
package com.cherry.wakeupschedule.util

import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private const val TAG = "CourseAlarmDebug"
    private const val LOG_FILE = "course_alarm_debug.log"
    private var writer: FileWriter? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: android.content.Context) {
        try {
            val logDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val logFile = File(logDir, LOG_FILE)
            writer = FileWriter(logFile, true)
            log("I", "=== Log system initialized ===")
            log("I", "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            log("I", "Android: ${android.os.Build.VERSION.RELEASE}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to init log file", e)
        }
    }

    fun close() {
        try {
            writer?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close log", e)
        }
    }

    fun logInfo(message: String) {
        Log.i(TAG, message)
        log("I", message)
    }

    fun logDebug(message: String) {
        Log.d(TAG, message)
        log("D", message)
    }

    fun logWarn(message: String) {
        Log.w(TAG, message)
        log("W", message)
    }

    fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
        log("E", message)
    }

    fun logAlarmSet(course: String, time: Date, reqCode: Int) {
        val msg = "ALARM_SET: $course at ${dateFormat.format(time)} (reqCode=$reqCode)"
        Log.i(TAG, msg)
        log("I", msg)
    }

    fun logAlarmReceive(course: String) {
        val msg = "ALARM_RECEIVE: $course"
        Log.i(TAG, msg)
        log("I", msg)
    }

    fun logNotificationShow(id: Int, hasPermission: Boolean) {
        val msg = "NOTIFICATION_SHOW: id=$id, permission=$hasPermission"
        Log.i(TAG, msg)
        log("I", msg)
    }

    private fun log(level: String, message: String) {
        try {
            writer?.apply {
                val time = dateFormat.format(Date())
                write("[$time] [$level] $message\n")
                flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write log", e)
        }
    }
}

