package com.cherry.wakeupschedule.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cherry.wakeupschedule.App
import com.cherry.wakeupschedule.model.Course
import com.cherry.wakeupschedule.service.AlarmService
import com.cherry.wakeupschedule.service.CourseDataManager
import com.cherry.wakeupschedule.service.SettingsManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

class CourseViewModel(application: Application) : AndroidViewModel(application) {

    private val _courses = MutableLiveData<List<Course>>()
    val courses: LiveData<List<Course>> = _courses

    private val courseDataManager = CourseDataManager.getInstance(application)
    private val settingsManager = SettingsManager(application)
    private val alarmService = App.instance.alarmService
    @Volatile
    private var activeWeek: Int = calculateCurrentWeek()

    init {
        // 监听课程数据变化
        viewModelScope.launch {
            courseDataManager.coursesFlow.collect { allCourses ->
                val week = activeWeek
                val coursesForWeek = allCourses.filter { course ->
                    week in course.startWeek..course.endWeek
                }
                _courses.postValue(coursesForWeek)
            }
        }
    }

    fun getAllCourses(): Flow<List<Course>> {
        return flow { emit(courseDataManager.getAllCourses()) }
    }

    fun getCoursesByDay(dayOfWeek: Int): Flow<List<Course>> {
        return flow {
            emit(courseDataManager.getAllCourses().filter { it.dayOfWeek == dayOfWeek })
        }
    }

    fun loadCoursesForWeek(week: Int) {
        activeWeek = week
        viewModelScope.launch {
            val coursesForWeek = courseDataManager.getCoursesForWeek(week)
            _courses.postValue(coursesForWeek)
        }
    }

    fun addCourse(course: Course) {
        viewModelScope.launch {
            courseDataManager.addCourse(course)
            // 设置闹钟
            alarmService?.setCourseAlarm(course)
        }
    }

    fun addCourses(courses: List<Course>) {
        viewModelScope.launch {
            courseDataManager.addCourses(courses)
            // 为所有课程设置闹钟
            courses.forEach { course ->
                alarmService?.setCourseAlarm(course)
            }
        }
    }

    fun updateCourse(course: Course) {
        viewModelScope.launch {
            courseDataManager.updateCourse(course)
            // 更新闹钟
            alarmService?.setCourseAlarm(course)
        }
    }

    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            courseDataManager.deleteCourse(course)
            // 取消闹钟
            alarmService?.cancelCourseAlarm(course)
        }
    }

    fun clearAllCourses() {
        viewModelScope.launch {
            courseDataManager.clearAllCourses()
        }
    }

    fun refreshCourses() {
        viewModelScope.launch {
            courseDataManager.refreshCourses()
        }
    }

    private fun calculateCurrentWeek(): Int {
        val semesterStartDate = settingsManager.getSemesterStartDate()
        if (semesterStartDate <= 0L) {
            return settingsManager.getDefaultWeek().coerceIn(1, 20)
        }
        val diffMillis = System.currentTimeMillis() - semesterStartDate
        val diffDays = (diffMillis / (24 * 60 * 60 * 1000L)).toInt()
        return ((diffDays / 7) + 1).coerceIn(1, 20)
    }
}
