# Datasets

真实训练数据不纳入 git。建议按以下结构放置：

```text
datasets/
├─ yolo-seg-v1/
│  ├─ images/
│  │  ├─ train/
│  │  ├─ val/
│  │  └─ test/
│  └─ labels/
│     ├─ train/
│     ├─ val/
│     └─ test/
│
└─ patchcore-v1/
   ├─ inner_area/
   │  ├─ train/good/
   │  ├─ val/good/
   │  └─ test/
   └─ surface_area/
      ├─ train/good/
      ├─ val/good/
      └─ test/
```

标注原则：

- YOLO 分割标注应覆盖接线盒整体、盒内区域、螺栓、漆标、密封胶和端子区域。
- PatchCore `train/good` 只放正常样本。
- 异常样本只用于阈值选择、评估和回归测试。
