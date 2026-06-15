const { spawnSync } = require("child_process");
const fs = require("fs");
const path = require("path");

function findChrome() {
  const candidates = [
    "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
    "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
  ];
  const found = candidates.find((candidate) => fs.existsSync(candidate));
  if (!found) {
    throw new Error("Chrome or Edge was not found. Install Chrome, or update render-ui-prototypes.js.");
  }
  return found;
}

function main() {
  const root = path.resolve(__dirname, "..");
  const htmlPath = path.join(root, "docs", "metapad-ui-prototypes.html");
  const outDir = path.join(root, "docs");
  const htmlUrl = `file:///${htmlPath.replace(/\\/g, "/")}`;
  const chrome = findChrome();

  const targets = [
    ["capture", "ui-prototype-capture.png"],
    ["inspection", "ui-prototype-inspection.png"],
    ["storage", "ui-prototype-storage.png"],
  ];

  for (const [screen, filename] of targets) {
    const output = path.join(outDir, filename);
    const url = `${htmlUrl}?screen=${screen}`;
    const result = spawnSync(
      chrome,
      [
        "--headless=new",
        "--disable-gpu",
        "--hide-scrollbars",
        "--force-device-scale-factor=1",
        "--window-size=1280,800",
        `--screenshot=${output}`,
        url,
      ],
      { stdio: "inherit" }
    );
    if (result.status !== 0) {
      throw new Error(`Failed to render ${screen} prototype.`);
    }
  }
}

main();
