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

class ScheduleWidgetUpdateService {

    companion object {
        private const val TAG = "ScheduleWidgetUpdate"

        fun triggerUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ScheduleWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            if (appWidgetIds.isNotEmpty()) {
                val provider = ScheduleWidgetProvider()
                provider.onUpdate(context, appWidgetManager, appWidgetIds)
            }

            scheduleNextUpdate(context)
        }

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

class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            ScheduleWidgetUpdateService.triggerUpdate(it)
        }
    }
}

class WidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            context?.let {
                ScheduleWidgetUpdateService.triggerUpdate(it)
            }
        }
    }
}

class WidgetCourseEndReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.let {
            ScheduleWidgetProvider().onUpdate(
                it,
                AppWidgetManager.getInstance(it),
                AppWidgetManager.getInstance(it).getAppWidgetIds(
                    ComponentName(it, ScheduleWidgetProvider::class.java)
                )
            )
            MinimalWidgetProvider().onUpdate(
                it,
                AppWidgetManager.getInstance(it),
                AppWidgetManager.getInstance(it).getAppWidgetIds(
                    ComponentName(it, MinimalWidgetProvider::class.java)
                )
            )
        }
    }
}

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
            ScheduleWidgetProvider().schedulePeriodicUpdate(it)
        }
    }
}

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
            MinimalWidgetProvider().schedulePeriodicUpdate(it)
        }
    }
}
