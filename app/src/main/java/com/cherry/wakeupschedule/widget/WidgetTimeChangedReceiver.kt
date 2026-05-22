package com.cherry.wakeupschedule.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cherry.wakeupschedule.service.AlarmService
import com.cherry.wakeupschedule.service.SettingsManager

class WidgetTimeChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        when (intent?.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // 更新小组件
                ScheduleWidgetProvider.triggerUpdate(context)
                MinimalWidgetProvider.triggerUpdate(context)
                WidgetMidnightReceiver.scheduleMidnightUpdate(context)
                ScheduleWidgetUpdateService.scheduleNextUpdate(context)

                // 时间/时区变更后，重新计算所有课程闹钟
                try {
                    if (SettingsManager(context).isAlarmEnabled()) {
                        AlarmService(context).registerAllCourseNotifications()
                        Log.d("WidgetTimeChanged", "时间变更后已重新注册所有课程闹钟")
                    }
                } catch (e: Exception) {
                    Log.e("WidgetTimeChanged", "重新注册闹钟失败", e)
                }
            }
        }
    }
}
