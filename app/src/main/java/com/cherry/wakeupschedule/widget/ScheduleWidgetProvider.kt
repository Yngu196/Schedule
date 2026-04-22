package com.cherry.wakeupschedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.widget.RemoteViews
import com.cherry.wakeupschedule.MainActivity
import com.cherry.wakeupschedule.R
import com.cherry.wakeupschedule.service.CourseDataManager
import com.cherry.wakeupschedule.service.SettingsManager
import com.cherry.wakeupschedule.service.TimeTableManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * 今日课程桌面小组件提供者
 * 显示当前日期和接下来要上的课程
 */
class ScheduleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.cherry.wakeupschedule.widget.ACTION_REFRESH"
        private const val WIDGET_COURSE_END_REQUEST_CODE = 10002
        private const val WIDGET_PERIODIC_UPDATE_REQUEST_CODE = 10003
        private const val PERIODIC_UPDATE_INTERVAL = 15 * 60 * 1000L // 15分钟
        private val courseColors = intArrayOf(
            Color.parseColor("#E57373"), Color.parseColor("#F06292"), Color.parseColor("#BA68C8"),
            Color.parseColor("#9575CD"), Color.parseColor("#7986CB"), Color.parseColor("#64B5F6"),
            Color.parseColor("#4FC3F7"), Color.parseColor("#4DD0E1"), Color.parseColor("#4DB6AC"),
            Color.parseColor("#81C784"), Color.parseColor("#FFB74D"), Color.parseColor("#FF8A65")
        )
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) updateAppWidget(context, appWidgetManager, appWidgetId)
        scheduleNextCourseEndUpdate(context)
        schedulePeriodicUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updateAllWidgets(context)
        schedulePeriodicUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> updateAllWidgets(context)
            "com.cherry.wakeupschedule.widget.ACTION_PERIODIC_UPDATE" -> updateAllWidgets(context)
        }
    }

    // 定期更新小组件
    private fun schedulePeriodicUpdate(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WidgetPeriodicUpdateReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                WIDGET_PERIODIC_UPDATE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + PERIODIC_UPDATE_INTERVAL, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + PERIODIC_UPDATE_INTERVAL, pendingIntent)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // 更新所有小组件
    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
        if (appWidgetIds.isNotEmpty()) onUpdate(context, appWidgetManager, appWidgetIds)
    }

    // 更新单个小组件
    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_today_schedule)
        views.setOnClickPendingIntent(R.id.widget_container,
            PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        updateWidgetContent(context, views)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    // 更新小组件内容
    private fun updateWidgetContent(context: Context, views: RemoteViews) {
        try {
            val settingsManager = SettingsManager(context)
            val calendar = Calendar.getInstance()
            val dayOfWeek = if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else calendar.get(Calendar.DAY_OF_WEEK) - 1
            val currentTime = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)

            views.setTextViewText(R.id.tv_widget_title, settingsManager.getCurrentSemester())
            views.setTextViewText(R.id.tv_widget_date, "${calendar.get(Calendar.MONTH) + 1}.${calendar.get(Calendar.DAY_OF_MONTH)} ${arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")[dayOfWeek]}")
            views.setTextViewText(R.id.tv_widget_week, "第${calculateCurrentWeek(settingsManager)}周")

            val allCourses = CourseDataManager.getInstance(context).getAllCourses()
            val currentWeek = calculateCurrentWeek(settingsManager)
            val todayCourses = allCourses.filter { it.dayOfWeek == dayOfWeek && currentWeek in it.startWeek..it.endWeek && isCourseInCurrentWeekType(it, currentWeek) }
            val upcomingCourses = todayCourses.filter { getCourseEndTimeInMinutes(context, it) > currentTime }.sortedBy { it.startTime }
            updateCourseDisplay(context, views, upcomingCourses, todayCourses)
        } catch (e: Exception) { e.printStackTrace() }
    }

    // 调度下次课程结束时更新小组件
    private fun scheduleNextCourseEndUpdate(context: Context) {
        try {
            val calendar = Calendar.getInstance()
            val dayOfWeek = if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 7 else calendar.get(Calendar.DAY_OF_WEEK) - 1
            val currentTime = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
            val currentWeek = calculateCurrentWeek(SettingsManager(context))

            val todayEndCourses = CourseDataManager.getInstance(context).getAllCourses()
                .filter { it.dayOfWeek == dayOfWeek && currentWeek in it.startWeek..it.endWeek && isCourseInCurrentWeekType(it, currentWeek) }
                .mapNotNull { val end = getCourseEndTimeInMinutes(context, it); if (end > currentTime) end to it else null }
                .sortedBy { it.first }

            if (todayEndCourses.isEmpty()) return
            val delayMillis = (todayEndCourses[0].first - currentTime) * 60 * 1000L
            if (delayMillis <= 0) { triggerWidgetUpdate(context); return }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(context, WIDGET_COURSE_END_REQUEST_CODE, Intent(context, WidgetCourseEndReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMillis, pendingIntent)
            else alarmManager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMillis, pendingIntent)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun triggerWidgetUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, ScheduleWidgetProvider::class.java))
        if (appWidgetIds.isNotEmpty()) onUpdate(context, appWidgetManager, appWidgetIds)
    }

    // 更新课程显示区域
    private fun updateCourseDisplay(context: Context, views: RemoteViews, upcomingCourses: List<com.cherry.wakeupschedule.model.Course>, allTodayCourses: List<com.cherry.wakeupschedule.model.Course>) {
        when {
            upcomingCourses.isEmpty() -> {
                views.setViewVisibility(R.id.course_item_1, android.view.View.VISIBLE); views.setViewVisibility(R.id.course_item_2, android.view.View.GONE)
                views.setInt(R.id.course_indicator_1, "setBackgroundColor", if (allTodayCourses.isEmpty()) Color.parseColor("#CCCCCC") else Color.parseColor("#4CAF50"))
                views.setTextViewText(R.id.tv_course_name_1, if (allTodayCourses.isEmpty()) "今天没有课程" else "今日课程已完成")
                views.setTextViewText(R.id.tv_course_location_1, if (allTodayCourses.isEmpty()) "好好休息吧" else "共${allTodayCourses.size}节课")
                views.setTextViewText(R.id.tv_course_time_1, if (allTodayCourses.isEmpty()) "" else "明天继续加油！")
            }
            upcomingCourses.size == 1 -> {
                val course = upcomingCourses[0]; val color = courseColors[(course.id % courseColors.size).toInt()]
                views.setViewVisibility(R.id.course_item_1, android.view.View.VISIBLE); views.setInt(R.id.course_indicator_1, "setBackgroundColor", color)
                views.setTextViewText(R.id.tv_course_name_1, course.name); views.setTextViewText(R.id.tv_course_location_1, course.classroom); views.setTextViewText(R.id.tv_course_time_1, getCourseTimeString(context, course))
                views.setViewVisibility(R.id.course_item_2, android.view.View.VISIBLE); views.setInt(R.id.course_indicator_2, "setBackgroundColor", Color.parseColor("#CCCCCC"))
                views.setTextViewText(R.id.tv_course_name_2, "今日最后一节课"); views.setTextViewText(R.id.tv_course_location_2, "之后没有课程了"); views.setTextViewText(R.id.tv_course_time_2, "")
            }
            else -> {
                val (c1, c2) = upcomingCourses[0] to upcomingCourses[1]
                views.setViewVisibility(R.id.course_item_1, android.view.View.VISIBLE); views.setInt(R.id.course_indicator_1, "setBackgroundColor", courseColors[(c1.id % courseColors.size).toInt()])
                views.setTextViewText(R.id.tv_course_name_1, c1.name); views.setTextViewText(R.id.tv_course_location_1, c1.classroom); views.setTextViewText(R.id.tv_course_time_1, getCourseTimeString(context, c1))
                views.setViewVisibility(R.id.course_item_2, android.view.View.VISIBLE); views.setInt(R.id.course_indicator_2, "setBackgroundColor", courseColors[(c2.id % courseColors.size).toInt()])
                views.setTextViewText(R.id.tv_course_name_2, c2.name); views.setTextViewText(R.id.tv_course_location_2, c2.classroom); views.setTextViewText(R.id.tv_course_time_2, getCourseTimeString(context, c2))
            }
        }
    }

    private fun getCourseEndTimeInMinutes(context: Context, course: com.cherry.wakeupschedule.model.Course): Int = try {
        val slot = TimeTableManager.getInstance(context).getTimeSlots().find { it.node == course.endTime }
        if (slot != null) { val p = slot.endTime.split(":"); p[0].toInt() * 60 + p[1].toInt() } else (8 + course.endTime) * 60 + 45
    } catch (e: Exception) { (8 + course.endTime) * 60 + 45 }

    private fun getCourseTimeString(context: Context, course: com.cherry.wakeupschedule.model.Course): String = try {
        val slots = TimeTableManager.getInstance(context).getTimeSlots()
        val start = slots.find { it.node == course.startTime }; val end = slots.find { it.node == course.endTime }
        if (start != null && end != null) "${start.startTime} - ${end.endTime}" else "第${course.startTime}-${course.endTime}节"
    } catch (e: Exception) { "第${course.startTime}-${course.endTime}节" }

    private fun calculateCurrentWeek(settingsManager: SettingsManager): Int {
        val startDate = settingsManager.getSemesterStartDate()
        if (startDate == 0L) return settingsManager.getDefaultWeek()
        return (((System.currentTimeMillis() - startDate) / (1000 * 60 * 60 * 24)).toInt() / 7 + 1).coerceIn(1, 20)
    }

    private fun isCourseInCurrentWeekType(course: com.cherry.wakeupschedule.model.Course, week: Int): Boolean = when (course.weekType) { 1 -> week % 2 == 1; 2 -> week % 2 == 0; else -> true }
}
