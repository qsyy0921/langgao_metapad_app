from __future__ import annotations

import html
import json
import os
import re
import uuid
from datetime import datetime
from pathlib import Path

from flask import Flask, jsonify, redirect, request, send_from_directory, url_for
from PIL import Image, ImageDraw, ImageOps, ImageStat


BASE_DIR = Path(__file__).resolve().parent
WEB_DIR = BASE_DIR / "webapp"
SHARE_DIR = Path(os.environ.get("PAD_SHARE_DIR", BASE_DIR / "pad-share")).resolve()
RECORDS_DIR = SHARE_DIR / "inspection-records"
CONFIG_FILE = SHARE_DIR / "storage_config.json"

SHARE_DIR.mkdir(parents=True, exist_ok=True)
RECORDS_DIR.mkdir(parents=True, exist_ok=True)

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 8 * 1024 * 1024 * 1024


WINDOWS_BAD_CHARS = re.compile(r'[<>:"/\\|?*\x00-\x1f]')
ALLOWED_IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".bmp"}


def safe_filename(name: str) -> str:
    name = Path(name).name.strip()
    name = WINDOWS_BAD_CHARS.sub("_", name)
    name = name.rstrip(". ")
    if not name:
        name = datetime.now().strftime("upload_%Y%m%d_%H%M%S")
    return name


def unique_target(name: str) -> Path:
    target = SHARE_DIR / safe_filename(name)
    if not target.exists():
        return target

    stem = target.stem
    suffix = target.suffix
    for index in range(1, 10000):
        candidate = SHARE_DIR / f"{stem}_{index}{suffix}"
        if not candidate.exists():
            return candidate
    raise RuntimeError("Too many duplicate filenames")


def human_size(size: int) -> str:
    value = float(size)
    for unit in ("B", "KB", "MB", "GB"):
        if value < 1024 or unit == "GB":
            return f"{value:.1f} {unit}" if unit != "B" else f"{int(value)} B"
        value /= 1024
    return f"{size} B"


def now_text() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def read_storage_config() -> dict[str, str]:
    default = {
        "mode": "server",
        "serverPath": str(RECORDS_DIR),
        "updatedAt": "",
    }
    if not CONFIG_FILE.exists():
        return default
    try:
        saved = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return default
    return {**default, **saved, "serverPath": str(RECORDS_DIR)}


def write_storage_config(data: dict[str, str]) -> dict[str, str]:
    mode = data.get("mode", "server")
    if mode not in {"server", "pad", "both"}:
        mode = "server"
    config = {
        "mode": mode,
        "serverPath": str(RECORDS_DIR),
        "updatedAt": now_text(),
    }
    CONFIG_FILE.write_text(json.dumps(config, ensure_ascii=False, indent=2), encoding="utf-8")
    return config


def build_detection_image(original_path: Path, detected_path: Path) -> tuple[bool, list[str]]:
    with Image.open(original_path) as source:
        image = ImageOps.exif_transpose(source).convert("RGB")

    width, height = image.size
    grayscale = image.convert("L")
    brightness = ImageStat.Stat(grayscale).mean[0]

    reasons: list[str] = []
    if width < 900 or height < 600:
        reasons.append("照片分辨率偏低，建议靠近后重新拍摄")
    if brightness < 75:
        reasons.append("画面偏暗，请打开补光灯后重新拍摄")
    if width < height:
        reasons.append("建议横屏拍摄，确保工具箱整体进入画面")

    passed = not reasons

    canvas = image.copy()
    draw = ImageDraw.Draw(canvas)
    margin_x = max(24, int(width * 0.06))
    margin_y = max(24, int(height * 0.08))
    box = (margin_x, margin_y, width - margin_x, height - margin_y)
    status_color = (28, 130, 78) if passed else (205, 91, 36)
    guide_color = (28, 130, 78) if passed else (228, 151, 44)

    line_width = max(4, width // 180)
    draw.rectangle(box, outline=guide_color, width=line_width)

    label = "PASS" if passed else "REVIEW"
    label_bg = (28, 130, 78) if passed else (205, 91, 36)
    label_box = (margin_x, margin_y, min(width - margin_x, margin_x + 220), margin_y + 58)
    draw.rectangle(label_box, fill=label_bg)
    draw.text((margin_x + 18, margin_y + 16), label, fill=(255, 255, 255))

    # Placeholder markers for the future model output. The real detector can replace this block.
    point_y = int(height * 0.56)
    for index, point_x in enumerate((int(width * 0.32), int(width * 0.50), int(width * 0.68)), start=1):
        radius = max(18, width // 55)
        draw.ellipse(
            (point_x - radius, point_y - radius, point_x + radius, point_y + radius),
            outline=status_color,
            width=max(3, line_width - 1),
        )
        draw.text((point_x - radius, point_y + radius + 8), f"P{index}", fill=status_color)

    if reasons:
        panel_height = min(height // 3, 150)
        draw.rectangle((margin_x, height - margin_y - panel_height, width - margin_x, height - margin_y), fill=(255, 248, 238))
        draw.text((margin_x + 18, height - margin_y - panel_height + 18), "未通过原因:", fill=(124, 64, 22))
        for offset, reason in enumerate(reasons[:3], start=1):
            draw.text((margin_x + 18, height - margin_y - panel_height + 18 + offset * 30), reason, fill=(124, 64, 22))

    canvas.thumbnail((1920, 1920))
    canvas.save(detected_path, quality=92)
    return passed, reasons


def list_files() -> list[dict[str, str]]:
    rows = []
    for path in sorted(SHARE_DIR.iterdir(), key=lambda p: p.stat().st_mtime, reverse=True):
        if not path.is_file():
            continue
        stat = path.stat()
        rows.append(
            {
                "name": path.name,
                "escaped": html.escape(path.name),
                "size": human_size(stat.st_size),
                "mtime": datetime.fromtimestamp(stat.st_mtime).strftime("%Y-%m-%d %H:%M"),
            }
        )
    return rows


def read_record(record_dir: Path) -> dict[str, object] | None:
    meta_path = record_dir / "metadata.json"
    if not meta_path.exists():
        return None
    try:
        metadata = json.loads(meta_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return None
    record_id = record_dir.name
    return {
        **metadata,
        "recordId": record_id,
        "originalUrl": f"/records/{record_id}/original.jpg",
        "detectedUrl": f"/records/{record_id}/detected.jpg",
    }


@app.get("/")
def app_index() -> object:
    return send_from_directory(WEB_DIR, "index.html")


@app.get("/webapp/<path:filename>")
def webapp_asset(filename: str) -> object:
    return send_from_directory(WEB_DIR, filename)


@app.get("/transfer")
def transfer_index() -> str:
    files = list_files()
    file_rows = "\n".join(
        f"""
        <tr>
          <td><a href="{url_for('download_file', filename=row['name'])}">{row['escaped']}</a></td>
          <td>{row['size']}</td>
          <td>{row['mtime']}</td>
        </tr>
        """
        for row in files
    )
    if not file_rows:
        file_rows = '<tr><td colspan="3" class="empty">No files yet.</td></tr>'

    return f"""<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Pad File Transfer</title>
  <style>
    body {{ margin: 0; font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: #f4f6f8; color: #1f2933; }}
    main {{ max-width: 920px; margin: 0 auto; padding: 28px 18px 44px; }}
    .panel, table {{ background: #fff; border: 1px solid #d9e0e8; border-radius: 8px; }}
    .panel {{ padding: 18px; margin-bottom: 18px; }}
    input[type=file] {{ display: block; width: 100%; box-sizing: border-box; margin-bottom: 12px; padding: 10px; }}
    button {{ border: 0; border-radius: 6px; background: #176b87; color: white; padding: 10px 14px; font-weight: 650; }}
    table {{ width: 100%; border-collapse: collapse; overflow: hidden; }}
    th, td {{ padding: 12px; border-bottom: 1px solid #e3e8ee; text-align: left; word-break: break-word; }}
    th {{ background: #edf2f7; font-size: 13px; color: #526071; }}
    a {{ color: #0f5f7a; font-weight: 650; text-decoration: none; }}
    .empty {{ color: #6b7684; text-align: center; }}
  </style>
</head>
<body>
  <main>
    <h1>Pad File Transfer</h1>
    <p>共享目录：<code>{html.escape(str(SHARE_DIR))}</code></p>
    <section class="panel">
      <form action="/upload" method="post" enctype="multipart/form-data">
        <input type="file" name="files" multiple>
        <button type="submit">Upload to PC</button>
      </form>
    </section>
    <table>
      <thead><tr><th>Name</th><th>Size</th><th>Modified</th></tr></thead>
      <tbody>{file_rows}</tbody>
    </table>
  </main>
</body>
</html>"""


@app.post("/upload")
def upload() -> object:
    for uploaded in request.files.getlist("files"):
        if not uploaded or not uploaded.filename:
            continue
        target = unique_target(uploaded.filename)
        uploaded.save(target)
    return redirect(url_for("transfer_index"))


@app.get("/files/<path:filename>")
def download_file(filename: str) -> object:
    return send_from_directory(SHARE_DIR, filename, as_attachment=True)


@app.get("/records/<record_id>/<path:filename>")
def record_file(record_id: str, filename: str) -> object:
    return send_from_directory(RECORDS_DIR / safe_filename(record_id), filename)


@app.get("/api/storage")
def get_storage() -> object:
    return jsonify(read_storage_config())


@app.get("/api/health")
def health() -> object:
    return jsonify(
        {
            "status": "ok",
            "serverTime": now_text(),
            "modelVersion": "prototype",
        }
    )


@app.post("/api/storage")
def update_storage() -> object:
    data = request.get_json(silent=True) or {}
    return jsonify(write_storage_config(data))


@app.get("/api/records")
def get_records() -> object:
    records = []
    for record_dir in sorted(RECORDS_DIR.iterdir(), key=lambda p: p.stat().st_mtime, reverse=True):
        if not record_dir.is_dir():
            continue
        record = read_record(record_dir)
        if record:
            records.append(record)
    return jsonify({"records": records})


@app.post("/api/inspect")
def inspect_image() -> object:
    uploaded = request.files.get("image")
    if not uploaded or not uploaded.filename:
        return jsonify({"error": "missing image"}), 400

    suffix = Path(uploaded.filename).suffix.lower() or ".jpg"
    if suffix not in ALLOWED_IMAGE_SUFFIXES:
        return jsonify({"error": "unsupported image type"}), 400

    record_id = datetime.now().strftime("%Y%m%d_%H%M%S_") + uuid.uuid4().hex[:8]
    record_dir = RECORDS_DIR / record_id
    record_dir.mkdir(parents=True, exist_ok=True)

    original_path = record_dir / "original.jpg"
    detected_path = record_dir / "detected.jpg"
    temp_path = record_dir / f"upload{suffix}"
    uploaded.save(temp_path)

    with Image.open(temp_path) as image:
        image = ImageOps.exif_transpose(image).convert("RGB")
        image.save(original_path, quality=94)
    temp_path.unlink(missing_ok=True)

    passed, reasons = build_detection_image(original_path, detected_path)
    config = read_storage_config()
    metadata = {
        "createdAt": now_text(),
        "pass": passed,
        "reasons": reasons,
        "storageMode": config["mode"],
        "serverPath": str(record_dir),
        "note": "当前为界面联调用的占位检测结果，后续可替换为真实模型输出。",
    }
    (record_dir / "metadata.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding="utf-8")

    return jsonify(
        {
            "recordId": record_id,
            "createdAt": metadata["createdAt"],
            "pass": passed,
            "reasons": reasons,
            "originalUrl": f"/records/{record_id}/original.jpg",
            "detectedUrl": f"/records/{record_id}/detected.jpg",
            "storageMode": config["mode"],
            "serverPath": str(record_dir),
        }
    )


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8765)
