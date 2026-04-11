@echo off
setlocal
call "%~dp0..\gradlew.bat" :TMessagesProj_App:assembleDebug --configuration-cache --configuration-cache-problems=warn %*
exit /b %errorlevel%
