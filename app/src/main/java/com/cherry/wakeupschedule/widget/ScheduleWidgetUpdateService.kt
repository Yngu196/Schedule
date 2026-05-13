package com.cherry.wakeupschedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 小组件更新服务
 * 用于管理小组件的定时更新
 */
class ScheduleWidgetUpdateService {

    companion object {
        private const val TAG = "ScheduleWidgetUpdate"

        /**
         * 触发所有小组件更新
         */
        fun triggerUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ScheduleWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            if (appWidgetIds.isNotEmpty()) {
                val provider = ScheduleWidgetProvider()
                provider.onUpdate(context, appWidgetManager, appWidgetIds)
            }

            val minimalComponentName = ComponentName(context, MinimalWidgetProvider::class.java)
            val minimalAppWidgetIds = appWidgetManager.getAppWidgetIds(minimalComponentName)
            if (minimalAppWidgetIds.isNotEmpty()) {
                val minimalProvider = MinimalWidgetProvider()
                minimalProvider.onUpdate(context, appWidgetManager, minimalAppWidgetIds)
            }

            scheduleNextUpdate(context)
        }

        /**
         * 调度下次小组件更新
         */
        fun scheduleNextUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WidgetUpdateReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                WIDGET_UPDATE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val updateInterval = 30 * 60 * 1000L

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + updateInterval,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + updateInterval,
                    pendingIntent
                )
            }
            Log.d(TAG, "已调度小组件下次更新")
        }

        /**
         * 取消定时更新
         */
        fun cancelScheduledUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WidgetUpdateReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                WIDGET_UPDATE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "已取消小组件定时更新")
        }

        private const val WIDGET_UPDATE_REQUEST_CODE = 10001
    }
}

/**
 * 小组件更新接收器
 */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            ScheduleWidgetUpdateService.triggerUpdate(it)
        }
    }
}

/**
 * 开机广播接收器
 * 设备启动后恢复小组件
 */
class WidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            context?.let {
                ScheduleWidgetUpdateService.triggerUpdate(it)
                ScheduleWidgetProvider().schedulePeriodicUpdate(it)
                MinimalWidgetProvider().schedulePeriodicUpdate(it)
                WidgetMidnightReceiver.scheduleMidnightUpdate(it)
            }
        }
    }
}

/**
 * 课程结束时更新小组件
 */
class WidgetCourseEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            val provider = ScheduleWidgetProvider()
            val appWidgetManager = AppWidgetManager.getInstance(it)
            provider.onUpdate(
                it,
                appWidgetManager,
                appWidgetManager.getAppWidgetIds(ComponentName(it, ScheduleWidgetProvider::class.java))
            )
            MinimalWidgetProvider().onUpdate(
                it,
                appWidgetManager,
                appWidgetManager.getAppWidgetIds(ComponentName(it, MinimalWidgetProvider::class.java))
            )
            ScheduleWidgetUpdateService.scheduleNextUpdate(it)
        }
    }
}

/**
 * 周期性更新小组件接收器
 */
class WidgetPeriodicUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            ScheduleWidgetProvider().onUpdate(
                it,
                AppWidgetManager.getInstance(it),
                AppWidgetManager.getInstance(it).getAppWidgetIds(
                    ComponentName(it, ScheduleWidgetProvider::class.java)
                )
            )
            ScheduleWidgetUpdateService.scheduleNextUpdate(it)
        }
    }
}

/**
 * 最小化小组件周期性更新接收器
 */
class MinimalWidgetPeriodicReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            MinimalWidgetProvider().onUpdate(
                it,
                AppWidgetManager.getInstance(it),
                AppWidgetManager.getInstance(it).getAppWidgetIds(
                    ComponentName(it, MinimalWidgetProvider::class.java)
                )
            )
            ScheduleWidgetUpdateService.scheduleNextUpdate(it)
        }
    }
}

/**
 * 最小化小组件课程结束更新接收器
 */
class MinimalWidgetCourseEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            MinimalWidgetProvider().onUpdate(
                it,
                AppWidgetManager.getInstance(it),
                AppWidgetManager.getInstance(it).getAppWidgetIds(
                    ComponentName(it, MinimalWidgetProvider::class.java)
                )
            )
            ScheduleWidgetUpdateService.scheduleNextUpdate(it)
        }
    }
}
