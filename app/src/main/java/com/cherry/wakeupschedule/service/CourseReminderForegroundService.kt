package com.cherry.wakeupschedule.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * 课程提醒前台服务
 * 保持应用在后台活跃，确保闹钟正常工作
 */
class CourseReminderForegroundService : Service() {

    private val notificationHelper by lazy { NotificationHelper(this) }

    companion object {
        private const val TAG = "CourseReminderService"
        private const val NOTIFICATION_ID = 10001
        private var isServiceRunning = false

        /**
         * 启动前台服务
         */
        fun start(context: Context) {
            val intent = Intent(context, CourseReminderForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止前台服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, CourseReminderForegroundService::class.java)
            context.stopService(intent)
        }

        /**
         * 检查服务是否正在运行
         */
        @Suppress("UNUSED_PARAMETER")
        fun isRunning(context: Context): Boolean {
            return isServiceRunning
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        Log.d(TAG, "前台服务已创建")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.d(TAG, "前台服务已停止")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "前台服务启动")

        notificationHelper.createNotificationChannels()

        val notification = notificationHelper.buildForegroundNotification()

        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "任务被移除，尝试重启服务")

        if (SettingsManager(this).isAlarmEnabled()) {
            val restartIntent = Intent(this, CourseReminderForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restartIntent)
            } else {
                startService(restartIntent)
            }
        }
    }
}
