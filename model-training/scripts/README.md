# Training Scripts

训练脚本按批次日期放置。当前批次为：

```text
scripts/20260615/
```

后续脚本命名建议：

```text
train_yolo_seg.py
export_yolo.py
prepare_patchcore_rois.py
train_patchcore.py
eval_patchcore.py
export_server_bundle.py
```

第一版可以先用官方 YOLO CLI 和 PatchCore/Anomalib 训练流程，等流程稳定后再固化脚本。
