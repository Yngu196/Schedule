package com.cherry.wakeupschedule.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.cherry.wakeupschedule.R
import com.cherry.wakeupschedule.model.Course
import com.cherry.wakeupschedule.service.TimeTableManager

class CourseAdapter(
    private var courses: List<Course> = emptyList(),
    private val onCourseClick: (Course) -> Unit = {},
    private val onCourseLongClick: (Course) -> Unit = {}
) : RecyclerView.Adapter<CourseAdapter.CourseViewHolder>() {

    class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.card_course)
        val tvCourseName: TextView = itemView.findViewById(R.id.tv_course_name)
        val tvCourseTime: TextView = itemView.findViewById(R.id.tv_course_time)
        val tvCourseLocation: TextView = itemView.findViewById(R.id.tv_course_location)
        val tvCourseTeacher: TextView = itemView.findViewById(R.id.tv_course_teacher)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CourseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_course, parent, false)
        return CourseViewHolder(view)
    }

    override fun onBindViewHolder(holder: CourseViewHolder, position: Int) {
        val course = courses[position]
        
        holder.tvCourseName.text = course.name
        holder.tvCourseTime.text = TimeTableManager.getTimeRange(course.startTime, course.endTime)
        holder.tvCourseLocation.text = course.classroom
        holder.tvCourseTeacher.text = course.teacher
        
        // 设置课程颜色
        holder.cardView.setCardBackgroundColor(course.color)
        
        // 设置文本颜色为白色以确保可读性
        holder.tvCourseName.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
        holder.tvCourseTime.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
        holder.tvCourseLocation.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
        holder.tvCourseTeacher.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.white))
        
        holder.itemView.setOnClickListener {
            onCourseClick(course)
        }
        
        holder.itemView.setOnLongClickListener {
            onCourseLongClick(course)
            true
        }
    }

    override fun getItemCount(): Int = courses.size

    fun updateCourses(newCourses: List<Course>) {
        courses = newCourses
        notifyDataSetChanged()
    }
}