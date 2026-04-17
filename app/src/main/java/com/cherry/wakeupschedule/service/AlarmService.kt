package com.cherry.wakeupschedule.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cherry.wakeupschedule.model.Course
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmService(private val context: Context) {

    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private val timeTableManager by lazy { TimeTableManager.getInstance(context) }
    private val notificationHelper by lazy { NotificationHelper(context) }

    fun setCourseAlarm(course: Course) {
        if (!course.alarmEnabled) return

        val calendar = Calendar.getInstance()
        val currentWeek = getCurrentWeek()

        if (currentWeek < course.startWeek || currentWeek > course.endWeek) return
        if (!isWeekTypeMatched(course, currentWeek)) return

        calendar.set(Calendar.DAY_OF_WEEK, course.dayOfWeek + 1)

        val timeSlots = timeTableManager.getTimeSlots()
        val timeSlot = timeSlots.find { it.node == course.startTime }

        if (timeSlot != null) {
            val timeParts = timeSlot.startTime.split(":")
            if (timeParts.size == 2) {
                val startHour = timeParts[0].toInt()
                val startMinute = timeParts[1].toInt()
                calendar.set(Calendar.HOUR_OF_DAY, startHour)
                calendar.set(Calendar.MINUTE, startMinute - course.alarmMinutesBefore)
            }
        } else {
            val startHour = 8 + (course.startTime - 1) * 45 / 60
            val startMinute = (course.startTime - 1) * 45 % 60
            calendar.set(Calendar.HOUR_OF_DAY, startHour)
            calendar.set(Calendar.MINUTE, startMinute - course.alarmMinutesBefore)
        }
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 7)
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("course_name", course.name)
            putExtra("course_teacher", course.teacher)
            putExtra("course_location", course.classroom)
            putExtra("course", course)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            course.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            AlarmManagerCompat.setExact(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
        Log.d("AlarmService", "Alarm scheduled for ${course.name} at ${calendar.time}")
    }

    fun cancelCourseAlarm(course: Course) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            course.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        ExactAlarmWorker.cancelReminder(context, course.id)
        Log.d("AlarmService", "Cancelled alarm for ${course.name}")
    }

    fun scheduleExactReminder(course: Course) {
        if (!course.alarmEnabled) return

        val calendar = Calendar.getInstance()
        val currentWeek = getCurrentWeek()

        if (currentWeek < course.startWeek || currentWeek > course.endWeek) return
        if (!isWeekTypeMatched(course, currentWeek)) return

        calendar.set(Calendar.DAY_OF_WEEK, course.dayOfWeek + 1)

        val timeSlots = timeTableManager.getTimeSlots()
        val timeSlot = timeSlots.find { it.node == course.startTime }

        if (timeSlot != null) {
            val timeParts = timeSlot.startTime.split(":")
            if (timeParts.size == 2) {
                val startHour = timeParts[0].toInt()
                val startMinute = timeParts[1].toInt()
                calendar.set(Calendar.HOUR_OF_DAY, startHour)
                calendar.set(Calendar.MINUTE, startMinute - course.alarmMinutesBefore)
            }
        } else {
            val startHour = 8 + (course.startTime - 1) * 45 / 60
            val startMinute = (course.startTime - 1) * 45 % 60
            calendar.set(Calendar.HOUR_OF_DAY, startHour)
            calendar.set(Calendar.MINUTE, startMinute - course.alarmMinutesBefore)
        }
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 7)
        }

        val now = System.currentTimeMillis()
        val delayMillis = calendar.timeInMillis - now
        val delayMinutes = (delayMillis / (1000 * 60)).coerceAtLeast(1)

        ExactAlarmWorker.scheduleReminder(context, course, delayMinutes)
        Log.d("AlarmService", "Scheduled WorkManager reminder for ${course.name} in $delayMinutes minutes")
    }

    fun scheduleAllReminders() {
        CourseReminderWorker.schedulePeriodicCheck(context)
        scheduleForegroundServiceIfNeeded()

        val allCourses = CourseDataManager.getInstance(context).getAllCourses()
        allCourses.forEach { course ->
            if (course.alarmEnabled) {
                setCourseAlarm(course)
            }
        }
        Log.d("AlarmService", "Scheduled all reminders for ${allCourses.size} courses")
    }

    fun registerAllCourseNotifications() {
        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
        notificationManager.cancelAll()

        CourseReminderWorker.schedulePeriodicCheck(context)
        scheduleForegroundServiceIfNeeded()

        val allCourses = CourseDataManager.getInstance(context).getAllCourses()
        val semesterEndWeek = 20

        allCourses.forEach { course ->
            if (course.alarmEnabled) {
                registerCourseNotificationsForSemester(course, semesterEndWeek)
            }
        }
        Log.d("AlarmService", "Registered all notifications for ${allCourses.size} courses for semester")
    }

    private fun registerCourseNotificationsForSemester(course: Course, semesterEndWeek: Int) {
        for (week in course.startWeek..Math.min(course.endWeek, semesterEndWeek)) {
            if (!isWeekTypeMatched(course, week)) continue

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.DAY_OF_WEEK, course.dayOfWeek + 1)

            val timeSlots = timeTableManager.getTimeSlots()
            val timeSlot = timeSlots.find { it.node == course.startTime }

            if (timeSlot != null) {
                val timeParts = timeSlot.startTime.split(":")
                if (timeParts.size == 2) {
                    val startHour = timeParts[0].toInt()
                    val startMinute = timeParts[1].toInt()
                    calendar.set(Calendar.HOUR_OF_DAY, startHour)
                    calendar.set(Calendar.MINUTE, startMinute - course.alarmMinutesBefore)
                }
            } else {
                val startHour = 8 + (course.startTime - 1) * 45 / 60
                val startMinute = (course.startTime - 1) * 45 % 60
                calendar.set(Calendar.HOUR_OF_DAY, startHour)
                calendar.set(Calendar.MINUTE, startMinute - course.alarmMinutesBefore)
            }
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.set(Calendar.WEEK_OF_YEAR, calendar.get(Calendar.WEEK_OF_YEAR) + (week - getCurrentWeek()))

            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                continue
            }

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("course_name", course.name)
                putExtra("course_teacher", course.teacher)
                putExtra("course_location", course.classroom)
                putExtra("course", course)
                putExtra("notification_week", week)
            }

            val notificationId = (course.id * 100 + week).toInt()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                AlarmManagerCompat.setExact(
                    alarmManager,
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("AlarmService", "Registered alarm for ${course.name} week $week at ${calendar.time}")
        }
    }

    fun cancelAllReminders() {
        val allCourses = CourseDataManager.getInstance(context).getAllCourses()
        allCourses.forEach { course ->
            cancelCourseAlarm(course)
        }
        CourseReminderWorker.cancelPeriodicCheck(context)
        stopForegroundService()
        Log.d("AlarmService", "Cancelled all reminders")
    }

    private fun scheduleForegroundServiceIfNeeded() {
        if (CourseReminderForegroundService.isRunning(context)) {
            return
        }
        CourseReminderForegroundService.start(context)
    }

    private fun stopForegroundService() {
        CourseReminderForegroundService.stop(context)
    }

    private fun getCurrentWeek(): Int {
        val settingsManager = SettingsManager(context)
        val semesterStartDate = settingsManager.getSemesterStartDate()

        if (semesterStartDate == 0L) {
            return 1
        }

        val now = System.currentTimeMillis()
        val diffMillis = now - semesterStartDate
        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
        val week = (diffDays / 7) + 1

        return week.coerceIn(1, 20)
    }

    private fun isWeekTypeMatched(course: Course, week: Int): Boolean {
        return when (course.weekType) {
            1 -> week % 2 == 1
            2 -> week % 2 == 0
            else -> true
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            val courseName = it.getStringExtra("course_name") ?: ""
            val teacher = it.getStringExtra("course_teacher") ?: ""
            val location = it.getStringExtra("course_location") ?: ""

            if (context != null && courseName.isNotEmpty()) {
                val notificationHelper = NotificationHelper(context)
                notificationHelper.createNotificationChannels()

                val course = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    it.getSerializableExtra("course", Course::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    it.getSerializableExtra("course") as? Course
                }

                val notification = notificationHelper.buildCourseReminderNotification(
                    courseName = courseName,
                    teacher = teacher,
                    location = location,
                    minutesBefore = course?.alarmMinutesBefore ?: 15,
                    notificationId = courseName.hashCode()
                )

                notificationHelper.showNotification(courseName.hashCode(), notification)

                if (course != null) {
                    AlarmService(context).scheduleExactReminder(course)
                }
            }
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (SettingsManager(context).isAlarmEnabled()) {
                    AlarmService(context).registerAllCourseNotifications()
                }
            } catch (e: Exception) {
                Log.e("BootCompletedReceiver", "Failed to restore alarms after boot", e)
            }
        }
    }
}