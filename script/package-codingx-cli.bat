@echo off
setlocal EnableExtensions
chcp 65001 >nul

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "PROJECT_ROOT=%%~fI"
set "OUTPUT_DIR="
set "CLEAN=0"
set "SKIP_TESTS=0"
set "INSTALL=0"
set "INSTALL_ROOT="

:parse_args
if "%~1"=="" goto after_parse
if /I "%~1"=="--project-root" (
    set "PROJECT_ROOT=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="-ProjectRoot" (
    set "PROJECT_ROOT=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="--output-dir" (
    set "OUTPUT_DIR=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="-OutputDir" (
    set "OUTPUT_DIR=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="--install-root" (
    set "INSTALL_ROOT=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="-InstallRoot" (
    set "INSTALL_ROOT=%~2"
    shift
    shift
    goto parse_args
)
if /I "%~1"=="--clean" (
    set "CLEAN=1"
    shift
    goto parse_args
)
if /I "%~1"=="-Clean" (
    set "CLEAN=1"
    shift
    goto parse_args
)
if /I "%~1"=="--skip-tests" (
    set "SKIP_TESTS=1"
    shift
    goto parse_args
)
if /I "%~1"=="-SkipTests" (
    set "SKIP_TESTS=1"
    shift
    goto parse_args
)
if /I "%~1"=="--install" (
    set "INSTALL=1"
    shift
    goto parse_args
)
if /I "%~1"=="-Install" (
    set "INSTALL=1"
    shift
    goto parse_args
)
echo Unknown argument: %~1
exit /b 2

:after_parse
for %%I in ("%PROJECT_ROOT%") do set "PROJECT_ROOT=%%~fI"
set "CLI_ROOT=%PROJECT_ROOT%\cli"
set "JAR_PATH=%CLI_ROOT%\target\codingx.jar"
if not defined OUTPUT_DIR set "OUTPUT_DIR=%CLI_ROOT%\target\dist"
for %%I in ("%OUTPUT_DIR%") do set "ARTIFACT_DIR=%%~fI"
set "ARTIFACT_JAR=%ARTIFACT_DIR%\codingx.jar"
set "CHECKSUM_PATH=%ARTIFACT_DIR%\codingx.jar.sha256"

if not defined INSTALL_ROOT (
    if defined USERPROFILE (
        set "INSTALL_ROOT=%USERPROFILE%\.codingx\bin"
    ) else if defined HOMEDRIVE (
        set "INSTALL_ROOT=%HOMEDRIVE%%HOMEPATH%\.codingx\bin"
    ) else (
        set "INSTALL_ROOT=%CD%\.codingx\bin"
    )
)
for %%I in ("%INSTALL_ROOT%") do set "INSTALL_ROOT=%%~fI"

if not exist "%CLI_ROOT%\" (
    echo CLI directory not found: %CLI_ROOT%
    exit /b 1
)

set "MAVEN_ARGS=package"
if "%CLEAN%"=="1" set "MAVEN_ARGS=clean package"
if "%SKIP_TESTS%"=="1" set "MAVEN_ARGS=%MAVEN_ARGS% -DskipTests"

pushd "%CLI_ROOT%" || exit /b 1
rem CI and local install share the same Maven package pipeline.
call mvn %MAVEN_ARGS%
set "MAVEN_EXIT=%ERRORLEVEL%"
popd
if not "%MAVEN_EXIT%"=="0" (
    echo CLI Maven package failed, exit code: %MAVEN_EXIT%
    exit /b %MAVEN_EXIT%
)

if not exist "%JAR_PATH%" (
    echo CLI executable jar was not generated: %JAR_PATH%
    exit /b 1
)

if not exist "%ARTIFACT_DIR%\" mkdir "%ARTIFACT_DIR%" || exit /b 1
copy /Y "%JAR_PATH%" "%ARTIFACT_JAR%" >nul || exit /b 1

set "HASH="
for /f "skip=1 tokens=* delims=" %%H in ('certutil -hashfile "%ARTIFACT_JAR%" SHA256 2^>nul') do (
    if not defined HASH set "HASH=%%H"
)
if not defined HASH (
    echo Failed to calculate SHA256: %ARTIFACT_JAR%
    exit /b 1
)
> "%CHECKSUM_PATH%" echo %HASH%  codingx.jar

echo CodingX CLI package completed
echo Artifact: %ARTIFACT_JAR%
echo Checksum: %CHECKSUM_PATH%

if not "%INSTALL%"=="1" exit /b 0

set "INSTALLED_JAR=%INSTALL_ROOT%\codingx.jar"
set "CMD_LAUNCHER=%INSTALL_ROOT%\codingx.cmd"
set "PS_LAUNCHER=%INSTALL_ROOT%\codingx.ps1"

if not exist "%INSTALL_ROOT%\" mkdir "%INSTALL_ROOT%" || exit /b 1
copy /Y "%ARTIFACT_JAR%" "%INSTALLED_JAR%" >nul || exit /b 1

rem Windows cmd launcher for where codingx and regular terminals.
> "%CMD_LAUNCHER%" echo @echo off
>> "%CMD_LAUNCHER%" echo setlocal
>> "%CMD_LAUNCHER%" echo set "CODINGX_JAR=%%~dp0codingx.jar"
>> "%CMD_LAUNCHER%" echo java -jar "%%CODINGX_JAR%%" %%*
>> "%CMD_LAUNCHER%" echo exit /b %%ERRORLEVEL%%

rem PowerShell launcher with the same argument semantics as codingx.cmd.
> "%PS_LAUNCHER%" echo $ErrorActionPreference = 'Stop'
>> "%PS_LAUNCHER%" echo $jar = Join-Path $PSScriptRoot 'codingx.jar'
>> "%PS_LAUNCHER%" echo ^& java -jar $jar @args
>> "%PS_LAUNCHER%" echo exit $LASTEXITCODE

set "USER_PATH="
for /f "tokens=2,*" %%A in ('reg query HKCU\Environment /v Path 2^>nul ^| findstr /I "Path"') do set "USER_PATH=%%B"
echo ;%USER_PATH%; | find /I ";%INSTALL_ROOT%;" >nul
if errorlevel 1 (
    if defined USER_PATH (
        set "NEW_USER_PATH=%USER_PATH%;%INSTALL_ROOT%"
    ) else (
        set "NEW_USER_PATH=%INSTALL_ROOT%"
    )
    setx Path "%NEW_USER_PATH%" >nul
)

echo ;%PATH%; | find /I ";%INSTALL_ROOT%;" >nul
if errorlevel 1 set "PATH=%PATH%;%INSTALL_ROOT%"

echo CodingX CLI installed to: %INSTALL_ROOT%
echo Open a new terminal and run: codingx
exit /b 0
