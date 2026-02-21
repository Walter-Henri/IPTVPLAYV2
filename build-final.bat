@echo off
setlocal
echo ==================================================
echo   IPTV PLAY V2 - Build Sequencial Otimizado (LEAN)
echo ==================================================

:: Configurações de Memória para Minimizar Travamentos
set GRADLE_OPTS=-Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=512m" -Dorg.gradle.workers.max=1

echo.
echo [1/3] Limpando projeto...
call gradlew.bat clean --no-daemon %GRADLE_OPTS%

echo.
echo [2/3] Compilando Modulo Universal (App + TV)...
call gradlew.bat :app:universal:assembleRelease --no-daemon %GRADLE_OPTS% --console=plain
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Erro no build do modulo Universal. Verifique a RAM livre.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [3/3] Compilando Modulo M3U Extension...
call gradlew.bat :app:m3u-extension:assembleRelease --no-daemon %GRADLE_OPTS% --console=plain
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Erro no build do modulo Extension.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ==================================================
echo   SUCESSO! APKs gerados com o minimo de recursos.
echo ==================================================
echo Localização dos APKs:
dir /s /b *.apk | findstr "release"
pause
