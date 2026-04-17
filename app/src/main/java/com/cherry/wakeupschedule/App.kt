package com.cherry.wakeupschedule

import android.app.Application
import android.util.Log
import com.cherry.wakeupschedule.service.AlarmService
import com.cherry.wakeupschedule.service.CourseReminderWorker
import com.cherry.wakeupschedule.service.NotificationHelper
import com.cherry.wakeupschedule.service.SettingsManager

class App : Application() {

    var alarmService: AlarmService? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        android.util.Log.d("App", "Application onCreate called")

        try {
            NotificationHelper(this).createNotificationChannels()
            android.util.Log.d("App", "Notification channels created")
        } catch (e: Exception) {
            android.util.Log.e("App", "Failed to create notification channels", e)
        }

        try {
            alarmService = AlarmService(this)
            android.util.Log.d("App", "AlarmService initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("App", "Failed to initialize AlarmService", e)
        }
    }

    fun registerAllCourseNotifications() {
        if (SettingsManager(this).isAlarmEnabled()) {
            alarmService?.registerAllCourseNotifications()
            Log.d("App", "All course notifications have been re-registered")
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }
}