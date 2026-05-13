package com.cherry.wakeupschedule.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.cherry.wakeupschedule.WebViewActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新服务
 * 检查GitHub最新版本并提示用户更新
 */
class UpdateService(private val context: Context) {

    companion object {
        private const val TAG = "UpdateService"
        private const val GITHUB_API_URL = "https://api.github.com/repos/Yngu196/Schedule/releases/latest"
    }

    private val currentVersion: String = com.cherry.wakeupschedule.BuildConfig.VERSION_NAME

    private var latestVersion: String = ""
    private var downloadUrl: String = ""
    private var releaseNotes: String = ""

    // 静默检查更新（不显示任何提示，只在新版本时弹出对话框）
    fun checkForUpdateSilently() {
        val settingsManager = SettingsManager(context)
        if (settingsManager.isCheckedForUpdateToday()) {
            Log.d(TAG, "今日已检查过更新，跳过")
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = fetchLatestRelease()
                if (result.first) {
                    val latestVer = result.second
                    val latestUrl = result.third
                    val notes = result.fourth
                    if (isNewVersion(latestVer)) {
                        withContext(Dispatchers.Main) {
                            showUpdateDialog(latestVer, latestUrl, notes)
                        }
                    }
                }
                settingsManager.markUpdateCheckedToday()
            } catch (e: Exception) {
                Log.e(TAG, "静默检查更新失败", e)
                settingsManager.markUpdateCheckedToday()
            }
        }
    }

    // 手动检查更新（显示提示）
    fun manualUpdate() = checkForUpdate(showNoUpdateToast = true)

    // 检查更新
    fun checkForUpdate(showNoUpdateToast: Boolean = true) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (showNoUpdateToast) showToast("正在检查更新...")
                val result = fetchLatestRelease()
                withContext(Dispatchers.Main) {
                    if (result.first) {
                        val latestVer = result.second
                        val latestUrl = result.third
                        val notes = result.fourth
                        if (isNewVersion(latestVer)) {
                            showUpdateDialog(latestVer, latestUrl, notes)
                        } else {
                            if (showNoUpdateToast) showToast("当前已是最新版本 ($currentVersion)")
                        }
                    } else {
                        if (showNoUpdateToast) showToast("检查更新失败，请稍后重试")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查更新失败", e)
                withContext(Dispatchers.Main) {
                    if (showNoUpdateToast) showToast("检查更新失败: ${e.message ?: "未知错误"}")
                }
            }
        }
    }

    // 获取最新发布信息
    private suspend fun fetchLatestRelease(): Quartet<Boolean, String, String, String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "Schedule-App")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()
                val json = JSONObject(response)
                latestVersion = json.optString("tag_name", "").removePrefix("v")
                releaseNotes = json.optString("body", "")

                // 从 assets 中获取直接的 APK 下载链接
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                // 如果没有找到 APK asset，使用 release 页面的 URL
                if (downloadUrl.isEmpty()) {
                    downloadUrl = json.optString("html_url", "")
                }

                if (latestVersion.isNotEmpty() && downloadUrl.isNotEmpty()) {
                    Quartet(true, latestVersion, downloadUrl, releaseNotes)
                } else Quartet(false, "", "", "")
            } else {
                Quartet(false, "", "", "")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取发布信息失败", e)
            Quartet(false, "", "", "")
        }
    }

    // 比较版本号
    private fun isNewVersion(serverVersion: String): Boolean {
        if (serverVersion.isEmpty()) return false
        val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        val serverParts = serverVersion.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(currentParts.size, serverParts.size)) {
            val current = currentParts.getOrElse(i) { 0 }
            val server = serverParts.getOrElse(i) { 0 }
            if (server > current) return true
            if (server < current) return false
        }
        return false
    }

    // 简单的 Markdown 到 HTML 转换
    private fun markdownToHtml(markdown: String): String {
        var html = markdown
            .replace(Regex("^### (.+)"), "<h3>$1</h3>")
            .replace(Regex("^## (.+)"), "<h2>$1</h2>")
            .replace(Regex("^# (.+)"), "<h1>$1</h1>")
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
            .replace(Regex("\\*(.+?)\\*"), "<em>$1</em>")
            .replace(Regex("`(.+?)`"), "<code>$1</code>")
            .replace(Regex("^- (.+)"), "<li>$1</li>")
            .replace(Regex("\\n\\n"), "</li><li>")
            .replace("<li></li>", "")
            .replace(Regex("\\n"), "<br>")

        html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body { font-family: sans-serif; font-size: 14px; line-height: 1.5; padding: 0; margin: 0; }
                    h1, h2, h3 { margin-top: 16px; margin-bottom: 8px; }
                    li { margin-left: 16px; }
                    code { background: #f0f0f0; padding: 2px 4px; border-radius: 3px; }
                </style>
            </head>
            <body>$html</body>
            </html>
        """.trimIndent()
        return html
    }

    // 显示更新对话框
    private fun showUpdateDialog(version: String, url: String, notes: String) {
        val dialogView = LayoutInflater.from(context).inflate(com.cherry.wakeupschedule.R.layout.dialog_update, null)
        
        val tvVersionInfo = dialogView.findViewById<TextView>(com.cherry.wakeupschedule.R.id.tv_version_info)
        val webViewNotes = dialogView.findViewById<WebView>(com.cherry.wakeupschedule.R.id.webview_notes)
        
        tvVersionInfo.text = "发现新版本: $version\n当前版本: $currentVersion"
        
        val htmlContent = markdownToHtml(notes)
        webViewNotes.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        
        dialogView.findViewById<TextView>(com.cherry.wakeupschedule.R.id.btn_download).setOnClickListener {
            openDownloadPage(url)
            dialog.dismiss()
        }
        
        dialogView.findViewById<TextView>(com.cherry.wakeupschedule.R.id.btn_later).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun openDownloadPage(downloadUrl: String) {
        try {
            // 使用内置 WebViewActivity 打开下载页面
            // WebViewActivity 会检测 APK 文件并使用系统浏览器处理
            val intent = Intent(context, WebViewActivity::class.java).apply {
                putExtra("url", downloadUrl)
                putExtra("title", "下载更新")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            showToast("无法打开下载页面")
        }
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // 四元组数据类
    data class Quartet<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
