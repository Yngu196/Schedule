package com.cherry.wakeupschedule.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log

class CourseReminderForegroundService : Service() {

    private val notificationHelper by lazy { NotificationHelper(this) }

    companion object {
        private const val TAG = "CourseReminderService"
        private const val NOTIFICATION_ID = 10001

        fun start(context: Context) {
            val intent = Intent(context, CourseReminderForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CourseReminderForegroundService::class.java)
            context.stopService(intent)
        }

        fun isRunning(context: Context): Boolean {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
            return runningServices.any { it.service.className == CourseReminderForegroundService::class.java.name }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "前台服务已创建")
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

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "前台服务已停止")
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
