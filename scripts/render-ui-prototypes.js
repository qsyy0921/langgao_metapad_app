const path = require("path");
const { chromium } = require("playwright");

async function main() {
  const root = path.resolve(__dirname, "..");
  const htmlPath = path.join(root, "docs", "metapad-ui-prototypes.html");
  const outDir = path.join(root, "docs");

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage({
    viewport: { width: 1360, height: 900 },
    deviceScaleFactor: 1,
  });

  await page.goto(`file://${htmlPath.replace(/\\/g, "/")}`, { waitUntil: "networkidle" });

  const targets = [
    ["#capture-screen", "ui-prototype-capture.png"],
    ["#inspection-screen", "ui-prototype-inspection.png"],
    ["#storage-screen", "ui-prototype-storage.png"],
  ];

  for (const [selector, filename] of targets) {
    await page.locator(selector).screenshot({
      path: path.join(outDir, filename),
      animations: "disabled",
    });
  }

  await browser.close();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
