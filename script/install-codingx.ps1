param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path,
    [switch]$Clean,
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

# 兼容旧入口：本地安装也走统一打包脚本，避免 CI 产物和用户目录 jar 两套逻辑分叉。
& (Join-Path $PSScriptRoot 'package-codingx-cli.ps1') `
    -ProjectRoot $ProjectRoot `
    -Clean:$Clean `
    -SkipTests:$SkipTests `
    -Install
