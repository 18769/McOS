@echo off
chcp 65001 > nul
echo ==========================================
echo       McOS 智慧廚房 - 系統啟動中...
echo ==========================================

:: 0. 檢查並清除佔用 Port 9999 的舊行程 (避免 Address already in use 錯誤)
echo [0/2] 正在清理可能殘留的 Port 9999...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :9999') do (
    if not "%%a"=="0" (
        taskkill /F /PID %%a > nul 2>&1
    )
)
timeout /t 1 /nobreak > nul

:: 1. 使用相對路徑啟動 Python 伺服器 (假設你的伺服器叫 server.py)
echo [1/2] 正在啟動 Python 後端伺服器...
start /B .\env\python_env\python.exe .\src\algo\scheduler.py

:: 等待 2 秒鐘讓 Python 伺服器綁定 Port 9999
timeout /t 2 /nobreak > nul

:: 2. 啟動 Java GUI 前端 (這會讀取 bin 資料夾內編譯好的 class，以及 lib 下的 json 庫)
echo [2/2] 正在啟動 Java GUI 前端...
.\env\jre\bin\java.exe -cp "bin;lib\*" KitchenGUI

echo 系統已關閉。
pause
