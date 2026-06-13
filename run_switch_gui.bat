@echo off
setlocal enabledelayedexpansion

set "PROJECT_ROOT=%~dp0"
set "SRC_DIR=%PROJECT_ROOT%src"
set "BIN_DIR=%PROJECT_ROOT%bin"
set "LIB_DIR=%PROJECT_ROOT%lib"
set "ENV_DIR=%PROJECT_ROOT%env"

echo ==========================================
echo    McOS - GUI Integrated Launcher
echo ==========================================
echo.

:: Detect and use embedded JDK if present, otherwise fall back to system commands
set "JDK_DIR=%ENV_DIR%\jdk"
if exist "%JDK_DIR%\bin\java.exe" (
    set "JAVA_CMD=%JDK_DIR%\bin\java.exe"
    set "JAVAC_CMD=%JDK_DIR%\bin\javac.exe"
    echo [*] Using embedded JDK: %JDK_DIR%
) else (
    set "JAVA_CMD=java"
    set "JAVAC_CMD=javac"
    echo [*] Using system JDK from PATH
)

:: Detect and use embedded Python if present, otherwise fall back to system commands
set "PYTHON_PATH=%ENV_DIR%\python_env\python.exe"
if not exist "%PYTHON_PATH%" (
    set "PYTHON_PATH=python"
    echo [*] Using system Python from PATH
) else (
    echo [*] Using embedded Python: %PYTHON_PATH%
)

:: Configure OpenJFX DLL path if present
set "DLL_DIR=%ENV_DIR%\openjfx_dll"
if exist "%DLL_DIR%" (
    echo [*] Setting JavaFX DLL path: %DLL_DIR%
    set "PATH=%DLL_DIR%;%PATH%"
    set "JAVA_OPTS=-Djavafx.native.path=%DLL_DIR% -Djava.library.path=%DLL_DIR%"
) else (
    set "JAVA_OPTS="
)

if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"

echo [1/3] Compile Java source (db + gui)...
pushd "%SRC_DIR%"
"%JAVAC_CMD%" -encoding UTF-8 -cp "%LIB_DIR%\*;%BIN_DIR%" -d "%BIN_DIR%" db\*.java gui\*.java
popd

if !errorlevel! neq 0 (
    echo ERROR: Java compile failed.
    pause
    exit /b 1
)

:: Clear port 9999 if occupied
echo [2/3] Cleaning up port 9999...
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr :9999') do (
    if not "%%a"=="0" (
        taskkill /F /PID %%a >nul 2>&1
    )
)
timeout /t 1 /nobreak >nul

:: Start Python scheduling engine
echo [3/3] Launching Python scheduling engine (engine.py)...
start "McOS_Engine" "%PYTHON_PATH%" "%SRC_DIR%\engine.py"
timeout /t 2 /nobreak >nul

echo ==========================================
echo Launching Integrated Switch GUI...
cd /d "%PROJECT_ROOT%"
"%JAVA_CMD%" -Dfile.encoding=UTF-8 %JAVA_OPTS% --module-path "%LIB_DIR%" --add-modules javafx.controls,javafx.graphics,javafx.swing,javafx.web -cp "%BIN_DIR%;%LIB_DIR%\json-20240303.jar;%LIB_DIR%\mysql-connector-j-9.6.0.jar" gui.SwitchGUI

pause
