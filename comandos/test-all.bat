@echo off
pushd "%~dp0..\backend"
call mvnw.cmd test
popd
pause
