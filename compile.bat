@echo off
title aCis Compiler

REM ============================================================
REM  aCis Compiler — compila Gameserver + Datapack
REM  Requiere JDK 21 (javac, jar)
REM ============================================================

set "PROJECT_DIR=%~dp0"
set "GS_DIR=%PROJECT_DIR%aCis_gameserver"
set "DP_DIR=%PROJECT_DIR%aCis_datapack"

REM Verificar JAVA_HOME
if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME no esta definida.
    echo.
    echo Configurala temporalmente con:
    echo   set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12
    echo.
    pause
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo ERROR: No se encontro javac.exe en %%JAVA_HOME%%\bin
    echo Verifica que JAVA_HOME apunte a un JDK, no a un JRE.
    pause
    exit /b 1
)

REM ============================================================
REM  COMPILAR GAMESERVER
REM ============================================================
echo.
echo ========================================
echo     COMPILANDO aCis GAMESERVER
echo ========================================
echo.

REM Respaldo del ultimo JAR bueno ANTES de limpiar (build\ se borra completo)
if exist "%GS_DIR%\build\l2jserver.jar" (
    echo Respaldo del JAR anterior...
    copy /y "%GS_DIR%\build\l2jserver.jar" "%GS_DIR%\l2jserver.jar.bak" >nul
)

if exist "%GS_DIR%\build" (
    echo Limpiando build anterior...
    rmdir /s /q "%GS_DIR%\build"
)

REM Classpath
set "CLASSPATH="
for %%j in ("%GS_DIR%\lib\*.jar") do (
    if defined CLASSPATH (set "CLASSPATH=%CLASSPATH%;%%j") else (set "CLASSPATH=%%j")
)

REM Directorios
mkdir "%GS_DIR%\build\classes" 2>nul
mkdir "%GS_DIR%\build\dist\login\libs" 2>nul
mkdir "%GS_DIR%\build\dist\gameserver\libs" 2>nul

REM Compilar
echo Buscando archivos .java...
dir /s /b "%GS_DIR%\java\*.java" > "%GS_DIR%\build\_files.txt"
for /f %%a in ('type "%GS_DIR%\build\_files.txt" ^| find /c /v ""') do set FILE_COUNT=%%a
echo Encontrados %FILE_COUNT% archivos. Compilando...

"%JAVA_HOME%\bin\javac" -encoding UTF-8 -source 21 -target 21 -cp "%CLASSPATH%" -d "%GS_DIR%\build\classes" -sourcepath "%GS_DIR%\java" @"%GS_DIR%\build\_files.txt"
if %ERRORLEVEL% NEQ 0 goto :build_failed
del "%GS_DIR%\build\_files.txt"
echo Gameserver compilado exitosamente.

REM Crear JAR
echo Creando l2jserver.jar...
pushd "%GS_DIR%\build\classes"
"%JAVA_HOME%\bin\jar" cf "%GS_DIR%\build\l2jserver.jar" .
popd
if %ERRORLEVEL% NEQ 0 goto :build_failed
echo JAR creado exitosamente.

REM Actualizar el respaldo con el JAR recien creado
copy /y "%GS_DIR%\build\l2jserver.jar" "%GS_DIR%\l2jserver.jar.bak" >nul

REM ============================================================
REM  DIST GAMESERVER — estructura igual que build.xml
REM ============================================================
echo Copiando librerias, scripts y configuracion...

REM JARs a ambos
copy "%GS_DIR%\build\l2jserver.jar" "%GS_DIR%\build\dist\login\libs\" >nul
copy "%GS_DIR%\build\l2jserver.jar" "%GS_DIR%\build\dist\gameserver\libs\" >nul
copy "%GS_DIR%\lib\*.jar" "%GS_DIR%\build\dist\login\libs\" >nul
copy "%GS_DIR%\lib\*.jar" "%GS_DIR%\build\dist\gameserver\libs\" >nul

REM Scripts login
copy "%GS_DIR%\dist\LoginServer_loop.sh" "%GS_DIR%\build\dist\login\" >nul 2>nul
copy "%GS_DIR%\dist\startLoginServer.*" "%GS_DIR%\build\dist\login\" >nul 2>nul
copy "%GS_DIR%\dist\RegisterGameServer.*" "%GS_DIR%\build\dist\login\" >nul 2>nul
copy "%GS_DIR%\dist\startSQLAccountManager.*" "%GS_DIR%\build\dist\login\" >nul 2>nul

REM Scripts gameserver
copy "%GS_DIR%\dist\GameServer_loop.sh" "%GS_DIR%\build\dist\gameserver\" >nul 2>nul
copy "%GS_DIR%\dist\startGameServer.*" "%GS_DIR%\build\dist\gameserver\" >nul 2>nul

REM Config gameserver (todos excepto banned_ips y loginserver)
mkdir "%GS_DIR%\build\dist\gameserver\config" 2>nul
copy "%GS_DIR%\config\server.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul 2>nul
copy "%GS_DIR%\config\players.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul 2>nul
copy "%GS_DIR%\config\npcs.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul 2>nul
copy "%GS_DIR%\config\factionwar.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul 2>nul
copy "%GS_DIR%\config\events.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul 2>nul
copy "%GS_DIR%\config\dungeon.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul 2>nul
copy "%GS_DIR%\config\siege.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul 2>nul
copy "%GS_DIR%\config\clans.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul 2>nul
copy "%GS_DIR%\config\geoengine.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul 2>nul
copy "%GS_DIR%\config\logging.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul 2>nul
copy "%GS_DIR%\config\phantoms.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul 2>nul

REM Config login (solo banned_ips, logging, loginserver)
mkdir "%GS_DIR%\build\dist\login\config" 2>nul
copy "%GS_DIR%\config\banned_ips.properties" "%GS_DIR%\build\dist\login\config\" >nul 2>nul
copy "%GS_DIR%\config\logging.properties" "%GS_DIR%\build\dist\login\config\" >nul 2>nul
copy "%GS_DIR%\config\loginserver.properties" "%GS_DIR%\build\dist\login\config\" >nul 2>nul

REM Directorios log
mkdir "%GS_DIR%\build\dist\gameserver\log" 2>nul
mkdir "%GS_DIR%\build\dist\login\log" 2>nul
mkdir "%GS_DIR%\build\dist\gameserver\data" 2>nul

REM ============================================================
REM  COMPILAR DATAPACK
REM ============================================================
echo.
echo ========================================
echo     COMPILANDO aCis DATAPACK
echo ========================================
echo.

REM Crear directorios
mkdir "%DP_DIR%\build\gameserver\data" 2>nul
mkdir "%DP_DIR%\build\login" 2>nul
mkdir "%DP_DIR%\build\sql" 2>nul
mkdir "%DP_DIR%\build\tools" 2>nul

REM Copiar data
echo Copiando datos...
robocopy "%DP_DIR%\data" "%DP_DIR%\build\gameserver\data" /E /XD .project log cachedir clans /NFL /NJH /NJS /NDL >nul 2>nul

REM Copiar sql
copy "%DP_DIR%\sql\*.sql" "%DP_DIR%\build\sql\" >nul 2>nul

REM Copiar tools
copy "%DP_DIR%\tools\*" "%DP_DIR%\build\tools\" >nul 2>nul

REM serverNames.xml a login
copy "%DP_DIR%\data\serverNames.xml" "%DP_DIR%\build\login\" >nul 2>nul

REM ============================================================
REM  COPIA FINAL A C:\server\
REM ============================================================
echo.
echo ========================================
echo     COPIA FINAL A C:\server\
echo ========================================
echo.

if not exist "C:\server\" mkdir "C:\server\"

REM Copiar dist del gameserver (gameserver\, login\)
xcopy /e /i /y "%GS_DIR%\build\dist\*" "C:\server\" >nul

REM Copiar datapack (gameserver\data, login, sql, tools)
xcopy /e /i /y "%DP_DIR%\build\*" "C:\server\" >nul

echo Copiado exitosamente a C:\server\
echo.

echo ========================================
echo     COMPILACION COMPLETA
echo ========================================
echo.
pause
exit /b 0

REM ============================================================
REM  MANEJO DE FALLOS: restaurar el JAR anterior y avisar claro
REM ============================================================
:build_failed
if not exist "%GS_DIR%\l2jserver.jar.bak" goto :no_backup

for %%F in ("%GS_DIR%\l2jserver.jar.bak") do set "JAR_DATE=%%~tF"
if not exist "%GS_DIR%\build" mkdir "%GS_DIR%\build"
copy /y "%GS_DIR%\l2jserver.jar.bak" "%GS_DIR%\build\l2jserver.jar" >nul

echo.
echo ============================================================
echo  ERROR: LA COMPILACION DEL GAMESERVER FALLO.
echo ============================================================
echo.
echo  El JAR anterior NO se perdio: quedo restaurado en
echo    aCis_gameserver\build\l2jserver.jar   (fecha: %JAR_DATE%)
echo  Respaldo guardado en aCis_gameserver\l2jserver.jar.bak
echo.
echo  IMPORTANTE: ese JAR es la version ANTERIOR y NO incluye
echo  los cambios recientes. El server seguira funcionando con
echo  el JAR VIEJO (el desplegado en C:\server no fue tocado)
echo  hasta que corrijas los errores de arriba y vuelvas a
echo  compilar con exito.
echo ============================================================
echo.
pause
exit /b 1

:no_backup
echo.
echo ============================================================
echo  ERROR: LA COMPILACION DEL GAMESERVER FALLO.
echo ============================================================
echo.
echo  No se encontro un JAR anterior para restaurar (primer
echo  build o respaldo inexistente). El build quedo SIN
echo  l2jserver.jar. Corregi los errores de arriba y volve a
echo  compilar.
echo ============================================================
echo.
pause
exit /b 1