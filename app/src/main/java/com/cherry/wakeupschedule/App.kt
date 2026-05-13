package com.cherry.wakeupschedule

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cherry.wakeupschedule.service.AlarmService
import com.cherry.wakeupschedule.service.CourseDataManager
import com.cherry.wakeupschedule.service.CourseReminderWorker
import com.cherry.wakeupschedule.service.NotificationHelper
import com.cherry.wakeupschedule.service.SettingsManager
import com.cherry.wakeupschedule.widget.MinimalWidgetProvider
import com.cherry.wakeupschedule.widget.ScheduleWidgetProvider
import com.cherry.wakeupschedule.widget.ScheduleWidgetUpdateService
import com.cherry.wakeupschedule.widget.WidgetMidnightReceiver

class App : Application() {

    var alarmService: AlarmService? = null
    private var timeTickReceiver: BroadcastReceiver? = null
    private val secondTickHandler = Handler(Looper.getMainLooper())
    private var secondTickRunnable: Runnable? = null

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
            CourseDataManager.getInstance(this)
            android.util.Log.d("App", "CourseDataManager initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("App", "Failed to initialize CourseDataManager", e)
        }

        try {
            alarmService = AlarmService(this)
            android.util.Log.d("App", "AlarmService initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("App", "Failed to initialize AlarmService", e)
        }

        try {
            ScheduleWidgetUpdateService.scheduleNextUpdate(this)
            WidgetMidnightReceiver.scheduleMidnightUpdate(this)
            android.util.Log.d("App", "Widget update chains initialized")
        } catch (e: Exception) {
            android.util.Log.e("App", "Failed to initialize widget update chains", e)
        }

        try {
            registerTimeTickReceiver()
        } catch (e: Exception) {
            android.util.Log.e("App", "Failed to register time tick receiver", e)
        }

        try {
            startSecondTick()
            Log.d("App", "Per-second widget tick started")
        } catch (e: Exception) {
            android.util.Log.e("App", "Failed to start per-second widget tick", e)
        }

        try {
            registerAllCourseNotifications()
        } catch (e: Exception) {
            android.util.Log.e("App", "Failed to restore course alarms on app init", e)
        }
    }

    private fun registerTimeTickReceiver() {
        timeTickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                context ?: return
                ScheduleWidgetProvider.triggerUpdate(context)
                MinimalWidgetProvider.triggerUpdate(context)
            }
        }
        registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        Log.d("App", "TIME_TICK receiver registered dynamically for per-minute widget updates")
    }

    private fun startSecondTick() {
        stopSecondTick()
        secondTickRunnable = object : Runnable {
            override fun run() {
                MinimalWidgetProvider.triggerUpdate(this@App)
                ScheduleWidgetProvider.triggerUpdate(this@App)
                secondTickHandler.postDelayed(this, 1000L)
            }
        }
        secondTickHandler.post(secondTickRunnable!!)
    }

    private fun stopSecondTick() {
        secondTickRunnable?.let { secondTickHandler.removeCallbacks(it) }
        secondTickRunnable = null
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
