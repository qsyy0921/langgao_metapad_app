# Model Training

本目录放模型训练相关内容。当前规划是两阶段视觉方案：

```text
第一阶段：YOLO 分割模型
用于定位接线盒整体、盒内区域、螺栓、漆标、白色密封胶、端子区域。

第二阶段：PatchCore
使用正常样本学习正常外观，用于检测端面脏污、磕碰、铁屑、铝屑、多余螺栓等异常。
```

## 当前训练约定

当前批次固定为 `20260615`：

```text
Conda 环境：yolo26
训练设备：GPU / cuda:0
训练根目录：E:\company\app\model-training
批次目录：20260615
```

所有和本批次相关的内容都放在对应模块的 `20260615` 目录中：

```text
model-training/
├─ configs/20260615/     本批次训练配置，纳入 git
├─ datasets/20260615/    本批次真实数据，不纳入 git
├─ models/20260615/      本批次预训练权重和导出模型，不纳入 git
├─ runs/20260615/        本批次训练运行目录，不纳入 git
├─ results/20260615/     本批次评估结果和报告，不纳入 git
└─ scripts/20260615/     本批次训练脚本和命令说明，纳入 git
```

进入训练环境：

```powershell
cd E:\company\app\model-training
conda activate yolo26
nvidia-smi
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
├─ configs/              按日期保存训练配置
├─ datasets/             按日期保存真实数据，不纳入 git
├─ models/               按日期保存权重和导出模型，不纳入 git
├─ runs/                 按日期保存训练运行输出，不纳入 git
├─ results/              按日期保存评估结果，不纳入 git
└─ scripts/              按日期保存训练/导出/评估脚本
```

## 20260615 YOLO 分割类别

```text
0 luoshuan       螺栓
1 biaoqi         漆标
2 mifengjiao     白色密封胶
```

本批次先完成螺栓、漆标、密封胶三类分割。接线盒整体、盒内区域、端面区域后续可以补充为 ROI 类别，或者先用固定 ROI / 规则 ROI 实现。

## PatchCore ROI 建议

```text
inner_area        检测内部杂物：铁屑、铝屑、多余螺栓
surface_area      检测端面脏污、磕碰
sealant_area      检测密封胶异常，可选
```

PatchCore 的训练集必须只放正常样本，并覆盖正常光照、角度、加工纹理和轻微拍摄差异。

20260615 批次使用 `patchcore_dataset/videos/good.mp4` 抽取正常样本并建立 PatchCore 特征库。`fu1.mp4`、`fu2.mp4` 都是包含多余螺栓的异常样本，只用于异常验证、阈值选择和效果复核，不参与正常特征库建立。
