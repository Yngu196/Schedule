# Schedule 课表

基于 WakeUp 课程表 3.612 魔改重构的 Android 课表应用。

## 声明

本项目是对 [WakeUp课程表](https://github.com/YZune/WakeUpSchedule) 3.612 版本的魔改重构项目。

开源旨在可以降低后来者的门槛，借鉴可以，但是希望在相关 App 中能有所声明。

教务网页解析的部分使用了 [CourseAdapter](https://github.com/YZune/CourseAdapter) 库。

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
- **目标 SDK**: Android 33
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

