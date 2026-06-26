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
import java.util.Objects

/**
 * 通知辅助工具
 * 用于创建和管理课程提醒通知。
 *
 * 通知 ID 生成规则（统一管理，所有触发路径必须一致）：
 * - 无周次：courseId.hashCode()
 * - 有周次：Objects.hash(courseId, week)
 *
 * 使用 hashCode 替代乘法运算，避免 courseId 较大时乘法溢出 Int 导致
 * notificationId / requestCode 冲突。
 *
 * 涉及路径：
 * - AlarmReceiver.onReceive() — 闹钟广播弹出通知
 * - ExactAlarmWorker.doWork() — WorkManager 备份/补救触发
 * - cancelCourseNotifications() — 删除课程时清理通知栏
 */
class NotificationHelper(private val context: Context) {

    init {
        DebugLogger.init(context)
    }

    companion object {
        const val CHANNEL_ID = "course_reminder"
        const val CHANNEL_NAME = "课程提醒"
        const val FOREGROUND_CHANNEL_ID = "course_reminder_foreground"
        const val FOREGROUND_CHANNEL_NAME = "课程提醒服务"

        /**
         * 生成课程通知 ID（统一公式，所有触发路径共用）
         *
         * 使用 hashCode 替代 courseId * 100 乘法，避免大 ID 时溢出 Int 导致
         * notificationId 冲突，从而引发 PendingIntent 错乱或通知覆盖。
         *
         * @param courseId 课程 ID
         * @param week 周次，0 表示不区分周次
         * @return 通知 ID
         */
        fun generateNotificationId(courseId: Long, week: Int = 0): Int {
            return if (week > 0) {
                Objects.hash(courseId, week)
            } else {
                courseId.hashCode()
            }
        }
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
     *
     * 使用统一的 generateNotificationId() 覆盖：
     * - courseId.hashCode()：setCourseAlarm 触发的通知（无周次）
     * - Objects.hash(courseId, week)：registerCourseNotificationsForSemester 触发的每周通知
     * - 保留旧版 hashCode 清理以兼容存量通知（过渡期后可移除）
     */
    fun cancelCourseNotifications(courseId: Long, courseName: String, maxWeeks: Int = 20) {
        val nm = NotificationManagerCompat.from(context)

        // 清理无周次通知（setCourseAlarm 路径）
        nm.cancel(generateNotificationId(courseId, 0))

        // 清理每周通知（registerCourseNotificationsForSemester 路径）
        for (week in 1..maxWeeks) {
            nm.cancel(generateNotificationId(courseId, week))
        }

        // 过渡期兼容：清理旧版 hashCode 方式生成的通知 ID
        if (courseName.isNotEmpty()) {
            nm.cancel(courseName.hashCode())
            for (week in 1..maxWeeks) {
                nm.cancel("$courseName#$week".hashCode())
            }
        }
    }

    /**
     * 根据课程ID获取通知ID（无周次）
     */
    fun getNotificationId(courseId: Long): Int {
        return generateNotificationId(courseId, 0)
    }
}
