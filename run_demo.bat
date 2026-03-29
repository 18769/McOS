@echo off
title McOS 智慧廚房一鍵啟動工具 [套餐&外帶優化版]
chcp 65001 > nul

echo [1/4] 正在清理 Port 9999 佔用...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :9999') do (
    if NOT "%%a"=="0" (
        echo 發現舊進程 PID: %%a，正在強制結束...
        taskkill /F /PID %%a >nul 2>nul
    )
)

echo [2/4] 正在編譯 Java 程式碼 (含 JSON 函式庫)...
:: 確保你的 json-20240303.jar 檔名正確，若有更換請修改此處
javac -encoding utf-8 -cp "%~dp0json-20240303.jar;." KitchenGUI.java
if %errorlevel% neq 0 (
    echo [錯誤] Java 編譯失敗！請確認是否缺少 json 函式庫或程式碼有誤。
    pause
    exit /b
)

echo [3/4] 正在啟動 Python 排程核心 (scheduler.py)...
:: 使用 start 啟動新視窗執行 Python，方便你觀察後端的「套餐拆解」與「自動打包」訊息
start "McOS_Python_Engine" python "%~dp0scheduler.py"

echo [4/4] 正在開啟 Java 智慧廚房管理介面...
echo --------------------------------------------------
echo 提示：請先勾選「外帶模式」再點選套餐測試優化邏輯。
echo --------------------------------------------------
java -Dfile.encoding=UTF-8 -cp "%~dp0json-20240303.jar;." KitchenGUI

echo --------------------------------------------------
echo [結束] 介面已關閉，正在清理背景程序...
:: 改用 taskkill 標籤過濾，避免誤殺其他 python 程式
taskkill /FI "WINDOWTITLE eq McOS_Python_Engine*" /F /T 2>nul
echo 系統已安全關閉。
pause