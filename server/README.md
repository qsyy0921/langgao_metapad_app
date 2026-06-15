# Server

本目录放服务器端行为，包括局域网/远程 HTTP API、AI 检测流程编排、记录存储和后续模型服务集成。

当前状态是 Flask 本地服务原型，后续会逐步拆成 AI Pipeline：

```text
接收图片 -> 图像预处理 -> YOLO 分割 -> ROI 裁剪 -> PatchCore 异常检测 -> 规则融合 -> 生成检测图 -> 记录存储
```

## 目录

```text
server/
├─ src/metapad_server/      服务端源码
├─ scripts/                 启动脚本
├─ requirements.txt         Python 依赖
└─ pad-share/               运行时共享/记录目录，未纳入 git
```

## 本地启动

```powershell
cd E:\company\app\server
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\scripts\start-pad-server.ps1
```

默认服务地址：

```text
http://<PC局域网IP>:8765/
```

## API 草案

```text
GET  /api/health
POST /api/inspect
GET  /api/records
GET  /api/records/{recordId}
POST /api/records/sync
```

`POST /api/inspect` 应返回结构化 AI 结果：总结果、检测图、四类检测项、异常位置、置信度和原因。
