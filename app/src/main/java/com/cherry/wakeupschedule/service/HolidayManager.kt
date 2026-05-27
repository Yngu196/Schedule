package com.cherry.wakeupschedule.service

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

/**
 * 节假日管理器
 * 用于管理节假日数据，判断某一天是否是节假日
 */
class HolidayManager private constructor(private val context: Context) {

    private val gson = Gson()
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _customHolidays: Set<String>
        get() = prefs.getStringSet(KEY_CUSTOM_HOLIDAYS, emptySet()) ?: emptySet()

    companion object {
        private const val PREFS_NAME = "holiday_data"
        private const val KEY_CUSTOM_HOLIDAYS = "custom_holidays"

        @Volatile
        private var instance: HolidayManager? = null

        fun getInstance(context: Context): HolidayManager {
            return instance ?: synchronized(this) {
                instance ?: HolidayManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * 内置中国法定节假日（固定日期的节假日
         */
        private val FIXED_HOLIDAYS = mapOf(
            "01-01" to "元旦",
            "05-01" to "劳动节",
            "10-01" to "国庆节",
            "12-25" to "圣诞节" // 可选
        )
    }

    /**
     * 判断指定日期是否是节假日
     * @param calendar 日期
     * @return 是否是节假日
     */
    fun isHoliday(calendar: Calendar): Boolean {
        val dateStr = formatDate(calendar)
        val monthDay = formatMonthDay(calendar)
        
        // 1. 检查自定义节假日
        if (_customHolidays.contains(dateStr)) return true

        // 2. 检查固定日期的节假日
        if (FIXED_HOLIDAYS.containsKey(monthDay)) return true

        // 3. 检查春节（农历节假日（近似计算农历
        return isLunarNewYear(calendar)
    }

    /**
     * 获取节假日名称，如果不是节假日返回 null
     */
    fun getHolidayName(calendar: Calendar): String? {
        val dateStr = formatDate(calendar)
        val monthDay = formatMonthDay(calendar)

        if (_customHolidays.contains(dateStr)) return "自定义节假日"

        FIXED_HOLIDAYS[monthDay]?.let { return it }

        if (isLunarNewYear(calendar)) return "春节"

        return null
    }

    /**
     * 添加自定义节假日
     */
    fun addCustomHoliday(date: Calendar) {
        val dateStr = formatDate(date)
        val newSet = _customHolidays.toMutableSet()
        newSet.add(dateStr)
        prefs.edit().putStringSet(KEY_CUSTOM_HOLIDAYS, newSet).apply()
    }

    /**
     * 移除自定义节假日
     */
    fun removeCustomHoliday(date: Calendar) {
        val dateStr = formatDate(date)
        val newSet = _customHolidays.toMutableSet()
        newSet.remove(dateStr)
        prefs.edit().putStringSet(KEY_CUSTOM_HOLIDAYS, newSet).apply()
    }

    /**
     * 获取所有自定义节假日
     */
    fun getCustomHolidays(): List<Calendar> {
        return _customHolidays.mapNotNull { dateStr ->
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = sdf.parse(dateStr)
                Calendar.getInstance().apply { time = date }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun formatDate(calendar: Calendar): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    private fun formatMonthDay(calendar: Calendar): String {
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return String.format("%02d-%02d", month, day)
    }

    /**
     * 简单农历春节判断（近似算法，覆盖农历新年期间
     * 春节一般在1月21日到2月20日之间
     */
    private fun isLunarNewYear(calendar: Calendar): Boolean {
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        if (month == 1 && day >= 21) return true
        if (month == 2 && day <= 20) return true
        return false
    }
}
