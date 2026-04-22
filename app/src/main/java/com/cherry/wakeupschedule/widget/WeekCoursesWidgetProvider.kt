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
import java.text.SimpleDateFormat
import java.util.*

class WeekCoursesWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.cherry.wakeupschedule.widget.week.ACTION_REFRESH"

        fun triggerUpdate(context: Context) {
            val intent = Intent(context, WeekCoursesWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeekWidgetWorker.schedulePeriodicUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WeekWidgetWorker.cancelPeriodicUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            updateAllWidgets(context)
        }
    }

    private fun updateAllWidgets(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WeekCoursesWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_week_courses)
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
            val currentWeek = calculateCurrentWeek(settingsManager)
            val calendar = Calendar.getInstance()
            val dayOfWeek = if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else calendar.get(Calendar.DAY_OF_WEEK) - 1

            val dateFormat = SimpleDateFormat("M月d日", Locale.getDefault())
            val weekDayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val dateStr = "${dateFormat.format(calendar.time)} ${weekDayFormat.format(calendar.time)}"

            views.setTextViewText(R.id.tv_widget_title, "本周课程")
            views.setTextViewText(R.id.tv_widget_week, "第${currentWeek}周")
            views.setTextViewText(R.id.tv_widget_date, dateStr)

            val allCourses = try {
                CourseDataManager.getInstance(context).getAllCourses()
            } catch (e: Exception) {
                android.util.Log.e("WeekCoursesWidget", "Failed to get courses", e)
                emptyList()
            }

            val todayCourses = allCourses.filter { course ->
                course.dayOfWeek == dayOfWeek &&
                currentWeek >= course.startWeek &&
                currentWeek <= course.endWeek &&
                isCourseInCurrentWeekType(course, currentWeek)
            }.sortedBy { course -> course.startTime }

            val courseText = StringBuilder()
            for (course in todayCourses) {
                if (courseText.isNotEmpty()) {
                    courseText.append("\n")
                }
                courseText.append("${course.startTime}-${course.endTime}节 ${course.name}")
                if (course.classroom.isNotEmpty()) {
                    courseText.append(" @${course.classroom}")
                }
            }

            if (courseText.isEmpty()) {
                views.setTextViewText(R.id.tv_course_1, "今日无课程")
            } else {
                views.setTextViewText(R.id.tv_course_1, courseText.toString())
            }

            views.setTextViewText(R.id.tv_course_2, "")
            views.setTextViewText(R.id.tv_course_3, "")

        } catch (e: Exception) {
            android.util.Log.e("WeekCoursesWidget", "Failed to update widget", e)
        }
    }

    private fun calculateCurrentWeek(settingsManager: SettingsManager): Int {
        val startDate = settingsManager.getSemesterStartDate()
        if (startDate == 0L) return settingsManager.getDefaultWeek()
        return (((System.currentTimeMillis() - startDate) / (1000 * 60 * 60 * 24)).toInt() / 7 + 1).coerceIn(1, 20)
    }

    private fun isCourseInCurrentWeekType(course: com.cherry.wakeupschedule.model.Course, week: Int): Boolean {
        return when (course.weekType) {
            1 -> week % 2 == 1
            2 -> week % 2 == 0
            else -> true
        }
    }
}