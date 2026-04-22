package com.cherry.wakeupschedule.service

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.cherry.wakeupschedule.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 导入服务
 * 从Excel/CSV文件导入课程数据
 */
class ImportService(private val context: Context) {

    private val courseDataManager = CourseDataManager.getInstance(context)

    // 从文件导入
    suspend fun importFromFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            var fileName = getFileName(uri) ?: uri.path?.let { java.io.File(it).name }
            Log.d("ImportService", "开始导入文件: $fileName")
            when {
                fileName?.endsWith(".xls") == true || fileName?.endsWith(".xlsx") == true -> importFromExcel(uri)
                fileName?.endsWith(".csv") == true -> importFromCsvFile(uri)
                else -> tryImportByContent(uri)
            }
        } catch (e: Exception) {
            Log.e("ImportService", "导入失败", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "导入失败: ${e.message}\n请检查文件格式", Toast.LENGTH_LONG).show()
            }
            false
        }
    }

    // 根据内容判断类型并导入
    private suspend fun tryImportByContent(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                val firstLine = reader.readLine()
                if (firstLine?.contains(",") == true) {
                    reader.close()
                    context.contentResolver.openInputStream(uri)?.use { csvStream ->
                        return@withContext importFromCsvStream(BufferedReader(InputStreamReader(csvStream, "UTF-8")))
                    }
                } else {
                    try {
                        return@withContext importFromExcel(uri)
                    } catch (e: Exception) {
                        context.contentResolver.openInputStream(uri)?.use { csvStream ->
                            try {
                                return@withContext importFromCsvStream(BufferedReader(InputStreamReader(csvStream, "UTF-8")))
                            } catch (e2: Exception) {
                                withContext(Dispatchers.Main) { Toast.makeText(context, "无法识别文件格式", Toast.LENGTH_LONG).show() }
                                return@withContext false
                            }
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) { Toast.makeText(context, "无法读取文件内容", Toast.LENGTH_LONG).show() }
            false
        } catch (e: Exception) {
            Log.e("ImportService", "tryImportByContent失败", e)
            withContext(Dispatchers.Main) { Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show() }
            false
        }
    }

    private suspend fun importFromCsvFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                return@withContext importFromCsvStream(BufferedReader(InputStreamReader(inputStream, "UTF-8")))
            }
            false
        } catch (e: Exception) {
            Log.e("ImportService", "importFromCsvFile失败", e)
            throw e
        }
    }

    // 解析CSV流
    private suspend fun importFromCsvStream(reader: BufferedReader): Boolean = withContext(Dispatchers.IO) {
        try {
            val courses = mutableListOf<Course>()
            reader.readLine() // 跳过标题行
            var line: String?
            var lineNumber = 1
            while (reader.readLine().also { line = it } != null) {
                lineNumber++
                line?.let { parseCsvLine(it, lineNumber)?.let { course -> courses.add(course) } }
            }
            if (courses.isEmpty()) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "未找到有效课程数据", Toast.LENGTH_LONG).show() }
                return@withContext false
            }
            val merged = mergeCourses(courses)
            courseDataManager.addCourses(merged)
            withContext(Dispatchers.Main) { Toast.makeText(context, "成功导入 ${merged.size} 门课程", Toast.LENGTH_SHORT).show() }
            Log.d("ImportService", "成功导入 ${merged.size} 门课程")
            true
        } catch (e: Exception) {
            Log.e("ImportService", "CSV导入失败", e)
            throw e
        }
    }

    // 从Excel导入
    private suspend fun importFromExcel(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook: Workbook? = try { WorkbookFactory.create(inputStream) } catch (e: Exception) { null }
                if (workbook != null) {
                    val sheet = workbook.getSheetAt(0)
                    val courses = mutableListOf<Course>()
                    val format = detectExcelFormat(sheet)
                    Log.d("ImportService", "检测到Excel格式: $format")
                    when (format) {
                        "horizontal_schedule" -> courses.addAll(parseHorizontalSchedule(sheet))
                        "standard" -> for (i in 1..sheet.lastRowNum) sheet.getRow(i)?.let { parseStandardExcelRow(it)?.let { c -> courses.add(c) } }
                        "educational_system" -> for (i in 1..sheet.lastRowNum) sheet.getRow(i)?.let { parseEducationalSystemExcelRow(it)?.let { c -> courses.add(c) } }
                        else -> for (i in 1..sheet.lastRowNum) sheet.getRow(i)?.let { parseAutoDetectExcelRow(it)?.let { c -> courses.add(c) } }
                    }
                    if (courses.isEmpty()) {
                        withContext(Dispatchers.Main) { Toast.makeText(context, "未找到有效课程数据", Toast.LENGTH_LONG).show() }
                        workbook.close()
                        return@withContext false
                    }
                    val merged = mergeCourses(courses)
                    courseDataManager.addCourses(merged)
                    workbook.close()
                    withContext(Dispatchers.Main) { Toast.makeText(context, "成功导入 ${merged.size} 门课程", Toast.LENGTH_SHORT).show() }
                    return@withContext true
                }
            }
            // 回退到CSV
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                return@withContext importFromCsvStream(BufferedReader(InputStreamReader(inputStream, "UTF-8")))
            }
            false
        } catch (e: Exception) {
            Log.e("ImportService", "Excel导入失败", e)
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    return@withContext importFromCsvStream(BufferedReader(InputStreamReader(inputStream, "UTF-8")))
                }
            } catch (e2: Exception) { Log.e("ImportService", "CSV回退也失败", e2) }
            throw e
        }
    }

    // 检测Excel格式
    private fun detectExcelFormat(sheet: Sheet): String {
        val headerRow = sheet.getRow(0) ?: return "unknown"
        val headerTexts = (0 until headerRow.lastCellNum).map { getCellValue(headerRow.getCell(it)) }
        Log.d("ImportService", "Excel标题行: $headerTexts")
        if (headerTexts[0].contains("课表") && headerTexts.size > 10) return "horizontal_schedule"
        if (headerTexts.size >= 8 && (headerTexts[0].contains("课程") || headerTexts[0].contains("名称")) && (headerTexts[1].contains("教师") || headerTexts[1].contains("老师"))) return "standard"
        if (headerTexts.any { it.contains("课程名称") || it.contains("课程名") } && headerTexts.any { it.contains("教师") || it.contains("任课教师") }) return "educational_system"
        if (headerTexts.any { it.contains("周次") || it.contains("第") } && headerTexts.any { it.contains("星期") || it.contains("一") }) return "horizontal_schedule"
        return "auto"
    }

    // 解析标准格式Excel行
    private fun parseStandardExcelRow(row: Row): Course? {
        return try {
            if (row.lastCellNum < 7) return null
            val courseName = getCellValue(row.getCell(0)).takeIf { it.isNotBlank() } ?: return null
            val dayOfWeek = getCellValue(row.getCell(3)).toIntOrNull() ?: return null
            val startTime = getCellValue(row.getCell(4)).toIntOrNull() ?: return null
            val endTime = getCellValue(row.getCell(5)).toIntOrNull() ?: return null
            val startWeek = getCellValue(row.getCell(6)).toIntOrNull() ?: return null
            val endWeek = if (row.lastCellNum > 7) getCellValue(row.getCell(7)).toIntOrNull() ?: startWeek else startWeek
            if (dayOfWeek !in 1..7 || startTime !in 1..12 || endTime !in 1..12 || startWeek !in 1..20 || endWeek !in 1..20) return null
            Course(
                name = courseName,
                teacher = getCellValue(row.getCell(1)),
                classroom = getCellValue(row.getCell(2)),
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                startWeek = startWeek,
                endWeek = endWeek
            )
        } catch (e: Exception) {
            Log.e("ImportService", "解析Excel行失败", e)
            null
        }
    }

    // 解析教务系统Excel行
    private fun parseEducationalSystemExcelRow(row: Row): Course? {
        return try {
            if (row.lastCellNum < 6) return null
            var courseName = ""
            var teacher = ""
            var location = ""
            var dayOfWeek = 1
            var startTime = 1
            var endTime = 2
            var startWeek = 1
            var endWeek = 16

            for (i in 0 until row.lastCellNum) {
                val v = getCellValue(row.getCell(i))
                if (v.isBlank()) continue
                if (courseName.isBlank() && v.contains("[") && v.contains("]")) courseName = v
                else if (courseName.isBlank() && v.length > 2 && !v.matches("\\d+".toRegex())) courseName = v
                if (teacher.isBlank() && (v.contains("老师") || v.contains("教师") || v.contains("教授"))) teacher = v
                if (location.isBlank() && (v.contains("楼") || v.contains("教室") || v.contains("实验室"))) location = v
                if (v.contains("一") || v.contains("二") || v.contains("三") || v.contains("四") || v.contains("五") || v.contains("六") || v.contains("日")) {
                    dayOfWeek = when {
                        v.contains("一") -> 1
                        v.contains("二") -> 2
                        v.contains("三") -> 3
                        v.contains("四") -> 4
                        v.contains("五") -> 5
                        v.contains("六") -> 6
                        else -> 7
                    }
                }
                Regex("(\\d+)-(\\d+)节").find(v)?.let {
                    startTime = it.groupValues[1].toInt()
                    endTime = it.groupValues[2].toInt()
                }
                Regex("(\\d+)-(\\d+)周").find(v)?.let {
                    startWeek = it.groupValues[1].toInt()
                    endWeek = it.groupValues[2].toInt()
                }
            }
            if (courseName.isBlank()) return null
            Course(
                name = courseName,
                teacher = teacher,
                classroom = location,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                startWeek = startWeek,
                endWeek = endWeek
            )
        } catch (e: Exception) {
            Log.e("ImportService", "解析教务系统Excel行失败", e)
            null
        }
    }

    private fun parseAutoDetectExcelRow(row: Row): Course? {
        return try {
            parseStandardExcelRow(row) ?: parseEducationalSystemExcelRow(row)
        } catch (e: Exception) {
            Log.e("ImportService", "自动解析Excel行失败", e)
            null
        }
    }

    // 解析横向课表格式
    private fun parseHorizontalSchedule(sheet: Sheet): List<Course> {
        val courses = mutableListOf<Course>()
        var rowIndex = 0
        while (rowIndex < sheet.lastRowNum + 1) {
            val titleRow = sheet.getRow(rowIndex)
            if (titleRow == null) {
                rowIndex++
                continue
            }
            if (!getCellValue(titleRow.getCell(0)).contains("课表")) {
                rowIndex++
                continue
            }
            Log.d("ImportService", "找到课表标题行: $rowIndex")
            courses.addAll(parseScheduleBlock(sheet, rowIndex))
            rowIndex += 11
        }
        return courses
    }

    // 解析课表区块（11行）
    private fun parseScheduleBlock(sheet: Sheet, startRow: Int): List<Course> {
        val courses = mutableListOf<Course>()
        try {
            val weekRow = sheet.getRow(startRow + 1) ?: return courses
            val weekNumbers = mutableListOf<Int>()
            for (col in 1 until weekRow.lastCellNum step 7) {
                val weekCellValue = getCellValue(weekRow.getCell(col))
                Regex("""第(\d+)周""").find(weekCellValue)?.let { weekNumbers.add(it.groupValues[1].toInt()) }
            }
            if (weekNumbers.isEmpty()) return courses
            for (slotIndex in 0 until 7) {
                val courseRow = sheet.getRow(startRow + 4 + slotIndex) ?: continue
                if (!getCellValue(courseRow.getCell(0)).contains("大节")) continue
                for (weekIdx in weekNumbers.indices) {
                    val startCol = 1 + weekIdx * 7
                    val weekNum = weekNumbers[weekIdx]
                    for (dayOff in 0 until 7) {
                        val col = startCol + dayOff
                        val cellValue = getCellValue(courseRow.getCell(col))
                        if (cellValue.isBlank()) continue
                        parseCourseCell(cellValue, dayOff + 1, slotIndex * 2 + 1, weekNum)?.let { courses.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ImportService", "解析课表区块失败", e)
        }
        return courses
    }

    // 解析单个课程单元格
    private fun parseCourseCell(cellValue: String, dayOfWeek: Int, timeSlot: Int, weekNumber: Int): Course? {
        return try {
            val lines = cellValue.split("\n", "\r\n").filter { it.isNotBlank() }
            if (lines.isEmpty()) return null
            val courseName = lines[0].trim().replace(Regex("\\[.*?\\]"), "").trim()
            if (courseName.isEmpty() || courseName.matches(Regex("^[A-Z0-9]+$"))) return null
            var teacher = ""
            var classroom = ""
            var weekRangeLine = ""
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (teacher.isBlank() && (line.contains("老师") || line.contains("教师") || line.contains("教授") || line.length < 20)) teacher = line
                else if (classroom.isBlank() && (line.contains("楼") || line.contains("教室") || line.contains("实验室") || line.contains("室"))) classroom = line
                else if (weekRangeLine.isBlank() && (line.contains("周") || line.contains("-") || line.matches(Regex("\\d+.*\\d+")))) weekRangeLine = line
            }
            val (startWeek, endWeek, startNode, endNode) = parseWeekRange(weekRangeLine, weekNumber, timeSlot)
            Course(
                name = courseName,
                teacher = teacher,
                classroom = classroom,
                dayOfWeek = dayOfWeek,
                startTime = startNode,
                endTime = endNode,
                startWeek = startWeek,
                endWeek = endWeek
            )
        } catch (e: Exception) {
            Log.e("ImportService", "解析课程单元格失败", e)
            null
        }
    }

    // 解析周次范围
    private fun parseWeekRange(weekRangeStr: String, defaultWeek: Int, defaultTimeSlot: Int): Quadruple<Int, Int, Int, Int> {
        try {
            Regex("""(\d+)-(\d)(\d)(\d)(\d)""").find(weekRangeStr)?.let {
                val week = it.groupValues[1].toInt()
                // 正确解析：如"1-0102"表示第1周的第1-2节
                val startTime = (it.groupValues[2] + it.groupValues[3]).toInt()
                val endTime = (it.groupValues[4] + it.groupValues[5]).toInt()
                return Quadruple(week, week, startTime, endTime)
            }
            Regex("""(\d+)-(\d+)周""").find(weekRangeStr)?.let {
                return Quadruple(it.groupValues[1].toInt(), it.groupValues[2].toInt(), defaultTimeSlot, defaultTimeSlot + 1)
            }
        } catch (e: Exception) {
            Log.w("ImportService", "解析周次范围失败: $weekRangeStr")
        }
        return Quadruple(defaultWeek, defaultWeek, defaultTimeSlot, defaultTimeSlot + 1)
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    // 获取单元格值
    private fun getCellValue(cell: Cell?): String = when {
        cell == null -> ""
        cell.cellType == CellType.STRING -> cell.stringCellValue.trim()
        cell.cellType == CellType.NUMERIC -> cell.numericCellValue.toInt().toString()
        else -> ""
    }

    // 解析CSV行
    private fun parseCsvLine(line: String, lineNumber: Int): Course? {
        return try {
            val parts = line.split(",")
            if (parts.size < 7) return null
            val courseName = parts[0].trim().takeIf { it.isNotBlank() } ?: return null
            val dayOfWeek = parts[3].trim().toIntOrNull() ?: return null
            val startTime = parts[4].trim().toIntOrNull() ?: return null
            val endTime = parts[5].trim().toIntOrNull() ?: return null
            val startWeek = parts[6].trim().toIntOrNull() ?: return null
            val endWeek = if (parts.size > 7) parts[7].trim().toIntOrNull() ?: startWeek else startWeek
            if (dayOfWeek !in 1..7 || startTime !in 1..12 || endTime !in 1..12 || startWeek !in 1..20 || endWeek !in 1..20) return null
            Course(
                name = courseName,
                teacher = parts[1].trim(),
                classroom = parts[2].trim(),
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                startWeek = startWeek,
                endWeek = endWeek
            )
        } catch (e: Exception) {
            Log.e("ImportService", "解析CSV行 $lineNumber 失败", e)
            null
        }
    }

    // 获取CSV模板
    fun getCsvTemplate(): String {
        return "课程名称,教师姓名,上课地点,星期(1-7),开始节次,结束节次,开始周,结束周\n高等数学,张老师,教学楼A101,1,1,2,1,16\n大学英语,李老师,教学楼B201,2,3,4,1,16\n"
    }

    // 合并相同课程
    private fun mergeCourses(courses: List<Course>): List<Course> {
        val groups = courses.groupBy { "${it.name}-${it.teacher}-${it.classroom}-${it.dayOfWeek}-${it.startTime}-${it.endTime}" }
        return groups.map { (_, group) ->
            val startWeek = group.minOf { it.startWeek }
            val endWeek = group.maxOf { it.endWeek }
            val weekNumbers = group.map { it.startWeek }.toSet()
            val weekType = when {
                weekNumbers.all { w -> w % 2 == 1 } -> 1
                weekNumbers.all { w -> w % 2 == 0 } -> 2
                else -> 0
            }
            val first = group[0]
            Course(
                name = first.name,
                teacher = first.teacher,
                classroom = first.classroom,
                dayOfWeek = first.dayOfWeek,
                startTime = first.startTime,
                endTime = first.endTime,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = weekType,
                alarmEnabled = first.alarmEnabled,
                alarmMinutesBefore = first.alarmMinutesBefore,
                color = first.color
            )
        }
    }

    private fun getFileName(uri: Uri): String? {
        val projection = arrayOf(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns.DISPLAY_NAME))
        }
        return null
    }

    companion object {
        // 从JSON解析课程列表
        fun parseCoursesFromJson(json: String): List<Course> {
            return try {
                val courses = mutableListOf<Course>()
                val jsonArray = org.json.JSONArray(json)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    courses.add(Course(
                        id = obj.optLong("id", 0),
                        name = obj.optString("name", "").takeIf { it.isNotBlank() } ?: continue,
                        teacher = obj.optString("teacher", ""),
                        classroom = obj.optString("classroom", ""),
                        dayOfWeek = obj.optInt("dayOfWeek", 1).coerceIn(1, 7),
                        startTime = obj.optInt("startTime", 1).coerceIn(1, 12),
                        endTime = obj.optInt("endTime", 2).coerceIn(1, 12),
                        startWeek = obj.optInt("startWeek", 1).coerceIn(1, 20),
                        endWeek = obj.optInt("endWeek", 16).coerceIn(1, 20),
                        weekType = obj.optInt("weekType", 0).coerceIn(0, 2),
                        alarmEnabled = obj.optBoolean("alarmEnabled", true),
                        alarmMinutesBefore = obj.optInt("alarmMinutesBefore", 15),
                        color = obj.optInt("color", 0xFF6200EE.toInt())
                    ))
                }
                courses
            } catch (e: Exception) {
                Log.e("ImportService", "JSON解析失败", e)
                emptyList()
            }
        }
    }
}
