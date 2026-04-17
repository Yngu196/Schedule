package com.cherry.wakeupschedule.service

import android.content.Context
import android.content.Intent
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class UpdateService(private val context: Context) {

    companion object {
        private const val TAG = "UpdateService"
        private const val GITHUB_API_URL = "https://api.github.com/repos/Yngu196/Schedule/releases/latest"
        private const val CURRENT_VERSION = "1.6.1"
    }

    private var latestVersion: String = ""
    private var downloadUrl: String = ""
    private var releaseNotes: String = ""

    fun checkForUpdate(showNoUpdateToast: Boolean = true) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                showToast("正在检查更新...")
                val result = fetchLatestRelease()

                withContext(Dispatchers.Main) {
                    if (result.first) {
                        val latestVer = result.second
                        val latestUrl = result.third
                        val notes = result.fourth

                        if (isNewVersion(latestVer)) {
                            showUpdateDialog(latestVer, latestUrl, notes)
                        } else {
                            if (showNoUpdateToast) {
                                showToast("当前已是最新版本 ($CURRENT_VERSION)")
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

    fun manualUpdate() {
        checkForUpdate(showNoUpdateToast = true)
    }

    private suspend fun fetchLatestRelease(): Quartet<Boolean, String, String, String> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "Schedule-App")

                val responseCode = connection.responseCode
                Log.d(TAG, "GitHub API响应码: $responseCode")

                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val json = JSONObject(response)

                    latestVersion = json.optString("tag_name", "").removePrefix("v")
                    downloadUrl = json.optString("html_url", "")
                    releaseNotes = json.optString("body", "").take(500)

                    Log.d(TAG, "最新版本: $latestVersion, 下载地址: $downloadUrl")

                    if (latestVersion.isNotEmpty() && downloadUrl.isNotEmpty()) {
                        Quartet(true, latestVersion, downloadUrl, releaseNotes)
                    } else {
                        Quartet(false, "", "", "")
                    }
                } else if (responseCode == 404) {
                    Log.w(TAG, "未找到发布页面，可能还没有发布")
                    Quartet(false, "", "", "")
                } else {
                    Log.w(TAG, "GitHub API请求失败: $responseCode")
                    Quartet(false, "", "", "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取发布信息失败", e)
                Quartet(false, "", "", "")
            }
        }
    }

    private fun isNewVersion(serverVersion: String): Boolean {
        try {
            if (serverVersion.isEmpty()) return false

            Log.d(TAG, "比较版本: 当前=$CURRENT_VERSION, 服务器=$serverVersion")

            val currentParts = CURRENT_VERSION.split(".").map { it.toIntOrNull() ?: 0 }
            val serverParts = serverVersion.split(".").map { it.toIntOrNull() ?: 0 }

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

    private fun showUpdateDialog(version: String, url: String, notes: String) {
        val message = buildString {
            append("发现新版本: $version\n")
            append("当前版本: $CURRENT_VERSION\n\n")
            if (notes.isNotEmpty()) {
                append("更新说明:\n${notes.take(200)}")
                if (notes.length > 200) append("...")
            }
        }

        AlertDialog.Builder(context)
            .setTitle("发现新版本")
            .setMessage(message)
            .setPositiveButton("前往下载") { _, _ ->
                openDownloadPage(url)
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun openDownloadPage(downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                showToast("无法打开链接，请手动访问:\n$downloadUrl")
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开下载页面失败", e)
            showToast("无法打开链接:\n$downloadUrl")
        }
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    data class Quartet<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
