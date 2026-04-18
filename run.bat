@echo off
setlocal enabledelayedexpansion

set "PROJECT_ROOT=%~dp0"
set "SRC_DIR=%PROJECT_ROOT%src"
set "BIN_DIR=%PROJECT_ROOT%bin"
set "LIB_DIR=%PROJECT_ROOT%lib"
set "CLASSPATH=%BIN_DIR%;%LIB_DIR%\json-20240303.jar"

title McOS 智慧廚房
:: 建議改用 65001 (UTF-8) 並確保 .bat 檔案本身也是以 UTF-8 儲存
chcp 65001 >nul

echo ==========================================
echo    McOS 智慧廚房 - 系統啟動中...
echo ==========================================
echo.

echo [1/5] 檢查 Java 環境...
java -version >nul 2>&1
if !errorlevel! neq 0 (
    echo ✗ 錯誤：系統未安裝 Java！
    pause
    exit /b 1
)

echo [2/5] 編譯 Java 程式碼...
if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"

:: 先進入 gui 資料夾，避開長路徑引號與通配符衝突
pushd "%SRC_DIR%\gui"
javac -encoding UTF-8 -cp "%LIB_DIR%\json-20240303.jar;%BIN_DIR%" -d "%BIN_DIR%" *.java
popd

if !errorlevel! neq 0 (
    echo ✗ Java 編譯失敗！
    pause
    exit /b 1
)

echo [3/5] 清理可能殘留的行程 (Port 9999)...
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr :9999') do (
    taskkill /F /PID %%a >nul 2>&1
)
timeout /t 1 /nobreak >nul

echo [4/5] 啟動 Python 排程引擎...
:: 【修正點】確保路徑正確
start "" python "%SRC_DIR%\algo\scheduler.py"
timeout /t 2 /nobreak >nul

echo [5/5] 啟動 Java GUI 前端...
echo ==========================================
cd /d "%PROJECT_ROOT%"
:: 【提醒】這裡使用 gui.KitchenGUI 前提是你的 Java 檔內有寫 package gui;
java -Dfile.encoding=UTF-8 -cp "%CLASSPATH%" gui.KitchenGUI

echo.
echo 系統已關閉。
pause