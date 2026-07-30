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

echo.
echo ========================================
echo     COMPILANDO aCis GAMESERVER
echo ========================================
echo.

REM Clean
if exist "%GS_DIR%\build" (
    echo Limpiando build anterior...
    rmdir /s /q "%GS_DIR%\build"
)

REM Classpath con todas las libs
set "CLASSPATH="
for %%j in ("%GS_DIR%\lib\*.jar") do (
    if defined CLASSPATH (set "CLASSPATH=%CLASSPATH%;%%j") else (set "CLASSPATH=%%j")
)

REM Crear directorios
mkdir "%GS_DIR%\build\classes" 2>nul
mkdir "%GS_DIR%\build\dist\login\libs" 2>nul
mkdir "%GS_DIR%\build\dist\gameserver\libs" 2>nul

REM Compilar
echo Buscando archivos .java...
dir /s /b "%GS_DIR%\java\*.java" > "%GS_DIR%\build\_files.txt"
for /f %%a in ('type "%GS_DIR%\build\_files.txt" ^| find /c /v ""') do set FILE_COUNT=%%a
echo Encontrados %FILE_COUNT% archivos. Compilando...

"%JAVA_HOME%\bin\javac" -encoding UTF-8 -source 21 -target 21 -cp "%CLASSPATH%" -d "%GS_DIR%\build\classes" -sourcepath "%GS_DIR%\java" @"%GS_DIR%\build\_files.txt"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Fallo la compilacion del Gameserver.
    pause
    exit /b 1
)

del "%GS_DIR%\build\_files.txt"
echo Gameserver compilado exitosamente.
echo.

REM Crear JAR
echo Creando l2jserver.jar...
pushd "%GS_DIR%\build\classes"
"%JAVA_HOME%\bin\jar" cf "%GS_DIR%\build\l2jserver.jar" .
popd

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Fallo al crear el JAR.
    pause
    exit /b 1
)

echo JAR creado exitosamente.
echo.

REM Copiar libs, configs y scripts a dist
echo Copiando librerias y configuracion...

REM --- JARs a ambos ---
copy "%GS_DIR%\build\l2jserver.jar" "%GS_DIR%\build\dist\login\libs\" >nul
copy "%GS_DIR%\build\l2jserver.jar" "%GS_DIR%\build\dist\gameserver\libs\" >nul
copy "%GS_DIR%\lib\*.jar" "%GS_DIR%\build\dist\login\libs\" >nul
copy "%GS_DIR%\lib\*.jar" "%GS_DIR%\build\dist\gameserver\libs\" >nul

REM --- Scripts gameserver ---
copy "%GS_DIR%\dist\startGameServer.bat" "%GS_DIR%\build\dist\gameserver\" >nul
copy "%GS_DIR%\dist\startGameServer.sh" "%GS_DIR%\build\dist\gameserver\" >nul
copy "%GS_DIR%\dist\GameServer_loop.sh" "%GS_DIR%\build\dist\gameserver\" >nul
copy "%GS_DIR%\dist\RegisterGameServer.bat" "%GS_DIR%\build\dist\gameserver\" >nul
copy "%GS_DIR%\dist\RegisterGameServer.sh" "%GS_DIR%\build\dist\gameserver\" >nul
copy "%GS_DIR%\dist\startSQLAccountManager.bat" "%GS_DIR%\build\dist\gameserver\" >nul
copy "%GS_DIR%\dist\startSQLAccountManager.sh" "%GS_DIR%\build\dist\gameserver\" >nul

REM --- Scripts login ---
copy "%GS_DIR%\dist\startLoginServer.bat" "%GS_DIR%\build\dist\login\" >nul
copy "%GS_DIR%\dist\startLoginServer.sh" "%GS_DIR%\build\dist\login\" >nul
copy "%GS_DIR%\dist\LoginServer_loop.sh" "%GS_DIR%\build\dist\login\" >nul
copy "%GS_DIR%\dist\RegisterGameServer.bat" "%GS_DIR%\build\dist\login\" >nul
copy "%GS_DIR%\dist\RegisterGameServer.sh" "%GS_DIR%\build\dist\login\" >nul

REM --- Config gameserver ---
xcopy /e /i /y "%GS_DIR%\config\*.properties" "%GS_DIR%\build\dist\gameserver\config\" >nul
del "%GS_DIR%\build\dist\gameserver\config\banned_ips.properties" 2>nul
del "%GS_DIR%\build\dist\gameserver\config\loginserver.properties" 2>nul

REM --- Config login ---
mkdir "%GS_DIR%\build\dist\login\config" 2>nul
copy "%GS_DIR%\config\loginserver.properties" "%GS_DIR%\build\dist\login\config\" >nul
copy "%GS_DIR%\config\logging.properties" "%GS_DIR%\build\dist\login\config\" >nul
copy "%GS_DIR%\config\banned_ips.properties" "%GS_DIR%\build\dist\login\config\" >nul

REM Crear carpeta data y log
mkdir "%GS_DIR%\build\dist\gameserver\data" 2>nul
mkdir "%GS_DIR%\build\dist\gameserver\log" 2>nul
mkdir "%GS_DIR%\build\dist\login\log" 2>nul

echo.
echo ========================================
echo     COMPILANDO aCis DATAPACK
echo ========================================
echo.

REM Crear directorios datapack
mkdir "%DP_DIR%\build\gameserver\data" 2>nul
mkdir "%DP_DIR%\build\login" 2>nul
mkdir "%DP_DIR%\build\sql" 2>nul
mkdir "%DP_DIR%\build\tools" 2>nul

REM Copiar data (excluyendo carpetas no deseadas con robocopy)
echo Copiando datos...
robocopy "%DP_DIR%\data" "%DP_DIR%\build\gameserver\data" /E /XD .project log cachedir clans crests /NFL /NJH /NJS /NDL >nul 2>nul

REM Copiar sql
echo Copiando SQL...
copy "%DP_DIR%\sql\*.sql" "%DP_DIR%\build\sql\" >nul

REM Copiar tools
echo Copiando tools...
copy "%DP_DIR%\tools\*" "%DP_DIR%\build\tools\" >nul

REM Copiar serverNames.xml a login
copy "%DP_DIR%\data\serverNames.xml" "%DP_DIR%\build\login\" >nul

echo.
echo ========================================
echo     COMPILACION COMPLETA
echo ========================================
echo.
echo Gameserver: %GS_DIR%\build\dist\
echo Datapack:   %DP_DIR%\build\
echo.

REM ============================================================
REM  Copiar automaticamente a C:\server\
REM  Se copian ambos builds directamente, mergeando estructuras:
REM    aCis_gameserver\build\dist\*  -> C:\server\
REM    aCis_datapack\build\*         -> C:\server\
REM ============================================================
echo.
echo ========================================
echo     COPIANDO A C:\server\
echo ========================================
echo.

if not exist "C:\server\" mkdir "C:\server\"

REM Copiar dist del gameserver (gameserver\, login\, etc)
xcopy /e /i /y "%GS_DIR%\build\dist\*" "C:\server\" >nul

REM Copiar build del datapack (gameserver\data, login, sql, tools)
xcopy /e /i /y "%DP_DIR%\build\*" "C:\server\" >nul

echo Copiado exitosamente a C:\server\
echo.

pause
