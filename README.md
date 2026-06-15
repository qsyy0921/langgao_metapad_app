# langgao_metapad_app

MetaPad 工具箱视觉检测 App。

本项目目标是开发一个可安装在华为 MetaPad 上的 Android APK，用于现场拍照检测接线盒 / 工具箱装配质量。App 负责拍照引导、补光、图片上传、检测结果展示和记录存储；本机或远程 AI 检测服务器负责图像预处理、模型推理、规则判定、检测图生成和记录归档。

> 当前阶段是产品与架构原型阶段，图片和界面原型用于对齐功能、信息结构和开发边界，不作为最终 UI 视觉稿。

## 核心流程

```text
拍照引导 -> 拍照采集 -> 上传图片 -> AI 检测 -> 返回结果 -> 保存记录
```

## 检测内容

第一版视觉检测范围：

1. 14 个螺栓无漏装，所有漆标无漏画。
2. 白色密封胶无漏打。
3. 接线盒端面无脏污、磕碰等缺陷。
4. 接线盒内部无多出的螺栓、铁屑、铝屑等杂物。

检测结果分为：

- `PASS`：全部检测项通过。
- `NG`：任意检测项未通过。
- `REVIEW`：模型置信度不足，需要人工复核。

## 架构设计

客户端推荐架构：

```text
模块化 Android + Clean Architecture + 轻量 DDD + MVVM/MVI 单向数据流
```

服务端推荐架构：

```text
Python AI Server + HTTP API + AI Pipeline + 模型版本管理 + 记录存储
```

技术选型：

- Android：Kotlin、Jetpack Compose、CameraX、Retrofit / OkHttp、Room、DataStore、Hilt。
- 服务端：Python、FastAPI/Flask、OpenCV/Pillow、ONNXRuntime/PyTorch 预留、文件/数据库存储。
- 网络：第一阶段局域网 HTTP，后续扩展 HTTPS、内网穿透或云服务器。

新版架构图：

![AI 检测架构图](docs/architecture-ai-inspection-zh.png)

## Android 模块规划

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

## 三个界面

### 1. 拍照界面

目标：引导用户拍到符合检测要求的图片。

核心功能：

- CameraX 实时预览。
- 进入页面默认开启补光灯。
- 实时分析相机画面，并提示用户如何移动。
- 显示取景框、方向箭头和构图状态。
- 支持重拍、拍照、提交检测。

示例提示：

```text
向左移动
向右移动
靠近一点
请保持水平
画面偏暗，已开启补光灯
右侧密封胶未完整入框
可以拍照
```

![拍照界面原型](docs/ui-prototype-capture.png)

### 2. 检测界面

目标：展示原图、检测图、总结果和四类检测清单。

核心功能：

- 展示原图和服务器返回的检测图。
- 显示 `PASS / NG / REVIEW`。
- 显示螺栓与漆标、白色密封胶、端面缺陷、内部杂物四类检测项。
- 如果检测到杂物，用红框/黄框标出位置，并显示类型、区域和置信度。

杂物提示示例：

```text
NG 检测未通过
发现 1 处疑似内部杂物，请清理后重新检测。
F1：盒内右下区域检测到疑似铝屑，置信度 86%。
```

![检测界面原型](docs/ui-prototype-inspection.png)

### 3. 存储界面

目标：管理检测记录、存储策略、同步状态和数据导出。

核心功能：

- 历史记录列表。
- 记录详情查看。
- 原图和检测图预览。
- 存储模式选择：只存平板、只存服务器、平板和服务器都保存。
- 服务器地址配置、连接测试、失败记录同步、记录导出。

![存储界面原型](docs/ui-prototype-storage.png)

## 服务端 API 草案

```text
GET  /api/health
POST /api/inspect
GET  /api/records
GET  /api/records/{recordId}
POST /api/records/sync
```

`POST /api/inspect` 返回结构需要支持结构化 AI 结果：

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

## 当前本地原型

当前目录里已有一个本机服务端原型：

- `pad_transfer_server.py`：Flask 本地服务原型。
- `start-pad-server.ps1`：本地服务启动脚本。
- `requirements.txt`：Python 依赖。

启动本机服务：

```powershell
cd E:\company\app
.\start-pad-server.ps1
```

MetaPad 在同一局域网时可访问：

```text
http://192.168.0.141:8765/
```

## 设计文档与原型资产

- [完整设计文档](docs/DESIGN.md)
- [新版中文架构图](docs/architecture-ai-inspection-zh.png)
- [拍照界面原型](docs/ui-prototype-capture.png)
- [检测界面原型](docs/ui-prototype-inspection.png)
- [存储界面原型](docs/ui-prototype-storage.png)
- [界面原型 HTML 源文件](docs/metapad-ui-prototypes.html)

历史架构图：

- [英文架构图](docs/architecture-metapad-app.png)
- [中文架构图](docs/architecture-metapad-app-zh.png)

## 后续开发计划

第一阶段：

1. 创建 Android APK 工程。
2. 完成三个界面的静态 UI。
3. 接入 CameraX 拍照和默认补光。
4. 接入本机服务器 `/api/inspect`。
5. 展示原图、检测图、PASS/NG 和检测清单。
6. 使用 Room 保存本地记录。
7. 使用 DataStore 保存服务器地址和存储模式。

第二阶段：

1. 完善拍照实时引导。
2. 服务端拆分为 AI Pipeline 架构。
3. 接入真实视觉检测模型。
4. 支持模型版本管理。
5. 支持同步失败重试。
6. 支持非局域网 HTTPS 连接。

第三阶段：

1. 多设备管理。
2. 操作员账号。
3. 工单号 / 产品编号绑定。
4. 批量导出报表。
5. 云端部署和权限控制。
