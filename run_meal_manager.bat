@echo off
REM McOS - 餐點管理 GUI
REM 用於 Teammate 管理資料庫

setlocal enabledelayedexpansion

set "PROJECT_ROOT=%~dp0"
set "SRC_DIR=%PROJECT_ROOT%src"
set "BIN_DIR=%PROJECT_ROOT%bin"
set "LIB_DIR=%PROJECT_ROOT%lib"
set "CLASSPATH=%LIB_DIR%\json-20240303.jar"

echo === McOS 餐點管理系統 ===
echo.

REM 清理舊的編譯文件
echo [1/3] 清理舊的編譯文件...
if exist "%BIN_DIR%" rmdir /s /q "%BIN_DIR%"
mkdir "%BIN_DIR%"

REM 編譯 Java 源文件
echo [2/3] 編譯 Java 源文件...
cd "%SRC_DIR%"
javac -encoding UTF-8 -cp "%CLASSPATH%" -d "%BIN_DIR%" db\DBrequest.java gui\MealManagerGUI.java

if !errorlevel! neq 0 (
    echo ✗ 編譯失敗！
    pause
    exit /b 1
)

echo ✓ 編譯成功！
echo.
echo [3/3] 啟動 餐點管理系統...
cd "%PROJECT_ROOT%"
java -cp "%BIN_DIR%;%CLASSPATH%" gui.MealManagerGUI

pause
