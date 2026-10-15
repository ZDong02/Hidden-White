# ============================================
# HiddenWhite unified backend - local dev launcher
# Usage (PowerShell):
#   .\start-dev.ps1                          # default DB password 123456, port 8080
#   .\start-dev.ps1 -DbPassword "yourpass"   # custom MySQL password
# ============================================
param(
    [string]$DbPassword = "123456",
    [int]$Port = 8080,
    [string]$DbName = "hidden_white"
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

$jar = Join-Path $PSScriptRoot "target\hiddenwhite-server-0.2.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) {
    Write-Host "[start-dev] Jar not found: $jar - run first: mvnw -DskipTests package" -ForegroundColor Red
    exit 1
}

Write-Host "[start-dev] Starting unified backend: http://localhost:$Port" -ForegroundColor Green
java -jar $jar
