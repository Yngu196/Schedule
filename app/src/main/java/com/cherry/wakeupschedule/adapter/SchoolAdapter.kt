package com.cherry.wakeupschedule.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.cherry.wakeupschedule.R
import com.cherry.wakeupschedule.School

class SchoolAdapter(
    private val schools: List<School>,
    private val onSchoolClick: (School) -> Unit
) : RecyclerView.Adapter<SchoolAdapter.SchoolViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SchoolViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_school, parent, false)
        return SchoolViewHolder(view)
    }

    override fun onBindViewHolder(holder: SchoolViewHolder, position: Int) {
        val school = schools[position]
        holder.bind(school)
        holder.itemView.setOnClickListener { onSchoolClick(school) }
    }

    override fun getItemCount(): Int = schools.size

    class SchoolViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSchoolName: TextView = itemView.findViewById(R.id.tv_school_name)
        
        fun bind(school: School) {
            tvSchoolName.text = school.name
        }
    }
}