@echo off
setlocal
where adb >nul 2>&1
if errorlevel 1 (
  echo Run this from your Windows platform-tools folder, with USB debugging connected.
  exit /b 1
)
adb get-state >nul 2>&1
if errorlevel 1 exit /b 1
set "FEEDBACK_DIR=HeckerOS-feedback"
if not exist "%FEEDBACK_DIR%" mkdir "%FEEDBACK_DIR%"
(
  adb shell getprop ro.build.display.id
  adb shell getprop ro.heckeros.suite_revision
  adb shell getprop ro.heckeros.manifest_revision
  adb shell getprop ro.hecker.app_compatibility
  adb shell getprop init.svc.heckervpnd
  adb shell getenforce
  adb shell ls -lZ /dev/socket/heckervpnd
) > "%FEEDBACK_DIR%\build-services.txt" 2>&1
adb shell dumpsys package app.com.brd | findstr /C:"versionName=" /C:"versionCode=" > "%FEEDBACK_DIR%\ubl-version.txt"
echo Opening UBL to capture this launch. Do not sign in during the capture.
adb shell am force-stop app.com.brd
adb shell monkey -p app.com.brd -c android.intent.category.LAUNCHER 1 >nul 2>&1
timeout /t 12 /nobreak >nul
adb logcat -d -b crash -v threadtime > "%FEEDBACK_DIR%\ubl-crash.txt"
adb shell dumpsys activity exit-info app.com.brd > "%FEEDBACK_DIR%\ubl-exit-info.txt"
adb logcat -d -v threadtime -s AndroidRuntime:E HeckerAppCompatibility:V HeckerHeadphones:V auditd:W > "%FEEDBACK_DIR%\system-errors.txt"
echo Saved logs in %FEEDBACK_DIR%. Review them before sharing.
endlocal
