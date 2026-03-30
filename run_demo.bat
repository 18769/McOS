@echo off
title McOS 智慧廚房一鍵啟動工具
chcp 65001 > nul

echo [1/4] 正在清理舊的連接埠 9999 (Python 進程)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :9999') do (
    if NOT "%%a"=="0" (
        taskkill /F /PID %%a >nul 2>nul
    )
)

echo [2/4] 正在編譯 Java 程式碼 (包含 JSON 函式庫)...
:: 建立 bin 資料夾以保持整潔
if not exist "bin" mkdir bin
javac -d bin -encoding utf-8 -cp "lib\json-20240303.jar;." src\KitchenGUI.java

if %errorlevel% neq 0 (
    echo [錯誤] Java 編譯失敗，請檢查程式碼或 lib 路徑！
    pause
    exit /b
)

echo [3/4] 正在背景啟動 Python 排程引擎...
start "McOS_Python_Engine" /b python "src\algo\scheduler.py"

echo [4/4] 啟動 Java 模擬畫面...
echo --------------------------------------------------
echo 提示：勾選「外送模式」後點擊餐點以測試邏輯。
echo --------------------------------------------------
:: 執行時需包含 bin 資料夾與 jar 包
java -Dfile.encoding=UTF-8 -cp "lib\json-20240303.jar;bin" KitchenGUI

echo --------------------------------------------------
echo 視窗已關閉，正在結束所有相關程序...
taskkill /FI "WINDOWTITLE eq McOS_Python_Engine*" /F /T 2>nul
echo 系統已成功關閉。
pause