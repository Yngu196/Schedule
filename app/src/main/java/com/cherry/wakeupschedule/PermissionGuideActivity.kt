package com.cherry.wakeupschedule

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cherry.wakeupschedule.service.BatteryOptimizationHelper

class PermissionGuideActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvNotificationStatus: TextView
    private lateinit var btnNotification: Button
    private lateinit var tvBatteryStatus: TextView
    private lateinit var btnBattery: Button
    private lateinit var tvAutostartStatus: TextView
    private lateinit var btnAutostart: Button
    private lateinit var tvInstructions: TextView

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_guide)

        initViews()
        setupClickListeners()
        updatePermissionStatus()
        updateInstructions()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        tvNotificationStatus = findViewById(R.id.tv_notification_status)
        btnNotification = findViewById(R.id.btn_notification)
        tvBatteryStatus = findViewById(R.id.tv_battery_status)
        btnBattery = findViewById(R.id.btn_battery)
        tvAutostartStatus = findViewById(R.id.tv_autostart_status)
        btnAutostart = findViewById(R.id.btn_autostart)
        tvInstructions = findViewById(R.id.tv_instructions)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnNotification.setOnClickListener {
            requestNotificationPermission()
        }

        btnBattery.setOnClickListener {
            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(this)
        }

        btnAutostart.setOnClickListener {
            BatteryOptimizationHelper.openManufacturerPowerSettings(this)
        }
    }

    private fun updatePermissionStatus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            tvNotificationStatus.text = if (hasPermission) "已授权 ✓" else "未授权"
            tvNotificationStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (hasPermission) android.R.color.holo_green_dark else android.R.color.holo_red_dark
                )
            )
            btnNotification.text = if (hasPermission) "已授权" else "去授权"
            btnNotification.isEnabled = !hasPermission
        } else {
            tvNotificationStatus.text = "Android 13 以下无需授权"
            tvNotificationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnNotification.text = "无需授权"
            btnNotification.isEnabled = false
        }

        val batteryOptimized = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)
        tvBatteryStatus.text = if (batteryOptimized) "已关闭 ✓" else "未关闭"
        tvBatteryStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (batteryOptimized) android.R.color.holo_green_dark else android.R.color.holo_red_dark
            )
        )
        btnBattery.text = if (batteryOptimized) "已关闭" else "去关闭"
        btnBattery.isEnabled = !batteryOptimized

        val hasAutostartIntent = BatteryOptimizationHelper.getAutoStartIntent(this) != null
        tvAutostartStatus.text = if (hasAutostartIntent) "可设置" else "请手动设置"
        tvAutostartStatus.setTextColor(
            ContextCompat.getColor(
                this,
                if (hasAutostartIntent) android.R.color.holo_green_dark else android.R.color.holo_orange_dark
            )
        )
        btnAutostart.text = if (hasAutostartIntent) "去设置" else "查看教程"
        btnAutostart.isEnabled = true
    }

    private fun updateInstructions() {
        val manufacturer = BatteryOptimizationHelper.getManufacturerName()
        val instructions = BatteryOptimizationHelper.getDetailedInstructions(this)
        tvInstructions.text = "${getManufacturerChineseName(manufacturer)} 系统设置教程：\n\n$instructions"
    }

    private fun getManufacturerChineseName(manufacturer: String): String {
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> "小米/红米/POCO"
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> "华为/荣耀"
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> "OPPO/Realme/一加"
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> "Vivo/iQOO"
            manufacturer.contains("meizu") -> "魅族"
            manufacturer.contains("samsung") -> "三星"
            manufacturer.contains("lenovo") -> "联想"
            else -> "通用"
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            updatePermissionStatus()
        }
    }
}
