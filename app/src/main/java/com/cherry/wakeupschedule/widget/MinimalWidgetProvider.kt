package com.cherry.wakeupschedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAllWidgets(context)
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

            val nextCourse = todayCourses.find { course ->
                val startMinutes = getCourseStartMinutes(context, course)
                startMinutes > currentTime
            }

            val currentCourse = todayCourses.find { course ->
                val startMinutes = getCourseStartMinutes(context, course)
                val endMinutes = getCourseEndMinutes(context, course)
                currentTime in startMinutes..endMinutes
            }

            when {
                currentCourse != null -> {
                    views.setTextViewText(R.id.tv_widget_title, "正在上课")
                    views.setTextViewText(R.id.tv_course_name, currentCourse.name)
                    val endMinutes = getCourseEndMinutes(context, currentCourse)
                    val remainingMinutes = endMinutes - currentTime
                    views.setTextViewText(R.id.tv_countdown, "${remainingMinutes}分钟")
                    views.setTextViewText(R.id.tv_course_time, "后下课")
                }
                nextCourse != null -> {
                    views.setTextViewText(R.id.tv_widget_title, "下节课")
                    views.setTextViewText(R.id.tv_course_name, nextCourse.name)
                    val startMinutes = getCourseStartMinutes(context, nextCourse)
                    val remainingMinutes = startMinutes - currentTime
                    views.setTextViewText(R.id.tv_countdown, "${remainingMinutes}分钟")
                    views.setTextViewText(R.id.tv_course_time, "后开始")
                }
                todayCourses.isNotEmpty() -> {
                    views.setTextViewText(R.id.tv_widget_title, "今日课程")
                    views.setTextViewText(R.id.tv_course_name, "已全部结束")
                    views.setTextViewText(R.id.tv_countdown, "✓")
                    views.setTextViewText(R.id.tv_course_time, "好好休息")
                }
                else -> {
                    views.setTextViewText(R.id.tv_widget_title, "今日课程")
                    views.setTextViewText(R.id.tv_course_name, "暂无课程")
                    views.setTextViewText(R.id.tv_countdown, "--")
                    views.setTextViewText(R.id.tv_course_time, "")
                }
            }
        } catch (e: Exception) {
            views.setTextViewText(R.id.tv_widget_title, "课程表")
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
