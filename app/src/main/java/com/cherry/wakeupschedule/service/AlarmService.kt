package com.cherry.wakeupschedule.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cherry.wakeupschedule.model.Course
import com.cherry.wakeupschedule.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 闹钟服务
 * 负责课程提醒闹钟的设置、取消和管理
 * 使用系统AlarmManager实现精确闹钟提醒
 *
 * @param context 上下文环境
 */
class AlarmService(private val context: Context) {

    // 懒加载AlarmManager服务
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    // 懒加载时间表管理器
    private val timeTableManager by lazy { TimeTableManager.getInstance(context) }
    // 懒加载通知助手
    private val notificationHelper by lazy { NotificationHelper(context) }

    /**
     * 设置课程闹钟
     * 在设置新闹钟前会先取消该课程的旧闹钟，避免重复提醒
     *
     * @param course 要设置闹钟的课程
     */
    fun setCourseAlarm(course: Course) {
        Log.d("CourseAlarmDebug", "setCourseAlarm called: ${course.name}, alarmEnabled: ${course.alarmEnabled}")
        // 如果课程未启用闹钟，则取消该课程的闹钟
        if (!course.alarmEnabled) {
            cancelCourseAlarm(course)
            return
        }

        // 先取消旧闹钟，避免重复
        cancelCourseAlarm(course)

        val currentWeek = getCurrentWeek()

        // 检查课程是否在当前周范围内
        if (currentWeek < course.startWeek || currentWeek > course.endWeek) return
        // 检查单双周是否匹配
        if (!isWeekTypeMatched(course, currentWeek)) return

        // 使用学期开始日期精确计算闹钟时间
        var alarmTime = calculateAlarmTimeMillis(course, currentWeek, course.alarmMinutesBefore)

        // 如果设置的时间已过，则改为下周
        if (alarmTime <= System.currentTimeMillis()) {
            alarmTime = calculateAlarmTimeMillis(course, currentWeek + 1, course.alarmMinutesBefore)
        }

        if (alarmTime <= System.currentTimeMillis()) return

        // 创建广播Intent，传递给AlarmReceiver
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("course_name", course.name)
            putExtra("course_teacher", course.teacher)
            putExtra("course_location", course.classroom)
            putExtra("course", course)
        }

        // 创建PendingIntent
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            course.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 使用 setAlarmClock 确保系统将其视为高优先级闹钟，在 Doze 模式下也能触发
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(alarmTime, null),
            pendingIntent
        )
        DebugLogger.logAlarmSet(course.name, java.util.Date(alarmTime), course.id.toInt())
    }

    /**
     * 取消课程的闹钟
     * 同时取消AlarmManager闹钟和WorkManager提醒
     * 以及整学期所有周次的预设闹钟
     *
     * @param course 要取消闹钟的课程
     */
    fun cancelCourseAlarm(course: Course) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            course.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        // 同时取消整学期所有周次的预设闹钟（requestCode = course.id * 100 + week）
        for (week in 1..20) {
            val weekIntent = Intent(context, AlarmReceiver::class.java)
            val weekPendingIntent = PendingIntent.getBroadcast(
                context,
                (course.id * 100 + week).toInt(),
                weekIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(weekPendingIntent)
        }

        // 同时取消WorkManager的提醒
        ExactAlarmWorker.cancelReminder(context, course.id)
        Log.d("AlarmService", "Cancelled alarm for ${course.name}")
    }

    /**
     * 使用WorkManager安排精确提醒
     * 作为AlarmManager的备份方案
     *
     * @param course 要安排的课程提醒
     */
    fun scheduleExactReminder(course: Course) {
        // 如果未启用闹钟，则取消
        if (!course.alarmEnabled) {
            cancelCourseAlarm(course)
            return
        }

        // 先取消旧提醒
        cancelCourseAlarm(course)

        val currentWeek = getCurrentWeek()

        // 检查周范围和单双周
        if (currentWeek < course.startWeek || currentWeek > course.endWeek) return
        if (!isWeekTypeMatched(course, currentWeek)) return

        // 使用学期开始日期精确计算闹钟时间
        var alarmTime = calculateAlarmTimeMillis(course, currentWeek, course.alarmMinutesBefore)

        // 如果时间已过，则安排到下周
        if (alarmTime <= System.currentTimeMillis()) {
            alarmTime = calculateAlarmTimeMillis(course, currentWeek + 1, course.alarmMinutesBefore)
        }

        if (alarmTime <= System.currentTimeMillis()) return

        // 计算延迟时间（分钟）
        val delayMillis = alarmTime - System.currentTimeMillis()
        val delayMinutes = (delayMillis / (1000 * 60)).coerceAtLeast(1)

        // 使用WorkManager安排提醒
        ExactAlarmWorker.scheduleReminder(context, course, delayMinutes)
        Log.d("AlarmService", "Scheduled WorkManager reminder for ${course.name} in $delayMinutes minutes")
    }

    /**
     * 设置所有课程的提醒
     * 在应用启动或设置更改时调用
     */
    fun scheduleAllReminders() {
        registerAllCourseNotifications()
    }

    // ────────── 每日凌晨闹钟刷新（解决 vivo/OPPO 杀进程后闹钟丢失问题）──────────

    companion object {
        private const val DAILY_REFRESH_REQUEST_CODE = 99999
        const val DAILY_REFRESH_ACTION = "com.cherry.wakeupschedule.DAILY_ALARM_REFRESH"

        /**
         * 调度每日凌晨闹钟刷新
         * 每天凌晨 4:00 自动重新注册所有课程闹钟，
         * 解决国产手机（vivo/OPPO/小米）杀进程后 AlarmManager 闹钟被清除的问题
         */
        fun scheduleDailyAlarmRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val settingsManager = SettingsManager(context)

            if (!settingsManager.isAlarmEnabled()) {
                DebugLogger.logInfo("每日刷新闹钟：课前提醒已关闭，跳过")
                return
            }

            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 4)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val intent = Intent(context, DailyAlarmReceiver::class.java).apply {
                action = DAILY_REFRESH_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DAILY_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(calendar.timeInMillis, null),
                    pendingIntent
                )
                DebugLogger.logInfo("每日刷新闹钟已设置：${calendar.time}")
            } catch (e: Exception) {
                DebugLogger.logError("每日刷新闹钟设置失败", e)
            }
        }

        /**
         * 取消每日凌晨闹钟刷新
         */
        fun cancelDailyAlarmRefresh(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, DailyAlarmReceiver::class.java).apply {
                action = DAILY_REFRESH_ACTION
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DAILY_REFRESH_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            DebugLogger.logInfo("每日刷新闹钟已取消")
        }
    }

    /**
     * 注册所有课程的通知
     * 为整个学期的每周课程安排闹钟
     */
    fun registerAllCourseNotifications() {
        // 取消所有现有通知
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
        notificationManager.cancelAll()

        // 启动每日凌晨刷新闹钟（防止 vivo/OPPO 杀进程后闹钟丢失）
        scheduleDailyAlarmRefresh(context)
        // 启动定期检查和前台服务
        CourseReminderWorker.schedulePeriodicCheck(context)
        scheduleForegroundServiceIfNeeded()

        val allCourses = CourseDataManager.getInstance(context).getAllCourses()
        val semesterEndWeek = allCourses.maxOfOrNull { it.endWeek } ?: 20

        // 先取消所有课程的旧闹钟，避免作息表变更后旧闹钟与新学期时间冲突
        allCourses.forEach { course ->
            cancelCourseAlarm(course)
        }

        // 为每门课程的每周安排通知
        allCourses.forEach { course ->
            if (course.alarmEnabled) {
                registerCourseNotificationsForSemester(course, semesterEndWeek)
            }
        }
        Log.d("AlarmService", "Registered all notifications for ${allCourses.size} courses for semester")
    }

    /**
     * 为课程的整个学期安排通知
     *
     * @param course 课程
     * @param semesterEndWeek 学期结束周
     */
    private fun registerCourseNotificationsForSemester(course: Course, semesterEndWeek: Int) {
        val currentWeek = getCurrentWeek()

        // 遍历课程的周数范围
        for (week in course.startWeek..Math.min(course.endWeek, semesterEndWeek)) {
            // 检查单双周
            if (!isWeekTypeMatched(course, week)) continue

            // 使用学期开始日期精确计算闹钟时间
            var alarmTime = calculateAlarmTimeMillis(course, week, course.alarmMinutesBefore)

            // 如果时间已过且是当前周，改为下周
            if (alarmTime <= System.currentTimeMillis()) {
                if (week == currentWeek) {
                    alarmTime = calculateAlarmTimeMillis(course, currentWeek + 1, course.alarmMinutesBefore)
                    if (alarmTime <= System.currentTimeMillis()) continue
                } else {
                    continue
                }
            }

            // 创建通知Intent
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("course_name", course.name)
                putExtra("course_teacher", course.teacher)
                putExtra("course_location", course.classroom)
                putExtra("course", course)
                putExtra("notification_week", week)
            }

            // 使用课程ID和周数生成唯一的通知ID
            val notificationId = (course.id * 100 + week).toInt()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 使用 setAlarmClock 确保系统将其视为高优先级闹钟
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(alarmTime, null),
                pendingIntent
            )
            Log.d("AlarmService", "Registered alarm for ${course.name} week $week at ${java.util.Date(alarmTime)}")
        }
    }

    /**
     * 取消所有课程的提醒
     */
    fun cancelAllReminders() {
        val allCourses = CourseDataManager.getInstance(context).getAllCourses()
        allCourses.forEach { course ->
            cancelCourseAlarm(course)
        }
        // 取消定期检查Worker
        CourseReminderWorker.cancelPeriodicCheck(context)
        // 取消每日凌晨刷新闹钟
        cancelDailyAlarmRefresh(context)
        // 停止前台服务
        stopForegroundService()
        Log.d("AlarmService", "Cancelled all reminders")
    }

    /**
     * 如果需要，启动前台服务
     */
    private fun scheduleForegroundServiceIfNeeded() {
        if (CourseReminderForegroundService.isRunning(context)) {
            return
        }
        CourseReminderForegroundService.start(context)
    }

    /**
     * 停止前台服务
     */
    private fun stopForegroundService() {
        CourseReminderForegroundService.stop(context)
    }

    /**
     * 获取当前周数
     * 根据学期开始日期计算
     *
     * @return 当前周数（1-20）
     */
    private fun getCurrentWeek(): Int {
        val settingsManager = SettingsManager(context)
        val semesterStartDate = settingsManager.getSemesterStartDate()

        // 如果未设置学期开始日期，返回默认值1
        if (semesterStartDate == 0L) {
            return 1
        }

        // 根据时间差计算周数
        val now = System.currentTimeMillis()
        val diffMillis = now - semesterStartDate
        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
        val week = (diffDays / 7) + 1

        return week.coerceIn(1, 20)
    }

    /**
     * 检查课程是否在当前单双周类型下应该显示
     *
     * @param course 课程
     * @param week 周数
     * @return true表示匹配
     */
    private fun isWeekTypeMatched(course: Course, week: Int): Boolean {
        return when (course.weekType) {
            1 -> week % 2 == 1   // 单周
            2 -> week % 2 == 0   // 双周
            else -> true          // 每周
        }
    }

    /**
     * 使用学期开始日期精确计算闹钟触发时间
     * 用学期开始日期 + 周偏移 + 星期偏移 来计算，避免 Calendar.set(DAY_OF_WEEK) 的解析不确定性
     *
     * @param course 课程
     * @param targetWeek 目标周数（1~20）
     * @param alarmMinutesBefore 提前提醒分钟数
     * @return 闹钟触发时间的毫秒时间戳，如果学期未设置则返回0
     */
    private fun calculateAlarmTimeMillis(course: Course, targetWeek: Int, alarmMinutesBefore: Int): Long {
        val settingsManager = SettingsManager(context)
        val semesterStart = settingsManager.getSemesterStartDate()
        if (semesterStart == 0L) return 0L

        // 学期开始日期是 Monday（周1），构造基准 Calendar
        val calendar = Calendar.getInstance().apply {
            timeInMillis = semesterStart
            // 清零时间部分
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 移动到目标周：semesterStart + (targetWeek - 1) * 7 天
        calendar.add(Calendar.DAY_OF_YEAR, (targetWeek - 1) * 7)
        // 移动到目标星期 dayOfWeek：1=周一, ..., 7=周日，偏移量为 dayOfWeek-1
        calendar.add(Calendar.DAY_OF_YEAR, course.dayOfWeek - 1)

        // 设置上课开始时间
        val timeSlots = timeTableManager.getTimeSlots()
        val timeSlot = timeSlots.find { it.node == course.startTime }
        if (timeSlot != null) {
            val timeParts = timeSlot.startTime.split(":")
            if (timeParts.size == 2) {
                calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                calendar.set(Calendar.MINUTE, timeParts[1].toInt())
            }
        } else {
            // 默认计算：每节课45分钟，从第1节8:00开始
            calendar.set(Calendar.HOUR_OF_DAY, 8 + (course.startTime - 1) * 45 / 60)
            calendar.set(Calendar.MINUTE, (course.startTime - 1) * 45 % 60)
        }

        // 减去提前提醒分钟数
        calendar.add(Calendar.MINUTE, -alarmMinutesBefore)

        return calendar.timeInMillis
    }
}

/**
 * 闹钟广播接收器
 * 当闹钟触发时接收广播并显示课程提醒通知
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        // 防重复通知：记录最近通知时间戳（课程名 -> 最后通知毫秒时间戳）
        private val lastNotificationTime = mutableMapOf<String, Long>()
        private const val DEBOUNCE_MS = 5000L // 5秒内同一课程不再弹通知
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let { DebugLogger.init(it) }
        DebugLogger.logInfo("AlarmReceiver onReceive called")
        intent?.let {
            val courseName = it.getStringExtra("course_name") ?: ""
            val teacher = it.getStringExtra("course_teacher") ?: ""
            val location = it.getStringExtra("course_location") ?: ""
            DebugLogger.logAlarmReceive(courseName)

            if (context != null && courseName.isNotEmpty()) {
                // 防重复通知：5秒内同一课程不重复弹通知
                val now = System.currentTimeMillis()
                lastNotificationTime[courseName]?.let { last ->
                    if (now - last < DEBOUNCE_MS) {
                        DebugLogger.logWarn("跳过重复通知: $courseName (距上次 ${now - last}ms)")
                        return
                    }
                }
                lastNotificationTime[courseName] = now
                // 清理超过1分钟的旧记录，防止 map 无限增长
                lastNotificationTime.entries.removeAll { now - it.value > 60000 }

                // 创建通知渠道
                val notificationHelper = NotificationHelper(context)
                notificationHelper.createNotificationChannels()

                // 获取课程对象
                val course = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    it.getSerializableExtra("course", Course::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    it.getSerializableExtra("course") as? Course
                }

                // 使用课程名+周次生成更精确的通知ID，先取消避免堆积
                val week = it.getIntExtra("notification_week", 0)
                val uniqueId = if (week > 0) "$courseName#$week".hashCode() else courseName.hashCode()
                notificationHelper.cancelNotification(uniqueId)

                // 构建并显示通知
                val notification = notificationHelper.buildCourseReminderNotification(
                    courseName = courseName,
                    teacher = teacher,
                    location = location,
                    minutesBefore = course?.alarmMinutesBefore ?: 15,
                    notificationId = uniqueId
                )

                notificationHelper.showNotification(uniqueId, notification, courseName)

                // 为下一周安排闹钟（持续性提醒）
                if (course != null) {
                    AlarmService(context).setCourseAlarm(course)
                }
            }
        }
    }
}

/**
 * 开机广播接收器
 * 设备启动后恢复所有课程的闹钟
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DebugLogger.init(context)
        // 只处理开机完成广播
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        // 在IO线程中恢复闹钟
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 如果闹钟启用，则恢复所有闹钟并设置每日刷新
                if (SettingsManager(context).isAlarmEnabled()) {
                    AlarmService(context).registerAllCourseNotifications()
                }
            } catch (e: Exception) {
                Log.e("BootCompletedReceiver", "Failed to restore alarms after boot", e)
            }
        }
    }
}

/**
 * 每日凌晨闹钟刷新接收器
 * 每天凌晨 4:00 自动重新注册所有课程闹钟，
 * 解决国产手机（vivo/OPPO/小米）杀进程后 AlarmManager 闹钟被清除的问题。
 * 用户当天无需手动打开应用即可确保课前通知正常工作。
 */
class DailyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        DebugLogger.init(context)
        DebugLogger.logInfo("DailyAlarmReceiver 触发：开始每日闹钟刷新")
        if (intent?.action != AlarmService.DAILY_REFRESH_ACTION) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settingsManager = SettingsManager(context)
                if (settingsManager.isAlarmEnabled()) {
                    val alarmService = AlarmService(context)
                    alarmService.registerAllCourseNotifications()
                    DebugLogger.logInfo("每日闹钟刷新完成")
                } else {
                    DebugLogger.logInfo("每日闹钟刷新：课前提醒已关闭，跳过")
                }
            } catch (e: Exception) {
                DebugLogger.logError("每日闹钟刷新失败", e)
                Log.e("DailyAlarmReceiver", "Failed to refresh daily alarms", e)
            }
        }
    }
}
