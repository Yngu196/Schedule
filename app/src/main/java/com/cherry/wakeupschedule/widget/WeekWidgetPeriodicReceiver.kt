package com.cherry.wakeupschedule.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WeekWidgetPeriodicReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updateIntent = Intent(context, WeekCoursesWidgetProvider::class.java).apply {
            action = WeekCoursesWidgetProvider.ACTION_REFRESH
        }
        context.sendBroadcast(updateIntent)
    }
}