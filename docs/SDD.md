# SDD 记录：MetaPad 视觉检测 App

SDD：Software Design Document / 软件设计记录  
版本：v0.2  
日期：2026-06-15  
状态：Android MVP 初版开发中

## 1. 背景

本项目为华为 Pad 上运行的视觉检测 APK。工人使用 Pad 拍摄接线盒，App 将图片上传到本机或远程服务器，由服务器完成 AI 检测并返回检测图和结构化结果。

当前 AI Pipeline 约定：

```text
图片上传
-> YOLO 分割检测螺栓、漆标、密封胶
-> 裁剪 ROI
-> PatchCore 检测脏污、磕碰、多余螺栓、铁屑、铝屑等异常
-> 规则融合
-> 输出检测图和 PASS / NG / REVIEW
-> 返回 App
```

App 端第一阶段不运行模型，只调用服务器 API。

## 2. 设计目标

- 工人端界面简单，减少现场操作负担。
- 支持局域网服务器，后续可扩展到非局域网 HTTPS。
- App 保留本地记录和设置能力。
- 业务逻辑和平台实现解耦，为后续模块化保留空间。
- 检测结果可追溯：原图、检测图、检测项、模型版本、原始 JSON。

## 3. 非目标

第一阶段不做：

- App 本地 AI 推理。
- 实时相机移动指导。
- 账号权限系统。
- 云端管理后台。
- 批量报表导出。
- 多模型动态切换。

## 4. 架构决策记录

### SDD-001：App 使用原生 Android

决策：

```text
Kotlin + Jetpack Compose + CameraX
```

原因：

- Pad 拍照、补光、权限、文件存储都依赖 Android 平台能力。
- 原生 CameraX 比跨端相机封装更容易处理兼容性问题。
- 第一阶段目标是稳定安装和现场使用，不追求跨端。

影响：

- iOS 不在第一阶段范围内。
- 后续如需要跨端，可只共享 API 协议和部分业务规则，不共享 UI。

### SDD-002：采用轻量 Clean Architecture / DDD 分层

决策：

```text
feature -> application -> domain
data -> domain
core-platform -> Android / Network / Camera / File
```

原因：

- 项目虽小，但后续会扩展非局域网、记录同步、设备管理和模型版本。
- 领域模型需要稳定，不应直接绑定 Compose、Room、Retrofit。
- 避免一开始过度复杂，所以第一阶段可以先单 Gradle module，多 package 分层。

约束：

- `domain` 不依赖 Android SDK。
- `application` 只编排用例，不直接操作 HTTP、数据库和相机。
- `feature` 只处理 UI state、UI event 和页面渲染。
- `data` 负责 API、Room、DataStore、文件读写。

### SDD-003：AI 推理由服务器负责

决策：

App 上传图片，服务器运行：

```text
YOLO -> PatchCore -> 规则融合
```

原因：

- GPU 训练和推理环境在本机服务器更好控制。
- PatchCore 特征库和 YOLO 权重不需要塞进 APK。
- 后续模型升级不要求重新安装 App。

影响：

- App 必须处理服务器不可用、超时、网络失败。
- 检测页需要展示上传中、检测中、失败、复核等状态。

### SDD-004：检测结果页只保留“返回拍照”

决策：

检测完成后，结果页只提供：

```text
返回拍照
```

原因：

- 现场工人不需要处理复杂功能。
- 自动存储、存储位置、服务器地址等配置放入设置页。

影响：

- 检测页需要自动按设置保存记录。
- 存储失败或同步失败只做状态提示，不打断主流程。

### SDD-005：设置页管理服务器地址和存储策略

决策：

设置页保存：

```text
server_base_url
auto_save_enabled
storage_mode
device_id
connection_mode
```

原因：

- 当前局域网 IP 可能变化。
- 后续要支持 HTTPS / 公网 / 隧道连接。
- App 不能写死 `192.168.x.x`。

影响：

- 所有 API 请求从 DataStore 读取服务器地址。
- 需要提供 `/api/health` 连接测试。

## 5. 领域模型

### InspectionRecord

```text
id
createdAt
status
originalImageUri
detectedImageUri
checks
storageMode
syncStatus
serverRecordId
modelVersion
rawResultJson
```

### InspectionCheck

```text
code
name
status
summary
findings
```

### InspectionFinding

```text
id
type
label
severity
confidence
locationText
bbox
maskUri
```

### 枚举

```text
InspectionStatus = PASS / NG / REVIEW / ERROR
StorageMode = PAD_ONLY / SERVER_ONLY / PAD_AND_SERVER
SyncStatus = LOCAL_ONLY / SYNCED / PENDING / FAILED
ConnectionMode = LAN / HTTPS / CLOUD / TUNNEL
FindingType = MISSING_BOLT / MISSING_PAINT_MARK / SEALANT_MISSING / SURFACE_DIRTY / DENT / FOREIGN_OBJECT / EXTRA_BOLT / METAL_CHIP
```

## 6. UI 状态设计

### CaptureUiState

```text
permissionRequired
previewReady
torchEnabled
capturedImageUri
isCapturing
errorMessage
```

### InspectionUiState

```text
originalImageUri
detectedImageUri
status
summary
checks
isUploading
isInspecting
errorMessage
saveStatus
```

### StorageUiState

```text
records
selectedRecord
isLoading
syncStatus
errorMessage
```

### SettingsUiState

```text
serverBaseUrl
autoSaveEnabled
storageMode
connectionMode
isTestingConnection
connectionTestResult
```

## 7. UseCase 设计

```text
CapturePhotoUseCase
ConfirmPhotoUseCase
SubmitInspectionUseCase
SaveInspectionRecordUseCase
LoadInspectionRecordsUseCase
GetInspectionRecordDetailUseCase
SyncPendingRecordsUseCase
TestServerConnectionUseCase
UpdateSettingsUseCase
```

## 8. Repository 接口

```text
InspectionRepository
- submitInspection(photo): InspectionResult
- saveRecord(record): Unit
- getRecords(): Flow<List<InspectionRecord>>
- getRecord(id): Flow<InspectionRecord?>
- syncPending(): SyncResult

SettingsRepository
- observeSettings(): Flow<AppSettings>
- updateServerBaseUrl(url): Unit
- updateAutoSave(enabled): Unit
- updateStorageMode(mode): Unit

CameraRepository / CameraController
- bindPreview()
- capturePhoto()
- setTorch(enabled)
```

## 9. API 协议基线

### GET /api/health

返回：

```json
{
  "status": "ok",
  "serverTime": "2026-06-15T20:00:00+08:00",
  "modelVersion": "toolbox-ai-v1"
}
```

### POST /api/inspect

请求：

```text
multipart/form-data
image
deviceId
clientTime
```

返回：

```json
{
  "recordId": "20260615-0001",
  "status": "NG",
  "summary": "检测未通过",
  "modelVersion": "toolbox-ai-v1",
  "originalImageUrl": "/records/20260615-0001/original.jpg",
  "detectedImageUrl": "/records/20260615-0001/detected.jpg",
  "checks": []
}
```

## 10. 本地存储基线

Room：

```text
inspection_records
```

DataStore：

```text
app_settings
```

文件目录：

```text
app-specific storage/
├─ photos/original/
├─ photos/detected/
└─ cache/upload/
```

## 11. 异常处理

| 场景 | App 行为 |
| --- | --- |
| 没有相机权限 | 显示权限说明和授权入口 |
| 补光开启失败 | 不阻塞拍照，显示补光未开启 |
| 服务器地址为空 | 引导进入设置 |
| `/api/health` 失败 | 设置页显示连接失败 |
| `/api/inspect` 超时 | 检测页显示服务器超时，可返回拍照 |
| 自动保存失败 | 检测页显示保存失败状态，记录错误原因 |
| 服务器返回 REVIEW | 结果页显示需要人工复核 |

## 12. 安全和扩展

第一阶段局域网：

```text
HTTP + 手动配置 IP + 无账号
```

非局域网阶段必须补充：

```text
HTTPS
Token 鉴权
设备注册
请求大小限制
服务端访问日志
失败重试
```

## 13. 当前模型记录

20260615 批次：

```text
YOLO 初始权重：model-training/models/20260615/yolo26s-seg.pt
YOLO 当前最佳权重：model-training/models/20260615/yolo-seg-v1-best.pt
PatchCore 正常样本：good.mp4 抽部分帧建立特征库
PatchCore 异常验证：fu1.mp4、fu2.mp4，多余螺栓异常样本
PatchCore ROI：inner_area
```

这些模型和结果文件不纳入 Git，只记录路径和使用约定。

## 14. SDD 变更记录

| 日期 | 版本 | 记录 |
| --- | --- | --- |
| 2026-06-15 | v0.1 | 建立 App 开发前设计基线，确认原生 Android、服务器 AI 推理、极简工人端流程和设置页配置策略。 |
