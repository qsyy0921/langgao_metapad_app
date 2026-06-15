$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$ShareDir = Join-Path $Root "pad-share"
$VenvPython = Join-Path $Root ".venv\Scripts\python.exe"
$Waitress = Join-Path $Root ".venv\Scripts\waitress-serve.exe"

if (-not (Test-Path $VenvPython)) {
    throw "Missing virtual environment. Run: python -m venv .venv; .\.venv\Scripts\python.exe -m pip install -r requirements.txt"
}

if (-not (Test-Path $Waitress)) {
    throw "Missing waitress. Run: .\.venv\Scripts\python.exe -m pip install -r requirements.txt"
}

New-Item -ItemType Directory -Force -Path $ShareDir | Out-Null
$env:PAD_SHARE_DIR = $ShareDir

$LanIp = Get-NetIPAddress -AddressFamily IPv4 |
    Where-Object { $_.IPAddress -like "192.168.*" -and $_.InterfaceAlias -like "WLAN*" } |
    Select-Object -First 1 -ExpandProperty IPAddress

if (-not $LanIp) {
    $LanIp = "YOUR_PC_LAN_IP"
}

Write-Host ""
Write-Host "Pad file server is starting..."
Write-Host "Share folder: $ShareDir"
Write-Host "Pad URL:      http://$LanIp`:8765/"
Write-Host "Stop:         Ctrl+C"
Write-Host ""

& $Waitress --host=0.0.0.0 --port=8765 pad_transfer_server:app
