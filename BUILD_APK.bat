@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle command not found.
  echo Open this folder in Android Studio and use Build ^> Build APK(s).
  pause
  exit /b 1
)
gradle assembleDebug
if errorlevel 1 exit /b %errorlevel%
echo.
echo APK: app\build\outputs\apk\debug\app-debug.apk
pause
