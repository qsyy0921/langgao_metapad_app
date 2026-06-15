# Android App

本目录放 MetaPad 端 APK 工程和 App 相关原型。

当前状态：

- `docs/`：中文界面原型图和 HTML 源文件。
- `docs/DEVELOPMENT_PLAN.md`：App 开发规划。
- `scripts/render-ui-prototypes.js`：原型截图脚本。
- `app/`：Android APK 工程，已实现 Kotlin + Jetpack Compose + CameraX 的第一版 MVP。

## 当前 MVP 能力

- 拍照页：相机权限申请、CameraX 预览、默认尝试开启补光灯、拍照、重拍、确认照片。
- 检测页：上传/检测中状态、原图预览、检测图占位、PASS/NG 检测清单、只保留 `返回拍照`。
- 记录页：检测记录列表原型。
- 设置页：服务器地址、`/api/health` 连接测试、自动存储开关、存储位置选择。

当前检测结果仍为模拟数据，真实上传需要服务端 `/api/inspect` 协议稳定后接入。

## App 端技术选型

```text
Kotlin
Jetpack Compose
CameraX
Retrofit / OkHttp
Room
DataStore
Hilt
```

## 本地构建

```powershell
cd E:\company\app\android-app
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
$env:ANDROID_SDK_ROOT='E:\company\app\tools\android-sdk'
$env:Path="$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;E:\company\app\tools\gradle-8.10.2\bin;$env:Path"
E:\company\app\tools\gradle-8.10.2\bin\gradle.bat --no-daemon :app:assembleDebug
```

构建产物：

```text
E:\company\app\android-app\app\build\outputs\apk\debug\app-debug.apk
```

## 工人端流程

```text
拍照 -> 照片确认 -> 自动上传检测 -> 自动展示结果 -> 按设置存储
```

检测页只保留一个操作按钮：

```text
返回拍照
```

是否自动存储、存储位置、服务器地址放在设置界面。

## 开发文档

- [App 开发规划](docs/DEVELOPMENT_PLAN.md)
- [项目 SDD 记录](../docs/SDD.md)
