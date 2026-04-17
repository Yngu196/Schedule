package com.cherry.wakeupschedule.service

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar

class SettingsManager(context: Context) {

    private val gson = Gson()
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_CURRENT_SEMESTER = "current_semester"
        private const val KEY_DEFAULT_WEEK = "default_week"
        private const val KEY_DEFAULT_ALARM_MINUTES = "default_alarm_minutes"
        private const val KEY_AUTO_SWITCH_WEEK = "auto_switch_week"
        private const val KEY_THEME = "theme"
        private const val KEY_BACKGROUND_TYPE = "background_type"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_ALARM_ENABLED = "alarm_enabled"
        private const val KEY_SEMESTER_START_DATE = "semester_start_date"
        private const val KEY_CUSTOM_BACKGROUND_PATH = "custom_background_path"
        private const val KEY_SOLID_BACKGROUND_COLOR = "solid_background_color"
        private const val KEY_COURSE_CARD_ALPHA = "course_card_alpha"
        private const val KEY_SHOW_NON_CURRENT_WEEK_COURSES = "show_non_current_week_courses"
        private const val KEY_NON_CURRENT_WEEK_ALPHA = "non_current_week_alpha"
        private const val KEY_VIEW_MODE = "view_mode"
        private const val KEY_CUSTOM_SEMESTERS = "custom_semesters"

        private const val DEFAULT_SEMESTER = "2024-2025学年 第一学期"
        private const val DEFAULT_WEEK = 1
        private const val DEFAULT_ALARM_MINUTES = 15
        private const val DEFAULT_AUTO_SWITCH = true
        private const val DEFAULT_THEME = "light"
        private const val DEFAULT_BACKGROUND_TYPE = "default"
        private const val DEFAULT_FONT_SIZE = "normal"
        private const val DEFAULT_ALARM_ENABLED = true
        private const val DEFAULT_SOLID_COLOR = -1  // 白色
        private const val DEFAULT_COURSE_CARD_ALPHA = 0.85f  // 课程卡片默认透明度
        private const val DEFAULT_SHOW_NON_CURRENT_WEEK_COURSES = true
        private const val DEFAULT_NON_CURRENT_WEEK_ALPHA = 0.3f  // 非本周课程默认透明度
    }
    
    // 获取当前学期
    fun getCurrentSemester(): String {
        return sharedPreferences.getString(KEY_CURRENT_SEMESTER, DEFAULT_SEMESTER) ?: DEFAULT_SEMESTER
    }
    
    // 设置当前学期
    fun setCurrentSemester(semester: String) {
        sharedPreferences.edit().putString(KEY_CURRENT_SEMESTER, semester).apply()
    }
    
    // 获取默认显示周
    fun getDefaultWeek(): Int {
        return sharedPreferences.getInt(KEY_DEFAULT_WEEK, DEFAULT_WEEK)
    }
    
    // 设置默认显示周
    fun setDefaultWeek(week: Int) {
        sharedPreferences.edit().putInt(KEY_DEFAULT_WEEK, week).apply()
    }
    
    // 获取默认闹钟提前时间
    fun getDefaultAlarmMinutes(): Int {
        return sharedPreferences.getInt(KEY_DEFAULT_ALARM_MINUTES, DEFAULT_ALARM_MINUTES)
    }
    
    // 设置默认闹钟提前时间
    fun setDefaultAlarmMinutes(minutes: Int) {
        sharedPreferences.edit().putInt(KEY_DEFAULT_ALARM_MINUTES, minutes).apply()
    }
    
    // 获取是否自动切换周
    fun getAutoSwitchWeek(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTO_SWITCH_WEEK, DEFAULT_AUTO_SWITCH)
    }
    
    // 设置是否自动切换周
    fun setAutoSwitchWeek(autoSwitch: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_AUTO_SWITCH_WEEK, autoSwitch).apply()
    }
    
    // 获取主题设置
    fun getTheme(): String {
        return sharedPreferences.getString(KEY_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
    }
    
    // 设置主题
    fun setTheme(theme: String) {
        sharedPreferences.edit().putString(KEY_THEME, theme).apply()
    }
    
    // 获取背景类型
    fun getBackgroundType(): String {
        return sharedPreferences.getString(KEY_BACKGROUND_TYPE, DEFAULT_BACKGROUND_TYPE) ?: DEFAULT_BACKGROUND_TYPE
    }
    
    // 设置背景类型
    fun setBackgroundType(backgroundType: String) {
        sharedPreferences.edit().putString(KEY_BACKGROUND_TYPE, backgroundType).apply()
    }
    
    // 获取字体大小
    fun getFontSize(): String {
        return sharedPreferences.getString(KEY_FONT_SIZE, DEFAULT_FONT_SIZE) ?: DEFAULT_FONT_SIZE
    }
    
    // 设置字体大小
    fun setFontSize(fontSize: String) {
        sharedPreferences.edit().putString(KEY_FONT_SIZE, fontSize).apply()
    }
    
    // 获取是否开启课前提醒
    fun isAlarmEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_ALARM_ENABLED, DEFAULT_ALARM_ENABLED)
    }
    
    // 设置是否开启课前提醒
    fun setAlarmEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ALARM_ENABLED, enabled).apply()
    }

    // 获取学期开始日期
    fun getSemesterStartDate(): Long {
        return sharedPreferences.getLong(KEY_SEMESTER_START_DATE, 0L)
    }

    // 设置学期开始日期
    fun setSemesterStartDate(dateMillis: Long) {
        sharedPreferences.edit().putLong(KEY_SEMESTER_START_DATE, dateMillis).apply()
    }

    // 获取自定义背景路径
    fun getCustomBackgroundPath(): String {
        return sharedPreferences.getString(KEY_CUSTOM_BACKGROUND_PATH, "") ?: ""
    }

    // 设置自定义背景路径
    fun setCustomBackgroundPath(path: String) {
        sharedPreferences.edit().putString(KEY_CUSTOM_BACKGROUND_PATH, path).apply()
    }

    // 获取纯色背景颜色
    fun getSolidBackgroundColor(): Int {
        return sharedPreferences.getInt(KEY_SOLID_BACKGROUND_COLOR, DEFAULT_SOLID_COLOR)
    }

    // 设置纯色背景颜色
    fun setSolidBackgroundColor(color: Int) {
        sharedPreferences.edit().putInt(KEY_SOLID_BACKGROUND_COLOR, color).apply()
    }

    // 获取课程卡片透明度
    fun getCourseCardAlpha(): Float {
        return sharedPreferences.getFloat(KEY_COURSE_CARD_ALPHA, DEFAULT_COURSE_CARD_ALPHA)
    }

    // 设置课程卡片透明度
    fun setCourseCardAlpha(alpha: Float) {
        sharedPreferences.edit().putFloat(KEY_COURSE_CARD_ALPHA, alpha.coerceIn(0.2f, 1.0f)).apply()
    }

    // 获取是否显示非本周课程
    fun isShowNonCurrentWeekCourses(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_NON_CURRENT_WEEK_COURSES, DEFAULT_SHOW_NON_CURRENT_WEEK_COURSES)
    }

    // 设置是否显示非本周课程
    fun setShowNonCurrentWeekCourses(show: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_NON_CURRENT_WEEK_COURSES, show).apply()
    }

    // 获取非本周课程透明度
    fun getNonCurrentWeekAlpha(): Float {
        return sharedPreferences.getFloat(KEY_NON_CURRENT_WEEK_ALPHA, DEFAULT_NON_CURRENT_WEEK_ALPHA)
    }

    // 设置非本周课程透明度
    fun setNonCurrentWeekAlpha(alpha: Float) {
        sharedPreferences.edit().putFloat(KEY_NON_CURRENT_WEEK_ALPHA, alpha.coerceIn(0.1f, 0.8f)).apply()
    }

    // 获取自定义学期列表
    fun getCustomSemesters(): List<String> {
        val json = sharedPreferences.getString(KEY_CUSTOM_SEMESTERS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson<List<String>>(json, type)
            } catch (e: Exception) {
                getDefaultSemesters()
            }
        } else {
            getDefaultSemesters()
        }
    }

    // 获取默认学期列表
    private fun getDefaultSemesters(): List<String> {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1

        val semesters = mutableListOf<String>()

        when {
            month >= 8 -> {
                semesters.add("${year}-${year + 1}学年 第一学期")
                semesters.add("${year}-${year + 1}学年 第二学期")
                semesters.add("${year + 1}-${year + 2}学年 第一学期")
            }
            month >= 2 && month <= 7 -> {
                semesters.add("${year - 1}-${year}学年 第二学期")
                semesters.add("${year}-${year + 1}学年 第一学期")
                semesters.add("${year}-${year + 1}学年 第二学期")
            }
            else -> {
                semesters.add("${year - 1}-${year}学年 第一学期")
                semesters.add("${year - 1}-${year}学年 第二学期")
                semesters.add("${year}-${year + 1}学年 第一学期")
            }
        }

        return semesters.distinct()
    }

    // 保存自定义学期列表
    fun saveCustomSemesters(semesters: List<String>) {
        val json = gson.toJson(semesters)
        sharedPreferences.edit().putString(KEY_CUSTOM_SEMESTERS, json).apply()
    }

    // 添加自定义学期
    fun addCustomSemester(semester: String) {
        val currentList = getCustomSemesters().toMutableList()
        if (!currentList.contains(semester)) {
            currentList.add(semester)
            saveCustomSemesters(currentList)
        }
    }

    // 删除自定义学期
    fun removeCustomSemester(semester: String) {
        val currentList = getCustomSemesters().toMutableList()
        currentList.remove(semester)
        saveCustomSemesters(currentList)
    }

    fun getViewMode(): String {
        return sharedPreferences.getString(KEY_VIEW_MODE, "week") ?: "week"
    }

    fun setViewMode(mode: String) {
        sharedPreferences.edit().putString(KEY_VIEW_MODE, mode).apply()
    }
}