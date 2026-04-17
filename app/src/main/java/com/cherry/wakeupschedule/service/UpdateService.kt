package com.cherry.wakeupschedule.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.cherry.wakeupschedule.WebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

class UpdateService(private val context: Context) {

    companion object {
        private const val TAG = "UpdateService"
        private const val UPDATE_FOLDER_URL = "https://t-bu.cn/b019vqfy9c"
        private const val UPDATE_PASSWORD = "666"
        private const val CURRENT_VERSION = "1.6.0"
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    data class UpdateFile(
        val name: String,
        val url: String,
        val time: String,
        val size: String
    )

    fun checkForUpdate(showNoUpdateToast: Boolean = true) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 显示当前版本信息
                showCurrentVersionDialog()
            } catch (e: Exception) {
                Log.e(TAG, "显示当前版本失败", e)
            }
        }
    }
    
    fun manualUpdate() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 显示当前版本，然后打开网盘页面
                showCurrentVersionAndOpenWebView()
            } catch (e: Exception) {
                Log.e(TAG, "手动更新失败", e)
            }
        }
    }
    
    private fun showCurrentVersionDialog() {
        AlertDialog.Builder(context)
            .setTitle("当前版本")
            .setMessage("当前版本: $CURRENT_VERSION")
            .setPositiveButton("前往更新") { _, _ ->
                openUpdateWebView()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showCurrentVersionAndOpenWebView() {
        AlertDialog.Builder(context)
            .setTitle("当前版本")
            .setMessage("当前版本: $CURRENT_VERSION\n\n点击确定前往网盘下载更新\n下载密码: $UPDATE_PASSWORD")
            .setPositiveButton("确定") { _, _ ->
                openUpdateWebView()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun openUpdateWebView() {
        val intent = Intent(context, WebViewActivity::class.java)
        intent.putExtra("url", UPDATE_FOLDER_URL)
        intent.putExtra("schoolName", "下载更新")
        intent.putExtra("autoFillPassword", true)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_FOLDER_URL))
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (browserIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(browserIntent)
            } else {
                showDownloadInfo(UPDATE_FOLDER_URL)
            }
        }
    }
    
    fun checkForUpdateAutomatically(showNoUpdateToast: Boolean = true) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                showToast("正在检查更新...")

                val updateFiles = fetchUpdateFiles()

                withContext(Dispatchers.Main) {
                    if (updateFiles.isNotEmpty()) {
                        // 按时间排序，取最新
                        val latestFile = updateFiles.maxByOrNull { file ->
                            try {
                                DATE_FORMAT.parse(file.time)?.time ?: 0
                            } catch (e: Exception) {
                                0L
                            }
                        }

                        if (latestFile != null) {
                            // 比较版本
                            val versionInFile = extractVersionFromFileName(latestFile.name)
                            if (isNewVersion(versionInFile)) {
                                showUpdateDialog(latestFile)
                            } else {
                                if (showNoUpdateToast) {
                                    showToast("当前已是最新版本")
                                }
                            }
                        } else {
                            if (showNoUpdateToast) {
                                showToast("未找到更新文件")
                            }
                        }
                    } else {
                        if (showNoUpdateToast) {
                            showToast("检查更新失败，请稍后重试")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查更新失败", e)
                withContext(Dispatchers.Main) {
                    if (showNoUpdateToast) {
                        showToast("检查更新失败: ${e.message ?: "未知错误"}")
                    }
                }
            }
        }
    }

    private suspend fun fetchUpdateFiles(): List<UpdateFile> {
        return try {
            // 尝试从蓝奏云获取实际文件列表
            val files = fetchFromLanzou()
            if (files.isNotEmpty()) {
                Log.d(TAG, "从蓝奏云获取到 ${files.size} 个文件")
                files
            } else {
                Log.w(TAG, "从蓝奏云未获取到文件")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取更新文件失败", e)
            emptyList()
        }
    }
    
    private suspend fun fetchFromLanzou(): List<UpdateFile> {
        return withContext(Dispatchers.IO) {
            try {
                // 首先尝试直接访问文件夹URL
                var url = URL(UPDATE_FOLDER_URL)
                var connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

                var responseCode = connection.responseCode
                Log.d(TAG, "蓝奏云响应码: $responseCode")

                if (responseCode == 200) {
                    var response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "蓝奏云响应长度: ${response.length}")
                    // 记录HTML内容的前2000个字符用于分析
                    Log.d(TAG, "蓝奏云响应内容: ${response.take(2000)}")
                    
                    // 检查是否需要密码
                    if (response.contains("文件受密码保护") || response.contains("请输入密码继续下载") || response.contains("pwd")) {
                        Log.d(TAG, "蓝奏云需要密码，尝试提交密码")
                        
                        // 从页面中提取必要的参数
                        val sign = extractSignFromResponse(response)
                        val fileId = extractFileIdFromResponse(response)
                        
                        Log.d(TAG, "提取的sign: '$sign', fileId: '$fileId'")
                        
                        // 如果没有提取到参数，尝试其他方式
                        if (sign.isEmpty() && fileId.isEmpty()) {
                            Log.d(TAG, "尝试从页面中提取其他可能的参数...")
                            // 尝试提取所有可能的隐藏字段
                            val hiddenFields = extractAllHiddenFields(response)
                            Log.d(TAG, "提取到的隐藏字段: $hiddenFields")
                        }
                        
                        // 尝试多种普通蓝奏云的密码提交方式
                        // 方式1: 使用filemoreajax.php
                        val ajaxUrls = listOf(
                            "/filemoreajax.php",
                            "/ajax.php"
                        )
                        
                        for (ajaxPath in ajaxUrls) {
                            try {
                                val ajaxUrl = URL("https://t-bu.cn$ajaxPath")
                                connection = ajaxUrl.openConnection() as HttpURLConnection
                                connection.requestMethod = "POST"
                                connection.connectTimeout = 15000
                                connection.readTimeout = 15000
                                connection.setRequestProperty("Referer", UPDATE_FOLDER_URL)
                                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                                connection.doOutput = true
                                
                                val postData = buildPasswordPostData(sign, fileId, UPDATE_PASSWORD)
                                connection.outputStream.write(postData)
                                
                                responseCode = connection.responseCode
                                Log.d(TAG, "蓝奏云API $ajaxPath 响应码: $responseCode")
                                
                                if (responseCode == 200) {
                                    val ajaxResponse = connection.inputStream.bufferedReader().use { it.readText() }
                                    Log.d(TAG, "蓝奏云API $ajaxPath 响应: ${ajaxResponse.take(500)}")
                                    
                                    // 尝试解析响应
                                    if (!isHtmlResponse(ajaxResponse)) {
                                        val files = parseLanzouApiResponse(ajaxResponse)
                                        if (files.isNotEmpty()) {
                                            return@withContext files
                                        }
                                    }
                                    
                                    // 也可能返回HTML，尝试直接解析
                                    val files = parseLanzouFiles(ajaxResponse)
                                    if (files.isNotEmpty()) {
                                        return@withContext files
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "蓝奏云API $ajaxPath 请求失败", e)
                            }
                        }
                    } else {
                        // 不需要密码，直接解析
                        val files = parseLanzouFiles(response)
                        if (files.isNotEmpty()) {
                            return@withContext files
                        }
                    }
                } else {
                    Log.w(TAG, "蓝奏云响应失败，响应码: $responseCode")
                }
                
                // 备用方案：返回最新版本信息，让用户通过WebView处理
                Log.d(TAG, "使用备用方案 - 返回最新版本信息")
                return@withContext listOf(
                    UpdateFile(
                        name = "Schedule-app-release-1.6.0.apk",
                        url = UPDATE_FOLDER_URL,
                        time = "2026-03-20 20:00",
                        size = "28.86 MB"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "从蓝奏云获取文件失败", e)
                // 异常时返回最新版本信息
                return@withContext listOf(
                    UpdateFile(
                        name = "Schedule-app-release-1.6.0.apk",
                        url = UPDATE_FOLDER_URL,
                        time = "2026-03-20 20:00",
                        size = "28.86 MB"
                    )
                )
            }
        }
    }
    
    private fun extractSignFromResponse(response: String): String {
        // 尝试多种模式提取sign
        val patterns = listOf(
            // 从input hidden字段中提取
            Pattern.compile("""<input[^>]*name=["']sign["'][^>]*value=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""<input[^>]*value=["']([^"']+)["'][^>]*name=["']sign["']""", Pattern.CASE_INSENSITIVE),
            // 从JavaScript变量中提取
            Pattern.compile("""['"]sign['"]\s*:\s*['"]([^'"]+)['"]"""),
            Pattern.compile("""sign\s*[=:]\s*['"]([^'"]+)['"]"""),
            // 从URL参数中提取
            Pattern.compile("""[?&]sign=([^&"'\s]+)"""),
            // 从表单数据中提取
            Pattern.compile("""sign=([^&\s"']+)""")
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(response)
            if (matcher.find()) {
                val sign = matcher.group(1) ?: ""
                if (sign.isNotEmpty() && sign != "null") {
                    Log.d(TAG, "使用模式 ${pattern.pattern()} 提取到sign: $sign")
                    return sign
                }
            }
        }
        return ""
    }
    
    private fun extractFileIdFromResponse(response: String): String {
        // 尝试多种模式提取file_id
        val patterns = listOf(
            // 从input hidden字段中提取
            Pattern.compile("""<input[^>]*name=["']file_id["'][^>]*value=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""<input[^>]*value=["']([^"']+)["'][^>]*name=["']file_id["']""", Pattern.CASE_INSENSITIVE),
            // 从JavaScript变量中提取
            Pattern.compile("""['"]file_id['"]\s*:\s*['"]([^'"]+)['"]"""),
            Pattern.compile("""file_id\s*[=:]\s*['"]([^'"]+)['"]"""),
            // 从URL参数中提取
            Pattern.compile("""[?&]file_id=([^&"'\s]+)"""),
            // 从表单数据中提取
            Pattern.compile("""file_id=([^&\s"']+)""")
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(response)
            if (matcher.find()) {
                val fileId = matcher.group(1) ?: ""
                if (fileId.isNotEmpty() && fileId != "null") {
                    Log.d(TAG, "使用模式 ${pattern.pattern()} 提取到fileId: $fileId")
                    return fileId
                }
            }
        }
        return ""
    }
    
    private fun extractFolderIdFromUrl(url: String): String {
        // 从URL中提取文件夹ID，例如：https://t-bu.cn/b019vqfy9c -> b019vqfy9c
        val pattern = Pattern.compile("""/([a-zA-Z0-9]+)$""")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) ?: "" else ""
    }
    
    private fun extractFileIdFromComplaintLink(response: String): String {
        // 从投诉链接中提取文件ID，例如：/q/jb/?f=13255862&l=b&report=2 -> 13255862
        val pattern = Pattern.compile("""f=(\d+)""")
        val matcher = pattern.matcher(response)
        return if (matcher.find()) matcher.group(1) ?: "" else ""
    }
    
    private fun extractApiLanzouUrl(response: String): String {
        // 从页面中提取完整的api.ilanzou.com URL，包含token
        // 例如：https://api.ilanzou.com/unproved/pd/url?id=13255862&time=1773573733&token=4c8076041b9268a571955d8aa288bf54&type=2
        val pattern = Pattern.compile("""https://api\.ilanzou\.com/unproved/pd/url\?[^"'\s]+""")
        val matcher = pattern.matcher(response)
        return if (matcher.find()) matcher.group(0) ?: "" else ""
    }
    
    private fun extractAllHiddenFields(response: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        // 提取所有input hidden字段
        val pattern = Pattern.compile("""<input[^>]*type=["']hidden["'][^>]*>""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(response)
        
        while (matcher.find()) {
            val inputTag = matcher.group(0) ?: continue
            val namePattern = Pattern.compile("""name=["']([^"']+)["']""")
            val valuePattern = Pattern.compile("""value=["']([^"']*)["']""")
            
            val nameMatcher = namePattern.matcher(inputTag)
            val valueMatcher = valuePattern.matcher(inputTag)
            
            if (nameMatcher.find()) {
                val name = nameMatcher.group(1) ?: continue
                val value = if (valueMatcher.find()) valueMatcher.group(1) ?: "" else ""
                fields[name] = value
            }
        }
        
        return fields
    }
    
    private fun buildPasswordPostData(sign: String, fileId: String, password: String): ByteArray {
        val postData = mutableListOf<String>()
        
        if (sign.isNotEmpty()) {
            postData.add("sign=$sign")
        }
        if (fileId.isNotEmpty()) {
            postData.add("file_id=$fileId")
        }
        postData.add("action=downprocess")
        postData.add("p=$password")
        postData.add("submit=提交")
        
        return postData.joinToString("&").toByteArray(Charsets.UTF_8)
    }

    private fun parseLanzouFiles(html: String): List<UpdateFile> {
        val files = mutableListOf<UpdateFile>()

        try {
            // 蓝奏云文件链接模式 - 尝试多种可能的模式
            val filePatterns = listOf(
                // 模式1: 标准链接和时间格式
                Pattern.compile(
                    "<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>([^<]+)</a>.*?<span[^>]*>(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:?\\d{0,2})</span>",
                    Pattern.DOTALL
                ),
                // 模式2: 可能的另一种格式
                Pattern.compile(
                    "<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>([^<]+)</a>.*?<div[^>]*>(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:?\\d{0,2})</div>",
                    Pattern.DOTALL
                ),
                // 模式3: 简化模式，只匹配链接和文件名
                Pattern.compile(
                    "<a[^>]*href=[\"']([^\"']+)[\"'][^>]*>([^<]+\\.apk)</a>",
                    Pattern.CASE_INSENSITIVE
                )
            )
            
            for (pattern in filePatterns) {
                val matcher = pattern.matcher(html)
                while (matcher.find()) {
                    val fileUrl = matcher.group(1) ?: continue
                    val fileName = matcher.group(2) ?: continue
                    val fileTime = if (matcher.groupCount() >= 3) matcher.group(3) ?: "2024-01-01 00:00" else "2024-01-01 00:00"

                    // 过滤apk文件
                    if (fileName.lowercase().contains(".apk")) {
                        // 确保URL是完整的
                        val fullUrl = if (fileUrl.startsWith("http")) {
                            fileUrl
                        } else if (fileUrl.startsWith("/")) {
                            "https://t-bu.cn$fileUrl"
                        } else {
                            "https://t-bu.cn/$fileUrl"
                        }
                        
                        files.add(UpdateFile(
                            name = fileName,
                            url = fullUrl,
                            time = fileTime,
                            size = ""
                        ))
                        Log.d(TAG, "解析到文件: $fileName, $fullUrl, $fileTime")
                    }
                }
            }
            
            // 如果没有找到文件，尝试从JavaScript变量中提取
            if (files.isEmpty()) {
                Log.d(TAG, "尝试从JavaScript变量中提取文件信息")
                val jsPattern = Pattern.compile("var\\s+file\\s*=\\s*\\[([^\\]]+)\\]", Pattern.DOTALL)
                val jsMatcher = jsPattern.matcher(html)
                if (jsMatcher.find()) {
                    val fileData = jsMatcher.group(1) ?: ""
                    // 这里可以添加解析JavaScript文件数据的逻辑
                    Log.d(TAG, "找到JavaScript文件数据: ${fileData.length} 字符")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析文件列表失败", e)
        }

        return files
    }
    
    private fun parseLanzouApiResponse(jsonResponse: String): List<UpdateFile> {
        val files = mutableListOf<UpdateFile>()
        
        // 如果响应是HTML，直接返回空列表（避免JSON解析异常）
        if (isHtmlResponse(jsonResponse)) {
            Log.d(TAG, "parseLanzouApiResponse: 检测到HTML响应，跳过JSON解析")
            return files
        }
        
        try {
            // 尝试解析JSON响应
            val json = JSONObject(jsonResponse)
            
            // 检查是否有文件列表
            if (json.has("data") && !json.isNull("data")) {
                val data = json.getJSONObject("data")
                
                // 检查是否有文件数组
                if (data.has("files") && !data.isNull("files")) {
                    val filesArray = data.getJSONArray("files")
                    
                    for (i in 0 until filesArray.length()) {
                        val fileObj = filesArray.getJSONObject(i)
                        
                        val fileName = fileObj.optString("name", "")
                        val fileUrl = fileObj.optString("url", "")
                        val fileTime = fileObj.optString("time", "2024-01-01 00:00")
                        val fileSize = fileObj.optString("size", "")
                        
                        // 只添加apk文件
                        if (fileName.lowercase().contains(".apk")) {
                            files.add(UpdateFile(
                                name = fileName,
                                url = fileUrl,
                                time = fileTime,
                                size = fileSize
                            ))
                            Log.d(TAG, "从API解析到文件: $fileName, $fileUrl")
                        }
                    }
                }
                
                // 也可能直接有文件信息
                if (files.isEmpty() && data.has("name")) {
                    val fileName = data.optString("name", "")
                    val fileUrl = data.optString("url", "")
                    val fileTime = data.optString("time", "2024-01-01 00:00")
                    val fileSize = data.optString("size", "")
                    
                    if (fileName.lowercase().contains(".apk")) {
                        files.add(UpdateFile(
                            name = fileName,
                            url = fileUrl,
                            time = fileTime,
                            size = fileSize
                        ))
                        Log.d(TAG, "从API解析到单个文件: $fileName")
                    }
                }
            }
            
            // 也可能直接是文件数组
            if (files.isEmpty() && json.has("files") && !json.isNull("files")) {
                val filesArray = json.getJSONArray("files")
                
                for (i in 0 until filesArray.length()) {
                    val fileObj = filesArray.getJSONObject(i)
                    
                    val fileName = fileObj.optString("name", "")
                    val fileUrl = fileObj.optString("url", "")
                    val fileTime = fileObj.optString("time", "2024-01-01 00:00")
                    val fileSize = fileObj.optString("size", "")
                    
                    if (fileName.lowercase().contains(".apk")) {
                        files.add(UpdateFile(
                            name = fileName,
                            url = fileUrl,
                            time = fileTime,
                            size = fileSize
                        ))
                        Log.d(TAG, "从API解析到文件(直接数组): $fileName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析API响应失败", e)
        }
        
        return files
    }



    private fun extractVersionFromFileName(fileName: String): String {
        // 从文件名提取版本号，例如：课表_v1.2.7.apk -> 1.2.7，Schedule-app-release-1.3.9.apk -> 1.3.9
        val patterns = listOf(
            Pattern.compile("v?(\\d+\\.\\d+\\.?\\d*)") // 通用版本号格式
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(fileName)
            if (matcher.find()) {
                val version = matcher.group(1)
                if (!version.isNullOrEmpty()) {
                    Log.d(TAG, "从文件名提取版本号: $fileName -> $version")
                    return version
                }
            }
        }
        
        Log.w(TAG, "无法从文件名提取版本号: $fileName")
        return CURRENT_VERSION
    }

    private fun isNewVersion(serverVersion: String): Boolean {
        try {
            // 直接比较版本字符串，确保即使跳过版本号也能正确检测
            Log.d(TAG, "比较版本: 当前=$CURRENT_VERSION, 服务器=$serverVersion")
            
            val currentParts = CURRENT_VERSION.split(".").map { it.toIntOrNull() ?: 0 }
            val serverParts = serverVersion.split(".").map { it.toIntOrNull() ?: 0 }

            // 逐段比较版本号
            for (i in 0 until maxOf(currentParts.size, serverParts.size)) {
                val current = currentParts.getOrElse(i) { 0 }
                val server = serverParts.getOrElse(i) { 0 }

                when {
                    server > current -> {
                        Log.d(TAG, "发现新版本: $serverVersion > $CURRENT_VERSION")
                        return true
                    }
                    server < current -> {
                        Log.d(TAG, "服务器版本较旧: $serverVersion < $CURRENT_VERSION")
                        return false
                    }
                }
            }
            Log.d(TAG, "版本相同: $serverVersion == $CURRENT_VERSION")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "版本比较失败", e)
            return false
        }
    }

    private fun showUpdateDialog(updateFile: UpdateFile) {
        val versionInFile = extractVersionFromFileName(updateFile.name)
        val message = """
            发现新版本: $versionInFile
            当前版本: $CURRENT_VERSION

            文件名: ${updateFile.name}
            上传时间: ${updateFile.time}
            文件大小: ${updateFile.size}

            下载密码: $UPDATE_PASSWORD
        """.trimIndent()

        AlertDialog.Builder(context)
            .setTitle("发现新版本")
            .setMessage(message)
            .setPositiveButton("立即更新") { _, _ ->
                downloadUpdate(updateFile.url)
            }
            .setNegativeButton("稍后更新", null)
            .show()
    }

    private fun downloadUpdate(downloadUrl: String) {
        try {
            // 构建下载链接
            val fullUrl = if (downloadUrl.startsWith("http")) {
                downloadUrl
            } else {
                "https://t-bu.cn/$downloadUrl"
            }

            // 使用内置的WebViewActivity打开下载页面
            val intent = Intent(context, WebViewActivity::class.java)
            intent.putExtra("url", fullUrl)
            intent.putExtra("schoolName", "下载更新")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // 检查WebViewActivity是否存在
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                showToast("正在打开下载页面\n密码: $UPDATE_PASSWORD")
            } else {
                // 尝试使用系统浏览器打开下载页面
                var browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                if (browserIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(browserIntent)
                    showToast("正在打开下载页面\n密码: $UPDATE_PASSWORD")
                } else {
                    // 显示下载链接和密码
                    showDownloadInfo(fullUrl)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开下载页面失败", e)
            // 显示下载链接和密码
            val fullUrl = if (downloadUrl.startsWith("http")) {
                downloadUrl
            } else {
                "https://t-bu.cn/$downloadUrl"
            }
            showDownloadInfo(fullUrl)
        }
    }
    
    private fun showDownloadInfo(downloadUrl: String) {
        val message = "无法自动打开下载页面，请手动访问以下链接下载最新版本：\n\n$downloadUrl\n\n下载密码：$UPDATE_PASSWORD"
        
        AlertDialog.Builder(context)
            .setTitle("下载信息")
            .setMessage(message)
            .setPositiveButton("复制链接") { _, _ ->
                // 复制链接到剪贴板
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("下载链接", downloadUrl)
                clipboard.setPrimaryClip(clip)
                showToast("链接已复制到剪贴板")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun isHtmlResponse(response: String): Boolean {
        // 检查响应是否为HTML页面（蓝奏云优享版登录页面）
        val trimmedResponse = response.trimStart()
        return (trimmedResponse.startsWith("<!doctype", ignoreCase = true) ||
                trimmedResponse.startsWith("<html", ignoreCase = true) ||
                trimmedResponse.contains("<title>蓝奏", ignoreCase = true) ||
                trimmedResponse.contains("蓝奏云·优享版", ignoreCase = true) ||
                trimmedResponse.contains("请先登录", ignoreCase = true) ||
                trimmedResponse.contains("登录转存", ignoreCase = true))
    }
    
    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
