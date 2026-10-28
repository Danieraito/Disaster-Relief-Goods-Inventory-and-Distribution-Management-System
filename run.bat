@echo off
rem Compiles BarangayCare and starts the website. Run it from the project folder.
if not exist out mkdir out
javac -cp "jars\*" -d out LIB\*.java SERVER\*.java
if errorlevel 1 (
    echo Compile failed. Fix the errors above and try again.
    pause
    exit /b 1
)
echo.
echo Starting BarangayCare... open http://localhost:8080 in your browser.
java -cp "out;jars\*" SERVER.WebServer
pause
