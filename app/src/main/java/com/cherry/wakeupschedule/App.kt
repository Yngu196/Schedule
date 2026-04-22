package com.cherry.wakeupschedule

import android.app.Application
import android.util.Log
import com.cherry.wakeupschedule.service.AlarmService
import com.cherry.wakeupschedule.service.CourseDataManager
import com.cherry.wakeupschedule.service.CourseReminderWorker
import com.cherry.wakeupschedule.service.NotificationHelper
import com.cherry.wakeupschedule.service.SettingsManager

/**
 * 应用Application类
 * 全局初始化闹钟服务和通知渠道
 */
class App : Application() {

    // 闹钟服务实例
    var alarmService: AlarmService? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        android.util.Log.d("App", "Application onCreate called")

        try {
            // 创建通知渠道（Android 8.0+必需）
            NotificationHelper(this).createNotificationChannels()
            android.util.Log.d("App", "Notification channels created")
        } catch (e: Exception) {
            android.util.Log.e("App", "Failed to create notification channels", e)
        }

        try {
            // 初始化课程数据管理器（确保Widget可以访问）
            CourseDataManager.getInstance(this)
            android.util.Log.d("App", "CourseDataManager initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("App", "Failed to initialize CourseDataManager", e)
        }

        try {
            // 初始化闹钟服务
            alarmService = AlarmService(this)
            android.util.Log.d("App", "AlarmService initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("App", "Failed to initialize AlarmService", e)
        }
    }

    // 重新注册所有课程通知
    fun registerAllCourseNotifications() {
        if (SettingsManager(this).isAlarmEnabled()) {
            alarmService?.registerAllCourseNotifications()
            Log.d("App", "All course notifications have been re-registered")
        }
    }

    companion object {
        // Application单例
        lateinit var instance: App
            private set
    }
}
