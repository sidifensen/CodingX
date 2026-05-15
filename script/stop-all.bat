@echo off
powershell -Command "Get-NetTCPConnection -LocalPort 5001,5002,5003 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }"
