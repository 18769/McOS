@echo off
REM McOS - 示範模式 (使用專案內的 env)
REM 使用專案內的 JRE 和 Python 環境

setlocal enabledelayedexpansion

set "PROJECT_ROOT=%~dp0"
set "SRC_DIR=%PROJECT_ROOT%src"
set "BIN_DIR=%PROJECT_ROOT%bin"
set "LIB_DIR=%PROJECT_ROOT%lib"
set "ENV_DIR=%PROJECT_ROOT%env"
set "JDK_DIR=%ENV_DIR%\jdk"
set "JAVA_PATH=%JDK_DIR%\bin\java.exe"
set "JAVAC_PATH=%JDK_DIR%\bin\javac.exe"
set "PYTHON_PATH=%ENV_DIR%\python_env\python.exe"
set "CLASSPATH=%BIN_DIR%;%LIB_DIR%\json-20240303.jar"

chcp 65001 > nul
echo ==========================================
echo    McOS 智慧廚房 - 示範模式啟動中...
echo ==========================================
echo.


:: 檢查 JDK 是否存在
if not exist "%JAVA_PATH%" (
    echo ERROR: JDK java not found
    echo Path: %JAVA_PATH%
    pause
    exit /b 1
)

:: 檢查 JDK (javac) 是否存在
if not exist "%JAVAC_PATH%" (
    echo ERROR: JDK javac not found
    echo Path: %JAVAC_PATH%
    pause
    exit /b 1
)

:: 檢查 Python 是否存在
if not exist "%PYTHON_PATH%" (
    echo ✗ 錯誤：專案內的 Python 不存在！
    echo 路徑：%PYTHON_PATH%
    pause
    exit /b 1
)

:: 編譯 Java 程式碼
echo [1/3] Compile Java sources...
if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"

pushd "%SRC_DIR%"
"%JAVAC_PATH%" -encoding UTF-8 -cp "%LIB_DIR%\json-20240303.jar;%BIN_DIR%" -d "%BIN_DIR%" db\*.java gui\*.java
popd

if !errorlevel! neq 0 (
    echo ✗ Java 編譯失敗！
    pause
    exit /b 1
)

:: 清除佔用的 Port
echo [2/4] 清理可能殘留的行程...
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr :9999') do (
    if not "%%a"=="0" (
        taskkill /F /PID %%a >nul 2>&1
    )
)
timeout /t 1 /nobreak >nul

:: 啟動 Python 排程引擎
echo [3/4] 啟動 Python 排程引擎...
start "" "%PYTHON_PATH%" "%SRC_DIR%\algo\scheduler.py"
timeout /t 2 /nobreak >nul

:: 啟動 Java GUI
echo [4/4] 啟動 Java GUI 前端...
echo ==========================================
cd "%PROJECT_ROOT%"
"%JAVA_PATH%" -cp "%CLASSPATH%" gui.KitchenGUI

echo.
echo 系統已關閉。
pause
