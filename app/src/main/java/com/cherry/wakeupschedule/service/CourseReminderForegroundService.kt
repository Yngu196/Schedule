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
        // 缩短心跳间隔为 2 分钟：国产 ROM 杀进程频率高，更频繁刷新闹钟能补救
        private const val HEARTBEAT_INTERVAL_MS = 2 * 60 * 1000L
        // 距下一节课超过 1 小时时降频到 5 分钟，省电
        private const val HEARTBEAT_INTERVAL_RELAXED_MS = 5 * 60 * 1000L

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

        // Android 14+ 必须显式传入 foregroundServiceType
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startHeartbeat()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * 启动心跳协程
     * 每2分钟检查并刷新闹钟状态，解决进程被杀后 AlarmManager 延迟问题
     * 距下一节课超过1小时时降频为5分钟
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                val interval = computeNextHeartbeatInterval()
                delay(interval)
                try {
                    if (SettingsManager(this@CourseReminderForegroundService).isAlarmEnabled()) {
                        AlarmService(this@CourseReminderForegroundService).registerAllCourseNotifications()
                        Log.d(TAG, "心跳：闹钟已刷新（间隔=${interval / 1000}s）")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "心跳刷新闹钟失败", e)
                }
            }
        }
    }

    /**
     * 根据距下一节课的时间动态决定心跳间隔
     * 1小时内 -> 2分钟（高频守护）
     * 1~6小时  -> 5分钟（中等）
     * 超过6小时 -> 10分钟（低频省电）
     */
    private fun computeNextHeartbeatInterval(): Long {
        return try {
            val settingsManager = SettingsManager(this)
            val semesterStart = settingsManager.getSemesterStartDate()
            if (semesterStart == 0L) return HEARTBEAT_INTERVAL_MS

            val now = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance()
            val dayOfWeek = if (calendar.get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY) 7
                else calendar.get(java.util.Calendar.DAY_OF_WEEK) - 1
            val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)

            val timeTableManager = TimeTableManager.getInstance(this)
            val timeSlots = timeTableManager.getTimeSlots()
            val allCourses = CourseDataManager.getInstance(this).getAllCourses()

            val todayCourses = allCourses.filter { it.dayOfWeek == dayOfWeek }
                .mapNotNull { course ->
                    val slot = timeSlots.find { it.node == course.startTime } ?: return@mapNotNull null
                    val parts = slot.startTime.split(":")
                    if (parts.size != 2) return@mapNotNull null
                    val startMin = parts[0].toInt() * 60 + parts[1].toInt()
                    startMin to course
                }
                .filter { (startMin, course) ->
                    val alarmMin = startMin - course.alarmMinutesBefore
                    alarmMin > currentMinutes
                }
                .sortedBy { it.first }

            if (todayCourses.isEmpty()) return 10 * 60 * 1000L

            val nextStartMin = todayCourses.first().first
            val nextAlarmMin = nextStartMin - todayCourses.first().second.alarmMinutesBefore
            val minutesUntilNextAlarm = nextAlarmMin - currentMinutes

            when {
                minutesUntilNextAlarm <= 60 -> HEARTBEAT_INTERVAL_MS
                minutesUntilNextAlarm <= 6 * 60 -> HEARTBEAT_INTERVAL_RELAXED_MS
                else -> 10 * 60 * 1000L
            }
        } catch (e: Exception) {
            Log.w(TAG, "计算心跳间隔失败，使用默认值", e)
            HEARTBEAT_INTERVAL_MS
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
