@echo off
setlocal EnableExtensions
chcp 65001 >nul

rem Cmd install entry: delegate to the unified BAT package script.
call "%~dp0package-codingx-cli.bat" %* --install
exit /b %ERRORLEVEL%
