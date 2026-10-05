@echo off
cd /d "%~dp0"

if not exist bin mkdir bin

echo Compiling the Bus Pass project...
javac -encoding UTF-8 -cp "lib\sqlite-jdbc-3.53.4.0.jar" -d bin "src\BusPassManagementSystem.java" "src\BusPassGUI.java"

if errorlevel 1 (
    echo.
    echo Compilation failed. Read the error above.
    pause
    exit /b 1
)

echo Starting the Bus Pass GUI...
java -cp "bin;lib\sqlite-jdbc-3.53.4.0.jar" BusPassGUI

pause