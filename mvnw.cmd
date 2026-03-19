@REM Maven Wrapper for Windows
@REM This script will automatically download Maven if not installed
@REM Usage: mvnw.cmd clean package

@echo off
setlocal

set MAVEN_VERSION=3.9.6
set MAVEN_URL=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip
set MAVEN_DIR=%~dp0.mvn\maven
set MAVEN_HOME=%MAVEN_DIR%\apache-maven-%MAVEN_VERSION%

if exist "%MAVEN_HOME%\bin\mvn.cmd" goto run

echo Maven not found locally. Downloading Maven %MAVEN_VERSION%...
mkdir "%MAVEN_DIR%" 2>nul

echo Downloading from %MAVEN_URL%...
powershell -Command "Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%MAVEN_DIR%\maven.zip'"
if %ERRORLEVEL% neq 0 (
    echo Failed to download Maven. Please install Maven manually from https://maven.apache.org/download.cgi
    exit /b 1
)

echo Extracting Maven...
powershell -Command "Expand-Archive -Path '%MAVEN_DIR%\maven.zip' -DestinationPath '%MAVEN_DIR%' -Force"
del "%MAVEN_DIR%\maven.zip"

echo Maven %MAVEN_VERSION% installed successfully!

:run
"%MAVEN_HOME%\bin\mvn.cmd" %*
