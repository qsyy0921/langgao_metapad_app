# langgao_metapad_app

MetaPad 工具箱视觉检测项目。

项目分为三部分：

```text
model-training/   模型训练相关：YOLO 分割、PatchCore、数据集规范、训练配置
server/           服务器行为：HTTP API、AI Pipeline、检测结果生成、记录存储
android-app/      App 端：MetaPad APK 工程、界面原型、拍照/检测/存储流程
```

当前阶段是产品、架构和原型阶段。目录已按后续开发方向整理，真实 Android 工程、正式训练脚本和 AI 模型文件还未落地。

## 核心流程

```text
拍照 -> 照片确认 -> 自动上传检测 -> 自动展示结果 -> 按设置存储
```

检测页只保留一个操作按钮：

```text
返回拍照
```

是否自动存储、存储位置、服务器地址放在设置界面配置。

## 检测内容

第一版视觉检测范围：

1. 14 个螺栓无漏装，所有漆标无漏画。
2. 白色密封胶无漏打。
3. 接线盒端面无脏污、磕碰等缺陷。
4. 接线盒内部无多出的螺栓、铁屑、铝屑等杂物。

检测结果：

- `PASS`：全部检测项通过。
- `NG`：任意检测项未通过。
- `REVIEW`：模型置信度不足，需要人工复核。

## 架构图

![AI 检测架构图](docs/architecture-ai-inspection-zh.png)

## 目录结构

```text
.
├─ model-training/
│  ├─ configs/
│  │  ├─ yolo-seg/
│  │  └─ patchcore/
│  ├─ datasets/
│  └─ scripts/
│
├─ server/
│  ├─ src/metapad_server/
│  ├─ scripts/
│  └─ requirements.txt
│
├─ android-app/
│  ├─ docs/
│  └─ scripts/
│
└─ docs/
   ├─ DESIGN.md
   └─ architecture-ai-inspection-zh.png
```

运行时数据不放在根目录。旧的根目录 `pad-share/`、`ipad-share/` 已移除；服务端本地记录和共享数据统一放在 `server/pad-share/`，该目录不纳入 Git。

## 1. 模型训练相关

目录：[model-training](model-training/README.md)

推荐两阶段方案：

```text
第一阶段：YOLO 分割模型
用于定位接线盒整体、盒内区域、螺栓、漆标、白色密封胶、端子区域。

第二阶段：PatchCore
使用正常样本学习正常外观，用于检测端面脏污、磕碰、铁屑、铝屑、多余螺栓等异常。
```

训练流程：

```text
原图采集
-> YOLO 分割标注
-> 训练 YOLO 分割模型
-> 用 YOLO 裁剪 ROI
-> 收集正常 ROI 样本
-> 训练 PatchCore
-> 设定阈值
-> 服务端集成
```

已提供配置模板：

- [YOLO 分割配置](model-training/configs/yolo-seg/toolbox-seg-v1.yaml)
- [PatchCore 配置](model-training/configs/patchcore/toolbox-patchcore-v1.yaml)
- [数据集说明](model-training/datasets/README.md)

真实训练数据、权重和运行输出不纳入 git。

## 2. 服务器行为

目录：[server](server/README.md)

服务端职责：

```text
接收图片
-> 保存原图
-> 图像预处理
-> YOLO 分割
-> ROI 裁剪
-> PatchCore 异常检测
-> 规则融合
-> 生成检测图
-> 保存检测记录
-> 返回结构化结果
```

当前已有 Flask 本地服务原型：

- [server/src/metapad_server/app.py](server/src/metapad_server/app.py)
- [server/scripts/start-pad-server.ps1](server/scripts/start-pad-server.ps1)
- [server/requirements.txt](server/requirements.txt)

本地启动：

```powershell
cd E:\company\app\server
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\scripts\start-pad-server.ps1
```

服务端 API 草案：

```text
GET  /api/health
POST /api/inspect
GET  /api/records
GET  /api/records/{recordId}
POST /api/records/sync
```

## 3. App 端

目录：[android-app](android-app/README.md)

App 端技术选型：

```text
Kotlin
Jetpack Compose
CameraX
Retrofit / OkHttp
Room
DataStore
Hilt
```

App 端界面：

- 拍照界面：默认补光、拍照、照片确认。
- 检测界面：原图、检测图、PASS/NG、四类检测清单，只保留 `返回拍照`。
- 存储界面：记录查看、同步状态。
- 设置界面：自动存储开关、存储位置、服务器地址、连接测试。

界面原型：

- [拍照界面原型](android-app/docs/ui-prototype-capture.png)
- [检测界面原型](android-app/docs/ui-prototype-inspection.png)
- [存储界面原型](android-app/docs/ui-prototype-storage.png)
- [界面原型 HTML 源文件](android-app/docs/metapad-ui-prototypes.html)

## 设计文档

- [完整设计文档](docs/DESIGN.md)
- [新版中文架构图](docs/architecture-ai-inspection-zh.png)

历史架构图：

- [英文架构图](docs/architecture-metapad-app.png)
- [中文架构图](docs/architecture-metapad-app-zh.png)

## 后续开发计划

第一阶段：

1. 创建 Android APK 工程。
2. 完成极简版拍照、检测、存储、设置界面。
3. 接入 CameraX 拍照和默认补光。
4. 接入本机服务器 `/api/inspect`。
5. 展示原图、检测图、PASS/NG 和检测清单。
6. 使用 Room 保存本地记录。
7. 使用 DataStore 保存服务器地址和默认存储模式。

第二阶段：

1. 准备 YOLO 分割数据集和标注规范。
2. 训练 YOLO 分割模型。
3. 收集正常 ROI 样本并训练 PatchCore。
4. 服务端拆分为 AI Pipeline 架构。
5. 集成真实视觉检测模型。
6. 支持非局域网 HTTPS 连接。

第三阶段：

1. 多设备管理。
2. 操作员账号。
3. 工单号 / 产品编号绑定。
4. 批量导出报表。
5. 云端部署和权限控制。
