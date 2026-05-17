$ErrorActionPreference = "Stop"

# 统一目录基线，确保任意启动路径都能正确找到 frontend/user。
$desktopDir = Split-Path -Parent $PSScriptRoot
$frontendDir = Split-Path -Parent $desktopDir
$userDir = Join-Path $frontendDir "user"

# 启动前关闭历史残留的 dev 服务，避免 5002 端口冲突。
$listenConnections = Get-NetTCPConnection -LocalPort 5002 -State Listen -ErrorAction SilentlyContinue
if ($listenConnections) {
  $occupiedProcessIds = $listenConnections | Select-Object -ExpandProperty OwningProcess -Unique
  foreach ($processId in $occupiedProcessIds) {
    try {
      Stop-Process -Id $processId -Force -ErrorAction Stop
      Write-Host "Stopped process occupying port 5002: PID=$processId"
    } catch {
      Write-Host "Failed to stop PID=${processId}: $($_.Exception.Message)"
    }
  }
}

Write-Host "Starting user dev server at $userDir ..."
$userProcess = Start-Process -FilePath "npm.cmd" `
  -ArgumentList "run", "dev" `
  -WorkingDirectory $userDir `
  -WindowStyle Hidden `
  -PassThru

Write-Host "Waiting for dev server port 5002 ..."
$deadline = (Get-Date).AddMinutes(2)
$serverReady = $false
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Milliseconds 500
  $listenState = Get-NetTCPConnection -LocalPort 5002 -State Listen -ErrorAction SilentlyContinue
  if ($listenState) {
    $serverReady = $true
    break
  }
  if ($userProcess.HasExited) {
    throw "User dev server process exited early. PID=$($userProcess.Id)"
  }
}

if (-not $serverReady) {
  throw "User dev server did not become ready within 2 minutes."
}

Write-Host "Starting Electron host ..."
Set-Location $desktopDir
npm run start
