@echo off
echo ====================================
echo   Building Block Hide and Seek
echo ====================================
echo.

REM Check for Java
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: Java is not installed or not in PATH!
    echo Please install Java 21 from https://adoptium.net/
    pause
    exit /b 1
)

echo Building plugin...
call "%~dp0mvnw.cmd" clean package -q
if %ERRORLEVEL% neq 0 (
    echo.
    echo Build failed! Check the errors above.
    pause
    exit /b 1
)

echo.
echo ====================================
echo   BUILD SUCCESSFUL!
echo ====================================
echo.
echo Your plugin JAR is at:
echo   %~dp0target\BlockHideSeek-1.0.0.jar
echo.
echo Copy that file into your server's "plugins" folder!
echo.
pause
