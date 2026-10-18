# ============================================
# HiddenWhite unified backend - local dev launcher
# Usage (PowerShell):
#   .\start-dev.ps1                          # default DB password 123456, port 8080
#   .\start-dev.ps1 -DbPassword "yourpass"   # custom MySQL password
# ============================================
param(
    [string]$DbPassword = "123456",
    [int]$Port = 8080,
    [string]$DbName = "hidden_white",
    [switch]$DisableDevMode
)

$ErrorActionPreference = "Stop"

# Persist JWT_SECRET to a local file (not committed): a fixed secret keeps
# previously issued admin tokens valid across restarts.
$secretFile = Join-Path $PSScriptRoot ".jwt-secret.local"
if (Test-Path $secretFile) {
    $jwt = (Get-Content $secretFile -Raw).Trim()
    if ($jwt.Length -lt 32) { $jwt = $null }
}
if (-not $jwt) {
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $jwt = ([System.BitConverter]::ToString($bytes) -replace '-', '').ToLower()
    Set-Content -Path $secretFile -Value $jwt -NoNewline -Encoding ASCII
    Write-Host "[start-dev] Generated new JWT_SECRET and saved to $secretFile" -ForegroundColor Yellow
}

$env:JWT_SECRET  = $jwt
$env:DB_NAME     = $DbName
$env:DB_PASSWORD = $DbPassword
$env:SERVER_PORT = $Port

# ============================================================
# 支付开发模式（本地默认开启）
# ------------------------------------------------------------
# 项目尚未持有微信支付凭据，开发阶段需要跑通完整订单流程，
# 故下单直接置为 PAID，省去「下单→待支付→等超时」的死循环。
#
# 本脚本只用于本地开发（profile=dev），非 dev profile 下开启会被
# PayDevModeGuard 阻断启动，因此不会波及生产。
#
# 需要测真实支付时用 -DisableDevMode 关闭：
#   .\start-dev.ps1 -DisableDevMode
# ============================================================
if ($DisableDevMode) {
    $env:HW_PAY_DEV_MODE = "false"
    Write-Host "[start-dev] Payment dev mode: OFF (real payment flow)" -ForegroundColor Yellow
} else {
    $env:HW_PAY_DEV_MODE = "true"
    Write-Host "[start-dev] Payment dev mode: ON (orders auto-marked PAID, no real payment)" -ForegroundColor Yellow
}

$jar = Join-Path $PSScriptRoot "target\hiddenwhite-server-0.2.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) {
    Write-Host "[start-dev] Jar not found: $jar - run first: mvnw -DskipTests package" -ForegroundColor Red
    exit 1
}

Write-Host "[start-dev] Starting unified backend: http://localhost:$Port" -ForegroundColor Green
java -jar $jar
