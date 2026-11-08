@echo off
setlocal
set "PATCHSET_PY=python"
where py >nul 2>nul && set "PATCHSET_PY=py -3"
%PATCHSET_PY% "%~dp0tools\patchset.py" %*
exit /b %ERRORLEVEL%
