# MetaPad 工具箱视觉检测 App 设计文档

版本：v0.1 原型阶段  
日期：2026-06-15  
目标设备：华为 MetaPad / Android 平板  
核心形态：MetaPad 原生 APK + AI 检测服务器

> 当前图片和界面图用于原型阶段的结构对齐，不作为最终 UI 视觉稿。后续进入开发阶段后，需要基于真实相机画面、现场操作距离、平板分辨率和检测模型输出继续细化。

## 1. 项目目标

本项目用于接线盒 / 工具箱类结构件的现场视觉检测。操作人员使用 MetaPad 安装的 APK 进行拍照，App 根据实时相机画面引导用户调整位置并默认开启补光灯；拍照后上传到本机或远程 AI 检测服务器，服务器完成视觉检测并返回检测图、通过/未通过结果和原因；App 负责展示结果、保存记录和同步数据。

核心流程：

```text
拍照引导 -> 拍照采集 -> 上传图片 -> AI 检测 -> 返回结果 -> 保存记录
```

## 2. 检测内容

第一版视觉检测范围包括四类：

1. 14 个螺栓无漏装，所有漆标无漏画。
2. 白色密封胶无漏打。
3. 接线盒端面无脏污、磕碰等缺陷。
4. 接线盒内部无多出的螺栓、铁屑、铝屑等杂物。

总结果分为：

```text
PASS：全部检测项通过
NG：任意检测项未通过
REVIEW：模型置信度不足，需要人工复核
```

## 3. 架构总览

推荐架构：

```text
模块化 Android + Clean Architecture + 轻量 DDD + MVVM/MVI 单向数据流
```

服务端推荐架构：

```text
Python AI Server + HTTP API + AI Pipeline + 模型版本管理 + 记录存储
```

新版架构图：

![AI 检测架构图](architecture-ai-inspection-zh.png)

## 4. Android App 模块设计

建议 Gradle 模块：

```text
:app                    宿主工程，启动、导航、依赖注入、主题
:core-common            Result、Error、时间、ID、通用类型
:core-ui                通用 UI 组件、主题、图像预览组件
:core-platform          相机、权限、日志、网络基础配置
:domain                 领域模型和业务规则
:application            用例层，业务流程编排
:data                   Repository 实现、网络、Room、DataStore、文件
:feature-capture        拍照引导界面
:feature-inspection     检测结果界面
:feature-storage        存储与历史记录界面
```

依赖方向：

```text
feature -> application -> domain
data -> domain
data -> core-platform
app -> feature
```

约束：

- `domain` 不依赖 Android、Compose、CameraX、Retrofit、Room。
- `application` 只编排业务流程，不直接操作 HTTP、数据库、文件系统。
- `feature` 只处理 UI、ViewModel、UiState、UiEvent。
- `data` 和 `core-platform` 处理具体技术实现。

## 5. 三个界面设计

### 5.1 拍照界面

目标：引导用户拍到符合检测要求的图片。

核心能力：

- CameraX 实时预览。
- 进入页面默认开启补光灯。
- 实时分析相机帧，提示用户如何移动。
- 显示取景框和方向提示。
- 判断画面是否清晰、是否偏暗、是否完整入框。
- 拍照后支持重拍和提交检测。

原型图：

![拍照界面原型](ui-prototype-capture.png)

实时引导提示示例：

```text
向左移动
向右移动
向上移动
向下移动
靠近一点
离远一点
请保持水平
请保持稳定
画面偏暗，已开启补光灯
右侧密封胶未完整入框
可以拍照
```

拍照页状态：

```text
未识别到工具箱
需要调整位置
画面偏暗
画面模糊
构图合格
已拍照待提交
上传中
```

### 5.2 检测界面

目标：展示原图、检测图、总结果和四类检测项结果。

核心能力：

- 展示原图。
- 展示服务器返回的检测图。
- 显示 PASS / NG / REVIEW。
- 显示四类检测清单。
- 对异常项显示原因、位置、置信度和建议动作。
- 支持重新拍照、保存异常记录。

原型图：

![检测界面原型](ui-prototype-inspection.png)

检测清单结构：

```text
螺栓与漆标
- 螺栓数量：14/14
- 漆标数量：14/14
- 状态：PASS / NG / REVIEW

白色密封胶
- 状态：连续完整 / 漏打 / 不连续 / 复核

端面脏污 / 磕碰
- 脏污：无 / 有
- 磕碰：无 / 有

内部杂物
- 多余螺栓：无 / 有
- 铁屑：无 / 有
- 铝屑：无 / 有
```

杂物提示规则：

```text
总结果：NG 检测未通过
异常项：内部杂物
提示文案：发现疑似杂物，请清理后重新拍照检测。
图像标注：红框标出明确异常，黄框标出疑似异常。
详情示例：F1：盒内右下区域检测到疑似铝屑，置信度 86%。
```

### 5.3 存储界面

目标：管理检测记录、存储策略、同步状态和数据导出。

核心能力：

- 历史记录列表。
- 记录详情查看。
- 原图和检测图预览。
- 存储模式选择。
- 服务器地址配置。
- 连接测试。
- 同步失败记录。
- 导出记录。

原型图：

![存储界面原型](ui-prototype-storage.png)

存储模式：

```text
PAD_ONLY：只存平板
SERVER_ONLY：只存服务器
PAD_AND_SERVER：平板和服务器都保存
```

同步状态：

```text
已保存本地
已同步服务器
等待同步
同步失败
```

## 6. App 端核心领域模型

建议领域模型：

```kotlin
data class InspectionRecord(
    val id: InspectionRecordId,
    val createdAt: Instant,
    val status: InspectionStatus,
    val originalImageUri: String,
    val detectedImageUri: String?,
    val checks: List<InspectionCheck>,
    val storageMode: StorageMode,
    val syncStatus: SyncStatus,
    val serverRecordId: String?,
    val modelVersion: String?,
    val rawResultJson: String?
)
```

```kotlin
data class InspectionCheck(
    val code: InspectionCheckCode,
    val name: String,
    val status: InspectionStatus,
    val summary: String,
    val findings: List<InspectionFinding>
)
```

```kotlin
data class InspectionFinding(
    val id: String,
    val type: FindingType,
    val label: String,
    val severity: FindingSeverity,
    val confidence: Double?,
    val locationText: String?,
    val bbox: BoundingBox?
)
```

关键枚举：

```text
InspectionStatus：PASS / NG / REVIEW / ERROR
StorageMode：PAD_ONLY / SERVER_ONLY / PAD_AND_SERVER
SyncStatus：LOCAL_ONLY / SYNCED / PENDING / FAILED
ConnectionMode：LAN / HTTPS / CLOUD / TUNNEL
FindingType：MISSING_BOLT / MISSING_PAINT_MARK / SEALANT_MISSING / SURFACE_DIRTY / DENT / FOREIGN_OBJECT
```

## 7. App 端用例设计

核心 UseCase：

```text
CapturePhotoUseCase
AnalyzePreviewFrameUseCase
SubmitInspectionUseCase
SaveInspectionRecordUseCase
LoadInspectionRecordsUseCase
SyncPendingRecordsUseCase
TestServerConnectionUseCase
UpdateStorageModeUseCase
```

拍照实时引导不要写死在 UI 或 ViewModel 中，建议拆成：

```text
FrameAnalyzer
GuidanceEngine
TorchController
CaptureController
```

第一版实时引导可以先实现轻量规则：

```text
亮度检测
模糊检测
横屏检测
稳定性检测
目标框位置占位
```

后续可替换为本地轻量模型或服务器低频辅助引导。

## 8. AI 检测服务器设计

服务端不是简单文件服务，而是 AI 检测平台雏形。

建议目录：

```text
server/
├─ api
│  ├─ inspection_api.py
│  ├─ record_api.py
│  ├─ model_api.py
│  └─ health_api.py
│
├─ application
│  ├─ submit_inspection.py
│  ├─ generate_report.py
│  └─ manage_model.py
│
├─ domain
│  ├─ inspection_task.py
│  ├─ detection_result.py
│  ├─ finding.py
│  ├─ model_version.py
│  └─ inspection_rule.py
│
├─ ai
│  ├─ preprocess
│  ├─ detectors
│  ├─ classifiers
│  ├─ segmenters
│  ├─ postprocess
│  └─ visualizer
│
├─ infrastructure
│  ├─ storage
│  ├─ database
│  ├─ queue
│  ├─ model_runtime
│  └─ config
│
└─ bootstrap
   └─ main.py
```

AI Pipeline：

```text
接收图片
-> 保存原图
-> 图像预处理
-> 工具箱定位
-> 螺栓/漆标检测
-> 密封胶检测
-> 端面缺陷检测
-> 内部杂物检测
-> 规则融合
-> 生成检测图
-> 保存记录
-> 返回结果
```

## 9. HTTP API 设计

### 9.1 健康检查

```text
GET /api/health
```

返回：

```json
{
  "status": "ok",
  "serverTime": "2026-06-15T15:30:00+08:00",
  "modelVersion": "toolbox-ai-v1.0"
}
```

### 9.2 提交检测

```text
POST /api/inspect
Content-Type: multipart/form-data
```

参数：

```text
image：拍照图片
deviceId：设备 ID
operatorId：操作员，可选
taskType：检测任务类型
clientTime：客户端时间
```

返回：

```json
{
  "recordId": "20260615-0008",
  "status": "NG",
  "summary": "检测未通过",
  "modelVersion": "toolbox-ai-v1.0",
  "originalImageUrl": "/records/20260615-0008/original.jpg",
  "detectedImageUrl": "/records/20260615-0008/detected.jpg",
  "checks": [
    {
      "code": "BOLT_AND_PAINT_MARK",
      "name": "螺栓与漆标",
      "status": "PASS",
      "summary": "螺栓 14/14，漆标 14/14",
      "findings": []
    },
    {
      "code": "FOREIGN_OBJECT",
      "name": "内部杂物",
      "status": "NG",
      "summary": "检测到 1 处疑似杂物",
      "findings": [
        {
          "id": "F1",
          "type": "ALUMINUM_CHIP",
          "label": "疑似铝屑",
          "severity": "NG",
          "confidence": 0.86,
          "locationText": "盒内右下区域",
          "bbox": [820, 460, 880, 510]
        }
      ]
    }
  ]
}
```

### 9.3 查询记录

```text
GET /api/records
GET /api/records/{recordId}
```

### 9.4 同步记录

```text
POST /api/records/sync
```

用于后续非局域网、离线缓存、失败重传。

## 10. 非局域网扩展

App 不应写死局域网地址。服务器配置必须由 DataStore 保存：

```text
serverBaseUrl
connectionMode
authToken
timeout
useHttps
```

第一阶段：

```text
http://192.168.0.141:8765
```

后续阶段：

```text
https://api.example.com
https://frp.example.com
https://ngrok-like-domain
```

公网访问必须补充：

```text
HTTPS
Token 鉴权
设备注册
上传大小限制
访问日志
异常告警
```

## 11. 原型资产

当前设计文档使用以下原型资产：

```text
docs/architecture-ai-inspection-zh.png
docs/ui-prototype-capture.png
docs/ui-prototype-inspection.png
docs/ui-prototype-storage.png
docs/assets/toolbox-reference.jpg
docs/metapad-ui-prototypes.html
```

旧版架构图保留：

```text
docs/architecture-metapad-app.png
docs/architecture-metapad-app-zh.png
```

## 12. 后续开发建议

第一阶段目标：

```text
1. 创建 Android APK 工程。
2. 完成三界面静态 UI。
3. 接入 CameraX 拍照和补光灯。
4. 接入本机服务器 /api/inspect。
5. 显示原图、检测图、PASS/NG 和四类检测清单。
6. 使用 Room 保存本地记录。
7. 使用 DataStore 保存服务器地址和存储模式。
```

第二阶段目标：

```text
1. 完善拍照实时引导。
2. 服务端拆分为 AI Pipeline 架构。
3. 接入真实检测模型。
4. 支持模型版本管理。
5. 支持同步失败重试。
6. 支持非局域网 HTTPS 连接。
```

第三阶段目标：

```text
1. 多设备管理。
2. 操作员账号。
3. 工单号 / 产品编号绑定。
4. 批量导出报表。
5. 云端部署和权限控制。
```
