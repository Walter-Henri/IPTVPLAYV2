@echo off
setlocal
echo ==================================================
echo   IPTV PLAY V2 - Build Sequencial Otimizado (DEBUG)
echo ==================================================

:: Limpar variaveis antigas e consertar GRADLE_OPTS
set GRADLE_OPTS=-Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=512m" -Dorg.gradle.workers.max=1

echo.
echo [1/3] Limpando projeto...
call gradlew.bat clean --no-daemon

echo.
echo [2/3] Compilando Modulo Universal (App + TV)...
call gradlew.bat :app:universal:assembleDebug --no-daemon --console=plain
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Erro no build do modulo Universal.
    exit /b %ERRORLEVEL%
)

echo.
echo [3/3] Compilando Modulo M3U Plugin...
call gradlew.bat :app:m3u-plugin:assembleDebug --no-daemon --console=plain
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [!] Erro no build do modulo Plugin.
    exit /b %ERRORLEVEL%
)

echo.
echo ==================================================
echo   SUCESSO! APKs Debug gerados.
echo ==================================================
