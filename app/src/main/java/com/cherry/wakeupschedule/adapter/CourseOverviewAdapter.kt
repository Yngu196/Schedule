package com.cherry.wakeupschedule.adapter

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cherry.wakeupschedule.R
import com.cherry.wakeupschedule.model.Course

class CourseOverviewAdapter(
    private val context: Context,
    private val courses: List<Course>,
    private val courseColors: IntArray
) : RecyclerView.Adapter<CourseOverviewAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val colorIndicator: View = view.findViewById(R.id.color_indicator)
        val tvCourseName: TextView = view.findViewById(R.id.tv_course_name)
        val tvCourseInfo: TextView = view.findViewById(R.id.tv_course_info)
        val tvCourseWeeks: TextView = view.findViewById(R.id.tv_course_weeks)
        val tvCourseLocation: TextView = view.findViewById(R.id.tv_course_location)
        val tvCourseTeacher: TextView = view.findViewById(R.id.tv_course_teacher)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_course_overview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val course = courses[position]
        val color = if (course.color != 0) course.color else courseColors[(course.id % courseColors.size).toInt()]
        holder.colorIndicator.setBackgroundColor(color)
        holder.tvCourseName.text = course.name

        val dayNames = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val weekTypeStr = when (course.weekType) {
            1 -> " (单周)"
            2 -> " (双周)"
            else -> ""
        }
        holder.tvCourseInfo.text = "${dayNames[course.dayOfWeek]} 第${course.startTime}-${course.endTime}节$weekTypeStr"

        val weekRangeStr = "第${course.startWeek}-${course.endWeek}周"
        holder.tvCourseWeeks.text = weekRangeStr

        holder.tvCourseLocation.text = if (course.classroom.isNotBlank()) course.classroom else "未设置上课地点"
        holder.tvCourseLocation.visibility = View.VISIBLE

        holder.tvCourseTeacher.text = if (course.teacher.isNotBlank()) course.teacher else "未设置老师"
        holder.tvCourseTeacher.visibility = View.VISIBLE
    }

    override fun getItemCount(): Int = courses.size
}