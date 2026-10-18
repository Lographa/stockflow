@echo off
pushd "%~dp0..\backend"
call mvnw.cmd clean
popd
pause
