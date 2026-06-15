# MetaPad App 开发规划

版本：v0.1  
日期：2026-06-15  
范围：Android 平板 APK 第一阶段开发  
当前状态：M0 / M1 / M2 初版已完成，M3 服务器真实检测接口待接入

## 1. 开发目标

第一阶段目标是做出可以安装到华为 Pad 上运行的 MVP：

```text
拍照 -> 照片确认 -> 上传服务器检测 -> 展示检测结果 -> 按设置保存记录
```

服务器端负责 AI 推理，App 端不直接运行 YOLO / PatchCore 模型。App 只负责拍照、上传、展示、设置、记录和同步。

## 2. MVP 功能边界

必须实现：

- 拍照界面：CameraX 预览、默认打开补光灯、拍照、重拍、确认照片。
- 检测界面：上传中状态、原图、检测图、PASS / NG / REVIEW、检测项列表、返回拍照。
- 存储界面：历史记录列表、记录详情、原图和检测图查看、同步状态。
- 设置界面：服务器地址、连接测试、自动存储开关、存储位置。
- 网络：调用服务器 `/api/health` 和 `/api/inspect`。
- 本地存储：保存检测记录、设置项、图片文件路径。

第一阶段暂不实现：

- 实时相机移动指导。
- 工人账号和权限系统。
- 云端账号体系。
- App 端本地 AI 推理。
- 批量报表导出。
- 动态模型下发。

## 3. 技术栈

```text
语言：Kotlin
UI：Jetpack Compose
相机：CameraX
网络：Retrofit / OkHttp
本地数据库：Room
设置存储：DataStore
依赖注入：Hilt
异步：Kotlin Coroutines / Flow
最低版本：Android 8.0+，以华为 Pad 实测为准
```

## 4. 推荐模块结构

第一阶段可以先做单工程多 package，等功能稳定后再拆 Gradle 多模块。代码结构按未来模块化边界设计：

```text
android-app/
├─ app/
│  └─ src/main/java/.../
│     ├─ app                  启动、导航、主题、依赖注入
│     ├─ core/common          Result、错误、时间、ID、通用工具
│     ├─ core/platform        CameraX、权限、文件、日志、网络基础
│     ├─ domain               领域模型和业务规则
│     ├─ application          UseCase，业务流程编排
│     ├─ data                 Repository、API、Room、DataStore、文件实现
│     └─ feature
│        ├─ capture           拍照和照片确认
│        ├─ inspection        检测进度和结果
│        ├─ storage           历史记录
│        └─ settings          设置
```

依赖方向：

```text
feature -> application -> domain
data -> domain
app -> feature
core/platform -> Android SDK / CameraX / OkHttp
```

## 5. 页面开发计划

### 5.1 拍照页

交付内容：

- 相机权限申请。
- CameraX Preview。
- ImageCapture 拍照。
- 进入页面默认请求开启补光灯。
- 拍照后显示确认照片界面。
- `重拍` 和 `确认照片` 两个动作。

验收标准：

- 华为 Pad 上可以打开相机预览。
- 点击拍照后能保存临时照片。
- 默认补光灯开启失败时不崩溃，并显示普通拍照状态。
- 点击确认后进入检测上传流程。

### 5.2 检测页

交付内容：

- 上传中状态。
- 服务器成功返回后的结果展示。
- 原图和检测图切换或并排预览。
- 总状态：`PASS` / `NG` / `REVIEW` / `ERROR`。
- 四类检测项：螺栓与漆标、密封胶、端面缺陷、内部杂物。
- 只保留一个主按钮：`返回拍照`。

验收标准：

- 服务器返回成功时能完整展示结果。
- 服务器失败、超时、地址错误时显示可理解的错误。
- 检测页不暴露复杂操作，符合工人端极简要求。

### 5.3 存储页

交付内容：

- 历史记录列表。
- 记录详情页。
- 本地保存状态和服务器同步状态。
- 原图和检测图预览。

验收标准：

- 自动存储打开后，检测完成记录自动入库。
- 断网或服务器失败时，记录能保留为待同步/失败状态。
- 历史记录能按时间倒序查看。

### 5.4 设置页

交付内容：

- 服务器地址输入。
- `/api/health` 连接测试。
- 自动存储开关。
- 存储位置选择：只存平板、只存服务器、平板和服务器都保存。
- 后续扩展字段预留：设备编号、操作员、连接模式。

验收标准：

- 地址修改后立即持久化。
- 重启 App 后设置不丢失。
- 连接测试能明确显示成功/失败。

## 6. API 对接计划

第一阶段使用 HTTP 局域网地址：

```text
GET  /api/health
POST /api/inspect
```

`POST /api/inspect` 请求：

```text
multipart/form-data
image       拍照图片
deviceId    设备 ID
clientTime  客户端时间
```

App 端统一转成领域模型：

```text
InspectionRecord
InspectionCheck
InspectionFinding
```

App 不直接依赖服务器内部模型名称。服务器可以使用：

```text
YOLO 分割 -> PatchCore 异常检测 -> 规则融合 -> 检测图 -> JSON 结果
```

## 7. 本地数据设计

Room 表：

```text
inspection_records
- id
- created_at
- status
- original_image_path
- detected_image_path
- summary
- checks_json
- storage_mode
- sync_status
- server_record_id
- model_version
- raw_result_json
```

DataStore 设置：

```text
server_base_url
auto_save_enabled
storage_mode
device_id
connection_mode
```

## 8. 开发里程碑

### M0：工程初始化

- 创建可构建 Android 工程。
- 配置 Kotlin、Compose、CameraX、Retrofit、Room、DataStore、Hilt。
- 建立基础包结构和导航。

完成标准：能生成 debug APK，Pad 可安装打开。

### M1：静态页面和导航

- 拍照页静态 UI。
- 检测页静态 UI。
- 存储页静态 UI。
- 设置页静态 UI。

完成标准：不接真实相机/服务器也能跑通页面跳转。

### M2：CameraX 拍照

- 权限。
- 预览。
- 拍照。
- 默认补光。
- 照片确认。

完成标准：Pad 上能拍照并确认进入上传流程。

### M3：服务器对接

- `/api/health` 连接测试。
- `/api/inspect` 上传图片。
- 检测结果解析。
- 错误和超时处理。

完成标准：可以连接本机服务器并展示检测结果。

### M4：本地记录和设置

- Room 保存检测记录。
- DataStore 保存设置。
- 自动存储策略。
- 历史记录列表和详情。

完成标准：重启 App 后记录和设置仍存在。

### M5：现场可用性打磨

- 横竖屏策略。
- 大按钮和高对比状态。
- 网络失败提示。
- 图片压缩和上传大小控制。
- APK 安装测试。

完成标准：工人按固定流程可以连续完成多次检测。

## 9. 当前优先级

已完成：

1. 创建 Android 工程。
2. 实现页面导航和静态 UI。
3. 接入 CameraX 预览、拍照和默认补光尝试。

下一步：

1. 接服务器 `/api/inspect`，替换当前模拟检测结果。
2. 将设置持久化到 DataStore。
3. 将检测记录持久化到 Room。
4. 增加检测图下载/展示。

不要先做：

- 复杂实时引导。
- 完整账号系统。
- 多设备云端管理。
- App 内模型推理。

## 10. 风险和处理

| 风险 | 影响 | 处理 |
| --- | --- | --- |
| 华为 Pad 相机/补光兼容性 | 拍照不可用或补光失败 | CameraX 实机验证，补光失败时允许普通拍照 |
| 局域网地址变化 | App 无法连接服务器 | 设置页可编辑服务器地址，支持连接测试 |
| 服务器检测慢 | 用户等待时间长 | 检测页明确显示上传/检测中状态，后续可加队列 |
| 图片过大 | 上传慢、服务器压力高 | App 端压缩长边和 JPEG 质量 |
| AI 结果不稳定 | 误报/漏报 | App 支持 REVIEW 状态，服务器保留原始结果 JSON |
| 非局域网扩展 | 后续架构调整成本 | App 只依赖 `server_base_url`，不写死 IP |
