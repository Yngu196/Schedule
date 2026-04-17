package com.cherry.wakeupschedule.service

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.cherry.wakeupschedule.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.*
import java.io.BufferedReader
import java.io.InputStreamReader

class ImportService(private val context: Context) {

    private val courseDataManager = CourseDataManager.getInstance(context)

    suspend fun importFromFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // 首先尝试从Uri路径获取文件名
            var fileName = getFileName(uri)
            
            // 如果从MediaStore获取失败，尝试从Uri路径直接获取
            if (fileName == null) {
                val path = uri.path
                if (path != null) {
                    fileName = java.io.File(path).name
                }
            }
            
            Log.d("ImportService", "开始导入文件: $fileName")

            if (fileName?.endsWith(".xls") == true || fileName?.endsWith(".xlsx") == true) {
                return@withContext importFromExcel(uri)
            } else if (fileName?.endsWith(".csv") == true) {
                return@withContext importFromCsvFile(uri)
            } else {
                // 尝试根据内容判断文件类型
                return@withContext tryImportByContent(uri)
            }
        } catch (e: Exception) {
            Log.e("ImportService", "导入失败", e)
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "导入失败: ${e.message}\n请检查文件格式是否正确", android.widget.Toast.LENGTH_LONG).show()
            }
            return@withContext false
        }
    }

    private suspend fun tryImportByContent(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        // 第一次打开流读取首行判断文件类型
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val firstLine = reader.readLine()
            
            if (firstLine?.contains(",") == true) {
                // 看起来是CSV文件
                reader.close()
                // 重新打开流进行完整解析
                context.contentResolver.openInputStream(uri)?.use { csvStream ->
                    val csvReader = BufferedReader(InputStreamReader(csvStream, "UTF-8"))
                    return@withContext importFromCsvStream(csvReader)
                }
            } else {
                // 尝试Excel
                try {
                    return@withContext importFromExcel(uri)
                } catch (e: Exception) {
                    Log.e("ImportService", "Excel解析失败，尝试CSV", e)
                    // 回退到CSV解析，重新打开流
                    context.contentResolver.openInputStream(uri)?.use { csvStream ->
                        val csvReader = BufferedReader(InputStreamReader(csvStream, "UTF-8"))
                        try {
                            return@withContext importFromCsvStream(csvReader)
                        } catch (e2: Exception) {
                            Log.e("ImportService", "CSV解析也失败", e2)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "无法识别文件格式\n请使用标准的CSV或Excel格式", Toast.LENGTH_LONG).show()
                            }
                            return@withContext false
                        }
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "无法读取文件内容\n请检查文件是否损坏", Toast.LENGTH_LONG).show()
        }
        return@withContext false
    }

    private suspend fun importFromCsvFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            return@withContext importFromCsvStream(reader)
        }
        return@withContext false
    }

    private suspend fun importFromCsvStream(reader: BufferedReader): Boolean = withContext(Dispatchers.IO) {
        try {
            val courses = mutableListOf<Course>()

            // 跳过标题行
            reader.readLine()

            var line: String?
            var lineNumber = 1
            while (reader.readLine().also { line = it } != null) {
                lineNumber++
                line?.let { lineText ->
                    val course = parseCsvLine(lineText, lineNumber)
                    course?.let { courses.add(it) }
                }
            }

            if (courses.isEmpty()) {
                Log.w("ImportService", "未找到有效的课程数据")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "未找到有效的课程数据\n请检查CSV格式：课程名称,教师,教室,星期,开始节次,结束节次,开始周,结束周", Toast.LENGTH_LONG).show()
                }
                return@withContext false
            }

            // 合并课程并添加
            val mergedCourses = mergeCourses(courses)
            courseDataManager.addCourses(mergedCourses)

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "成功导入 ${mergedCourses.size} 门课程", Toast.LENGTH_SHORT).show()
            }

            Log.d("ImportService", "成功导入 ${mergedCourses.size} 门课程")
            return@withContext true
        } catch (e: Exception) {
            Log.e("ImportService", "CSV导入失败", e)
            throw e
        }
    }

    private suspend fun importFromExcel(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // 第一次尝试
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // 尝试多种Excel解析方式
                val workbook: Workbook? = try {
                    WorkbookFactory.create(inputStream) // 支持.xlsx文件
                } catch (e: Exception) {
                    Log.e("ImportService", "WorkbookFactory解析失败，尝试HSSFWorkbook", e)
                    null
                }

                if (workbook != null) {
                    val sheet = workbook.getSheetAt(0)
                    val courses = mutableListOf<Course>()

                    // 智能检测格式并解析
                    val detectedFormat = detectExcelFormat(sheet)
                    Log.d("ImportService", "检测到Excel格式: $detectedFormat")

                    // 根据检测到的格式解析数据
                    when (detectedFormat) {
                        "horizontal_schedule" -> {
                            // 横向课表格式（如教务系统导出的Excel）
                            val horizontalCourses = parseHorizontalSchedule(sheet)
                            courses.addAll(horizontalCourses)
                        }
                        "standard" -> {
                            // 标准格式：课程名称,教师,教室,星期,开始节次,结束节次,开始周,结束周
                            for (rowIndex in 1..sheet.lastRowNum) {
                                val row = sheet.getRow(rowIndex) ?: continue
                                val course = parseStandardExcelRow(row)
                                course?.let { courses.add(it) }
                            }
                        }
                        "educational_system" -> {
                            // 教务系统格式：可能有特定的列顺序
                            for (rowIndex in 1..sheet.lastRowNum) {
                                val row = sheet.getRow(rowIndex) ?: continue
                                val course = parseEducationalSystemExcelRow(row)
                                course?.let { courses.add(it) }
                            }
                        }
                        else -> {
                            // 尝试自动检测列顺序
                            for (rowIndex in 1..sheet.lastRowNum) {
                                val row = sheet.getRow(rowIndex) ?: continue
                                val course = parseAutoDetectExcelRow(row)
                                course?.let { courses.add(it) }
                            }
                        }
                    }

                    if (courses.isEmpty()) {
                        Log.w("ImportService", "Excel文件中未找到有效的课程数据")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "未找到有效的课程数据\n请检查Excel格式或尝试CSV格式", Toast.LENGTH_LONG).show()
                        }
                        workbook.close()
                        return@withContext false
                    }

                    // 合并课程并添加
                    val mergedCourses = mergeCourses(courses)
                    courseDataManager.addCourses(mergedCourses)

                    workbook.close()

                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "成功导入 ${mergedCourses.size} 门课程", Toast.LENGTH_SHORT).show()
                    }

                    Log.d("ImportService", "Excel导入成功: ${mergedCourses.size} 门课程")
                    return@withContext true
                }
            }
            
            // 第二次尝试：作为CSV处理
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                Log.w("ImportService", "无法解析Excel文件，尝试作为CSV处理")
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                return@withContext importFromCsvStream(reader)
            }
            
            Log.e("ImportService", "无法打开文件流")
            return@withContext false
        } catch (e: Exception) {
            Log.e("ImportService", "Excel导入失败", e)
            // 尝试作为CSV处理
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                    return@withContext importFromCsvStream(reader)
                }
            } catch (e2: Exception) {
                Log.e("ImportService", "CSV回退也失败", e2)
            }
            throw e
        }
    }



    private fun detectExcelFormat(sheet: Sheet): String {
        // 检查第一行标题
        val headerRow = sheet.getRow(0) ?: return "unknown"

        val headerTexts = mutableListOf<String>()
        for (i in 0 until headerRow.lastCellNum) {
            val cellValue = getCellValue(headerRow.getCell(i))
            headerTexts.add(cellValue)
        }

        Log.d("ImportService", "Excel标题行: $headerTexts")

        // 检测横向课表格式（如"2025-2026-2课表"）
        if (headerTexts[0].contains("课表") && headerTexts.size > 10) {
            return "horizontal_schedule"
        }

        // 检测标准格式
        if (headerTexts.size >= 8 &&
            (headerTexts[0].contains("课程") || headerTexts[0].contains("名称")) &&
            (headerTexts[1].contains("教师") || headerTexts[1].contains("老师"))) {
            return "standard"
        }

        // 检测教务系统格式
        if (headerTexts.any { it.contains("课程名称") || it.contains("课程名") } &&
            headerTexts.any { it.contains("教师") || it.contains("任课教师") }) {
            return "educational_system"
        }

        // 检测横向格式（包含周次、日期、星期等）
        if (headerTexts.any { it.contains("周次") || it.contains("第") } &&
            headerTexts.any { it.contains("星期") || it.contains("一") }) {
            return "horizontal_schedule"
        }

        return "auto"
    }

    private fun parseStandardExcelRow(row: Row): Course? {
        return try {
            if (row.lastCellNum < 7) {
                Log.w("ImportService", "行数据列数不足: ${row.lastCellNum}")
                return null
            }

            val courseName = getCellValue(row.getCell(0)).takeIf { it.isNotBlank() } ?: return null
            val teacher = getCellValue(row.getCell(1))
            val location = getCellValue(row.getCell(2))
            val dayOfWeek = getCellValue(row.getCell(3)).toIntOrNull() ?: return null
            val startTime = getCellValue(row.getCell(4)).toIntOrNull() ?: return null
            val endTime = getCellValue(row.getCell(5)).toIntOrNull() ?: return null
            val startWeek = getCellValue(row.getCell(6)).toIntOrNull() ?: return null
            val endWeek = if (row.lastCellNum > 7) getCellValue(row.getCell(7)).toIntOrNull() ?: startWeek else startWeek

            // 验证数据有效性
            if (dayOfWeek !in 1..7 || startTime !in 1..12 || endTime !in 1..12 || startWeek !in 1..20 || endWeek !in 1..20) {
                Log.w("ImportService", "数据验证失败: day=$dayOfWeek, start=$startTime, end=$endTime, startWeek=$startWeek, endWeek=$endWeek")
                return null
            }

            Course(
                name = courseName,
                teacher = teacher,
                classroom = location,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = 0,
                alarmEnabled = true,
                alarmMinutesBefore = 15
            )
        } catch (e: Exception) {
            Log.e("ImportService", "解析Excel行失败", e)
            null
        }
    }

    private fun parseEducationalSystemExcelRow(row: Row): Course? {
        return try {
            if (row.lastCellNum < 6) {
                Log.w("ImportService", "行数据列数不足: ${row.lastCellNum}")
                return null
            }

            // 教务系统常见格式：课程名称,任课教师,上课地点,星期,节次,周次
            var courseName = ""
            var teacher = ""
            var location = ""
            var dayOfWeek = 1
            var startTime = 1
            var endTime = 2
            var startWeek = 1
            var endWeek = 16

            // 智能检测列顺序
            for (i in 0 until row.lastCellNum) {
                val cellValue = getCellValue(row.getCell(i))
                if (cellValue.isBlank()) continue

                // 检测课程名称（通常包含中文且不是数字）
                if (courseName.isBlank() && cellValue.contains("[") && cellValue.contains("]")) {
                    courseName = cellValue
                } else if (courseName.isBlank() && cellValue.length > 2 && !cellValue.matches("\\d+".toRegex())) {
                    courseName = cellValue
                }

                // 检测教师（通常包含"老师"或特定格式）
                if (teacher.isBlank() && (cellValue.contains("老师") || cellValue.contains("教师") || cellValue.contains("教授"))) {
                    teacher = cellValue
                }

                // 检测地点（包含"楼"、"教室"等关键词）
                if (location.isBlank() && (cellValue.contains("楼") || cellValue.contains("教室") || cellValue.contains("实验室"))) {
                    location = cellValue
                }

                // 检测星期
                if (cellValue.contains("一") || cellValue.contains("二") || cellValue.contains("三") ||
                    cellValue.contains("四") || cellValue.contains("五") || cellValue.contains("六") || cellValue.contains("日")) {
                    dayOfWeek = when {
                        cellValue.contains("一") -> 1
                        cellValue.contains("二") -> 2
                        cellValue.contains("三") -> 3
                        cellValue.contains("四") -> 4
                        cellValue.contains("五") -> 5
                        cellValue.contains("六") -> 6
                        cellValue.contains("日") || cellValue.contains("天") -> 7
                        else -> 1
                    }
                }

                // 检测节次（格式如"1-2节"）
                val timeMatch = Regex("(\\d+)-(\\d+)节").find(cellValue)
                if (timeMatch != null) {
                    startTime = timeMatch.groupValues[1].toInt()
                    endTime = timeMatch.groupValues[2].toInt()
                }

                // 检测周次（格式如"1-16周"）
                val weekMatch = Regex("(\\d+)-(\\d+)周").find(cellValue)
                if (weekMatch != null) {
                    startWeek = weekMatch.groupValues[1].toInt()
                    endWeek = weekMatch.groupValues[2].toInt()
                }
            }

            if (courseName.isBlank()) {
                Log.w("ImportService", "未找到课程名称")
                return null
            }

            Course(
                name = courseName,
                teacher = teacher,
                classroom = location,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = 0,
                alarmEnabled = true,
                alarmMinutesBefore = 15
            )
        } catch (e: Exception) {
            Log.e("ImportService", "解析教务系统Excel行失败", e)
            null
        }
    }

    private fun parseAutoDetectExcelRow(row: Row): Course? {
        return try {
            // 尝试多种解析方法
            var course = parseStandardExcelRow(row)
            if (course == null) {
                course = parseEducationalSystemExcelRow(row)
            }
            course
        } catch (e: Exception) {
            Log.e("ImportService", "自动解析Excel行失败", e)
            null
        }
    }

    /**
     * 解析横向课表格式（如教务系统导出的Excel）
     * 格式：每11行为一个区块，包含3周的数据
     * 第0行：标题（如"2025-2026-2课表"）
     * 第1行：周次标题（第X周）
     * 第2行：日期（MM-DD）
     * 第3行：星期（一、二、三、四、五、六、日）
     * 第4-10行：各节次的课程
     */
    private fun parseHorizontalSchedule(sheet: Sheet): List<Course> {
        val courses = mutableListOf<Course>()
        val rowCount = sheet.lastRowNum + 1

        var rowIndex = 0
        while (rowIndex < rowCount) {
            // 查找课表标题行
            val titleRow = sheet.getRow(rowIndex)
            if (titleRow == null) {
                rowIndex++
                continue
            }
            val titleCell = getCellValue(titleRow.getCell(0))

            if (!titleCell.contains("课表")) {
                rowIndex++
                continue
            }

            Log.d("ImportService", "找到课表标题行: $rowIndex")

            // 解析这个区块（11行）
            val blockCourses = parseScheduleBlock(sheet, rowIndex)
            courses.addAll(blockCourses)

            // 跳过这个区块（11行）
            rowIndex += 11
        }

        return courses
    }

    /**
     * 解析一个课表区块（11行，包含3周的数据）
     */
    private fun parseScheduleBlock(sheet: Sheet, startRow: Int): List<Course> {
        val courses = mutableListOf<Course>()

        try {
            // 第1行（相对）：周次标题
            val weekRow = sheet.getRow(startRow + 1) ?: return courses
            // 第2行（相对）：日期
            val dateRow = sheet.getRow(startRow + 2) ?: return courses
            // 第3行（相对）：星期
            val weekdayRow = sheet.getRow(startRow + 3) ?: return courses

            // 解析周次信息
            val weekNumbers = mutableListOf<Int>()
            for (col in 1 until weekRow.lastCellNum step 7) {
                val weekCell = getCellValue(weekRow.getCell(col))
                val weekMatch = Regex("""第(\d+)周""").find(weekCell)
                weekMatch?.let {
                    weekNumbers.add(it.groupValues[1].toInt())
                }
            }

            if (weekNumbers.isEmpty()) {
                Log.w("ImportService", "未找到周次信息")
                return courses
            }

            // 解析每天的课程（第4-10行是各节次）
            val timeSlotNames = listOf("第一大节", "第二大节", "第三大节", "第四大节", "第五大节", "第六大节", "第七大节")

            for (timeSlotIndex in 0 until 7) {
                val courseRow = sheet.getRow(startRow + 4 + timeSlotIndex) ?: continue
                val timeSlotName = getCellValue(courseRow.getCell(0))

                if (!timeSlotName.contains("大节")) continue

                // 解析这个节次的所有课程（每天一列）
                for (weekIndex in weekNumbers.indices) {
                    val startCol = 1 + weekIndex * 7
                    val weekNumber = weekNumbers[weekIndex]

                    for (dayOffset in 0 until 7) {
                        val col = startCol + dayOffset
                        val cellValue = getCellValue(courseRow.getCell(col))

                        if (cellValue.isBlank()) continue

                        // 解析课程信息 - 每个大节对应2个小节，所以第一大节对应1-2小节，第二大节对应3-4小节，以此类推
                        val actualTimeSlot = timeSlotIndex * 2 + 1
                        val course = parseCourseCell(cellValue, dayOffset + 1, actualTimeSlot, weekNumber)
                        course?.let { courses.add(it) }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("ImportService", "解析课表区块失败", e)
        }

        return courses
    }

    /**
     * 解析单个课程单元格的内容
     * 格式：课程名称[类型]\n教师\n周次范围\n教室
     */
    private fun parseCourseCell(cellValue: String, dayOfWeek: Int, timeSlot: Int, weekNumber: Int): Course? {
        try {
            val lines = cellValue.split("\n", "\r\n").filter { it.isNotBlank() }
            if (lines.isEmpty()) return null

            // 第一行：课程名称[类型]
            val courseNameLine = lines[0].trim()
            val courseName = courseNameLine.replace(Regex("\\[.*?\\]"), "").trim()
            if (courseName.isEmpty()) return null

            // 过滤掉乱码课程（只包含数字和字母的课程名）
            if (courseName.matches(Regex("^[A-Z0-9]+$"))) {
                Log.w("ImportService", "跳过乱码课程: $courseName")
                return null
            }

            // 第二行：教师
            var teacher = ""
            var classroom = ""
            var weekRangeLine = ""

            // 智能解析剩余行
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                
                // 检查是否是教师信息
                if (teacher.isEmpty() && (line.contains("老师") || line.contains("教师") || line.contains("教授") || line.length < 20)) {
                    teacher = line
                }
                // 检查是否是教室信息
                else if (classroom.isEmpty() && (line.contains("楼") || line.contains("教室") || line.contains("实验室") || line.contains("室"))) {
                    classroom = line
                }
                // 检查是否是周次范围
                else if (weekRangeLine.isEmpty() && (line.contains("周") || line.contains("-") || line.matches(Regex("\\d+.*\\d+")))) {
                    weekRangeLine = line
                }
            }

            // 解析周次范围
            val (startWeek, endWeek, startNode, endNode) = parseWeekRange(weekRangeLine, weekNumber, timeSlot)

            return Course(
                name = courseName,
                teacher = teacher,
                classroom = classroom,
                dayOfWeek = dayOfWeek,
                startTime = startNode,
                endTime = endNode,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = 0,
                alarmEnabled = true,
                alarmMinutesBefore = 15
            )
        } catch (e: Exception) {
            Log.e("ImportService", "解析课程单元格失败: $cellValue", e)
            return null
        }
    }

    /**
     * 解析周次范围字符串
     * 格式如："13-0102" 表示第13周第1-2节
     * 或："1-16周" 表示第1-16周
     */
    private fun parseWeekRange(weekRangeStr: String, defaultWeek: Int, defaultTimeSlot: Int): Quadruple<Int, Int, Int, Int> {
        try {
            // 尝试匹配格式如 "13-0102"
            val pattern1 = Regex("""(\d+)-(\d)(\d)(\d)(\d)""")
            val match1 = pattern1.find(weekRangeStr)
            if (match1 != null) {
                val week = match1.groupValues[1].toInt()
                val startNode = match1.groupValues[2].toInt() * 10 + match1.groupValues[3].toInt()
                val endNode = match1.groupValues[4].toInt() * 10 + match1.groupValues[5].toInt()
                // 确保每个课程只占2个时间段
                val adjustedEndNode = startNode + 1
                return Quadruple(week, week, startNode, adjustedEndNode)
            }

            // 尝试匹配格式如 "1-16周"
            val pattern2 = Regex("""(\d+)-(\d+)周""")
            val match2 = pattern2.find(weekRangeStr)
            if (match2 != null) {
                val startWeek = match2.groupValues[1].toInt()
                val endWeek = match2.groupValues[2].toInt()
                // 对于普通周次范围，每个大节占2小节
                return Quadruple(startWeek, endWeek, defaultTimeSlot, defaultTimeSlot + 1)
            }

        } catch (e: Exception) {
            Log.w("ImportService", "解析周次范围失败: $weekRangeStr")
        }

        // 默认返回 - 每个大节占2小节
        return Quadruple(defaultWeek, defaultWeek, defaultTimeSlot, defaultTimeSlot + 1)
    }

    // 辅助类用于返回四个值
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private fun getCellValue(cell: Cell?): String {
        return when {
            cell == null -> ""
            cell.cellType == CellType.STRING -> cell.stringCellValue.trim()
            cell.cellType == CellType.NUMERIC -> cell.numericCellValue.toInt().toString()
            else -> ""
        }
    }

    private fun parseCsvLine(line: String, lineNumber: Int): Course? {
        return try {
            val parts = line.split(",")
            if (parts.size < 7) {
                Log.w("ImportService", "CSV行 $lineNumber 列数不足: ${parts.size}")
                return null
            }

            val courseName = parts[0].trim().takeIf { it.isNotBlank() } ?: return null
            val teacher = parts[1].trim()
            val location = parts[2].trim()
            val dayOfWeek = parts[3].trim().toIntOrNull() ?: return null
            val startTime = parts[4].trim().toIntOrNull() ?: return null
            val endTime = parts[5].trim().toIntOrNull() ?: return null
            val startWeek = parts[6].trim().toIntOrNull() ?: return null
            val endWeek = if (parts.size > 7) parts[7].trim().toIntOrNull() ?: startWeek else startWeek

            // 验证数据有效性
            if (dayOfWeek !in 1..7 || startTime !in 1..12 || endTime !in 1..12 || startWeek !in 1..20 || endWeek !in 1..20) {
                Log.w("ImportService", "CSV行 $lineNumber 数据验证失败")
                return null
            }

            Course(
                name = courseName,
                teacher = teacher,
                classroom = location,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = 0,
                alarmEnabled = true,
                alarmMinutesBefore = 15
            )
        } catch (e: Exception) {
            Log.e("ImportService", "解析CSV行 $lineNumber 失败", e)
            null
        }
    }

    fun getCsvTemplate(): String {
        return "课程名称,教师姓名,上课地点,星期(1-7),开始节次,结束节次,开始周,结束周\n" +
                "高等数学,张老师,教学楼A101,1,1,2,1,16\n" +
                "大学英语,李老师,教学楼B201,2,3,4,1,16\n" +
                "计算机基础,王老师,计算机实验室,3,5,6,1,8\n" +
                "体育课,赵老师,操场,4,7,8,9,16"
    }

    private fun mergeCourses(courses: List<Course>): List<Course> {
        // 按照课程名称、教师、教室、星期、开始节次、结束节次分组
        val courseGroups = courses.groupBy {
            "${it.name}-${it.teacher}-${it.classroom}-${it.dayOfWeek}-${it.startTime}-${it.endTime}"
        }

        val mergedCourses = mutableListOf<Course>()

        courseGroups.forEach { (_, groupCourses) ->
            if (groupCourses.isEmpty()) return@forEach

            // 找到最小的开始周和最大的结束周
            val startWeek = groupCourses.minOf { it.startWeek }
            val endWeek = groupCourses.maxOf { it.endWeek }

            // 检测是否所有周次都是单周或双周
            val weekNumbers = groupCourses.map { it.startWeek }.toSet()
            val isAllOdd = weekNumbers.all { it % 2 == 1 }
            val isAllEven = weekNumbers.all { it % 2 == 0 }

            val weekType = when {
                isAllOdd -> 1 // 单周
                isAllEven -> 2 // 双周
                else -> 0 // 每周
            }

            // 使用第一个课程的其他属性
            val firstCourse = groupCourses[0]
            val mergedCourse = Course(
                name = firstCourse.name,
                teacher = firstCourse.teacher,
                classroom = firstCourse.classroom,
                dayOfWeek = firstCourse.dayOfWeek,
                startTime = firstCourse.startTime,
                endTime = firstCourse.endTime,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = weekType,
                alarmEnabled = firstCourse.alarmEnabled,
                alarmMinutesBefore = firstCourse.alarmMinutesBefore,
                color = firstCourse.color
            )

            mergedCourses.add(mergedCourse)
        }

        return mergedCourses
    }

    private fun getFileName(uri: Uri): String? {
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    companion object {
        fun parseCoursesFromJson(json: String): List<Course> {
            return try {
                val courses = mutableListOf<Course>()
                val jsonArray = org.json.JSONArray(json)

                for (i in 0 until jsonArray.length()) {
                    val courseObj = jsonArray.getJSONObject(i)

                    val course = Course(
                        id = courseObj.optLong("id", 0),
                        name = courseObj.optString("name", "").takeIf { it.isNotBlank() } ?: continue,
                        teacher = courseObj.optString("teacher", ""),
                        classroom = courseObj.optString("classroom", ""),
                        dayOfWeek = courseObj.optInt("dayOfWeek", 1).coerceIn(1, 7),
                        startTime = courseObj.optInt("startTime", 1).coerceIn(1, 12),
                        endTime = courseObj.optInt("endTime", 2).coerceIn(1, 12),
                        startWeek = courseObj.optInt("startWeek", 1).coerceIn(1, 20),
                        endWeek = courseObj.optInt("endWeek", 16).coerceIn(1, 20),
                        weekType = courseObj.optInt("weekType", 0).coerceIn(0, 2),
                        alarmEnabled = courseObj.optBoolean("alarmEnabled", true),
                        alarmMinutesBefore = courseObj.optInt("alarmMinutesBefore", 15),
                        color = courseObj.optInt("color", 0xFF6200EE.toInt())
                    )

                    courses.add(course)
                }

                courses
            } catch (e: Exception) {
                Log.e("ImportService", "JSON解析失败", e)
                emptyList()
            }
        }
    }
}
