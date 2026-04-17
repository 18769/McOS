@echo off
REM McOS - 使用系統 Java 和 Python 環境
REM 無需 build_and_run.bat，直接編譯和執行

setlocal enabledelayedexpansion

set "PROJECT_ROOT=%~dp0"
set "SRC_DIR=%PROJECT_ROOT%src"
set "BIN_DIR=%PROJECT_ROOT%bin"
set "LIB_DIR=%PROJECT_ROOT%lib"
set "CLASSPATH=%BIN_DIR%;%LIB_DIR%\json-20240303.jar"

title McOS 智慧廚房
chcp 65001 >nul

echo ==========================================
echo    McOS 智慧廚房 - 系統啟動中...
echo ==========================================
echo.

:: 檢查系統中是否有 Java
echo [1/3] 檢查 Java 環境...
java -version >nul 2>&1
if !errorlevel! neq 0 (
    echo ✗ 錯誤：系統未安裝 Java！
    echo 請安裝 Java 11 或更高版本，或使用 run_demo.bat
    pause
    exit /b 1
)

:: 編譯 Java 程式碼
echo [2/3] 編譯 Java 程式碼...
if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"
cd "%SRC_DIR%"
javac -encoding UTF-8 -cp "%LIB_DIR%\json-20240303.jar" -d "%BIN_DIR%" db\DBHelper.java gui\KitchenGUI.java

if !errorlevel! neq 0 (
    echo ✗ Java 編譯失敗！
    pause
    exit /b 1
)

:: 清除佔用的 Port
echo [3/4] 清理可能殘留的行程...
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr :9999') do (
    if not "%%a"=="0" (
        taskkill /F /PID %%a >nul 2>&1
    )
)
timeout /t 1 /nobreak >nul

:: 啟動 Python 排程引擎
echo [4/5] 啟動 Python 排程引擎...
start "" python "%SRC_DIR%\algo\scheduler.py"
timeout /t 2 /nobreak >nul

:: 啟動 Java GUI
echo [5/5] 啟動 Java GUI 前端...
echo ==========================================
cd "%PROJECT_ROOT%"
java -Dfile.encoding=UTF-8 -cp "%CLASSPATH%" gui.KitchenGUI

echo.
echo 系統已關閉。
pause