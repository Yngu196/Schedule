package com.cherry.wakeupschedule.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cherry.wakeupschedule.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class CourseReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val courseDataManager = CourseDataManager.getInstance(context)
    private val timeTableManager = TimeTableManager.getInstance(context)
    private val alarmService = AlarmService(context)

    companion object {
        private const val TAG = "CourseReminderWorker"
        private const val WORK_NAME = "course_reminder_check"
        private val REPEAT_INTERVAL_MINUTES = 15L

        fun schedulePeriodicCheck(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<CourseReminderWorker>(
                REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )

            Log.d(TAG, "已调度定期课程检查工作，每 ${REPEAT_INTERVAL_MINUTES} 分钟执行一次")
        }

        fun cancelPeriodicCheck(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "已取消定期课程检查工作")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始检查课程提醒...")

            if (!SettingsManager(applicationContext).isAlarmEnabled()) {
                Log.d(TAG, "课前提醒已关闭，跳过检查")
                return@withContext Result.success()
            }

            val currentWeek = getCurrentWeek()
            val currentDayOfWeek = getCurrentDayOfWeek()
            val currentTime = getCurrentTimeSlot()

            val allCourses = courseDataManager.getAllCourses()
            val todayCourses = allCourses.filter { course ->
                course.alarmEnabled &&
                course.dayOfWeek == currentDayOfWeek &&
                currentWeek in course.startWeek..course.endWeek &&
                isWeekTypeMatched(course, currentWeek)
            }

            todayCourses.forEach { course ->
                val timeSlots = timeTableManager.getTimeSlots()
                val timeSlot = timeSlots.find { it.node == course.startTime }

                if (timeSlot != null) {
                    val courseStartMinutes = timeSlot.startTime.split(":").let {
                        it[0].toInt() * 60 + it[1].toInt()
                    }
                    val currentMinutes = currentTime.first * 60 + currentTime.second
                    val minutesUntilClass = courseStartMinutes - currentMinutes - course.alarmMinutesBefore

                    when {
                        minutesUntilClass in 0..REPEAT_INTERVAL_MINUTES.toInt() -> {
                            Log.d(TAG, "课程 ${course.name} 将在 ${minutesUntilClass} 分钟后开始，调度精确提醒")
                            alarmService.scheduleExactReminder(course)
                        }
                        minutesUntilClass > REPEAT_INTERVAL_MINUTES -> {
                            Log.d(TAG, "课程 ${course.name} 还有 ${minutesUntilClass} 分钟，暂时不处理")
                        }
                        else -> {
                            Log.d(TAG, "课程 ${course.name} 已错过提醒或已开始")
                        }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "检查课程提醒失败", e)
            Result.retry()
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

    private fun getCurrentDayOfWeek(): Int {
        return Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
    }

    private fun getCurrentTimeSlot(): Pair<Int, Int> {
        val calendar = Calendar.getInstance()
        return Pair(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    private fun isWeekTypeMatched(course: Course, week: Int): Boolean {
        return when (course.weekType) {
            1 -> week % 2 == 1
            2 -> week % 2 == 0
            else -> true
        }
    }
}
