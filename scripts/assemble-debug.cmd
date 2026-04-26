@echo off
setlocal
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
call "%ROOT%\gradlew.bat" -p "%ROOT%" :TMessagesProj_App:assembleDebug --configuration-cache --configuration-cache-problems=warn %* --console=plain
set "EXIT_CODE=%errorlevel%"
if not defined CI if not defined LAWX_NO_PAUSE pause
exit /b %EXIT_CODE%
