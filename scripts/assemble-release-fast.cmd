@echo off
setlocal
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
call "%ROOT%\gradlew.bat" -p "%ROOT%" :TMessagesProj_App:assembleRelease --configuration-cache %*
exit /b %errorlevel%
