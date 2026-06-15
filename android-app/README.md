# Android App

本目录放 MetaPad 端 APK 工程和 App 相关原型。

当前状态：

- `docs/`：中文界面原型图和 HTML 源文件。
- `scripts/render-ui-prototypes.js`：原型截图脚本。
- Android 工程尚未创建，后续建议在本目录下创建 Gradle 工程。

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

## 工人端流程

```text
拍照 -> 照片确认 -> 自动上传检测 -> 自动展示结果 -> 按设置存储
```

检测页只保留一个操作按钮：

```text
返回拍照
```

是否自动存储、存储位置、服务器地址放在设置界面。
