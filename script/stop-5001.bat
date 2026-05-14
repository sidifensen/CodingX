@echo off
powershell -Command "Get-NetTCPConnection -LocalPort 5001 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }"
