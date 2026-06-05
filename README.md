# Schedule 课表

基于 WakeUp 课程表 3.612 魔改重构的 Android 课表应用。

🌐 [官方网站](https://yngu196.github.io/Schedule/)

## 声明

本项目是对 [WakeUp课程表](https://github.com/YZune/WakeUpSchedule) 3.612 版本的魔改重构项目。

开源旨在可以降低后来者的门槛，借鉴可以，但是希望在相关 App 中能有所声明。

教务网页解析的部分使用了 [CourseAdapter](https://github.com/YZune/CourseAdapter) 库。

本项目的初衷是为自己日常使用、有查看课程信息需求的人，以及某些总是问别人上什么课，哪怕不回答也不愿意打开教务系统看一眼，还要一直问的人。但由于我低估了`某些人`的烦人程度，他们并不会为了看课程信息方便而使用这个项目，哪怕我把安装包发给他们，他们也不会去使用。而本人又没有在社交平台上宣传这个项目，所以这个项目的用户数量非常少。目前的主要用户是本人及身边的朋友。

在这里由衷地给每个使用这个项目的的人一个建议：如果你的身边有`“寄生虫/巨婴”`以及那些一点边界感都没有而且又与你不熟的人的话，请尽量减少与`他们/她们`的接触。

由于测试设备是虚拟设备pixel原生安卓，所以在您实际使用时可能会出现一些在测试设备上不存在的问题，如果您遇到了，可以在设置页面点击反馈/建议，我会尽快修复。

## 功能特点

### 核心功能

- 课程表显示（周视图/今日视图切换）
- 课程导入（支持教务系统导入）
- 课程手动添加/编辑/删除
- 课前提醒通知
- 桌面小组件（今日课程/下节课倒计时/近日课程预览）
- 日/周视图切换
- **自定义图片背景**：支持选择手机相册图片作为背景，使用 Glide 优化加载，移除不可靠的系统裁剪，提高兼容性
- **小组件课程列表可上下滑动**：今日课程和近日课程小组件的课程显示区改为可滚动列表（基于 `ListView + RemoteViewsService`），不再受 2 项限制，可查看完整课程

### 新增功能

- **近日课程桌面小组件**：横4竖2布局，左侧显示今日未上课程，右侧显示明日课程，自动滚动预览
- 权限设置引导
- 新手引导教程
- **小组件多重更新链保障**：15分钟定期更新 + 30分钟备份链 + 课程结束精确更新 + 00:00/01:00 午夜闹钟兜底，彻底解决课少/无课/凌晨场景下小组件不更新的问题
- **可拖动视图切换按钮**：周视图/今日视图切换按钮支持拖动，拖动结束自动吸附到最近的屏幕左右边缘
- **卡片配色主题**：6套预设配色方案（清新马卡龙、莫兰迪低灰、校园标准正色、冷淡极简高级、春日治愈温柔、暗色模式专属），支持浅色/深色背景自动适配文字颜色
- **背景配色主题**：14款背景色（温柔护眼款、极简高级款、低饱和淡彩款、深色模式款、磨砂透明风），默认深色「暗紫灰」护眼主题
- **节假日管理**：新增「节假日隐藏课程」功能，内置法定节假日，支持自定义节假日，自动在节假日隐藏课程和提醒
- **小组件跨组件同步更新**：下课倒计时/今日课程小组件在课程结束时互相通知刷新，避免出现负数时间和组件内容不一致
- **课前通知四重保障**：解决真机上通知无法按时触发问题
  - `setAlarmClock` 最高优先级主闹钟
  - `WorkManager` 兜底调度（即使主闹钟被清也能弹通知）
  - `specialUse` 类型前台服务保活（防国产 ROM 杀进程）
  - 智能心跳（2/5/10分钟动态调整）持续刷新闹钟

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

### v1.7.4

**修复 Chronometer 长时间显示负数倒计时**

- 修复最小化小组件在 Chronometer 模式下，课程结束后未及时刷新导致负数倒计时长时间停留的问题
- 现在无论倒计时剩余多少秒，都会在课程结束 1 秒后通过 `MinimalWidgetSafetyReceiver` 强制刷新小组件，切换到「当前没课」状态
- 修复「最后一节课」或「距离下一节课很久」时负数倒计时持续很久的场景

**修复删除/修改课程后通知栏残留旧通知**

- 在 [NotificationHelper](file:///d:/CherryProject/Schedule/app/src/main/java/com/cherry/wakeupschedule/service/NotificationHelper.kt) 中新增 `cancelCourseNotifications(courseName)`，按 `AlarmReceiver` 相同的规则批量清理通知栏
- `AlarmService.cancelCourseAlarm` 在取消闹钟时同步清理通知栏
- `AddCourseActivity` 编辑课程时先用旧课程对象取消通知，再写入新课程，避免改名后旧名字通知残留

**「关于」页面调整**

- 关闭「查看调试日志」入口，简化用户界面
- `DebugLogger` 工具类仍保留供内部服务调用

**更新对话框新增 GitCode 下载源**

- 在 [dialog_update.xml](file:///d:/CherryProject/Schedule/app/src/main/res/layout/dialog_update.xml) 中新增「GitCode 下载 (国内推荐)」按钮
- [UpdateService](file:///d:/CherryProject/Schedule/app/src/main/java/com/cherry/wakeupschedule/service/UpdateService.kt) 新增 `GITCODE_URL` 常量：`https://gitcode.com/2401_87059416/Schedule/releases/`
- 蓝奏云按钮降级为「蓝奏云下载」，把国内推荐位让给 GitCode

**版本号**

- `versionCode`: 1504 → 1505
- `versionName`: 1.7.3 → 1.7.4

### v1.7.3

**小组件课程列表可滑动**

- 今日课程小组件和近日课程小组件的课程显示区从写死的 2 个课程项改为可滚动列表（基于 `ListView + RemoteViewsService`）
- 不再受显示 2 项的限制，可上下滑动查看完整课程
- 近日课程小组件的"今天"和"明天"两个区域可独立滚动
- 每行课程显示颜色指示条、课程名、教室和时间段

**小组件跨组件同步更新**

- 修复下课倒计时小组件出现负数时间的问题
- 修复今日课程组件在当前课程结束后没有立即更新的问题
- 下课倒计时小组件和今日课程小组件在课程结束时互相通知刷新，避免组件内容不一致
- 倒计时小于 60 秒时主动切换到文本模式，并调度安全更新闹钟

**课前通知四重保障**

- 解决真机上通知无法按时触发甚至没有通知的问题
- 四层 API 降级链：`setAlarmClock` → `setExactAndAllowWhileIdle` → `setExact` → `setAndAllowWhileIdle` → `set`
- 每次设置主闹钟时同步调度 WorkManager 兜底（双保险）
- 通知渠道 importance 升级为 MAX，启用全屏弹窗
- `AlarmReceiver` 收到广播时持有 WakeLock 10 秒，避免被系统休眠打断
- 智能心跳：距下一节课 1h 内 2min，1-6h 5min，超过 6h 10min 省电
- 前台服务类型从 `dataSync` 升级为 `specialUse`（更符合保活语义，规避 Android 14 严格限制）

**问题修复**

- 修复 `ExactAlarmWorker` 中 `showNotification` 缺参数的 bug
- 重写 `ExactAlarmWorker.rescheduleNextWeek` 使用学期开始日期精确计算（之前 `set(DAY_OF_WEEK)` 存在解析不确定性问题）
- `CourseReminderWorker` 每次 doWork 都自动调用 `registerAllCourseNotifications` 兜底

### v1.7.2

**课程导入合并逻辑优化**

- 修改了合并逻辑：现在只有完全相同的课程信息（课程名+老师+教室+时间）才会被合并
- 不同老师或不同课程的相同时间段不再被错误合并
- 对于相同的课程信息，仅会合并连续或重叠的周范围

**更新说明显示和字体优化**

- 进一步调大了更新说明的字体大小（正文 30px，标题 36px）
- 添加了缩放支持，用户可以双指捏合缩放查看
- 增加了 WebView 高度到 350dp，内容显示更充分

### v1.7.1

**课程导入合并优化**

- 修复了同一节次不同老师的课程合并逻辑：移除了将 `teacher` 和 `classroom` 作为分组键的问题，现在同一时间、同一课程名的课程会正确合并为一个，老师和教室信息会用分号拼接显示

**周视图日期联动修复**

- 修复了周视图中"周几下方的日期不随左右滑动切换周次而联动更新的问题，现在日期栏下方的日期会正确随周次变化

**课前通知稳定性修复**

- 移除了 `notificationManager.cancelAll()，避免正在显示的课程通知被误杀
- 优化了课程通知的显示稳定性

**节假日管理优化**

- 移除了圣诞节等非法定成人节日（儿童、妇女节等保留，这些节日不会跳过课程通知
- 现在内置节假日更符合国内实际情况

**课前自动启动**

- 新增课前自动启动功能（后台启动服务，5分钟后自动关闭
- 优化了用户手动打开应用时取消自动关闭的逻辑

**更新界面优化**

- 修复了检查更新时更新说明看不到的问题，优化了 Markdown 转 HTML 逻辑
- 移除了 GitHub Proxy 加速下载按钮（实测无效

<details>
<summary><b>点击展开 v1.7.0 及更早版本更新日志</b></summary>

### v1.7.0

**新增近日课程桌面小组件**

- **近日课程小组件**：横4竖2布局，左侧显示今日未上课程，右侧显示明日课程，按时间排序，支持滚动查看完整课程列表
- **优化小组件架构**：所有小组件调度统一管理，15分钟定期更新 + 课程结束精确更新 + 午夜闹钟兜底

**新增节假日管理功能**

- 新增「节假日隐藏课程」设置开关
- 内置法定节假日判断（元旦、劳动节、国庆节、圣诞节等）
- 支持自定义添加/删除节假日
- 节假日自动隐藏课程和通知提醒
- 课程视图、桌面小组件、闹钟通知全面支持节假日过滤

**自定义图片背景优化**

- 移除不可靠的系统裁剪方式，直接使用用户选择的图片（带80%质量压缩）
- 使用 Glide 库优化图片加载，支持 centerCrop 自动适配
- 修复图片背景无法显示的问题（设置背景类型为 custom）

**问题修复**

- 修复设置页面每次进入都提示"已开启更新提醒"的问题
- 修复 `BroadcastReceiver` 在 Android 14+ 上因缺少 `RECEIVER_EXPORTED/RECEIVER_NOT_EXPORTED` 标志导致的崩溃
- 修复 `isCourseInCurrentWeekType` 缺失 `weekType=0`（每周都有课）的明确处理分支
- 修复小组件周期性更新重复调度的问题，现在设置新闹钟前会先取消旧的
- 修复签名文件路径从 `../wakeup-schedule-key.jks` 被错误改为 `wakeup-schedule-key.jks` 的问题
- 移除已禁用的 `WeekViewPeriodicReceiver` 在 AndroidManifest 中的残留注册

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

</details>

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

