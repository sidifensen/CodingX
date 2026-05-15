@echo off
powershell -Command "Get-NetTCPConnection -LocalPort 5003 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }"
