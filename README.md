# Schedule 课表

基于 WakeUp 课程表 3.612 魔改重构的 Android 课表应用。

🌐 [官方网站](https://yngu196.github.io/Schedule/)

## 声明

本项目是对 [WakeUp课程表](https://github.com/YZune/WakeUpSchedule) 3.612 版本的魔改重构项目。

开源旨在可以降低后来者的门槛，借鉴可以，但是希望在相关 App 中能有所声明。

教务网页解析的部分使用了 [CourseAdapter](https://github.com/YZune/CourseAdapter) 库。

由于测试设备是虚拟设备pixel原生安卓，所以在您实际使用时可能会出现一些在测试设备上不存在的问题，如果您遇到了，可以在设置页面点击反馈/建议，我会尽快修复。

## 功能特点

### 核心功能

- 课程表显示（周视图/今日视图切换）
- 课程导入（支持教务系统导入）
- 课程手动添加/编辑/删除
- 课前提醒通知
- 桌面小组件（今日课程/极简倒计时）

### 新增功能

- 下节课倒计时显示
- 极简桌面小组件（显示下节课及倒计时）
- 权限设置引导
- 新手引导教程
- 日/周视图切换
- **小组件多重更新链保障**：15分钟定期更新 + 30分钟备份链 + 课程结束精确更新 + 00:00/01:00 午夜闹钟兜底，彻底解决课少/无课/凌晨场景下小组件不更新的问题
- **可拖动视图切换按钮**：周视图/今日视图切换按钮支持拖动，拖动结束自动吸附到最近的屏幕左右边缘
- **卡片配色主题**：6套预设配色方案（清新马卡龙、莫兰迪低灰、校园标准正色、冷淡极简高级、春日治愈温柔、暗色模式专属），支持浅色/深色背景自动适配文字颜色
- **背景配色主题**：14款背景色（温柔护眼款、极简高级款、低饱和淡彩款、深色模式款、磨砂透明风），默认深色「暗紫灰」护眼主题

### 预览图

<div style="display:flex;gap:8px">
<img src="https://github.com/Yngu196/Schedule/blob/a59162a74e781869cc81244a24210ae464f8b445/%E6%9C%AC%E5%91%A8%E8%AF%BE%E7%A8%8B%E9%A2%84%E8%A7%88%E5%9B%BE.png" width="373">
<img src="https://github.com/Yngu196/Schedule/blob/a59162a74e781869cc81244a24210ae464f8b445/%E4%BB%8A%E6%97%A5%E8%AF%BE%E7%A8%8B%E9%A2%84%E8%A7%88%E5%9B%BE.png" width="374">
</div>

## 技术栈

- **语言**: Kotlin
- **最低 SDK**: Android 8.0 (API 26)
- **目标 SDK**: Android 35
- **架构**: MVVM
- **主要依赖**:
  - AndroidX
  - Material Design
  - Retrofit2
  - Glide
  - Gson
  - WorkManager
  - Room

## 构建

```bash
./gradlew assembleDebug      # Debug 版
./gradlew assembleRelease   # Release 签名版
```

## 项目结构

```
app/src/main/
├── java/com/cherry/wakeupschedule/
│   ├── model/           # 数据模型
│   ├── service/         # 服务类（闹钟、通知、设置等）
│   ├── widget/          # 桌面小组件
│   ├── activity/        # Activity
│   └── adapter/        # 适配器
└── res/
    ├── layout/         # 布局文件
    ├── drawable/       # 图片资源
    ├── values/         # 字符串、颜色等资源
    └── xml/            # 小组件配置等
```

## 更新日志

### v1.6.12

**通知稳定性紧急修复**

- 修复了真机实测发现的两个严重问题：

1. 通知延迟问题（上课十多分钟才弹通知
   - 重写闹钟时间计算逻辑，从 Calendar.set(DAY_OF_WEEK) 改为基于学期开始日期的精确计算
   - 新增 calculateAlarmTimeMillis() 函数，避免 Calendar 解析不确定性

2. 同课程出现两个通知
   - 新增时间窗口去重机制，5秒内同一课程不重复弹通知
   - 使用更精确的通知ID生成方式
   - 防止内存泄漏的记录自动清理

### v1.6.11

**通知稳定性全面优化**

- **闹钟稳定性提升**：使用 `setAlarmClock()` 替代 `setExactAndAllowWhileIdle()`，在系统限制下更可靠地唤醒闹钟
- **通知优化**：添加 `setBypassDnd(true)` 绕过勿扰模式，设置 `lockscreenVisibility` 在锁屏显示
- **时区与时间变更响应**：监听时区和时间变更，自动重新计算所有闹钟
- **API 35 适配**：升级 targetSdk 至 35，添加 `FOREGROUND_SERVICE_DATA_SYNC` 权限和 `windowOptOutEdgeToEdgeEnforcement` 属性

**新增功能**

- **每周自动清理日志**：防止日志文件过大，自动清理旧日志
- **更新提醒开关**：在设置页面可选择是否接收更新提醒（默认开启）

**界面优化**

- **课程全览重构**：完全重新设计，合并相同课程，直接显示列表，移除按周分组
- **今日视图优化**：卡片式布局，信息更清晰
- **状态栏适配**：优化全屏状态下状态栏显示

### v1.6.10

**备份功能增强**

- 备份文件现在包含时间表配置（节数和每节课的上课时间）
- 导入备份时会询问是否导入时间表配置
- 卸载重装后无需重新配置时间表，一键恢复所有课表、设置和作息时间

### v1.6.9

**倒计时优化**

- 修复主界面下课倒计时秒数不跳动的问题，改为每秒实时更新
- 修复桌面小组件倒计时秒数不更新的问题，App 内新增每秒触发的更新机制
- 小组件倒计时显示格式优化，精确到秒

**小组件稳定性**

- 修复 `ACTION_TIME_TICK` 在 Android 8.0+ 静态注册无效的问题，改为动态注册
- 小组件更新策略优化：从单次闹钟链改为 `setInexactRepeating` 定期更新，更加稳定可靠

**通知可靠性**

- 修复设置页修改时间表后未重新注册课程通知的问题
- 修复 `ExactAlarmWorker` 跨周闹钟调度时未正确设置星期几的问题

**新增功能**

- 新增设置页「反馈/建议」功能，支持 GitHub Issue 和邮件两种方式反馈

### v1.6.8

**稳定性与兼容性**

- 修复修改时间表后新增课程课前提醒失效的问题
- 统一课前闹钟注册机制，彻底解决单次闹钟与整学期闹钟并行导致的闹钟遗漏
- `cancelCourseAlarm()` 现可正确取消课程的所有周次闹钟
- 作息表修改后自动刷新所有课前提醒
- 应用启动时自动恢复课前闹钟，确保进程被系统杀死后闹钟不丢失

**代码质量**

- 移除所有已废弃 API 的使用（`startActivityForResult`、`getSerializableExtra`、`activeNetworkInfo`、`COLUMN_LOCAL_FILENAME`）
- 所有文件访问统一使用 FileProvider，消除 `FileUriExposedException` 风险
- WebView 安全加固：禁用文件/内容访问，JavaScript 接口增加域名白名单校验
- CourseDataManager 并发读写添加同步锁，防止多线程场景数据损坏

### v1.6.6

- 修复小组件在课少/无课/凌晨场景下不更新的问题，新增多重更新链保障
- 新增可拖动周/今日视图切换按钮，拖动结束自动吸附屏幕边缘
- 新增卡片配色主题功能，6套预设配色方案
- 新增背景配色主题，14款背景色，默认深色护眼主题
- 修复课程网格与时间轴高度不对齐的问题，动态同步高度

## 参考项目

- [WakeUp课程表](https://github.com/YZune/WakeUpSchedule) - 原始开源项目
- [CourseAdapter](https://github.com/YZune/CourseAdapter) - 教务解析库

## License

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

