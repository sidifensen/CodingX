param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [string]$OutputDir = '',
    [switch]$Clean,
    [switch]$SkipTests,
    [switch]$Install,
    [string]$InstallRoot = ''
)

$ErrorActionPreference = 'Stop'

$projectRootPath = (Resolve-Path -LiteralPath $ProjectRoot).Path
$cliRoot = Join-Path $projectRootPath 'cli'
$jarPath = Join-Path $cliRoot 'target\codingx.jar'
$defaultOutputDir = Join-Path $cliRoot 'target\dist'
$artifactDir = if ([string]::IsNullOrWhiteSpace($OutputDir)) { $defaultOutputDir } else { $OutputDir }
$resolvedArtifactDir = [System.IO.Path]::GetFullPath($artifactDir)
$artifactJar = Join-Path $resolvedArtifactDir 'codingx.jar'
$checksumPath = Join-Path $resolvedArtifactDir 'codingx.jar.sha256'

if ([string]::IsNullOrWhiteSpace($InstallRoot)) {
    $userHome = if (-not [string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
        $env:USERPROFILE
    } elseif (-not [string]::IsNullOrWhiteSpace($env:HOME)) {
        $env:HOME
    } else {
        [Environment]::GetFolderPath('UserProfile')
    }
    $InstallRoot = Join-Path (Join-Path $userHome '.codingx') 'bin'
}

if (-not (Test-Path -LiteralPath $cliRoot)) {
    throw "未找到 CLI 目录: $cliRoot"
}

$mavenArgs = @()
if ($Clean) {
    $mavenArgs += 'clean'
}
$mavenArgs += 'package'
if ($SkipTests) {
    $mavenArgs += '-DskipTests'
}

Push-Location $cliRoot
try {
    # CI 和本地安装共用同一条 Maven 打包链路，确保 target/codingx.jar 与发布产物一致。
    & mvn @mavenArgs
    if ($LASTEXITCODE -ne 0) {
        throw "CLI Maven 打包失败，退出码: $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $jarPath)) {
    throw "未生成 CLI 可执行 jar: $jarPath"
}

New-Item -ItemType Directory -Path $resolvedArtifactDir -Force | Out-Null
Copy-Item -LiteralPath $jarPath -Destination $artifactJar -Force

$hash = Get-FileHash -LiteralPath $artifactJar -Algorithm SHA256
Set-Content -Path $checksumPath -Value "$($hash.Hash.ToLowerInvariant())  codingx.jar" -Encoding ASCII

Write-Host "CodingX CLI 打包完成"
Write-Host "产物: $artifactJar"
Write-Host "校验: $checksumPath"

if ($Install) {
    $installedJar = Join-Path $InstallRoot 'codingx.jar'
    $cmdLauncher = Join-Path $InstallRoot 'codingx.cmd'
    $psLauncher = Join-Path $InstallRoot 'codingx.ps1'

    New-Item -ItemType Directory -Path $InstallRoot -Force | Out-Null
    Copy-Item -LiteralPath $artifactJar -Destination $installedJar -Force

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
    $alreadyInPath = $pathParts | Where-Object { $_.TrimEnd('\') -ieq $InstallRoot.TrimEnd('\') }
    if (-not $alreadyInPath) {
        $newUserPath = if ([string]::IsNullOrWhiteSpace($userPath)) {
            $InstallRoot
        } else {
            $userPath.TrimEnd(';') + ';' + $InstallRoot
        }
        [Environment]::SetEnvironmentVariable('Path', $newUserPath, 'User')
    }

    # 当前 PowerShell 进程也补 PATH，方便安装后立刻验证 `codingx`。
    $currentParts = $env:Path -split ';' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    $currentHasBin = $currentParts | Where-Object { $_.TrimEnd('\') -ieq $InstallRoot.TrimEnd('\') }
    if (-not $currentHasBin) {
        $env:Path = $env:Path.TrimEnd(';') + ';' + $InstallRoot
    }

    Write-Host "CodingX CLI 已安装到: $InstallRoot"
    Write-Host "新终端可直接运行: codingx"
}
