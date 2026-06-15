# 20260615 Training Commands

本批次约定使用 `yolo26` conda 环境和 GPU 训练。

```powershell
cd E:\company\app\model-training
conda activate yolo26
nvidia-smi
$env:POLARS_SKIP_CPU_CHECK = "1"
```

## YOLO Segmentation

当前数据集已经整理在：

```text
datasets/20260615/yolo_dataset
```

训练命令草案：

```powershell
yolo task=segment mode=train `
  model=models/20260615/yolo26s-seg.pt `
  data=configs/20260615/yolo-seg/toolbox-seg-v1.yaml `
  imgsz=1280 `
  epochs=100 `
  batch=4 `
  device=0 `
  project=runs/20260615 `
  name=yolo-seg-v1
```

`batch` 需要根据显存调整。训练前先确认 `nvidia-smi` 能看到 GPU。

当前环境里的 `polars 1.40.1` 在保存 checkpoint 时会触发 CPU feature 检查异常，因此训练进程需要设置 `POLARS_SKIP_CPU_CHECK=1`。

## PatchCore

当前 PatchCore 原始视频在：

```text
datasets/20260615/patchcore_dataset/videos
```

约定：

- `good.mp4`：正常样本来源，只用于建立 PatchCore 特征库。
- `fu1.mp4`、`fu2.mp4`：多余螺栓异常样本，只用于异常验证、阈值选择和效果复核。
- 训练前先抽帧，再按 ROI 裁剪到 `datasets/20260615/patchcore_rois`。
- 第一版只抽部分帧：`good.mp4` 每个 ROI 最多抽 30 帧，其中约 80% 用于特征库，20% 用于正常验证。

准备 ROI 数据：

```powershell
python scripts/20260615/prepare_patchcore_rois.py --overwrite
```

建立 `inner_area` 的 PatchCore 特征库，并在 `fu1/fu2` 多余螺栓样本上验证：

```powershell
python scripts/20260615/train_patchcore_torch.py --roi inner_area
```

输出目录：

```text
results/20260615/patchcore/inner_area/
```

当前阈值策略使用 `max(val_good) + 0.01`。本批次正常验证样本很少，`mean + 3std` 会把阈值抬得过高，导致多余螺栓漏检。
