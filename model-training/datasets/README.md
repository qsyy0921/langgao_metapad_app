# Datasets

真实训练数据不纳入 git。当前批次固定放在 `datasets/20260615`。

```text
datasets/
└─ 20260615/
   ├─ yolo_dataset/
   │  ├─ images/
   │  │  ├─ train/
   │  │  ├─ val/
   │  │  └─ test/
   │  ├─ labels/
   │  │  ├─ train/
   │  │  ├─ val/
   │  │  └─ test/
   │  └─ data.yaml
   │
   └─ patchcore_dataset/
      ├─ videos/
      │  ├─ good.mp4    正常样本，用于建立 PatchCore 特征库
      │  ├─ fu1.mp4     多余螺栓异常样本，只用于验证
      │  └─ fu2.mp4     多余螺栓异常样本，只用于验证
      └─ images/
```

后续 PatchCore 抽帧和 ROI 裁剪后，建议整理为：

```text
datasets/20260615/patchcore_rois/
├─ inner_area/
│  ├─ images/
│  ├─ train/good/
│  ├─ val/good/
│  └─ test/
└─ surface_area/
   ├─ train/good/
   ├─ val/good/
   └─ test/
```

标注原则：

- YOLO 20260615 批次当前覆盖螺栓、漆标、密封胶三类。
- PatchCore `train/good` 只放正常样本。
- `fu1.mp4`、`fu2.mp4` 属于多余螺栓异常样本，只用于阈值选择、评估和回归测试。
