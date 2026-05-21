package com.cherry.wakeupschedule

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cherry.wakeupschedule.BuildConfig
import com.cherry.wakeupschedule.util.DebugLogger

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val tvVersion = findViewById<TextView>(R.id.tv_version)
        val llOfficialWebsite = findViewById<LinearLayout>(R.id.ll_official_website)
        val llGithub = findViewById<LinearLayout>(R.id.ll_github)
        val llLicense = findViewById<LinearLayout>(R.id.ll_license)
        val llUpdateAdapter = findViewById<LinearLayout>(R.id.ll_update_adapter)
        val llViewLogs = findViewById<LinearLayout>(R.id.ll_view_logs)

        tvVersion.text = "版本: ${BuildConfig.VERSION_NAME}"

        btnBack.setOnClickListener { finish() }

        llOfficialWebsite.setOnClickListener { openUrl("https://yngu196.github.io/Schedule/") }

        llGithub.setOnClickListener { openUrl("https://github.com/Yngu196/Schedule") }

        llLicense.setOnClickListener { openUrl("https://github.com/Yngu196/Schedule/blob/main/LICENSE") }

        llUpdateAdapter.setOnClickListener { startActivity(Intent(this, SchoolListActivity::class.java)) }

        llViewLogs.setOnClickListener { showLogsDialog() }
    }

    private fun showLogsDialog() {
        val logs = DebugLogger.getLogs()

        val textView = TextView(this)
        textView.text = logs
        textView.setPadding(32, 32, 32, 32)
        textView.textSize = 12f
        textView.typeface = android.graphics.Typeface.MONOSPACE
        textView.setTextIsSelectable(true)
        textView.setTextColor(0xFFCCCCCC.toInt())

        val scrollView = ScrollView(this)
        scrollView.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            600.dpToPx()
        )
        scrollView.setBackgroundColor(0xFF1E1E1E.toInt())
        scrollView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("调试日志")
            .setView(scrollView)
            .setPositiveButton("清空日志") { _, _ ->
                DebugLogger.clearLogs()
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("上传日志") { _, _ ->
                showUploadLogDialog()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showUploadLogDialog() {
        val options = arrayOf("GitHub Issue（提交Bug反馈）", "发送邮件（发送给开发者）")

        AlertDialog.Builder(this)
            .setTitle("上传日志方式")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> uploadLogToGitHub()
                    1 -> uploadLogByEmail()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun uploadLogToGitHub() {
        try {
            val logs = DebugLogger.getLogs()
            val body = """### 调试日志

```
$logs
```

设备信息: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
Android版本: ${android.os.Build.VERSION.RELEASE}
应用版本: ${BuildConfig.VERSION_NAME}
""".trimIndent()

            val encodedBody = Uri.encode(body)
            val url = "https://github.com/Yngu196/Schedule/issues/new?body=$encodedBody" +
                    "&title=日志上传 - ${BuildConfig.VERSION_NAME}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uploadLogByEmail() {
        try {
            val logs = DebugLogger.getLogs()
            val emailBody = """
应用版本: ${BuildConfig.VERSION_NAME}
设备信息: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
Android版本: ${android.os.Build.VERSION.RELEASE}

=== 调试日志 ===
$logs
            """.trimIndent()

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf("Yngu196@qq.com"))
                putExtra(Intent.EXTRA_SUBJECT, "Schedule 调试日志 - ${BuildConfig.VERSION_NAME}")
                putExtra(Intent.EXTRA_TEXT, emailBody)
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(intent, "发送邮件"))
            } else {
                Toast.makeText(this, "未找到邮件应用", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开邮件应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开链接", Toast.LENGTH_SHORT).show()
        }
    }
}
