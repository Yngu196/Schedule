package com.cherry.wakeupschedule.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.cherry.wakeupschedule.MainActivity
import com.cherry.wakeupschedule.R
import com.cherry.wakeupschedule.service.CourseDataManager
import com.cherry.wakeupschedule.service.SettingsManager
import com.cherry.wakeupschedule.service.TimeTableManager
import java.text.SimpleDateFormat
import java.util.*

class ScheduleWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.cherry.wakeupschedule.widget.ACTION_REFRESH"

        // 课程颜色数组
        private val courseColors = intArrayOf(
            Color.parseColor("#E57373"),
            Color.parseColor("#F06292"),
            Color.parseColor("#BA68C8"),
            Color.parseColor("#9575CD"),
            Color.parseColor("#7986CB"),
            Color.parseColor("#64B5F6"),
            Color.parseColor("#4FC3F7"),
            Color.parseColor("#4DD0E1"),
            Color.parseColor("#4DB6AC"),
            Color.parseColor("#81C784"),
            Color.parseColor("#FFB74D"),
            Color.parseColor("#FF8A65")
        )
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
        when (intent.action) {
            ACTION_REFRESH -> updateAllWidgets(context)
        }
    }

    private fun updateAllWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, ScheduleWidgetProvider::class.java)
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
        val views = RemoteViews(context.packageName, R.layout.widget_today_schedule)

        // 设置点击打开主应用
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

        // 更新小组件内容
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

            val semesterName = settingsManager.getCurrentSemester()
            views.setTextViewText(R.id.tv_widget_title, semesterName)

            val month = calendar.get(Calendar.MONTH) + 1
            val date = calendar.get(Calendar.DAY_OF_MONTH)
            val weekDays = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
            views.setTextViewText(R.id.tv_widget_date, "$month.$date ${weekDays[adjustedDayOfWeek]}")

            val currentWeek = calculateCurrentWeek(settingsManager)
            views.setTextViewText(R.id.tv_widget_week, "第${currentWeek}周")

            val courseDataManager = CourseDataManager.getInstance(context)
            val allCourses = courseDataManager.getAllCourses()
            val todayCourses = allCourses.filter { course ->
                course.dayOfWeek == adjustedDayOfWeek &&
                currentWeek >= course.startWeek &&
                currentWeek <= course.endWeek &&
                isCourseInCurrentWeekType(course, currentWeek)
            }

            val upcomingCourses = todayCourses.filter { course ->
                val courseEndTime = getCourseEndTimeInMinutes(context, course)
                courseEndTime > currentTime
            }.sortedBy { it.startTime }

            updateCourseDisplay(context, views, upcomingCourses, todayCourses)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateCourseDisplay(
        context: Context,
        views: RemoteViews,
        upcomingCourses: List<com.cherry.wakeupschedule.model.Course>,
        allTodayCourses: List<com.cherry.wakeupschedule.model.Course>
    ) {
        when {
            upcomingCourses.isEmpty() -> {
                // 没有未结束的课程
                if (allTodayCourses.isEmpty()) {
                    // 今天本来就没有课
                    views.setViewVisibility(R.id.course_item_1, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.course_item_2, android.view.View.GONE)

                    views.setInt(R.id.course_indicator_1, "setBackgroundColor", Color.parseColor("#CCCCCC"))
                    views.setTextViewText(R.id.tv_course_name_1, "今天没有课程")
                    views.setTextViewText(R.id.tv_course_location_1, "好好休息吧")
                    views.setTextViewText(R.id.tv_course_time_1, "")
                } else {
                    // 今天的课都上完了
                    views.setViewVisibility(R.id.course_item_1, android.view.View.VISIBLE)
                    views.setViewVisibility(R.id.course_item_2, android.view.View.GONE)

                    views.setInt(R.id.course_indicator_1, "setBackgroundColor", Color.parseColor("#4CAF50"))
                    views.setTextViewText(R.id.tv_course_name_1, "今日课程已完成")
                    views.setTextViewText(R.id.tv_course_location_1, "共${allTodayCourses.size}节课")
                    views.setTextViewText(R.id.tv_course_time_1, "明天继续加油！")
                }
            }
            upcomingCourses.size == 1 -> {
                // 只剩一节课
                val course = upcomingCourses[0]
                val color = courseColors[(course.id % courseColors.size).toInt()]

                views.setViewVisibility(R.id.course_item_1, android.view.View.VISIBLE)
                views.setInt(R.id.course_indicator_1, "setBackgroundColor", color)
                views.setTextViewText(R.id.tv_course_name_1, course.name)
                views.setTextViewText(R.id.tv_course_location_1, course.classroom)
                views.setTextViewText(R.id.tv_course_time_1, getCourseTimeString(context, course))

                // 第二行显示提示
                views.setViewVisibility(R.id.course_item_2, android.view.View.VISIBLE)
                views.setInt(R.id.course_indicator_2, "setBackgroundColor", Color.parseColor("#CCCCCC"))
                views.setTextViewText(R.id.tv_course_name_2, "今日最后一节课")
                views.setTextViewText(R.id.tv_course_location_2, "之后没有课程了")
                views.setTextViewText(R.id.tv_course_time_2, "")
            }
            else -> {
                // 显示接下来两节课
                val course1 = upcomingCourses[0]
                val course2 = upcomingCourses[1]
                val color1 = courseColors[(course1.id % courseColors.size).toInt()]
                val color2 = courseColors[(course2.id % courseColors.size).toInt()]

                views.setViewVisibility(R.id.course_item_1, android.view.View.VISIBLE)
                views.setInt(R.id.course_indicator_1, "setBackgroundColor", color1)
                views.setTextViewText(R.id.tv_course_name_1, course1.name)
                views.setTextViewText(R.id.tv_course_location_1, course1.classroom)
                views.setTextViewText(R.id.tv_course_time_1, getCourseTimeString(context, course1))

                views.setViewVisibility(R.id.course_item_2, android.view.View.VISIBLE)
                views.setInt(R.id.course_indicator_2, "setBackgroundColor", color2)
                views.setTextViewText(R.id.tv_course_name_2, course2.name)
                views.setTextViewText(R.id.tv_course_location_2, course2.classroom)
                views.setTextViewText(R.id.tv_course_time_2, getCourseTimeString(context, course2))
            }
        }
    }

    private fun getCourseEndTimeInMinutes(context: Context, course: com.cherry.wakeupschedule.model.Course): Int {
        return try {
            val timeTableManager = TimeTableManager.getInstance(context)
            val timeSlots = timeTableManager.getTimeSlots()
            val endSlot = timeSlots.find { it.node == course.endTime }
            if (endSlot != null) {
                val parts = endSlot.endTime.split(":")
                if (parts.size == 2) {
                    parts[0].toInt() * 60 + parts[1].toInt()
                } else {
                    // 默认假设每节课45分钟
                    (8 + course.endTime) * 60 + 45
                }
            } else {
                // 默认假设每节课45分钟
                (8 + course.endTime) * 60 + 45
            }
        } catch (e: Exception) {
            // 默认假设每节课45分钟
            (8 + course.endTime) * 60 + 45
        }
    }

    private fun getCourseTimeString(context: Context, course: com.cherry.wakeupschedule.model.Course): String {
        return try {
            val timeTableManager = TimeTableManager.getInstance(context)
            val timeSlots = timeTableManager.getTimeSlots()
            val startSlot = timeSlots.find { it.node == course.startTime }
            val endSlot = timeSlots.find { it.node == course.endTime }
            if (startSlot != null && endSlot != null) {
                "${startSlot.startTime} - ${endSlot.endTime}"
            } else {
                "第${course.startTime}-${course.endTime}节"
            }
        } catch (e: Exception) {
            "第${course.startTime}-${course.endTime}节"
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
            0 -> true // 每周
            1 -> currentWeek % 2 == 1 // 单周
            2 -> currentWeek % 2 == 0 // 双周
            else -> true
        }
    }
}
