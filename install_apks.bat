@echo off
echo Instalando APKs Debug...

where adb >nul 2>nul
if %errorlevel% neq 0 (
    echo ADB nao encontrado no PATH. Verifique sua instalacao do Android SDK.
    pause
    exit /b
)

echo.
echo Instalando Extensao M3U...
adb install -r -g "app/APK BUILD/Debug/Extensao/app-m3u-extension-debug.apk"
if %errorlevel% neq 0 echo Falha ao instalar Extensao.

echo.
echo Instalando App Universal...
adb install -r -g "app/APK BUILD/Debug/Universal/app-universal-debug.apk"
if %errorlevel% neq 0 echo Falha ao instalar App Universal.

echo.
echo Concluido.
pause
