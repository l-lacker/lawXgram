@echo off
setlocal
call "%~dp0..\gradlew.bat" :TMessagesProj_App:assembleRelease --no-configuration-cache %*
exit /b %errorlevel%
