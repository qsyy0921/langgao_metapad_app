# Model Training

本目录放模型训练相关内容。当前规划是两阶段视觉方案：

```text
第一阶段：YOLO 分割模型
用于定位接线盒整体、盒内区域、螺栓、漆标、白色密封胶、端子区域。

第二阶段：PatchCore
使用正常样本学习正常外观，用于检测端面脏污、磕碰、铁屑、铝屑、多余螺栓等异常。
```

## 推荐训练流程

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

## 目录

```text
model-training/
├─ configs/
│  ├─ yolo-seg/          YOLO 分割数据配置
│  └─ patchcore/         PatchCore ROI 和阈值配置
├─ datasets/             数据集说明，真实数据不纳入 git
└─ scripts/              训练/导出/评估脚本占位
```

## YOLO 分割类别建议

```text
junction_box      接线盒整体
inner_area        盒内区域
bolt              螺栓
paint_mark        漆标
sealant           白色密封胶
terminal_block    黑色端子区域
```

## PatchCore ROI 建议

```text
inner_area        检测内部杂物：铁屑、铝屑、多余螺栓
surface_area      检测端面脏污、磕碰
sealant_area      检测密封胶异常，可选
```

PatchCore 的训练集必须只放正常样本，并覆盖正常光照、角度、加工纹理和轻微拍摄差异。
