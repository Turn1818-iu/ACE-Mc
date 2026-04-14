@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

if exist build rmdir /s /q build
mkdir build\classes
mkdir build\stubs

javac -d build\stubs stubs_src\net\fabricmc\api\ModInitializer.java stubs_src\net\fabricmc\api\DedicatedServerModInitializer.java
jar cf build\fabric-stubs.jar -C build\stubs .

javac -classpath build\fabric-stubs.jar -d build\classes src\main\java\com\anticheatexpert\AntiCheatExpertMod.java
if %errorlevel% neq 0 (
  echo Compilation failed.
  exit /b %errorlevel%
)

jar cf AntiCheatExpert-1.5.0.jar -C build\classes . -C src\main\resources .
if %errorlevel% neq 0 (
  echo Jar packaging failed.
  exit /b %errorlevel%
)

echo Build complete: AntiCheatExpert-1.5.0.jar
