package com.cherry.wakeupschedule.model

import java.io.Serializable

data class Course(
    val id: Long = 0,
    val name: String,
    val teacher: String,
    val classroom: String,
    val dayOfWeek: Int, // 1-7 表示周一到周日
    val startTime: Int, // 开始节次
    val endTime: Int,   // 结束节次
    val startWeek: Int, // 开始周
    val endWeek: Int,   // 结束周
    val weekType: Int = 0, // 0: 每周, 1: 单周, 2: 双周
    val alarmEnabled: Boolean = true,
    val alarmMinutesBefore: Int = 15, // 提前多少分钟提醒
    val color: Int = 0xFF6200EE.toInt(), // 课程颜色
    val coverImagePath: String = "" // 课程封面图片路径
) : Serializable