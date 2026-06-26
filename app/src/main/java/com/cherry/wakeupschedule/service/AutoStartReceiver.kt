package com.cherry.wakeupschedule.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.cherry.wakeupschedule.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutoStartReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_AUTO_START = "com.cherry.wakeupschedule.AUTO_START_APP"
        const val ACTION_AUTO_SHUTDOWN = "com.cherry.wakeupschedule.AUTO_SHUTDOWN_APP"
        const val EXTRA_COURSE_NAME = "auto_start_course_name"
        private const val SHUTDOWN_DELAY_MS = 5 * 60 * 1000L
        private const val SHUTDOWN_REQUEST_CODE = 99999

        fun cancelShutdown(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, AutoStartReceiver::class.java).apply {
                action = ACTION_AUTO_SHUTDOWN
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                SHUTDOWN_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let { DebugLogger.init(it) }
        DebugLogger.logInfo("AutoStartReceiver triggered")

        if (intent?.action == ACTION_AUTO_SHUTDOWN) {
            handleShutdown(context)
            return
        }

        handleAutoStart(context, intent)
    }

    private fun handleAutoStart(context: Context?, intent: Intent?) {
        if (context == null) return
        val courseName = intent?.getStringExtra(EXTRA_COURSE_NAME) ?: ""
        Log.d("AutoStartReceiver", "Auto-starting app in background for course: $courseName")

        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                if (SettingsManager(context).isAlarmEnabled()) {
                    AlarmService(context).registerAllCourseNotifications()
                    DebugLogger.logInfo("AutoStart: alarms refreshed")
                }

                CourseReminderForegroundService.start(context)
                DebugLogger.logInfo("AutoStart: foreground service started")

                scheduleShutdown(context)
                DebugLogger.logInfo("AutoStart: shutdown scheduled in 5 minutes")
            } catch (e: Exception) {
                DebugLogger.logError("AutoStart failed", e)
            }
        }
    }

    private fun handleShutdown(context: Context?) {
        if (context == null) return
        DebugLogger.logInfo("AutoShutdown: stopping foreground service")
        try {
            CourseReminderForegroundService.stop(context)
            DebugLogger.logInfo("AutoShutdown: service stopped")
        } catch (e: Exception) {
            DebugLogger.logError("AutoShutdown failed", e)
        }
    }

    private fun scheduleShutdown(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val shutdownTime = System.currentTimeMillis() + SHUTDOWN_DELAY_MS

        val shutdownIntent = Intent(context, AutoStartReceiver::class.java).apply {
            action = ACTION_AUTO_SHUTDOWN
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            SHUTDOWN_REQUEST_CODE,
            shutdownIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            AlarmService.scheduleAlarmWithFallback(
                context,
                alarmManager,
                shutdownTime,
                pendingIntent,
                "autoshutdown"
            )
        } catch (e: SecurityException) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, shutdownTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, shutdownTime, pendingIntent)
                }
            } catch (e2: Exception) {
                DebugLogger.logError("Failed to schedule shutdown alarm (fallback)", e2)
            }
        } catch (e: Exception) {
            DebugLogger.logError("Failed to schedule shutdown alarm", e)
        }
    }
}