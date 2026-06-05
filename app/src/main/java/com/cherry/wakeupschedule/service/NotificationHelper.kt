package com.cherry.wakeupschedule.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cherry.wakeupschedule.util.DebugLogger
import com.cherry.wakeupschedule.MainActivity
import com.cherry.wakeupschedule.R

/**
 * 通知辅助工具
 * 用于创建和管理课程提醒通知
 */
class NotificationHelper(private val context: Context) {

    init {
        // 确保 DebugLogger 在 receiver 冷启动时也能正常工作
        DebugLogger.init(context)
    }

    companion object {
        const val CHANNEL_ID = "course_reminder"
        const val CHANNEL_NAME = "课程提醒"
        const val FOREGROUND_CHANNEL_ID = "course_reminder_foreground"
        const val FOREGROUND_CHANNEL_NAME = "课程提醒服务"
    }

    /**
     * 创建通知渠道
     */
    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val reminderChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "课程开始前的提醒通知"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                setBypassDnd(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setImportance(NotificationManager.IMPORTANCE_MAX)
                }
            }

            val foregroundChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                FOREGROUND_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持课程提醒服务运行"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(listOf(reminderChannel, foregroundChannel))
        }
    }

    /**
     * 构建课程提醒通知
     */
    fun buildCourseReminderNotification(
        courseName: String,
        teacher: String,
        location: String,
        minutesBefore: Int,
        notificationId: Int
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildString {
            append("即将开始：$courseName")
            if (teacher.isNotEmpty()) {
                append("\n教师：$teacher")
            }
            if (location.isNotEmpty()) {
                append("\n地点：$location")
            }
            append("\n将于 $minutesBefore 分钟后开始")
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("课前提醒")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
            .setLights(0xFF6200EE.toInt(), 1000, 1000)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setWhen(System.currentTimeMillis())
            .build()
    }

    /**
     * 构建前台服务通知
     */
    fun buildForegroundNotification(): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("课程提醒服务运行中")
            .setContentText("确保您不会错过任何课程提醒")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * 显示通知
     */
    fun showNotification(notificationId: Int, notification: android.app.Notification, @Suppress("UNUSED_PARAMETER") courseName: String = "") {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    /**
     * 取消通知
     */
    fun cancelNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * 取消某门课程在通知栏中所有已展示的通知
     * 通知ID在 AlarmReceiver 中按规则生成：
     *  - 无周次时：courseName.hashCode()
     *  - 有周次时：("$courseName#$week").hashCode()
     * 删除/修改课程时必须同步清理通知栏，否则旧通知会一直残留
     */
    fun cancelCourseNotifications(courseName: String, maxWeeks: Int = 20) {
        if (courseName.isEmpty()) return
        val nm = NotificationManagerCompat.from(context)
        // 清理 setCourseAlarm 触发的通知（无周次）
        nm.cancel(courseName.hashCode())
        // 清理 registerCourseNotificationsForSemester 触发的每周通知
        for (week in 1..maxWeeks) {
            nm.cancel("$courseName#$week".hashCode())
        }
    }

    /**
     * 根据课程ID获取通知ID
     */
    fun getNotificationId(courseId: Long): Int {
        return courseId.toInt()
    }
}
