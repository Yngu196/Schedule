package com.cherry.wakeupschedule.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 课程提醒前台服务
 * 保持应用在后台活跃，确保闹钟正常工作
 * 每5分钟心跳检测并刷新闹钟状态
 */
class CourseReminderForegroundService : Service() {

    private val notificationHelper by lazy { NotificationHelper(this) }
    private var heartbeatJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "CourseReminderService"
        private const val NOTIFICATION_ID = 10001
        private var isServiceRunning = false
        private const val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L

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
        heartbeatJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
        isServiceRunning = false
        Log.d(TAG, "前台服务已停止")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "前台服务启动")

        notificationHelper.createNotificationChannels()

        val notification = notificationHelper.buildForegroundNotification()

        startForeground(NOTIFICATION_ID, notification)

        startHeartbeat()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * 启动心跳协程
     * 每5分钟检查并刷新闹钟状态，解决进程被杀后 AlarmManager 延迟问题
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    if (SettingsManager(this@CourseReminderForegroundService).isAlarmEnabled()) {
                        AlarmService(this@CourseReminderForegroundService).registerAllCourseNotifications()
                        Log.d(TAG, "心跳：闹钟已刷新")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "心跳刷新闹钟失败", e)
                }
            }
        }
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
