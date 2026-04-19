@echo off
setlocal enabledelayedexpansion

set "PROJECT_ROOT=%~dp0"
set "SRC_DIR=%PROJECT_ROOT%src"
set "BIN_DIR=%PROJECT_ROOT%bin"
set "LIB_DIR=%PROJECT_ROOT%lib"
:: 這裡先定義好相對於根目錄的 JAR 路徑
set "JAR_PATH=%LIB_DIR%\json-20240303.jar"

title McOS 智慧廚房 - 實驗啟動器
chcp 65001 >nul

echo ==========================================
echo     McOS 智慧廚房 - 系統啟動中...
echo ==========================================
echo.

echo [1/5] 檢查環境...
java -version >nul 2>&1
if !errorlevel! neq 0 (echo ✗ 找不到 Java && pause && exit /b 1)

echo [2/5] 編譯 Java 程式碼...
if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"

:: 修正點：使用 pushd 進入資料夾，避免在引號路徑中使用 *
pushd "%SRC_DIR%\gui"
javac -encoding UTF-8 -cp "%JAR_PATH%;%BIN_DIR%" -d "%BIN_DIR%" *.java
popd

if !errorlevel! neq 0 (
    echo ✗ Java 編譯失敗！
    pause
    exit /b 1
)

echo [3/5] 清理 Port 9999...
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr :9999') do (
    taskkill /F /PID %%a >nul 2>&1
)

echo [4/5] 啟動 Python 核心引擎...
:: 啟動 engine.py (記得這會處理 Aging 和 SJF 算法)
start "McOS_Engine" python "%SRC_DIR%\engine.py"
timeout /t 2 /nobreak >nul

echo [5/5] 啟動 McOS 主視窗...
echo ==========================================
cd /d "%PROJECT_ROOT%"
:: 執行時 CLASSPATH 必須包含 bin 和 jar
java -Dfile.encoding=UTF-8 -cp "%BIN_DIR%;%JAR_PATH%" gui.KitchenGUI

pause