param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$cliRoot = Join-Path $ProjectRoot 'cli'
$jarPath = Join-Path $cliRoot 'target\codingx.jar'
$installRoot = Join-Path $env:USERPROFILE '.codingx\bin'
$installedJar = Join-Path $installRoot 'codingx.jar'
$cmdLauncher = Join-Path $installRoot 'codingx.cmd'
$psLauncher = Join-Path $installRoot 'codingx.ps1'

if (-not (Test-Path $cliRoot)) {
    throw "未找到 CLI 目录: $cliRoot"
}

Push-Location $cliRoot
try {
    mvn -q package
} finally {
    Pop-Location
}

if (-not (Test-Path $jarPath)) {
    throw "未生成 CLI 可执行 jar: $jarPath"
}

New-Item -ItemType Directory -Path $installRoot -Force | Out-Null
Copy-Item -Path $jarPath -Destination $installedJar -Force

# Windows cmd 入口用于 `where codingx` 和普通终端；只负责转发参数到可执行 jar。
$cmdContent = @'
@echo off
setlocal
set "CODINGX_JAR=%USERPROFILE%\.codingx\bin\codingx.jar"
java -jar "%CODINGX_JAR%" %*
exit /b %ERRORLEVEL%
'@
Set-Content -Path $cmdLauncher -Value $cmdContent -Encoding ASCII

# PowerShell 入口用于显式执行 codingx.ps1；保持和 cmd 入口相同的参数语义。
$psContent = @'
$ErrorActionPreference = 'Stop'
$jar = Join-Path $PSScriptRoot 'codingx.jar'
& java -jar $jar @args
exit $LASTEXITCODE
'@
Set-Content -Path $psLauncher -Value $psContent -Encoding UTF8

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$pathParts = @()
if (-not [string]::IsNullOrWhiteSpace($userPath)) {
    $pathParts = $userPath -split ';' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
}
$alreadyInPath = $pathParts | Where-Object { $_.TrimEnd('\') -ieq $installRoot.TrimEnd('\') }
if (-not $alreadyInPath) {
    $newUserPath = if ([string]::IsNullOrWhiteSpace($userPath)) {
        $installRoot
    } else {
        $userPath.TrimEnd(';') + ';' + $installRoot
    }
    [Environment]::SetEnvironmentVariable('Path', $newUserPath, 'User')
}

# 当前 PowerShell 进程也补 PATH，方便安装后立刻验证 `codingx`。
$currentParts = $env:Path -split ';' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
$currentHasBin = $currentParts | Where-Object { $_.TrimEnd('\') -ieq $installRoot.TrimEnd('\') }
if (-not $currentHasBin) {
    $env:Path = $env:Path.TrimEnd(';') + ';' + $installRoot
}

Write-Host "CodingX CLI 已安装到: $installRoot"
Write-Host "新终端可直接运行: codingx"
