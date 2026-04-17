package com.cherry.wakeupschedule

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import com.cherry.wakeupschedule.App
import com.cherry.wakeupschedule.databinding.ActivityMainBinding
import com.cherry.wakeupschedule.model.Course
import com.cherry.wakeupschedule.service.CourseDataManager
import com.cherry.wakeupschedule.service.ImportService
import com.cherry.wakeupschedule.service.SettingsManager
import com.cherry.wakeupschedule.service.TimeTableManager
import com.cherry.wakeupschedule.viewmodel.CourseViewModel
import com.cherry.wakeupschedule.widget.ScheduleWidgetUpdateService
import com.google.gson.Gson
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: CourseViewModel
    private lateinit var settingsManager: SettingsManager
    private var displayWeek = 1  // 当前显示的周次
    private var currentWeek = 1  // 实际当前周（根据学期开始日期计算）
    private val dateFormat = SimpleDateFormat("yyyy/M/d", Locale.getDefault())
    private val weekFormat = SimpleDateFormat("EEE", Locale.getDefault())

    // 课程颜色数组
    private val courseColors = intArrayOf(
        Color.parseColor("#E57373"), // 红色
        Color.parseColor("#F06292"), // 粉色
        Color.parseColor("#BA68C8"), // 紫色
        Color.parseColor("#9575CD"), // 深紫
        Color.parseColor("#7986CB"), // 靛蓝
        Color.parseColor("#64B5F6"), // 蓝色
        Color.parseColor("#4FC3F7"), // 浅蓝
        Color.parseColor("#4DD0E1"), // 青色
        Color.parseColor("#4DB6AC"), // 蓝绿
        Color.parseColor("#81C784"), // 绿色
        Color.parseColor("#AED581"), // 浅绿
        Color.parseColor("#DCE775"), // 黄绿
        Color.parseColor("#FFF176"), // 黄色
        Color.parseColor("#FFD54F"), // 琥珀
        Color.parseColor("#FFB74D"), // 橙色
        Color.parseColor("#FF8A65")  // 深橙
    )

    // 存储所有课程，用于检测冲突
    private var allCourses: List<Course> = emptyList()

    // 滑动相关变量
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val SWIPE_THRESHOLD = 80
    private val SWIPE_VELOCITY_THRESHOLD = 200

    private val countdownHandler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    private var isWeekView = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在super.onCreate之前应用主题
        settingsManager = SettingsManager(this)
        applyTheme()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 请求通知权限（Android 13+）
        requestNotificationPermission()
        requestExactAlarmPermissionIfNeeded()

        currentWeek = calculateCurrentWeek()
        displayWeek = currentWeek

        val application = this.applicationContext as Application
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        viewModel = ViewModelProvider(this, factory)[CourseViewModel::class.java]

        setupViews()
        setupClickListeners()
        setupObservers()
        setupSwipeGesture()
        updateDateDisplay()
        generateTimeAxis()
    }

    /**
     * 根据学期开始日期计算当前周
     */
    private fun calculateCurrentWeek(): Int {
        val semesterStartDate = settingsManager.getSemesterStartDate()
        if (semesterStartDate == 0L) {
            // 如果没有设置学期开始日期，使用默认值（假设当前是第一周）
            return 1
        }
        
        val now = System.currentTimeMillis()
        val diffMillis = now - semesterStartDate
        val diffDays = (diffMillis / (1000 * 60 * 60 * 24)).toInt()
        val week = (diffDays / 7) + 1
        
        return week.coerceIn(1, 20)
    }

    /**
     * 请求通知权限（Android 13+）
     */
    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val notificationPermission = android.Manifest.permission.POST_NOTIFICATIONS
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, notificationPermission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    arrayOf(notificationPermission),
                    1001
                )
            }
        }
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        if (alarmManager.canScheduleExactAlarms()) return
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun setupSwipeGesture() {
        // 为课程表容器设置触摸监听
        val courseContainer = binding.scrollView
        courseContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    // 不拦截移动事件，让 ScrollView 正常滚动
                    false
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = event.x - touchStartX
                    val diffY = event.y - touchStartY
                    val duration = event.eventTime - event.downTime

                    // 检查是否是快速水平滑动
                    if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) * 2 &&
                        kotlin.math.abs(diffX) > SWIPE_THRESHOLD &&
                        duration < 300) {
                        if (diffX > 0) {
                            // 向右滑动 -> 上一周
                            switchToPreviousWeek()
                        } else {
                            // 向左滑动 -> 下一周
                            switchToNextWeek()
                        }
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun switchToPreviousWeek() {
        if (displayWeek > 1) {
            animateWeekSwitch(isNext = false) {
                displayWeek--
                updateWeekDisplay()
                viewModel.loadCoursesForWeek(displayWeek)
            }
        }
    }

    private fun switchToNextWeek() {
        if (displayWeek < 20) {
            animateWeekSwitch(isNext = true) {
                displayWeek++
                updateWeekDisplay()
                viewModel.loadCoursesForWeek(displayWeek)
            }
        }
    }

    private fun animateWeekSwitch(isNext: Boolean, onAnimationEnd: () -> Unit) {
        val courseGrid = binding.courseGrid
        val scrollView = binding.scrollView

        // 滑出动画
        val slideOut = android.animation.ObjectAnimator.ofFloat(
            scrollView,
            "translationX",
            0f,
            if (isNext) -scrollView.width.toFloat() else scrollView.width.toFloat()
        )
        slideOut.duration = 150
        slideOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // 执行周切换
                onAnimationEnd()

                // 重置位置并滑入
                scrollView.translationX = if (isNext) scrollView.width.toFloat() else -scrollView.width.toFloat()

                val slideIn = android.animation.ObjectAnimator.ofFloat(
                    scrollView,
                    "translationX",
                    scrollView.translationX,
                    0f
                )
                slideIn.duration = 150
                slideIn.start()
            }
        })
        slideOut.start()
    }

    private fun updateWeekDisplay() {
        val calendar = Calendar.getInstance()
        val weekText = if (displayWeek == currentWeek) {
            "第${displayWeek}周 (本周)"
        } else {
            "第${displayWeek}周"
        }
        binding.tvWeekInfo.text = "$weekText  ${weekFormat.format(calendar.time)}"
    }

    private fun setupViews() {
        applyBackgroundSettings()
        binding.btnViewToggle.setOnClickListener {
            toggleViewMode()
        }
        restoreViewMode()
    }

    private fun toggleViewMode() {
        isWeekView = !isWeekView
        settingsManager.setViewMode(if (isWeekView) "week" else "day")
        updateViewMode()
    }

    private fun updateViewMode() {
        if (isWeekView) {
            binding.scrollView.visibility = View.VISIBLE
            binding.scrollViewToday.visibility = View.GONE
            binding.layoutHeaderWeek.visibility = View.VISIBLE
            binding.tvToggleLabel.text = "周"
        } else {
            binding.scrollView.visibility = View.GONE
            binding.scrollViewToday.visibility = View.VISIBLE
            binding.layoutHeaderWeek.visibility = View.GONE
            binding.tvToggleLabel.text = "今"
            updateTodayView()
        }
    }

    private fun restoreViewMode() {
        isWeekView = settingsManager.getViewMode() == "week"
        updateViewMode()
    }

    private fun updateTodayView() {
        val container = findViewById<LinearLayout>(R.id.today_courses_container)
        val emptyView = findViewById<LinearLayout>(R.id.layout_empty_today)
        container.removeAllViews()

        val currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val adjustedDayOfWeek = if (currentDayOfWeek == Calendar.SUNDAY) 7 else currentDayOfWeek - 1

        val todayCourses = allCourses.filter { course ->
            course.dayOfWeek == adjustedDayOfWeek &&
            currentWeek >= course.startWeek &&
            currentWeek <= course.endWeek &&
            isCourseInCurrentWeekType(course, currentWeek)
        }.sortedBy { course -> getCourseStartMinutes(course) }

        if (todayCourses.isEmpty()) {
            container.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
        } else {
            container.visibility = View.VISIBLE
            emptyView.visibility = View.GONE

            todayCourses.forEach { course ->
                val courseView = createTodayCourseView(course)
                container.addView(courseView)
            }
        }
    }

    private fun createTodayCourseView(course: Course): View {
        val colorIndex = allCourses.indexOf(course) % courseColors.size
        val color = courseColors[colorIndex]

        val view = LayoutInflater.from(this).inflate(R.layout.item_today_course, null)
        val tvName = view.findViewById<TextView>(R.id.tv_course_name)
        val tvTime = view.findViewById<TextView>(R.id.tv_course_time)
        val tvLocation = view.findViewById<TextView>(R.id.tv_course_location)

        view.setBackgroundColor(color)
        view.setPadding(48, 32, 48, 32)

        tvName.text = course.name
        tvLocation.text = course.classroom.ifEmpty { "未设置地点" }

        val timeTableManager = TimeTableManager.getInstance(this)
        val timeSlots = timeTableManager.getTimeSlots()
        val startSlot = timeSlots.find { it.node == course.startTime }
        val endSlot = timeSlots.find { it.node == course.endTime }
        tvTime.text = if (startSlot != null && endSlot != null) {
            "第${course.startTime}-${course.endTime}节 ${startSlot.startTime}-${endSlot.endTime}"
        } else {
            "第${course.startTime}-${course.endTime}节"
        }

        view.setOnClickListener {
            showCourseDetail(course)
        }

        return view
    }

    private fun applyTheme() {
        val theme = settingsManager.getTheme()
        when (theme) {
            "light" -> setTheme(R.style.Theme_WakeupSchedule_Light)
            "dark" -> setTheme(R.style.Theme_WakeupSchedule_Dark)
            "frosted" -> setTheme(R.style.Theme_WakeupSchedule_Frosted)
            else -> setTheme(R.style.Theme_WakeupSchedule_Light)
        }
    }

    private fun applyBackgroundSettings() {
        val backgroundType = settingsManager.getBackgroundType()
        when (backgroundType) {
            "custom" -> {
                // 加载自定义背景
                val customBgPath = settingsManager.getCustomBackgroundPath()
                if (customBgPath.isNotEmpty() && File(customBgPath).exists()) {
                    try {
                        val bitmap = BitmapFactory.decodeFile(customBgPath)
                        binding.ivBackground.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        binding.ivBackground.setImageResource(R.drawable.home)
                    }
                } else {
                    binding.ivBackground.setImageResource(R.drawable.home)
                }
            }
            "solid" -> {
                // 磨砂背景
                binding.ivBackground.setImageResource(R.drawable.home)
            }
            else -> {
                // 默认背景
                binding.ivBackground.setImageResource(R.drawable.home)
            }
        }
    }

    private fun setupClickListeners() {
        // 添加按钮
        binding.btnAdd.setOnClickListener {
            showAddCourseDialog()
        }

        // 导入按钮
        binding.btnImport.setOnClickListener {
            showImportDialog()
        }

        // 导出按钮
        binding.btnExport.setOnClickListener {
            showExportDialog()
        }

        // 更多按钮
        binding.btnMore.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // 周次显示点击 - 快速跳转到当前周
        binding.tvWeekInfo.setOnClickListener {
            if (displayWeek != currentWeek) {
                displayWeek = currentWeek
                updateWeekDisplay()
                viewModel.loadCoursesForWeek(displayWeek)
                Toast.makeText(this, "已切换到本周 (第${currentWeek}周)", Toast.LENGTH_SHORT).show()
            }
        }

        // 日期选择
        val dateViews = listOf(
            binding.tvDate1, binding.tvDate2, binding.tvDate3,
            binding.tvDate4, binding.tvDate5, binding.tvDate6, binding.tvDate7
        )
        dateViews.forEachIndexed { index, textView ->
            textView.setOnClickListener {
                // 清除之前的选中状态
                dateViews.forEach { it.background = null }
                // 设置当前选中
                it.setBackgroundResource(R.drawable.bg_date_selected)
                // 可以在这里添加切换日期的逻辑
            }
        }
    }

    private fun showAddCourseDialog() {
        val intent = Intent(this, AddCourseActivity::class.java)
        startActivity(intent)
    }

    private fun showImportDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_import, null)
        val dialog = AlertDialog.Builder(this, R.style.RoundedDialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.btn_import_school).setOnClickListener {
            val intent = Intent(this, SchoolImportActivity::class.java)
            startActivity(intent)
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btn_import_excel).setOnClickListener {
            // 实现Excel导入
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQUEST_CODE_IMPORT_FILE)
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btn_import_file).setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQUEST_CODE_IMPORT_FILE)
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btn_apply_adapter).setOnClickListener {
            val intent = Intent(this, ApplyAdapterActivity::class.java)
            startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showExportDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_export, null)
        val dialog = AlertDialog.Builder(this, R.style.RoundedDialog)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.btn_export_backup).setOnClickListener {
            exportBackup()
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btn_export_ics).setOnClickListener {
            Toast.makeText(this, "导出ICS功能开发中", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btn_share_app).setOnClickListener {
            shareApp()
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.btn_cancel_export).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun shareApp() {
        val githubUrl = "https://github.com/Yngu196/Schedule"
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("GitHub链接", githubUrl)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "已复制 GitHub 链接", Toast.LENGTH_SHORT).show()
    }

    private fun exportBackup() {
        // 导出课程备份
        val courses = viewModel.courses.value
        if (courses.isNullOrEmpty()) {
            Toast.makeText(this, "没有课程可导出", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            // 生成备份文件名
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val fileName = "schedule_backup_${dateFormat.format(Date())}.json"
            
            // 转换课程数据为JSON
            val gson = Gson()
            val coursesJson = gson.toJson(courses)
            
            // 创建文件并写入数据
            val file = File(getExternalFilesDir(null), fileName)
            file.writeText(coursesJson, StandardCharsets.UTF_8)
            
            // 显示成功提示
            Toast.makeText(this, "备份已保存到: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            
            // 可选：分享备份文件
            shareBackupFile(file)
        } catch (e: Exception) {
            Log.e("MainActivity", "导出备份失败", e)
            Toast.makeText(this, "导出备份失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun shareBackupFile(file: File) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
            putExtra(Intent.EXTRA_SUBJECT, "课程表备份")
            putExtra(Intent.EXTRA_TEXT, "这是我的课程表备份文件，您可以在Schedule课程表App中导入恢复")
        }
        startActivity(Intent.createChooser(intent, "分享备份文件"))
    }

    private fun setupObservers() {
        viewModel.courses.observe(this) { courses ->
            allCourses = courses ?: emptyList()
            if (allCourses.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.courseGrid.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.courseGrid.visibility = View.VISIBLE
                displayCourses(allCourses)
            }
            if (!isWeekView) {
                updateTodayView()
            }
        }
    }

    private fun updateDateDisplay() {
        val calendar = Calendar.getInstance()
        binding.tvDate.text = dateFormat.format(calendar.time)
        updateWeekDisplay()

        // 更新日期数字
        val dateViews = listOf(
            binding.tvDate1, binding.tvDate2, binding.tvDate3,
            binding.tvDate4, binding.tvDate5, binding.tvDate6, binding.tvDate7
        )

        // 获取本周一的日期
        val weekCalendar = Calendar.getInstance()
        weekCalendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        dateViews.forEachIndexed { index, textView ->
            textView.text = weekCalendar.get(Calendar.DAY_OF_MONTH).toString()
            weekCalendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun generateTimeAxis() {
        val timeAxis = binding.timeAxis
        timeAxis.removeAllViews()

        val timeTableManager = TimeTableManager.getInstance(this)
        val maxNodes = timeTableManager.getMaxNodes()
        
        // 获取课程单元格的高度
        val cellHeight = resources.getDimensionPixelSize(R.dimen.course_cell_height)

        // 为每个节次创建时间轴项
        for (node in 1..maxNodes) {
            // 尝试获取自定义时间槽，否则使用默认值
            val timeSlot = timeTableManager.getTimeSlots().find { it.node == node }
                ?: TimeTableManager.getTimeSlot(node) ?: TimeTableManager.TimeSlot(node, "08:00", "08:45")
            
            val timeView = LayoutInflater.from(this)
                .inflate(R.layout.item_time_slot, timeAxis, false) as LinearLayout

            // 设置时间轴项的高度，与课程单元格高度一致
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, cellHeight)
            timeView.layoutParams = params

            val tvNode = timeView.findViewById<TextView>(R.id.tv_node)
            val tvStartTime = timeView.findViewById<TextView>(R.id.tv_start_time)
            val tvEndTime = timeView.findViewById<TextView>(R.id.tv_end_time)

            tvNode.text = timeSlot.node.toString()
            // 确保时间格式正确
            val startTime = timeSlot.startTime
            val endTime = timeSlot.endTime
            tvStartTime.text = if (startTime.contains(":")) startTime else "00:00"
            tvEndTime.text = if (endTime.contains(":")) endTime else "00:00"

            timeAxis.addView(timeView)
        }
    }
    
    /**
     * 检查课程在当前显示周是否应该显示
     * 支持单双周判断
     */
    private fun isCourseActiveInWeek(course: Course, week: Int): Boolean {
        // 基础周次范围检查
        if (week < course.startWeek || week > course.endWeek) {
            return false
        }
        
        // 单双周判断
        val isWeekTypeMatch = when (course.weekType) {
            0 -> true // 每周
            1 -> week % 2 == 1 // 单周
            2 -> week % 2 == 0 // 双周
            else -> true
        }
        
        return isWeekTypeMatch
    }

    /**
     * 获取课程在当前周的透明度
     * 非本周课程透明度降低
     */
    private fun getCourseAlpha(course: Course): Float {
        val isActive = isCourseActiveInWeek(course, displayWeek)
        val baseAlpha = settingsManager.getCourseCardAlpha()

        return if (isActive) {
            baseAlpha  // 本周课程使用设置的透明度
        } else {
            // 非本周课程
            if (settingsManager.isShowNonCurrentWeekCourses()) {
                baseAlpha * settingsManager.getNonCurrentWeekAlpha()  // 应用非本周透明度
            } else {
                0f  // 不显示非本周课程
            }
        }
    }

    private fun displayCourses(courses: List<Course>) {
        val gridLayout = binding.courseGrid
        gridLayout.removeAllViews()

        try {
            val timeTableManager = TimeTableManager.getInstance(this)
            val maxNodes = timeTableManager.getMaxNodes()

            // 动态设置GridLayout的行数
            gridLayout.rowCount = maxNodes
            
            // 创建7列 x maxNodes行的网格背景
            for (row in 0 until maxNodes) {
                for (col in 0 until 7) {
                    val cellView = View(this)
                    val params = GridLayout.LayoutParams().apply {
                        rowSpec = GridLayout.spec(row, 1f)
                        columnSpec = GridLayout.spec(col, 1f)
                        width = 0
                        height = resources.getDimensionPixelSize(R.dimen.course_cell_height)
                    }
                    cellView.layoutParams = params
                    cellView.setBackgroundResource(R.drawable.bg_grid_cell)
                    gridLayout.addView(cellView)
                }
            }

            // 过滤掉超出范围的课程
            val validCourses = courses.filter {
                course ->
                course.startTime <= maxNodes
            }

            // 按位置分组课程，检测冲突
            val courseGroups = mutableMapOf<Pair<Int, Int>, MutableList<Pair<Course, Int>>>()

            validCourses.forEachIndexed { index, course ->
                val key = Pair(course.dayOfWeek - 1, course.startTime - 1)
                if (!courseGroups.containsKey(key)) {
                    courseGroups[key] = mutableListOf()
                }
                courseGroups[key]?.add(Pair(course, index))
            }

            val highlightCourse = findCurrentOrNextCourse()

            // 添加课程卡片
            validCourses.forEachIndexed { index, course ->
                val color = courseColors[index % courseColors.size]
                val key = Pair(course.dayOfWeek - 1, course.startTime - 1)
                val conflictCourses = courseGroups[key] ?: listOf()
                val hasConflict = conflictCourses.size > 1
                val alpha = getCourseAlpha(course)
                val isHighlight = course == highlightCourse

                addCourseCard(course, color, hasConflict, alpha, conflictCourses, isHighlight)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error displaying courses", e)
        }
    }

    private fun findCurrentOrNextCourse(): Course? {
        val currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val adjustedDayOfWeek = if (currentDayOfWeek == Calendar.SUNDAY) 7 else currentDayOfWeek - 1

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val todayCourses = allCourses.filter { course ->
            course.dayOfWeek == adjustedDayOfWeek &&
            currentWeek >= course.startWeek &&
            currentWeek <= course.endWeek &&
            isCourseInCurrentWeekType(course, currentWeek)
        }.sortedBy { course -> getCourseStartMinutes(course) }

        for (course in todayCourses) {
            val startMinutes = getCourseStartMinutes(course)
            val endMinutes = getCourseEndMinutes(course)

            if (currentMinutes in startMinutes..endMinutes) {
                return course
            } else if (currentMinutes < startMinutes && startMinutes - currentMinutes <= 30) {
                return course
            }
        }
        return null
    }

    private fun addCourseCard(
        course: Course,
        color: Int,
        hasConflict: Boolean,
        alpha: Float,
        conflictCourses: List<Pair<Course, Int>>,
        isHighlight: Boolean = false
    ) {
        try {
            // 如果透明度为0，不显示该课程
            if (alpha <= 0f) {
                return
            }

            // 获取当前最大节数，确保课程在有效范围内
            val timeTableManager = TimeTableManager.getInstance(this)
            val maxNodes = timeTableManager.getMaxNodes()

            // 检查课程时间是否在有效范围内
            if (course.startTime > maxNodes) {
                return  // 课程开始时间超出当前最大节数，不显示
            }

            val containerView = LayoutInflater.from(this)
                .inflate(R.layout.item_course_card, binding.courseGrid, false) as FrameLayout

            val cardView = containerView.findViewById<CardView>(R.id.card_course)
            val tvName = containerView.findViewById<TextView>(R.id.tv_course_name)
            val tvLocation = containerView.findViewById<TextView>(R.id.tv_course_location)
            val ivConflict = containerView.findViewById<ImageView>(R.id.iv_conflict_indicator)

            // 设置卡片背景色
            cardView.setCardBackgroundColor(color)

            // 根据当前周设置透明度
            cardView.alpha = alpha

            // 高亮样式
            if (isHighlight) {
                cardView.cardElevation = 12f
                val highlightColor = Color.argb(80, 255, 255, 255)
                cardView.setCardBackgroundColor(Color.argb(
                    Color.alpha(color),
                    Math.min(Color.red(color) + 30, 255),
                    Math.min(Color.green(color) + 30, 255),
                    Math.min(Color.blue(color) + 30, 255)
                ))
            }

            // 设置课程信息
            tvName.text = course.name
            tvLocation.text = "@${course.classroom}"

            // 显示冲突指示器
            if (hasConflict) {
                ivConflict.visibility = View.VISIBLE
            } else {
                ivConflict.visibility = View.GONE
            }

            // 计算位置，确保不超出网格范围
            val row = course.startTime - 1
            val col = course.dayOfWeek - 1
            
            // 计算课程跨度，确保三节课对应三个格子
            val calculatedSpan = course.endTime - course.startTime + 1
            // 确保rowSpan不会导致超出网格范围
            val maxPossibleSpan = maxNodes - row
            val rowSpan = if (maxPossibleSpan > 0) min(calculatedSpan, maxPossibleSpan) else 1

            // 计算课程卡片的实际高度
            val cellHeight = resources.getDimensionPixelSize(R.dimen.course_cell_height)
            val cardHeight = cellHeight * rowSpan

            val params = GridLayout.LayoutParams().apply {
                rowSpec = GridLayout.spec(row, rowSpan, 1f)
                columnSpec = GridLayout.spec(col, 1f)
                width = 0
                height = cardHeight
                setMargins(2, 2, 2, 2)
            }
            containerView.layoutParams = params

            // 点击事件 - 显示课程详情
            containerView.setOnClickListener {
                if (hasConflict && conflictCourses.size > 1) {
                    showConflictCourseDetail(conflictCourses.map { it.first })
                } else {
                    showCourseDetail(course)
                }
            }

            // 长按事件
            containerView.setOnLongClickListener {
                showDeleteDialog(course)
                true
            }

            binding.courseGrid.addView(containerView)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error adding course card", e)
        }
    }

    private fun showCourseDetail(course: Course) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_course_detail, null)
        val dialog = AlertDialog.Builder(this, R.style.RoundedDialog)
            .setView(dialogView)
            .create()

        // 设置课程信息
        dialogView.findViewById<TextView>(R.id.tv_course_name).text = course.name
        // 显示周次信息，添加单双周标注
        val weekInfo = when (course.weekType) {
            1 -> "第${course.startWeek}-${course.endWeek}周 (单周)"
            2 -> "第${course.startWeek}-${course.endWeek}周 (双周)"
            else -> "第${course.startWeek}-${course.endWeek}周"
        }
        dialogView.findViewById<TextView>(R.id.tv_week_info).text = weekInfo

        val weekDays = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val timeTableManager = TimeTableManager.getInstance(this)
        val timeSlots = timeTableManager.getTimeSlots()
        val timeSlot = timeSlots.find { it.node == course.startTime }
        val endTimeSlot = timeSlots.find { it.node == course.endTime }
        val timeStr = if (timeSlot != null && endTimeSlot != null) {
            "${weekDays[course.dayOfWeek]} 第${course.startTime}-${course.endTime}节 ${timeSlot.startTime}-${endTimeSlot.endTime}"
        } else {
            "${weekDays[course.dayOfWeek]} 第${course.startTime}-${course.endTime}节"
        }
        dialogView.findViewById<TextView>(R.id.tv_time_info).text = timeStr
        dialogView.findViewById<TextView>(R.id.tv_teacher).text = course.teacher.ifEmpty { "未设置" }
        dialogView.findViewById<TextView>(R.id.tv_location).text = course.classroom.ifEmpty { "未设置" }

        // 隐藏冲突区域
        dialogView.findViewById<LinearLayout>(R.id.layout_conflict_switch).visibility = View.GONE

        // 关闭按钮
        dialogView.findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
            dialog.dismiss()
        }

        // 删除按钮
        dialogView.findViewById<ImageButton>(R.id.btn_delete).setOnClickListener {
            showDeleteDialog(course)
            dialog.dismiss()
        }

        // 编辑按钮
        dialogView.findViewById<ImageButton>(R.id.btn_edit).setOnClickListener {
            editCourse(course)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showConflictCourseDetail(conflictCourses: List<Course>) {
        // 显示冲突课程列表，让用户选择查看哪一门
        val courseNames = conflictCourses.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("该时间段有多门课程")
            .setItems(courseNames) { _, which ->
                showCourseDetail(conflictCourses[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun editCourse(course: Course) {
        val intent = Intent(this, AddCourseActivity::class.java)
        intent.putExtra("course", course as java.io.Serializable)
        startActivity(intent)
    }

    private fun showDeleteDialog(course: Course) {
        AlertDialog.Builder(this)
            .setTitle("删除课程")
            .setMessage("确定要删除 ${course.name} 吗？")
            .setPositiveButton("删除所有周次") { _, _ ->
                viewModel.deleteCourse(course)
                Toast.makeText(this, "课程删除成功", Toast.LENGTH_SHORT).show()
                // 更新桌面小组件
                updateWidget()
            }
            .setNegativeButton("仅删除本周") { _, _ ->
                // 仅删除本周 - 实际上需要修改周次范围
                if (course.startWeek == displayWeek && course.endWeek == displayWeek) {
                    viewModel.deleteCourse(course)
                } else if (displayWeek == course.startWeek) {
                    // 调整开始周
                    val updatedCourse = course.copy(startWeek = course.startWeek + 1)
                    viewModel.updateCourse(updatedCourse)
                } else if (displayWeek == course.endWeek) {
                    // 调整结束周
                    val updatedCourse = course.copy(endWeek = course.endWeek - 1)
                    viewModel.updateCourse(updatedCourse)
                } else if (displayWeek > course.startWeek && displayWeek < course.endWeek) {
                    // 在中间，需要拆分成两个课程
                    // 简化处理：先删除原课程，添加两个新课程
                    viewModel.deleteCourse(course)
                    val course1 = course.copy(endWeek = displayWeek - 1)
                    val course2 = course.copy(startWeek = displayWeek + 1)
                    viewModel.addCourse(course1)
                    viewModel.addCourse(course2)
                }
                Toast.makeText(this, "已删除本周课程", Toast.LENGTH_SHORT).show()
                // 更新桌面小组件
                updateWidget()
            }
            .setNeutralButton("取消", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // 重新计算当前周
        currentWeek = calculateCurrentWeek()
        // 同步默认周，避免其他模块仍使用旧周次
        settingsManager.setDefaultWeek(currentWeek)
        // 同步显示周与当前周
        displayWeek = currentWeek
        // 重新应用背景设置
        applyBackgroundSettings()
        updateWeekDisplay()
        generateTimeAxis()
        viewModel.loadCoursesForWeek(displayWeek)
        // 如果已经有课程数据，重新显示以适配新的时间轴
        allCourses.let {
            if (it.isNotEmpty()) {
                displayCourses(it)
            }
        }

        // 为所有课程设置闹钟
        setupAllCoursesAlarms()

        // 检查是否有待导入的文件
        checkPendingImportFile()

        startCountdown()
    }

    override fun onPause() {
        super.onPause()
        stopCountdown()
    }

    private fun setupAllCoursesAlarms() {
        (application as App).registerAllCourseNotifications()
    }

    private fun checkPendingImportFile() {
        val prefs = getSharedPreferences("pending_imports", Context.MODE_PRIVATE)
        val pendingFilePath = prefs.getString("pending_file", null)

        if (!pendingFilePath.isNullOrEmpty()) {
            val file = File(pendingFilePath)
            if (file.exists()) {
                // 有文件待导入
                AlertDialog.Builder(this)
                    .setTitle("发现课程表文件")
                    .setMessage("检测到从教务系统下载的课程表文件，是否立即导入？")
                    .setPositiveButton("立即导入") { _, _ ->
                        importPendingFile(file)
                    }
                    .setNegativeButton("稍后手动导入") { _, _ ->
                        // 清除待导入标记
                        prefs.edit().remove("pending_file").apply()
                    }
                    .setCancelable(false)
                    .show()
            } else {
                // 文件不存在，清除标记
                prefs.edit().remove("pending_file").apply()
            }
        }
    }

    private fun importPendingFile(file: File) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val importService = ImportService(this@MainActivity)
                val uri = Uri.fromFile(file)
                val success = importService.importFromFile(uri)

                // 清除待导入标记
                val prefs = getSharedPreferences("pending_imports", Context.MODE_PRIVATE)
                prefs.edit().remove("pending_file").apply()

                if (success) {
                    Toast.makeText(this@MainActivity, "课程表导入成功！", Toast.LENGTH_LONG).show()
                    // 删除已导入的文件
                    file.delete()
                    // 更新小组件
                    updateWidget()
                } else {
                    Toast.makeText(this@MainActivity, "导入失败，请检查文件格式", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "导入错误: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_IMPORT_FILE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // 处理文件导入
                importFromFile(uri)
            }
        }
    }

    private fun importFromFile(uri: android.net.Uri) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val importService = ImportService(this@MainActivity)
                val success = importService.importFromFile(uri)
                if (success) {
                    // 更新小组件
                    updateWidget()
                } else {
                    // 如果 ImportService 失败，尝试直接解析 JSON
                    tryParseAsJson(uri)
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun tryParseAsJson(uri: android.net.Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().use { it.readText() }
                if (content.trim().startsWith("[")) {
                    importFromJson(content)
                } else {
                    Toast.makeText(this, "不支持的文件格式", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromJson(json: String) {
        try {
            val courses = com.cherry.wakeupschedule.service.ImportService.parseCoursesFromJson(json)
            if (courses.isNotEmpty()) {
                viewModel.addCourses(courses)
                Toast.makeText(this, "成功导入 ${courses.size} 门课程", Toast.LENGTH_LONG).show()
                // 更新小组件
                updateWidget()
            } else {
                Toast.makeText(this, "未找到有效课程数据", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "解析失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新桌面小组件
     */
    private fun updateWidget() {
        ScheduleWidgetUpdateService.triggerUpdate(this)
    }

    private fun startCountdown() {
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        countdownRunnable = object : Runnable {
            override fun run() {
                updateCountdown()
                countdownHandler.postDelayed(this, 60000)
            }
        }
        countdownRunnable?.let { countdownHandler.post(it) }
        updateCountdown()
    }

    private fun stopCountdown() {
        countdownRunnable?.let { countdownHandler.removeCallbacks(it) }
        countdownRunnable = null
    }

    private fun updateCountdown() {
        val currentDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        val adjustedDayOfWeek = if (currentDayOfWeek == Calendar.SUNDAY) 7 else currentDayOfWeek - 1

        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val todayCourses = allCourses.filter { course ->
            course.dayOfWeek == adjustedDayOfWeek &&
            currentWeek >= course.startWeek &&
            currentWeek <= course.endWeek &&
            isCourseInCurrentWeekType(course, currentWeek)
        }.sortedBy { course -> getCourseStartMinutes(course) }

        var nextCourse: Course? = null
        var currentCourse: Course? = null

        for (course in todayCourses) {
            val startMinutes = getCourseStartMinutes(course)
            val endMinutes = getCourseEndMinutes(course)

            if (currentMinutes in startMinutes..endMinutes) {
                currentCourse = course
                break
            } else if (currentMinutes < startMinutes) {
                nextCourse = course
                break
            }
        }

        when {
            currentCourse != null -> {
                val endMinutes = getCourseEndMinutes(currentCourse)
                val remainingMinutes = endMinutes - currentMinutes
                binding.tvCountdown.visibility = View.VISIBLE
                binding.tvCountdown.text = getString(R.string.countdown_class_end, formatDuration(remainingMinutes))
            }
            nextCourse != null -> {
                val startMinutes = getCourseStartMinutes(nextCourse)
                val remainingMinutes = startMinutes - currentMinutes
                binding.tvCountdown.visibility = View.VISIBLE
                binding.tvCountdown.text = getString(R.string.countdown_format, formatDuration(remainingMinutes))
            }
            todayCourses.isNotEmpty() -> {
                binding.tvCountdown.visibility = View.VISIBLE
                binding.tvCountdown.text = getString(R.string.countdown_no_more_classes)
            }
            else -> {
                binding.tvCountdown.visibility = View.GONE
            }
        }

        if (isWeekView && allCourses.isNotEmpty()) {
            displayCourses(allCourses)
        }
    }

    private fun getCourseStartMinutes(course: Course): Int {
        return try {
            val timeTableManager = TimeTableManager.getInstance(this)
            val timeSlots = timeTableManager.getTimeSlots()
            val startSlot = timeSlots.find { it.node == course.startTime }
            if (startSlot != null) {
                val parts = startSlot.startTime.split(":")
                if (parts.size == 2) {
                    parts[0].toInt() * 60 + parts[1].toInt()
                } else {
                    (8 + course.startTime) * 60
                }
            } else {
                (8 + course.startTime) * 60
            }
        } catch (e: Exception) {
            (8 + course.startTime) * 60
        }
    }

    private fun getCourseEndMinutes(course: Course): Int {
        return try {
            val timeTableManager = TimeTableManager.getInstance(this)
            val timeSlots = timeTableManager.getTimeSlots()
            val endSlot = timeSlots.find { it.node == course.endTime }
            if (endSlot != null) {
                val parts = endSlot.endTime.split(":")
                if (parts.size == 2) {
                    parts[0].toInt() * 60 + parts[1].toInt()
                } else {
                    (8 + course.endTime) * 60 + 45
                }
            } else {
                (8 + course.endTime) * 60 + 45
            }
        } catch (e: Exception) {
            (8 + course.endTime) * 60 + 45
        }
    }

    private fun formatDuration(minutes: Int): String {
        return when {
            minutes >= 60 -> {
                val hours = minutes / 60
                val mins = minutes % 60
                if (mins > 0) "${hours}小时${mins}分钟" else "${hours}小时"
            }
            minutes > 0 -> "${minutes}分钟"
            else -> "0分钟"
        }
    }

    private fun isCourseInCurrentWeekType(course: Course, week: Int): Boolean {
        return when (course.weekType) {
            0 -> true
            1 -> week % 2 == 1
            2 -> week % 2 == 0
            else -> true
        }
    }

    companion object {
        private const val REQUEST_CODE_IMPORT_FILE = 1001
    }
}
