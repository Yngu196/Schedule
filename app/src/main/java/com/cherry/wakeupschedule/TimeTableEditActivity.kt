package com.cherry.wakeupschedule

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cherry.wakeupschedule.databinding.ActivityTimeTableEditBinding
import com.cherry.wakeupschedule.service.TimeTableManager
import java.util.Calendar

class TimeTableEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimeTableEditBinding
    private lateinit var timeTableManager: TimeTableManager
    
    private val timeSlotViews = mutableListOf<TimeSlotView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimeTableEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        timeTableManager = TimeTableManager.getInstance(this)

        setupToolbar()
        setupButtons()
        loadAndDisplayTimeSlots()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "编辑时间表"
    }

    private fun setupButtons() {
        binding.btnSetMaxNodes.setOnClickListener {
            showMaxNodesDialog()
        }

        binding.btnResetToDefault.setOnClickListener {
            showResetConfirmDialog()
        }

        binding.btnSetAsDefault.setOnClickListener {
            setCurrentAsDefault()
        }

        binding.btnSave.setOnClickListener {
            saveTimeSlots()
        }
    }
    
    private fun setCurrentAsDefault() {
        timeTableManager.setCurrentAsDefault()
        Toast.makeText(this, "当前时间表已设为默认", Toast.LENGTH_SHORT).show()
    }

    private fun loadAndDisplayTimeSlots() {
        val maxNodes = timeTableManager.getMaxNodes()
        binding.tvMaxNodes.text = "$maxNodes 节"

        val timeSlots = timeTableManager.getTimeSlots().sortedBy { it.node }
        
        binding.llTimeSlots.removeAllViews()
        timeSlotViews.clear()

        for (node in 1..maxNodes) {
            val timeSlot = timeSlots.find { it.node == node }
                ?: TimeTableManager.TimeSlot(node, getDefaultStartTime(node), getDefaultEndTime(node))
            
            addTimeSlotView(timeSlot)
        }
    }

    private fun addTimeSlotView(timeSlot: TimeTableManager.TimeSlot) {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_time_slot_simple, binding.llTimeSlots, false)

        val tvNode = view.findViewById<TextView>(R.id.tv_node)
        val tvStartTime = view.findViewById<TextView>(R.id.tv_start_time)
        val tvEndTime = view.findViewById<TextView>(R.id.tv_end_time)

        tvNode.text = "第${timeSlot.node}节"
        tvStartTime.text = timeSlot.startTime
        tvEndTime.text = timeSlot.endTime

        tvStartTime.setOnClickListener {
            showTimePicker(tvStartTime)
        }

        tvEndTime.setOnClickListener {
            showTimePicker(tvEndTime)
        }

        val slotView = TimeSlotView(timeSlot.node, tvStartTime, tvEndTime)
        timeSlotViews.add(slotView)
        binding.llTimeSlots.addView(view)
    }

    private fun showTimePicker(textView: TextView) {
        val currentText = textView.text.toString()
        var hour = 8
        var minute = 0
        if (currentText.isNotEmpty() && currentText.contains(":")) {
            val parts = currentText.split(":")
            hour = parts[0].toIntOrNull() ?: 8
            minute = parts[1].toIntOrNull() ?: 0
        }

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            textView.text = String.format("%02d:%02d", selectedHour, selectedMinute)
        }, hour, minute, true).show()
    }

    private fun showMaxNodesDialog() {
        val currentMax = timeTableManager.getMaxNodes()
        val nodes = (4..16).map { "$it 节" }.toTypedArray()
        val currentIndex = (4..16).indexOf(currentMax).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("设置每天课程数")
            .setSingleChoiceItems(nodes, currentIndex) { dialog, which ->
                val maxNodes = which + 4
                timeTableManager.setMaxNodes(maxNodes)
                binding.tvMaxNodes.text = "$maxNodes 节"
                
                val currentSlots = timeTableManager.getTimeSlots()
                val maxNodeInSlots = currentSlots.maxOfOrNull { it.node } ?: 0
                if (maxNodes > maxNodeInSlots) {
                    for (node in (maxNodeInSlots + 1)..maxNodes) {
                        timeTableManager.addTimeSlot(
                            node, 
                            getDefaultStartTime(node), 
                            getDefaultEndTime(node)
                        )
                    }
                }
                
                dialog.dismiss()
                loadAndDisplayTimeSlots()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("确认重置")
            .setMessage("确定要重置为默认时间表吗？")
            .setPositiveButton("重置") { _, _ ->
                timeTableManager.resetToDefault()
                Toast.makeText(this, "已重置为默认时间表", Toast.LENGTH_SHORT).show()
                loadAndDisplayTimeSlots()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveTimeSlots() {
        for (slotView in timeSlotViews) {
            timeTableManager.updateTimeSlot(
                slotView.node,
                slotView.tvStartTime.text.toString(),
                slotView.tvEndTime.text.toString()
            )
        }
        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun getDefaultStartTime(node: Int): String {
        return when (node) {
            1 -> "08:00"
            2 -> "08:55"
            3 -> "10:00"
            4 -> "10:55"
            5 -> "14:30"
            6 -> "15:25"
            7 -> "16:30"
            8 -> "17:25"
            9 -> "19:00"
            10 -> "19:55"
            11 -> "20:50"
            12 -> "21:45"
            13 -> "22:40"
            14 -> "23:35"
            15 -> "00:30"
            16 -> "01:25"
            else -> "08:00"
        }
    }

    private fun getDefaultEndTime(node: Int): String {
        return when (node) {
            1 -> "08:45"
            2 -> "09:40"
            3 -> "10:45"
            4 -> "11:40"
            5 -> "15:15"
            6 -> "16:10"
            7 -> "17:15"
            8 -> "18:10"
            9 -> "19:45"
            10 -> "20:40"
            11 -> "21:35"
            12 -> "22:30"
            13 -> "23:25"
            14 -> "00:20"
            15 -> "01:15"
            16 -> "02:10"
            else -> "08:45"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private data class TimeSlotView(
        val node: Int,
        val tvStartTime: TextView,
        val tvEndTime: TextView
    )
}
