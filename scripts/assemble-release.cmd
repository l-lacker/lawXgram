@echo off
setlocal
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME if defined LOCALAPPDATA if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_SDK_ROOT if defined ANDROID_HOME set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
call "%ROOT%\gradlew.bat" -p "%ROOT%" :TMessagesProj_App:assembleRelease --configuration-cache --configuration-cache-problems=fail %* --console=plain
set "EXIT_CODE=%errorlevel%"
if not defined CI if not defined LAWX_NO_PAUSE pause
exit /b %EXIT_CODE%
