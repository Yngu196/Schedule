package com.cherry.wakeupschedule

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.cherry.wakeupschedule.model.Course
import com.cherry.wakeupschedule.viewmodel.CourseViewModel
import com.cherry.wakeupschedule.widget.ScheduleWidgetUpdateService

class AddCourseActivity : AppCompatActivity() {

    private lateinit var viewModel: CourseViewModel

    private lateinit var etCourseName: EditText
    private lateinit var etTeacher: EditText
    private lateinit var etLocation: EditText
    private lateinit var spinnerWeekDay: Spinner
    private lateinit var spinnerStartTime: Spinner
    private lateinit var spinnerEndTime: Spinner
    private lateinit var spinnerStartWeek: Spinner
    private lateinit var spinnerEndWeek: Spinner
    private lateinit var spinnerWeekType: Spinner
    private lateinit var cbAlarm: CheckBox
    private lateinit var spinnerAlarmTime: Spinner
    private lateinit var btnCancel: TextView
    private lateinit var btnSave: TextView
    private lateinit var btnBack: ImageButton

    private var isEditMode = false
    private var existingCourse: Course? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_add_course)
            android.util.Log.d("AddCourseActivity", "布局设置完成")

            // 初始化ViewModel
            val application = this.applicationContext as android.app.Application
            val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            viewModel = ViewModelProvider(this, factory)[CourseViewModel::class.java]
            android.util.Log.d("AddCourseActivity", "ViewModel初始化完成")

            initViews()
            setupSpinners()
            setupClickListeners()
            android.util.Log.d("AddCourseActivity", "视图初始化完成")

            // 检查是否传递了课程数据（编辑模式）
            existingCourse = intent.getSerializableExtra("course") as? Course
            if (existingCourse != null) {
                isEditMode = true
                populateCourseData(existingCourse!!)
                android.util.Log.d("AddCourseActivity", "编辑模式")
            } else {
                android.util.Log.d("AddCourseActivity", "添加模式")
            }
        } catch (e: Exception) {
            android.util.Log.e("AddCourseActivity", "初始化失败", e)
            e.printStackTrace()
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun populateCourseData(course: Course) {
        etCourseName.setText(course.name)
        etTeacher.setText(course.teacher)
        etLocation.setText(course.classroom)
        spinnerWeekDay.setSelection(course.dayOfWeek - 1)
        spinnerStartTime.setSelection(course.startTime - 1)
        spinnerEndTime.setSelection(course.endTime - 1)
        spinnerStartWeek.setSelection(course.startWeek - 1)
        spinnerEndWeek.setSelection(course.endWeek - 1)
        spinnerWeekType.setSelection(course.weekType)
        cbAlarm.isChecked = course.alarmEnabled
        
        val alarmIndex = when (course.alarmMinutesBefore) {
            5 -> 0
            10 -> 1
            15 -> 2
            20 -> 3
            30 -> 4
            else -> 2
        }
        spinnerAlarmTime.setSelection(alarmIndex)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun initViews() {
        try {
            etCourseName = findViewById(R.id.et_course_name)
            etTeacher = findViewById(R.id.et_teacher)
            etLocation = findViewById(R.id.et_location)
            spinnerWeekDay = findViewById(R.id.spinner_week_day)
            spinnerStartTime = findViewById(R.id.spinner_start_time)
            spinnerEndTime = findViewById(R.id.spinner_end_time)
            spinnerStartWeek = findViewById(R.id.spinner_start_week)
            spinnerEndWeek = findViewById(R.id.spinner_end_week)
            spinnerWeekType = findViewById(R.id.spinner_week_type)
            cbAlarm = findViewById(R.id.cb_alarm)
            spinnerAlarmTime = findViewById(R.id.spinner_alarm_time)
            btnCancel = findViewById(R.id.btn_cancel)
            btnSave = findViewById(R.id.btn_save)
            btnBack = findViewById(R.id.btn_back)
        } catch (e: Exception) {
            android.util.Log.e("AddCourseActivity", "初始化视图失败", e)
            throw e
        }
    }
    
    private fun setupSpinners() {
        // 星期选择
        val weekDays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        spinnerWeekDay.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weekDays)
        
        // 节次选择（1-12节）
        val timeSlots = (1..12).map { "第${it}节" }.toTypedArray()
        spinnerStartTime.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timeSlots)
        spinnerEndTime.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timeSlots)
        spinnerEndTime.setSelection(1) // 默认结束节次为第2节
        
        // 周次选择（1-20周）
        val weeks = (1..20).map { "第${it}周" }.toTypedArray()
        spinnerStartWeek.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weeks)
        spinnerEndWeek.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weeks)
        spinnerEndWeek.setSelection(19) // 默认结束周为第20周
        
        // 闹钟提前时间
        val alarmTimes = arrayOf("提前5分钟", "提前10分钟", "提前15分钟", "提前20分钟", "提前30分钟")
        spinnerAlarmTime.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, alarmTimes)
        spinnerAlarmTime.setSelection(2) // 默认提前15分钟
        
        // 周次类型选择
        val weekTypes = arrayOf("每周", "单周", "双周")
        spinnerWeekType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, weekTypes)
        spinnerWeekType.setSelection(0) // 默认每周
    }
    
    private fun setupClickListeners() {
        btnCancel.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveCourse()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
    
    private fun saveCourse() {
        val courseName = etCourseName.text.toString().trim()
        val teacher = etTeacher.text.toString().trim()
        val location = etLocation.text.toString().trim()
        
        if (courseName.isEmpty()) {
            Toast.makeText(this, "请输入课程名称", Toast.LENGTH_SHORT).show()
            return
        }
        
        val weekDay = spinnerWeekDay.selectedItemPosition + 1
        val startTime = spinnerStartTime.selectedItemPosition + 1
        val endTime = spinnerEndTime.selectedItemPosition + 1
        val startWeek = spinnerStartWeek.selectedItemPosition + 1
        val endWeek = spinnerEndWeek.selectedItemPosition + 1
        val weekType = spinnerWeekType.selectedItemPosition
        
        if (endTime < startTime) {
            Toast.makeText(this, "结束节次不能早于开始节次", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (endWeek < startWeek) {
            Toast.makeText(this, "结束周不能早于开始周", Toast.LENGTH_SHORT).show()
            return
        }
        
        val alarmMinutesBefore = when (spinnerAlarmTime.selectedItemPosition) {
            0 -> 5
            1 -> 10
            2 -> 15
            3 -> 20
            4 -> 30
            else -> 15
        }
        
        val existingCourse = intent.getSerializableExtra("course") as? Course
        val course = if (existingCourse != null) {
            // 编辑模式
            existingCourse.copy(
                name = courseName,
                teacher = teacher,
                classroom = location,
                dayOfWeek = weekDay,
                startTime = startTime,
                endTime = endTime,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = weekType,
                alarmEnabled = cbAlarm.isChecked,
                alarmMinutesBefore = alarmMinutesBefore
            )
        } else {
            // 添加模式
            Course(
                name = courseName,
                teacher = teacher,
                classroom = location,
                dayOfWeek = weekDay,
                startTime = startTime,
                endTime = endTime,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = weekType,
                alarmEnabled = cbAlarm.isChecked,
                alarmMinutesBefore = alarmMinutesBefore
            )
        }
        
        if (existingCourse != null) {
            viewModel.updateCourse(course)
            Toast.makeText(this, "课程更新成功", Toast.LENGTH_SHORT).show()
            if (course.alarmEnabled) {
                com.cherry.wakeupschedule.App.instance.alarmService?.setCourseAlarm(course)
            } else {
                com.cherry.wakeupschedule.App.instance.alarmService?.cancelCourseAlarm(course)
            }
        } else {
            viewModel.addCourse(course)
            Toast.makeText(this, "课程添加成功", Toast.LENGTH_SHORT).show()
            if (course.alarmEnabled) {
                com.cherry.wakeupschedule.App.instance.alarmService?.setCourseAlarm(course)
            }
        }
        ScheduleWidgetUpdateService.triggerUpdate(this)
        finish()
    }
}