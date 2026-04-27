package com.cherry.wakeupschedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.cherry.wakeupschedule.MainActivity
import com.cherry.wakeupschedule.R
import com.cherry.wakeupschedule.service.CourseDataManager
import com.cherry.wakeupschedule.service.SettingsManager
import com.cherry.wakeupschedule.service.TimeTableManager
import java.util.Calendar

class MinimalWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.cherry.wakeupschedule.widget.minimal.ACTION_REFRESH"
        private const val WIDGET_MINIMAL_PERIODIC_REQUEST_CODE = 10004
        private const val WIDGET_MINIMAL_COURSE_END_REQUEST_CODE = 10006
        private const val MINIMAL_PERIODIC_UPDATE_INTERVAL = 15 * 60 * 1000L

        fun triggerUpdate(context: Context) {
            val intent = Intent(context, MinimalWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        schedulePeriodicUpdate(context)
        scheduleNextCourseEndUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context)
        schedulePeriodicUpdate(context)
        WidgetMidnightReceiver.scheduleMidnightUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        try {
            cancelPeriodicUpdate(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> updateAllWidgets(context)
            "com.cherry.wakeupschedule.widget.ACTION_PERIODIC_UPDATE" -> updateAllWidgets(context)
        }
    }

    fun schedulePeriodicUpdate(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MinimalWidgetPeriodicReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                WIDGET_MINIMAL_PERIODIC_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + MINIMAL_PERIODIC_UPDATE_INTERVAL, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + MINIMAL_PERIODIC_UPDATE_INTERVAL, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cancelPeriodicUpdate(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, MinimalWidgetPeriodicReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                WIDGET_MINIMAL_PERIODIC_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scheduleNextCourseEndUpdate(context: Context) {
        try {
            val calendar = Calendar.getInstance()
            val dayOfWeek = if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else calendar.get(Calendar.DAY_OF_WEEK) - 1
            val currentTime = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            val currentWeek = calculateCurrentWeek(SettingsManager(context))

            val todayEndCourses = CourseDataManager.getInstance(context).getAllCourses()
                .filter { it.dayOfWeek == dayOfWeek && currentWeek in it.startWeek..it.endWeek && isCourseInCurrentWeekType(it, currentWeek) }
                .mapNotNull { val end = getCourseEndMinutes(context, it); if (end > currentTime) end to it else null }
                .sortedBy { it.first }

            if (todayEndCourses.isEmpty()) return
            val delayMillis = (todayEndCourses[0].first - currentTime) * 60 * 1000L
            if (delayMillis <= 0) {
                triggerWidgetUpdate(context)
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                WIDGET_MINIMAL_COURSE_END_REQUEST_CODE,
                Intent(context, WidgetCourseEndReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMillis, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerWidgetUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, MinimalWidgetProvider::class.java))
        if (appWidgetIds.isNotEmpty()) {
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MinimalWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isNotEmpty()) {
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_minimal)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        updateWidgetContent(context, views)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun updateWidgetContent(context: Context, views: RemoteViews) {
        try {
            val settingsManager = SettingsManager(context)
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val adjustedDayOfWeek = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)
            val currentTime = currentHour * 60 + currentMinute

            val currentWeek = calculateCurrentWeek(settingsManager)

            val courseDataManager = CourseDataManager.getInstance(context)
            val allCourses = courseDataManager.getAllCourses()
            val todayCourses = allCourses.filter { course ->
                course.dayOfWeek == adjustedDayOfWeek &&
                currentWeek >= course.startWeek &&
                currentWeek <= course.endWeek &&
                isCourseInCurrentWeekType(course, currentWeek)
            }.sortedBy { course -> getCourseStartMinutes(context, course) }

            val currentCourse = todayCourses.find { course ->
                val startMinutes = getCourseStartMinutes(context, course)
                val endMinutes = getCourseEndMinutes(context, course)
                currentTime in startMinutes..endMinutes
            }

            when {
                currentCourse != null -> {
                    views.setTextViewText(R.id.tv_widget_title, "下课倒计时")
                    views.setTextViewText(R.id.tv_course_name, currentCourse.name)
                    val endMinutes = getCourseEndMinutes(context, currentCourse)
                    val remainingMinutes = endMinutes - currentTime
                    views.setTextViewText(R.id.tv_countdown, "${remainingMinutes}分钟")
                    views.setTextViewText(R.id.tv_course_time, "后下课")
                }
                else -> {
                    views.setTextViewText(R.id.tv_widget_title, "下课倒计时")
                    views.setTextViewText(R.id.tv_course_name, "当前没课")
                    views.setTextViewText(R.id.tv_countdown, "--")
                    views.setTextViewText(R.id.tv_course_time, "")
                }
            }
        } catch (e: Exception) {
            views.setTextViewText(R.id.tv_widget_title, "下课倒计时")
            views.setTextViewText(R.id.tv_course_name, "加载失败")
            views.setTextViewText(R.id.tv_countdown, "--")
            views.setTextViewText(R.id.tv_course_time, "")
        }
    }

    private fun getCourseStartMinutes(context: Context, course: com.cherry.wakeupschedule.model.Course): Int {
        return try {
            val timeTableManager = TimeTableManager.getInstance(context)
            val timeSlots = timeTableManager.getTimeSlots()
            val startSlot = timeSlots.find { it.node == course.startTime }
            if (startSlot != null) {
                val parts = startSlot.startTime.split(":")
                if (parts.size == 2) {
                    parts[0].toInt() * 60 + parts[1].toInt()
                } else {
                    (8 + course.startTime) * 60
                }
            } else {
                (8 + course.startTime) * 60
            }
        } catch (e: Exception) {
            (8 + course.startTime) * 60
        }
    }

    private fun getCourseEndMinutes(context: Context, course: com.cherry.wakeupschedule.model.Course): Int {
        return try {
            val timeTableManager = TimeTableManager.getInstance(context)
            val timeSlots = timeTableManager.getTimeSlots()
            val endSlot = timeSlots.find { it.node == course.endTime }
            if (endSlot != null) {
                val parts = endSlot.endTime.split(":")
                if (parts.size == 2) {
                    parts[0].toInt() * 60 + parts[1].toInt()
                } else {
                    (8 + course.endTime) * 60 + 45
                }
            } else {
                (8 + course.endTime) * 60 + 45
            }
        } catch (e: Exception) {
            (8 + course.endTime) * 60 + 45
        }
    }

    private fun calculateCurrentWeek(settingsManager: SettingsManager): Int {
        val semesterStartDate = settingsManager.getSemesterStartDate()
        if (semesterStartDate == 0L) {
            return settingsManager.getDefaultWeek()
        }

        val now = System.currentTimeMillis()
        val diffMillis = now - semesterStartDate
        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
        val week = (diffDays / 7) + 1

        return week.coerceIn(1, 20)
    }

    private fun isCourseInCurrentWeekType(course: com.cherry.wakeupschedule.model.Course, currentWeek: Int): Boolean {
        return when (course.weekType) {
            0 -> true
            1 -> currentWeek % 2 == 1
            2 -> currentWeek % 2 == 0
            else -> true
        }
    }
}