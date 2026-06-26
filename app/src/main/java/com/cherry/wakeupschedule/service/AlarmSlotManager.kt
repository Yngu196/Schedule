package com.cherry.wakeupschedule.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 闹钟槽位管理器
 *
 * 借鉴拾光课表的固定槽位设计，引入槽位化闹钟管理：
 * - 槽位范围：50001 ~ 50110（共 110 个）
 * - 每个槽位对应一个 (courseId, week) 的唯一闹钟
 * - SharedPreferences 持久化活跃槽位状态
 * - 闹钟触发后自动释放槽位，防止幽灵闹钟
 */
class AlarmSlotManager private constructor(context: Context) {

    companion object {
        private const val TAG = "AlarmSlotManager"
        private const val PREFS_NAME = "alarm_slot_state"
        private const val KEY_ACTIVE_SLOTS = "active_slots"
        private const val KEY_SLOT_DATA_PREFIX = "slot_data_"

        /** 槽位 ID 范围 */
        const val SLOT_ID_MIN = 50001
        const val SLOT_ID_MAX = 50110
        const val SLOT_COUNT = SLOT_ID_MAX - SLOT_ID_MIN + 1

        @Volatile
        private var instance: AlarmSlotManager? = null

        fun getInstance(context: Context): AlarmSlotManager {
            return instance ?: synchronized(this) {
                instance ?: AlarmSlotManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 槽位元数据
     */
    data class SlotData(
        val slotId: Int,
        val courseId: Long,
        val courseName: String,
        val week: Int,
        val alarmTimeMillis: Long,
        val allocatedAtMillis: Long
    )

    /**
     * 分配一个空闲槽位
     *
     * @param courseId 课程 ID
     * @param courseName 课程名称
     * @param week 周次
     * @param alarmTimeMillis 闹钟触发时间戳
     * @return 分配的槽位 ID，-1 表示无可用槽位
     */
    @Synchronized
    fun allocateSlot(
        courseId: Long,
        courseName: String,
        week: Int,
        alarmTimeMillis: Long
    ): Int {
        val activeSlots = getActiveSlotIds()

        // 先查找是否已有该课程的槽位（同 courseId + week），避免重复分配
        for (slotId in activeSlots) {
            val data = getSlotData(slotId)
            if (data != null && data.courseId == courseId && data.week == week) {
                Log.d(TAG, "课程 $courseName (id=$courseId) week=$week 已有槽位 $slotId，更新闹钟时间")
                updateSlotData(slotId, courseId, courseName, week, alarmTimeMillis)
                return slotId
            }
        }

        // 查找空闲槽位
        for (slotId in SLOT_ID_MIN..SLOT_ID_MAX) {
            if (slotId !in activeSlots) {
                addToActiveSlots(slotId)
                updateSlotData(slotId, courseId, courseName, week, alarmTimeMillis)
                Log.d(TAG, "分配槽位 $slotId → 课程 $courseName (id=$courseId) week=$week")
                return slotId
            }
        }

        Log.w(TAG, "无可用槽位！活跃槽位: ${activeSlots.size}/$SLOT_COUNT")
        return -1
    }

    /**
     * 释放指定槽位（闹钟触发或取消时调用）
     *
     * @param slotId 槽位 ID
     * @return 释放的槽位数据，null 表示槽位不存在
     */
    @Synchronized
    fun freeSlot(slotId: Int): SlotData? {
        val data = getSlotData(slotId) ?: return null
        removeFromActiveSlots(slotId)
        removeSlotData(slotId)
        Log.d(TAG, "释放槽位 $slotId → 课程 ${data.courseName} week=${data.week}")
        return data
    }

    /**
     * 检查槽位是否处于活跃状态
     */
    fun isSlotActive(slotId: Int): Boolean {
        return slotId in getActiveSlotIds()
    }

    /**
     * 释放指定课程所有周的槽位
     *
     * @param courseId 课程 ID
     */
    @Synchronized
    fun freeAllSlotsForCourse(courseId: Long) {
        val activeSlots = getActiveSlotIds()
        for (slotId in activeSlots) {
            val data = getSlotData(slotId)
            if (data != null && data.courseId == courseId) {
                removeFromActiveSlots(slotId)
                removeSlotData(slotId)
                Log.d(TAG, "释放课程 id=$courseId 的槽位 $slotId")
            }
        }
    }

    /**
     * 释放所有槽位
     */
    @Synchronized
    fun freeAllSlots() {
        val activeSlots = getActiveSlotIds()
        for (slotId in activeSlots) {
            removeSlotData(slotId)
        }
        prefs.edit().remove(KEY_ACTIVE_SLOTS).commit()
        Log.d(TAG, "已释放全部 ${activeSlots.size} 个槽位")
    }

    /**
     * 获取当前所有活跃槽位数据列表
     */
    fun getActiveSlotDataList(): List<SlotData> {
        return getActiveSlotIds().mapNotNull { getSlotData(it) }
    }

    /**
     * 获取槽位数据
     */
    fun getSlotData(slotId: Int): SlotData? {
        val json = prefs.getString(KEY_SLOT_DATA_PREFIX + slotId, null) ?: return null
        return try {
            val parts = json.split("|")
            if (parts.size != 5) return null
            SlotData(
                slotId = slotId,
                courseId = parts[0].toLong(),
                courseName = parts[1],
                week = parts[2].toInt(),
                alarmTimeMillis = parts[3].toLong(),
                allocatedAtMillis = parts[4].toLong()
            )
        } catch (e: Exception) {
            Log.w(TAG, "解析槽位 $slotId 数据失败", e)
            null
        }
    }

    // ── 内部实现 ──

    private fun getActiveSlotIds(): Set<Int> {
        val raw = prefs.getString(KEY_ACTIVE_SLOTS, "") ?: ""
        if (raw.isEmpty()) return emptySet()
        return raw.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }

    private fun addToActiveSlots(slotId: Int) {
        val current = getActiveSlotIds().toMutableSet()
        current.add(slotId)
        saveActiveSlotIds(current)
    }

    private fun removeFromActiveSlots(slotId: Int) {
        val current = getActiveSlotIds().toMutableSet()
        current.remove(slotId)
        saveActiveSlotIds(current)
    }

    private fun saveActiveSlotIds(slots: Set<Int>) {
        prefs.edit().putString(KEY_ACTIVE_SLOTS, slots.sorted().joinToString(",")).commit()
    }

    private fun updateSlotData(
        slotId: Int,
        courseId: Long,
        courseName: String,
        week: Int,
        alarmTimeMillis: Long
    ) {
        val value = "$courseId|$courseName|$week|$alarmTimeMillis|${System.currentTimeMillis()}"
        prefs.edit().putString(KEY_SLOT_DATA_PREFIX + slotId, value).commit()
    }

    private fun removeSlotData(slotId: Int) {
        prefs.edit().remove(KEY_SLOT_DATA_PREFIX + slotId).commit()
    }
}
