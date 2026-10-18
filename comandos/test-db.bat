@echo off
cd /d "%~dp0..\backend"
call mvnw.cmd -Dtest=DatabaseConnectionTest test
popd
pause