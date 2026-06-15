from __future__ import annotations

import argparse
import csv
import json
import random
from pathlib import Path

import cv2
import numpy as np
import torch
import torch.nn.functional as F
import yaml
from PIL import Image
from torchvision import models, transforms


def resolve_path(root: Path, value: str) -> Path:
    path = Path(value)
    return path if path.is_absolute() else root / path


class WideResNetPatchExtractor(torch.nn.Module):
    def __init__(self, pretrained: bool = True) -> None:
        super().__init__()
        weights = models.Wide_ResNet50_2_Weights.IMAGENET1K_V2 if pretrained else None
        backbone = models.wide_resnet50_2(weights=weights)
        self.stem = torch.nn.Sequential(backbone.conv1, backbone.bn1, backbone.relu, backbone.maxpool)
        self.layer1 = backbone.layer1
        self.layer2 = backbone.layer2
        self.layer3 = backbone.layer3
        self.eval()

    @torch.no_grad()
    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = self.stem(x)
        x = self.layer1(x)
        f2 = self.layer2(x)
        f3 = self.layer3(f2)
        f3 = F.interpolate(f3, size=f2.shape[-2:], mode="bilinear", align_corners=False)
        features = torch.cat([f2, f3], dim=1)
        return F.normalize(features, dim=1)


def list_images(path: Path) -> list[Path]:
    if not path.exists():
        return []
    return sorted([p for p in path.rglob("*") if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".bmp"}])


def image_transform(input_size: int):
    return transforms.Compose(
        [
            transforms.Resize((input_size, input_size)),
            transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]),
        ]
    )


@torch.no_grad()
def extract_patches(model, image_path: Path, transform, device: torch.device) -> tuple[torch.Tensor, tuple[int, int], np.ndarray]:
    image = Image.open(image_path).convert("RGB")
    original = np.array(image)
    tensor = transform(image).unsqueeze(0).to(device)
    fmap = model(tensor)[0]
    c, h, w = fmap.shape
    patches = fmap.permute(1, 2, 0).reshape(h * w, c).contiguous()
    return patches.cpu(), (h, w), original


def nearest_distances(patches: torch.Tensor, memory_bank: torch.Tensor, chunk_size: int = 2048) -> torch.Tensor:
    distances = []
    bank = memory_bank.float()
    for start in range(0, patches.shape[0], chunk_size):
        chunk = patches[start : start + chunk_size].float()
        distances.append(torch.cdist(chunk, bank).min(dim=1).values)
    return torch.cat(distances, dim=0)


def build_memory_bank(model, images: list[Path], transform, device: torch.device, max_memory_patches: int) -> torch.Tensor:
    banks = []
    for image_path in images:
        patches, _, _ = extract_patches(model, image_path, transform, device)
        banks.append(patches)
    memory_bank = torch.cat(banks, dim=0)
    if memory_bank.shape[0] > max_memory_patches:
        random.seed(0)
        indices = random.sample(range(memory_bank.shape[0]), max_memory_patches)
        memory_bank = memory_bank[indices]
    return memory_bank.contiguous()


def save_heatmap(original: np.ndarray, dists: torch.Tensor, fmap_hw: tuple[int, int], out_path: Path, threshold: float) -> None:
    h, w = fmap_hw
    heat = dists.reshape(h, w).numpy()
    heat = cv2.resize(heat, (original.shape[1], original.shape[0]), interpolation=cv2.INTER_CUBIC)
    denom = max(float(threshold), 1e-6)
    heat_norm = np.clip(heat / denom, 0, 2) / 2
    heat_u8 = (heat_norm * 255).astype(np.uint8)
    colored = cv2.applyColorMap(heat_u8, cv2.COLORMAP_JET)
    bgr = cv2.cvtColor(original, cv2.COLOR_RGB2BGR)
    overlay = cv2.addWeighted(bgr, 0.62, colored, 0.38, 0)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    cv2.imwrite(str(out_path), overlay)


def score_images(model, images: list[Path], memory_bank: torch.Tensor, transform, device: torch.device, heatmap_dir: Path | None, threshold: float | None):
    rows = []
    for image_path in images:
        patches, fmap_hw, original = extract_patches(model, image_path, transform, device)
        dists = nearest_distances(patches, memory_bank)
        score = float(dists.max().item())
        mean_score = float(dists.mean().item())
        rows.append({"image": str(image_path), "score": score, "mean_patch_score": mean_score})
        if heatmap_dir is not None and threshold is not None:
            out_path = heatmap_dir / f"{image_path.stem}_heatmap.jpg"
            save_heatmap(original, dists, fmap_hw, out_path, threshold)
            rows[-1]["heatmap"] = str(out_path)
    return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="configs/20260615/patchcore/toolbox-patchcore-v1.yaml")
    parser.add_argument("--root", default=".")
    parser.add_argument("--roi", default="inner_area")
    parser.add_argument("--output", default="results/20260615/patchcore")
    parser.add_argument("--no-pretrained", action="store_true")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    cfg = yaml.safe_load(resolve_path(root, args.config).read_text(encoding="utf-8"))
    prepared_root = resolve_path(root, cfg["prepared_roi_root"])
    runtime = cfg.get("runtime", {})
    input_size = int(runtime.get("input_size", 224))
    max_memory_patches = int(runtime.get("max_memory_patches", 50000))
    threshold_strategy = runtime.get("threshold_strategy", "max_val_plus_margin")
    threshold_margin = float(runtime.get("threshold_margin", 0.01))
    device = torch.device("cuda:0" if torch.cuda.is_available() else "cpu")

    roi_root = prepared_root / args.roi
    train_good = list_images(roi_root / "train" / "good")
    val_good = list_images(roi_root / "val" / "good")
    test_images = list_images(roi_root / "test")
    if not train_good:
        raise RuntimeError(f"No train/good images found under {roi_root}")

    transform = image_transform(input_size)
    model = WideResNetPatchExtractor(pretrained=not args.no_pretrained).to(device).eval()

    memory_bank = build_memory_bank(model, train_good, transform, device, max_memory_patches)

    val_rows = score_images(model, val_good, memory_bank, transform, device, None, None) if val_good else []
    val_scores = [row["score"] for row in val_rows]
    if val_scores:
        if threshold_strategy == "max_val_plus_margin":
            threshold = max(val_scores) + threshold_margin
        elif threshold_strategy == "p95":
            threshold = float(np.percentile(val_scores, 95))
        elif threshold_strategy == "mean_plus_3std":
            threshold = float(np.mean(val_scores) + 3 * np.std(val_scores))
        else:
            raise ValueError(f"Unsupported threshold_strategy: {threshold_strategy}")
    else:
        threshold = 0.0

    out_root = resolve_path(root, args.output) / args.roi
    out_root.mkdir(parents=True, exist_ok=True)
    bank_path = out_root / "patchcore_bank.pt"
    torch.save(
        {
            "roi": args.roi,
            "input_size": input_size,
            "backbone": "wide_resnet50_2",
            "memory_bank": memory_bank,
            "threshold": threshold,
            "train_good_count": len(train_good),
            "val_good_count": len(val_good),
        },
        bank_path,
    )

    test_rows = score_images(model, test_images, memory_bank, transform, device, out_root / "heatmaps", threshold)
    for row in val_rows:
        row["split"] = "val/good"
        row["label"] = "good"
        row["prediction"] = "NG" if threshold and row["score"] > threshold else "PASS"
    for row in test_rows:
        row["split"] = "test"
        row["label"] = "extra_bolt"
        row["prediction"] = "NG" if threshold and row["score"] > threshold else "PASS"

    score_path = out_root / "scores.csv"
    rows = val_rows + test_rows
    fieldnames = ["split", "label", "prediction", "score", "mean_patch_score", "image", "heatmap"]
    with score_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)

    test_ng = sum(1 for row in test_rows if threshold and row["score"] > threshold)
    summary = {
        "roi": args.roi,
        "device": str(device),
        "input_size": input_size,
        "train_good_count": len(train_good),
        "val_good_count": len(val_good),
        "test_count": len(test_rows),
        "test_predicted_ng": test_ng,
        "threshold": threshold,
        "threshold_strategy": threshold_strategy,
        "threshold_margin": threshold_margin,
        "memory_bank_patches": int(memory_bank.shape[0]),
        "bank_path": str(bank_path),
        "score_path": str(score_path),
    }
    (out_root / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
