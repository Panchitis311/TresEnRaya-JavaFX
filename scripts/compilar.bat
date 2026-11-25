@echo off
cd /d "%~dp0.."
if "%JAVAFX_HOME%"=="" (
  echo Define JAVAFX_HOME, p. ej. set JAVAFX_HOME=C:\javafx-sdk-21.0.x
  exit /b 1
)
if not exist out mkdir out
javac --module-path "%JAVAFX_HOME%\lib" --add-modules javafx.controls -d out src\*.java && echo Compilado en .\out
