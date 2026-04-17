package com.cherry.wakeupschedule

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cherry.wakeupschedule.databinding.ActivityScheduleBinding

class ScheduleActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityScheduleBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupScheduleView()
    }
    
    private fun setupScheduleView() {
        // 设置课程表界面
    }
}