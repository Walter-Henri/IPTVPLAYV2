@echo off
setlocal

set GRADLE_OPTS=-Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=512m" -Dorg.gradle.workers.max=1

echo [1/3] Limpando projeto...
call gradlew.bat clean --no-daemon

echo [2/3] Compilando Modulo Universal...
call gradlew.bat :app:universal:assembleRelease --no-daemon --console=plain
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

echo [3/3] Compilando Modulo M3U Plugin...
call gradlew.bat :app:m3u-plugin:assembleRelease --no-daemon --console=plain
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

echo SUCESSO! APKs release gerados.
dir /s /b *.apk | findstr "release"
