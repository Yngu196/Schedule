package com.cherry.wakeupschedule

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cherry.wakeupschedule.adapter.CourseOverviewAdapter
import com.cherry.wakeupschedule.model.Course
import com.cherry.wakeupschedule.service.CourseDataManager
import com.cherry.wakeupschedule.service.SettingsManager

class CourseOverviewActivity : AppCompatActivity() {

    private lateinit var rvCourses: RecyclerView
    private lateinit var btnBack: ImageButton
    private lateinit var adapter: CourseOverviewAdapter

    private val courseColors = intArrayOf(
        Color.parseColor("#E53935"), Color.parseColor("#1E88E5"), Color.parseColor("#43A047"),
        Color.parseColor("#FDD835"), Color.parseColor("#F4511E"), Color.parseColor("#8E24AA"),
        Color.parseColor("#D81B60"), Color.parseColor("#00ACC1"), Color.parseColor("#FFB300"),
        Color.parseColor("#5E35B1"), Color.parseColor("#3949AB"), Color.parseColor("#039BE5"),
        Color.parseColor("#7CB342"), Color.parseColor("#C0CA33"), Color.parseColor("#FB8C00"),
        Color.parseColor("#AB47BC"), Color.parseColor("#E91E63"), Color.parseColor("#00897B"),
        Color.parseColor("#5C6BC0"), Color.parseColor("#26A69A"), Color.parseColor("#66BB6A"),
        Color.parseColor("#6D4C41"), Color.parseColor("#757575"), Color.parseColor("#546E7A")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_overview)

        rvCourses = findViewById(R.id.rv_courses)
        btnBack = findViewById(R.id.btn_back)

        btnBack.setOnClickListener { finish() }

        val courses = CourseDataManager.getInstance(this).getAllCourses()
        val sortedCourses = courses.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))

        adapter = CourseOverviewAdapter(this, sortedCourses, courseColors)
        rvCourses.layoutManager = LinearLayoutManager(this)
        rvCourses.adapter = adapter
    }
}