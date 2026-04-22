package com.cherry.wakeupschedule.service

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cherry.wakeupschedule.model.Course
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ExactAlarmWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val notificationHelper = NotificationHelper(context)
    private val courseDataManager = CourseDataManager.getInstance(context)

    companion object {
        private const val TAG = "ExactAlarmWorker"
        private const val KEY_COURSE_JSON = "course_json"
        private const val KEY_COURSE_ID = "course_id"

        fun scheduleReminder(context: Context, course: Course, delayMinutes: Long) {
            val courseJson = Gson().toJson(course)

            val inputData = Data.Builder()
                .putString(KEY_COURSE_JSON, courseJson)
                .putLong(KEY_COURSE_ID, course.id)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<ExactAlarmWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(inputData)
                .addTag("course_reminder_${course.id}")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d(TAG, "已调度课程 ${course.name} 的精确提醒，延迟 ${delayMinutes} 分钟")
        }

        fun cancelReminder(context: Context, courseId: Long) {
            WorkManager.getInstance(context).cancelAllWorkByTag("course_reminder_$courseId")
            Log.d(TAG, "已取消课程 ID=$courseId 的精确提醒")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val courseJson = inputData.getString(KEY_COURSE_JSON)
            @Suppress("UNUSED_VARIABLE")
            val courseId = inputData.getLong(KEY_COURSE_ID, -1)

            if (courseJson.isNullOrEmpty()) {
                Log.e(TAG, "课程数据为空")
                return@withContext Result.failure()
            }

            val course = Gson().fromJson(courseJson, Course::class.java)

            Log.d(TAG, "触发课程提醒：${course.name}")

            val notification = notificationHelper.buildCourseReminderNotification(
                courseName = course.name,
                teacher = course.teacher,
                location = course.classroom,
                minutesBefore = course.alarmMinutesBefore,
                notificationId = notificationHelper.getNotificationId(course.id)
            )

            notificationHelper.showNotification(notificationHelper.getNotificationId(course.id), notification)

            if (shouldReschedule(course)) {
                rescheduleNextWeek(course)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "处理精确提醒失败", e)
            Result.retry()
        }
    }

    private fun shouldReschedule(course: Course): Boolean {
        val currentWeek = getCurrentWeek()
        // 只在本学期周范围内且未超过结束周时重新调度
        return currentWeek in course.startWeek..course.endWeek
    }

    private fun rescheduleNextWeek(course: Course) {
        try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, 7)

            val timeTableManager = TimeTableManager.getInstance(applicationContext)
            val timeSlots = timeTableManager.getTimeSlots()
            val timeSlot = timeSlots.find { it.node == course.startTime }

            if (timeSlot != null) {
                val timeParts = timeSlot.startTime.split(":")
                if (timeParts.size == 2) {
                    calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                    calendar.set(Calendar.MINUTE, timeParts[1].toInt())
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    calendar.add(Calendar.MINUTE, -course.alarmMinutesBefore)

                    val now = System.currentTimeMillis()
                    val delayMillis = calendar.timeInMillis - now
                    val delayMinutes = (delayMillis / (1000 * 60)).coerceAtLeast(1)

                    scheduleReminder(applicationContext, course, delayMinutes)
                    Log.d(TAG, "已为课程 ${course.name} 调度下周提醒，延迟 ${delayMinutes} 分钟")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "调度下周提醒失败", e)
        }
    }

    private fun getCurrentWeek(): Int {
        val settingsManager = SettingsManager(applicationContext)
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
}
