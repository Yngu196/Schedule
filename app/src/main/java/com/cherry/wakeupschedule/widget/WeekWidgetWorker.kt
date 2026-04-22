package com.cherry.wakeupschedule.widget

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class WeekWidgetWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            WeekCoursesWidgetProvider.triggerUpdate(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "week_widget_periodic_update"

        fun schedulePeriodicUpdate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<WeekWidgetWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancelPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}