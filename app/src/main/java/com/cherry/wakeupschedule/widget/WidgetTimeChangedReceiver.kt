package com.cherry.wakeupschedule.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetTimeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        when (intent?.action) {
            Intent.ACTION_TIME_TICK -> {
                MinimalWidgetProvider.triggerUpdate(context)
                ScheduleWidgetProvider.triggerUpdate(context)
            }
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                ScheduleWidgetProvider.triggerUpdate(context)
                MinimalWidgetProvider.triggerUpdate(context)
            }
        }
    }
}