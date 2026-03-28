@echo off
title 智慧廚房一鍵啟動工具
chcp 65001 > nul

echo [1/4] 正在清理舊的 Python 進程 (Port 9999)...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :9999') do taskkill /F /PID %%a 2>nul

echo [2/4] 正在編譯 Java 程式碼...
javac -encoding utf-8 KitchenGUI.java
if %errorlevel% neq 0 (
    echo [錯誤] Java 編譯失敗，請檢查程式碼！
    pause
    exit /b
)

echo [3/4] 正在背景啟動 Python 排程引擎...
start /b python scheduler.py

echo [4/4] 啟動 Java 模擬畫面...
echo ---------------------------------------
java -Dfile.encoding=UTF-8 KitchenGUI

echo ---------------------------------------
echo 視窗已關閉，正在結束所有相關程序...
taskkill /F /IM python.exe /T 2>nul
pause