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

### 预览图

<div style="display:flex;gap:8px">
<img src="https://github.com/user-attachments/assets/a21ff4ca-ee82-495c-a1a3-29fb396bfe5c" width="373">
<img src="https://github.com/user-attachments/assets/984e0bab-e53a-4685-bbff-0a3a916d4d97" width="374">
</div>

## 技术栈

- **语言**: Kotlin
- **最低 SDK**: Android 8.0 (API 26)
- **目标 SDK**: Android 13 (API 33)
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
./gradlew assembleDebug
./gradlew assembleRelease
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
