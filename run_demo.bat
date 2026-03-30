@echo off
<<<<<<< Updated upstream
title 智慧廚房一鍵啟動工具
chcp 65001 > nul

echo [1/4] 正在清理舊的 Python 進程 (Port 9999)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :9999') do taskkill /F /PID %%a 2>nul

echo [2/4] 正在編譯 Java 程式碼...
javac -encoding utf-8 -cp ".;json-20240303.jar" KitchenGUI.java
if %errorlevel% neq 0 (
    echo [錯誤] Java 編譯失敗，請檢查程式碼！
=======
title McOS Smart Kitchen Launcher
echo [1/4] Cleaning Port 9999...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :9999') do (
    if NOT "%%a"=="0" (
        echo Found old PID: %%a, killing process...
        taskkill /F /PID %%a >nul 2>nul
    )
)

echo [2/4] Compiling Java source (including JSON lib)...
javac -d bin -encoding utf-8 -cp "%~dp0lib\json-20240303.jar" src\KitchenGUI.java
if %errorlevel% neq 0 (
    echo [ERROR] Java compilation failed!
>>>>>>> Stashed changes
    pause
    exit /b
)

<<<<<<< Updated upstream
echo [3/4] 正在背景啟動 Python 排程引擎...
start /b python scheduler.py

echo [4/4] 啟動 Java 模擬畫面...
echo ---------------------------------------
java -Dfile.encoding=UTF-8 -cp ".;json-20240303.jar" KitchenGUI

echo ---------------------------------------
echo 視窗已關閉，正在結束所有相關程序...
taskkill /F /IM python.exe /T 2>nul
=======
echo [3/4] Starting Python Scheduler...
start "McOS_Python_Engine" python "%~dp0src\algo\scheduler.py"

echo [4/4] Starting Java KitchenGUI Interface...
echo --------------------------------------------------
echo Tip: Check 'Takeout Mode' then click meals to test logic.
echo --------------------------------------------------
java -Dfile.encoding=UTF-8 -cp "%~dp0lib\json-20240303.jar;%~dp0bin" KitchenGUI

echo --------------------------------------------------
echo UI Closed. Cleaning background processes...
taskkill /FI "WINDOWTITLE eq McOS_Python_Engine*" /F /T 2>nul
echo System shutdown successfully.
>>>>>>> Stashed changes
pause