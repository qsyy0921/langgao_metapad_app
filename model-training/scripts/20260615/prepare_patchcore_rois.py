from __future__ import annotations

import argparse
import csv
import json
import shutil
from pathlib import Path

import cv2
import yaml


def resolve_path(root: Path, value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else root / path


def crop_frame(frame, crop_xyxy_norm: list[float]):
    h, w = frame.shape[:2]
    x1, y1, x2, y2 = crop_xyxy_norm
    left = max(0, min(w - 1, int(round(x1 * w))))
    top = max(0, min(h - 1, int(round(y1 * h))))
    right = max(left + 1, min(w, int(round(x2 * w))))
    bottom = max(top + 1, min(h, int(round(y2 * h))))
    return frame[top:bottom, left:right], [left, top, right, bottom]


def extract_video(
    *,
    root: Path,
    dataset_root: Path,
    prepared_root: Path,
    video_rel: str,
    roi: dict,
    group: str,
    defect_type: str | None,
    stride: int,
    normal_val_ratio: float,
    frame_limit: int | None,
    manifest_rows: list[dict],
) -> int:
    video_path = resolve_path(dataset_root, video_rel)
    if not video_path.exists():
        raise FileNotFoundError(video_path)

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise RuntimeError(f"Cannot open video: {video_path}")

    roi_name = roi["name"]
    crop_xyxy_norm = roi.get("crop_xyxy_norm")
    if not crop_xyxy_norm or len(crop_xyxy_norm) != 4:
        raise ValueError(f"ROI {roi_name} must define crop_xyxy_norm")

    saved = 0
    frame_idx = 0
    stem = video_path.stem
    val_every = max(2, round(1 / normal_val_ratio)) if normal_val_ratio > 0 else 0

    while True:
        if frame_limit is not None and saved >= frame_limit:
            break
        ok, frame = cap.read()
        if not ok:
            break
        if frame_idx % stride != 0:
            frame_idx += 1
            continue

        roi_img, crop_xyxy = crop_frame(frame, crop_xyxy_norm)
        if group == "normal":
            subset = "val/good" if val_every and saved % val_every == 0 else "train/good"
        else:
            subset = f"test/{defect_type}"

        out_dir = prepared_root / roi_name / subset
        out_dir.mkdir(parents=True, exist_ok=True)
        out_name = f"{stem}_f{frame_idx:06d}.jpg"
        out_path = out_dir / out_name
        cv2.imwrite(str(out_path), roi_img, [int(cv2.IMWRITE_JPEG_QUALITY), 95])

        manifest_rows.append(
            {
                "roi": roi_name,
                "group": group,
                "defect_type": defect_type or "",
                "video": str(video_path.relative_to(root)) if video_path.is_relative_to(root) else str(video_path),
                "frame_index": frame_idx,
                "output": str(out_path.relative_to(root)) if out_path.is_relative_to(root) else str(out_path),
                "crop_xyxy": json.dumps(crop_xyxy),
                "width": roi_img.shape[1],
                "height": roi_img.shape[0],
            }
        )
        saved += 1
        frame_idx += 1

    cap.release()
    return saved


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="configs/20260615/patchcore/toolbox-patchcore-v1.yaml")
    parser.add_argument("--root", default=".")
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    config_path = resolve_path(root, args.config)
    cfg = yaml.safe_load(config_path.read_text(encoding="utf-8"))

    dataset_root = resolve_path(root, cfg["dataset_root"])
    prepared_root = resolve_path(root, cfg["prepared_roi_root"])
    if args.overwrite and prepared_root.exists():
        shutil.rmtree(prepared_root)
    prepared_root.mkdir(parents=True, exist_ok=True)

    sampling = cfg.get("frame_sampling", {})
    normal_stride = int(sampling.get("normal_stride", 5))
    anomaly_stride = int(sampling.get("anomaly_stride", 5))
    normal_val_ratio = float(sampling.get("normal_val_ratio", 0.2))
    normal_frame_limit = sampling.get("normal_frame_limit_per_video")
    anomaly_frame_limit = sampling.get("anomaly_frame_limit_per_video")
    normal_frame_limit = int(normal_frame_limit) if normal_frame_limit else None
    anomaly_frame_limit = int(anomaly_frame_limit) if anomaly_frame_limit else None

    manifest_rows: list[dict] = []
    summary: dict[str, int] = {}

    for roi in cfg["rois"]:
        roi_name = roi["name"]
        for video_rel in cfg["source_videos"]["normal_feature_library"]:
            count = extract_video(
                root=root,
                dataset_root=dataset_root,
                prepared_root=prepared_root,
                video_rel=video_rel,
                roi=roi,
                group="normal",
                defect_type=None,
                stride=normal_stride,
                normal_val_ratio=normal_val_ratio,
                frame_limit=normal_frame_limit,
                manifest_rows=manifest_rows,
            )
            summary[f"{roi_name}:normal:{video_rel}"] = count

        for defect_type, videos in cfg["source_videos"]["anomaly_eval"].items():
            for video_rel in videos:
                count = extract_video(
                    root=root,
                    dataset_root=dataset_root,
                    prepared_root=prepared_root,
                    video_rel=video_rel,
                    roi=roi,
                    group="anomaly",
                    defect_type=defect_type,
                    stride=anomaly_stride,
                    normal_val_ratio=normal_val_ratio,
                    frame_limit=anomaly_frame_limit,
                    manifest_rows=manifest_rows,
                )
                summary[f"{roi_name}:anomaly:{defect_type}:{video_rel}"] = count

    manifest_path = prepared_root / "manifest.csv"
    with manifest_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(manifest_rows[0].keys()))
        writer.writeheader()
        writer.writerows(manifest_rows)

    summary_path = prepared_root / "summary.json"
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    print(json.dumps({"prepared_root": str(prepared_root), "summary": summary}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
