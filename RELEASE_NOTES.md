# v1.6.11 发布说明

## 📱 下载

- **Debug 版本**：[Schedule-app-debug-1.6.11.apk](https://github.com/Yngu196/Schedule/releases/download/v1.6.11/Schedule-app-debug-1.6.11.apk) (34.8MB)
- **Release 版本**：[Schedule-app-release-1.6.11.apk](https://github.com/Yngu196/Schedule/releases/download/v1.6.11/Schedule-app-release-1.6.11.apk) (30.0MB)

## 🚀 主要更新

### 🔔 通知稳定性全面优化

- **闹钟稳定性提升**：使用 `setAlarmClock()` 替代 `setExactAndAllowWhileIdle()`，在系统限制下更可靠地唤醒闹钟
- **通知优化**：添加 `setBypassDnd(true)` 绕过勿扰模式，设置 `lockscreenVisibility` 在锁屏显示
- **时区与时间变更响应**：监听时区和时间变更，自动重新计算所有闹钟
- **API 35 适配**：升级 targetSdk 至 35，添加 `FOREGROUND_SERVICE_DATA_SYNC` 权限和 `windowOptOutEdgeToEdgeEnforcement` 属性

### ✨ 新增功能

- **每周自动清理日志**：防止日志文件过大，自动清理旧日志
- **更新提醒开关**：在设置页面可选择是否接收更新提醒（默认开启）

### 🎨 界面优化

- **课程全览重构**：完全重新设计，合并相同课程，直接显示列表，移除按周分组
- **今日视图优化**：卡片式布局，信息更清晰
- **状态栏适配**：优化全屏状态下状态栏显示

## 🛠️ 技术升级

- 升级 Gradle 至 8.14.4
- 升级 Android Gradle Plugin 至 8.7.3
- 升级 Kotlin 至 1.9.24
- 升级 targetSdk 至 35
- 升级所有 AndroidX 依赖至最新版本

## 📋 完整修改列表

### 核心功能
- `AlarmService.kt`：替换为 `setAlarmClock()`
- `NotificationHelper.kt`：添加绕过勿扰和锁屏显示
- `WidgetTimeChangedReceiver.kt`：监听时区变更并重新计算闹钟
- `DebugLogger.kt`：自动清理逻辑
- `SettingsManager.kt`：新设置项
- `UpdateService.kt`：更新提醒开关支持

### UI 改进
- `CourseOverviewActivity.kt` 和 `CourseOverviewAdapter.kt`：课程全览重构
- `MainActivity.kt`：今日视图和状态栏适配
- `SettingsActivity.kt`：更新提醒开关 UI
- `activity_settings.xml`：新设置项布局
- `styles.xml`：Switch 样式

### 配置升级
- `build.gradle`：SDK 和依赖版本升级
- `AndroidManifest.xml`：新权限和接收器

## 🐛 Bug 修复

- 修复状态栏在主题切换时被遮挡的问题
- 修复小组件在时区变更时可能显示不正确的问题
- 修复课程全览在课程多时难以查看的问题
- 修复更新检查在用户关闭更新提醒时仍然弹出的问题

## 📌 升级建议

强烈建议所有用户升级到此版本，特别是遇到以下问题的用户：
- 课前提醒不稳定
- 勿扰模式下收不到通知
- 锁屏时没有通知
- 时区变更或手动调整时间后闹钟时间不正确

## 📝 更新方法

1. 下载对应的 APK 文件
2. 安装前请备份课程数据（设置 -> 导出课程）
3. 卸载旧版本或直接覆盖安装（建议先备份数据）

## 📞 反馈

如有任何问题，请通过以下方式反馈：
- GitHub Issue：[https://github.com/Yngu196/Schedule/issues](https://github.com/Yngu196/Schedule/issues)
- 邮件：Yngu196@qq.com

---

感谢您使用 Schedule 课表！
