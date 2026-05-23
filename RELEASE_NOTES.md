# v1.6.12 发布说明

## 📱 下载

- **Debug 版本**：[Schedule-app-debug-1.6.12.apk](https://github.com/Yngu196/Schedule/releases/download/v1.6.12/Schedule-app-debug-1.6.12.apk) (34.8MB)
- **Release 版本**：[Schedule-app-release-1.6.12.apk](https://github.com/Yngu196/Schedule/releases/download/v1.6.12/Schedule-app-release-1.6.12.apk) (28.6MB)

## 🐛 紧急修复

### ⚠️ 通知稳定性问题

修复了真机实测发现的两个严重问题：

#### 1. 通知延迟（上课十多分钟才弹通知）
- **根因**：Calendar.set(DAY_OF_WEEK) 是脆弱的时间计算方式，解析日期不可预测，且时间已过的处理逻辑有误
- **修复**：新增 calculateAlarmTimeMillis() 函数，改用基于学期开始日期的精确计算：
  - 学期开始日期 + (targetWeek-1)*7 天 → 目标周周一
  - + (dayOfWeek-1) 天 → 目标星期
  - + 开始时间 - 提前提醒分钟数
- **优势**：不再依赖 Calendar 的自动调整，完全可预测

#### 2. 同课程出现两个通知
- **根因**：同一课程可能有两个闹钟触发，又没有去重机制
- **修复**：
  - 新增时间窗口去重（5秒内同一课程不重复弹通知）
  - 使用更精确的通知ID（课程名 + 周次）
  - 自动清理 1分钟前的记录防止内存泄漏
  - 通知弹出后统一调用 setCourseAlarm() 延续下一次闹钟

## 📝 修改

仅修改 1 个文件：
- [AlarmService.kt](app/src/main/java/com/cherry/wakeupschedule/service/AlarmService.kt)

## 📌 建议

**强烈建议所有用户升级到此版本！** 特别是如果您之前遇到过：
- 课前提醒很晚才弹（有时甚至下课后）
- 同一课程弹出两次通知

---

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
- `AlarmService.kt`：替换为 `setAlarmClock()`、时区变更监听
- `NotificationHelper.kt`：添加绕过勿扰和锁屏显示
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

## 📌 升级建议

**强烈建议所有用户升级到此版本！** 特别是遇到以下问题的用户：
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
