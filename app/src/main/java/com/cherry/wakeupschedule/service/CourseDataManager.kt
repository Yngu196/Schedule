package com.cherry.wakeupschedule.service

import android.content.Context
import android.content.SharedPreferences
import com.cherry.wakeupschedule.model.Course
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CourseDataManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _coursesFlow = MutableStateFlow<List<Course>>(emptyList())
    val coursesFlow: StateFlow<List<Course>> = _coursesFlow

    init {
        loadCoursesFromPrefs()
    }

    private fun loadCoursesFromPrefs() {
        val coursesJson = prefs.getString(KEY_COURSES, null)
        android.util.Log.d("CourseDataManager", "loadCoursesFromPrefs: JSON数据存在=${coursesJson != null}")
        if (coursesJson != null) {
            try {
                val type = object : TypeToken<List<Course>>() {}.type
                val courses: List<Course> = gson.fromJson(coursesJson, type)
                android.util.Log.d("CourseDataManager", "loadCoursesFromPrefs: 解析到${courses.size}门课程")
                _coursesFlow.value = courses
            } catch (e: Exception) {
                android.util.Log.e("CourseDataManager", "loadCoursesFromPrefs: 解析失败", e)
                _coursesFlow.value = emptyList()
            }
        }
    }

    private fun saveCoursesToPrefs(courses: List<Course>) {
        val coursesJson = gson.toJson(courses)
        prefs.edit().putString(KEY_COURSES, coursesJson).apply()
    }

    fun getAllCourses(): List<Course> {
        return _coursesFlow.value
    }

    fun getCoursesForWeek(week: Int): List<Course> {
        return _coursesFlow.value.filter { course ->
            val isInWeekRange = week in course.startWeek..course.endWeek
            val isWeekTypeMatch = when (course.weekType) {
                0 -> true // 每周
                1 -> week % 2 == 1 // 单周
                2 -> week % 2 == 0 // 双周
                else -> true
            }
            isInWeekRange && isWeekTypeMatch
        }
    }

    fun addCourse(course: Course): Course {
        val currentCourses = _coursesFlow.value.toMutableList()
        val newId = if (currentCourses.isEmpty()) 1L else currentCourses.maxOf { it.id } + 1
        val newCourse = course.copy(id = newId)
        currentCourses.add(newCourse)
        _coursesFlow.value = currentCourses
        saveCoursesToPrefs(currentCourses)
        return newCourse
    }

    fun addCourses(courses: List<Course>) {
        val currentCourses = _coursesFlow.value.toMutableList()
        var nextId = if (currentCourses.isEmpty()) 1L else currentCourses.maxOf { it.id } + 1
        courses.forEach { course ->
            val newCourse = course.copy(id = nextId++)
            currentCourses.add(newCourse)
        }
        _coursesFlow.value = currentCourses
        saveCoursesToPrefs(currentCourses)
    }

    fun updateCourse(course: Course) {
        val currentCourses = _coursesFlow.value.toMutableList()
        val index = currentCourses.indexOfFirst { it.id == course.id }
        if (index != -1) {
            currentCourses[index] = course
            _coursesFlow.value = currentCourses
            saveCoursesToPrefs(currentCourses)
        }
    }

    fun deleteCourse(course: Course) {
        val currentCourses = _coursesFlow.value.toMutableList()
        currentCourses.removeAll { it.id == course.id }
        _coursesFlow.value = currentCourses
        saveCoursesToPrefs(currentCourses)
    }

    fun clearAllCourses() {
        _coursesFlow.value = emptyList()
        saveCoursesToPrefs(emptyList())
    }

    fun refreshCourses() {
        loadCoursesFromPrefs()
    }

    companion object {
        private const val PREFS_NAME = "course_data"
        private const val KEY_COURSES = "courses"

        @Volatile
        private var instance: CourseDataManager? = null

        fun getInstance(context: Context): CourseDataManager {
            return instance ?: synchronized(this) {
                instance ?: CourseDataManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
