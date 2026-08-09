@echo off
setlocal enabledelayedexpansion
title aCis Setup - Base de datos + hexid

REM ============================================================
REM  aCis SETUP
REM  Automatiza la puesta a punto del servidor:
REM    1. Crea la base de datos (acis) si no existe
REM    2. Importa todos los .sql del datapack
REM    3. Genera el hexid.txt del gameserver (registro en DB + archivo)
REM
REM  Uso:
REM    setup.bat             -> crea DB, importa SQL y genera el hexid si falta
REM    setup.bat -sql        -> solo crea la DB e importa los SQL
REM    setup.bat -hexid      -> solo genera/copia el hexid.txt
REM    setup.bat -force      -> igual que el default pero REGENERA el hexid
REM                            aunque ya exista (limpia los servers registrados)
REM    setup.bat -h          -> muestra esta ayuda
REM
REM  Variables de entorno opcionales (por si tu maquina difiere):
REM    SETUP_MYSQL       ruta al mysql.exe (default: C:\xampp\mysql\bin\mysql.exe
REM                      o el "mysql" del PATH)
REM    SETUP_DB_NAME     nombre de la base (default: acis)
REM    SETUP_DB_USER     usuario de la DB (default: root)
REM    SETUP_DB_PASS     password de la DB (default: vacio)
REM    SETUP_SQL_DIR     carpeta con los .sql (default: aCis_datapack\sql)
REM    SETUP_SERVER_ID   id del server a registrar (default: 1 - Bartz)
REM    SETUP_REGISTER_DIR carpeta con libs + config + serverNames.xml para
REM                      correr el registro (default: detecta C:\server\login
REM                      o crea una carpeta temporal en aCis_gameserver\build)
REM ============================================================

REM ------------------------------------------------------------
REM  Configuracion
REM ------------------------------------------------------------
set "SETUP_ROOT=%~dp0"

set "MYSQL_EXE="
if exist "C:\xampp\mysql\bin\mysql.exe" set "MYSQL_EXE=C:\xampp\mysql\bin\mysql.exe"
if not defined MYSQL_EXE for /f "delims=" %%m in ('where mysql 2^>nul') do if not defined MYSQL_EXE set "MYSQL_EXE=%%m"
if defined SETUP_MYSQL set "MYSQL_EXE=%SETUP_MYSQL%"

set "DB_NAME=acis"
if defined SETUP_DB_NAME set "DB_NAME=%SETUP_DB_NAME%"
set "DB_USER=root"
if defined SETUP_DB_USER set "DB_USER=%SETUP_DB_USER%"
set "DB_PASS="
if defined SETUP_DB_PASS set "DB_PASS=%SETUP_DB_PASS%"
set "SERVER_ID=1"
if defined SETUP_SERVER_ID set "SERVER_ID=%SETUP_SERVER_ID%"

set "SQL_DIR=%SETUP_ROOT%aCis_datapack\sql"
if not exist "%SQL_DIR%\*.sql" set "SQL_DIR=%SETUP_ROOT%aCis_datapack\build\sql"
if defined SETUP_SQL_DIR set "SQL_DIR=%SETUP_SQL_DIR%"

REM Carpeta de config del gameserver en el repo y en el deploy (C:\server)
set "GS_CONFIG=%SETUP_ROOT%aCis_gameserver\config"
set "DEPLOY_GS_CONFIG=C:\server\gameserver\config"

set "DB_PASS_FLAG="
if not "%DB_PASS%"=="" set "DB_PASS_FLAG=-p%DB_PASS%"

REM Flags
set "DO_SQL=1"
set "DO_HEXID=1"
set "FORCE=0"

REM ------------------------------------------------------------
REM  Parseo de argumentos
REM ------------------------------------------------------------
for %%a in (%*) do (
    if /i "%%~a"=="-h"     set "SHOW_HELP=1"
    if /i "%%~a"=="-sql"   set "DO_HEXID=0"
    if /i "%%~a"=="-hexid" set "DO_SQL=0"
    if /i "%%~a"=="-force" set "FORCE=1"
)
if defined SHOW_HELP goto show_help

if %DO_SQL%==0 if %DO_HEXID%==0 (
    echo [INFO] No hay nada que hacer: -sql y -hexid juntos no ejecutan nada.
    echo        Usa solo uno de ellos o ninguno.
    pause
    exit /b 0
)

echo.
echo ========================================
echo        aCis SETUP
echo ========================================
echo.
echo   MySQL : %MYSQL_EXE%
echo   DB    : %DB_NAME%  ^(user: %DB_USER%^)
echo   SQL   : %SQL_DIR%
echo   Server ID para hexid: %SERVER_ID%
echo.

REM ------------------------------------------------------------
REM  PASO 1 - Crear la base de datos e importar los SQL
REM ------------------------------------------------------------
if %DO_SQL%==1 (
    if not defined MYSQL_EXE (
        echo [ERROR] No se encontro mysql.exe.
        echo         Instala XAMPP o configura la variable SETUP_MYSQL con la ruta.
        pause
        exit /b 1
    )

    echo ----------------------------------------
    echo  [1/2] Creando la base de datos...
    echo ----------------------------------------
    echo.
    "%MYSQL_EXE%" -u %DB_USER% %DB_PASS_FLAG% -e "SELECT 1" >nul 2>nul
    if errorlevel 1 (
        echo [ERROR] No se pudo conectar a MySQL con "%MYSQL_EXE%" ^(user: %DB_USER%^).
        echo         Verifica que MySQL este corriendo y que la ruta sea correcta.
        pause
        exit /b 1
    )
    echo   Conexion a MySQL OK.
    echo.

    "%MYSQL_EXE%" -u %DB_USER% %DB_PASS_FLAG% -e "CREATE DATABASE IF NOT EXISTS `%DB_NAME%` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
    if errorlevel 1 (
        echo [ERROR] No se pudo crear la base de datos "%DB_NAME%".
        pause
        exit /b 1
    )
    echo   Base de datos "%DB_NAME%" lista.
    echo.

    echo   Importando SQL desde: %SQL_DIR%
    echo.
    set "IMPORT_FAILED=0"
    set "IMPORT_COUNT=0"

    REM Primero todos en orden alfabetico, excepto alter_* (dependen de las tablas base)
    for %%f in ("%SQL_DIR%\*.sql") do (
        echo %%~nxf | findstr /i /b "alter_" >nul
        if errorlevel 1 (
            "%MYSQL_EXE%" -u %DB_USER% %DB_PASS_FLAG% --default-character-set=utf8mb4 "%DB_NAME%" < "%%~f"
            if errorlevel 1 (set /a IMPORT_FAILED+=1) else (set /a IMPORT_COUNT+=1)
        )
    )

    REM Los alter_* se importan al final (ej: alter_add_factionPoints necesita mods_faction)
    for %%f in ("%SQL_DIR%\alter_*.sql") do (
        "%MYSQL_EXE%" -u %DB_USER% %DB_PASS_FLAG% --default-character-set=utf8mb4 "%DB_NAME%" < "%%~f"
        if errorlevel 1 (set /a IMPORT_FAILED+=1) else (set /a IMPORT_COUNT+=1)
    )

    echo.
    if !IMPORT_FAILED! gtr 0 (
        echo  [ATENCION] !IMPORT_FAILED! archivos fallaron ^(revisa los mensajes de arriba^).
        echo             !IMPORT_COUNT! se importaron correctamente.
    ) else (
        echo  [OK] Los !IMPORT_COUNT! archivos SQL se importaron correctamente.
    )
    echo.
)

REM ------------------------------------------------------------
REM  PASO 2 - Generar el hexid.txt del gameserver
REM ------------------------------------------------------------
if %DO_HEXID%==1 (
    echo ----------------------------------------
    echo  [2/2] Generando hexid.txt del gameserver
    echo ----------------------------------------
    echo.
    if exist "%GS_CONFIG%\hexid.txt" (
        if not %FORCE%==1 (
            echo   Ya existe %GS_CONFIG%\hexid.txt
            echo   Se omite el registro. Usa:  setup.bat -force  para regenerarlo.
        ) else (
            call :generate_hexid
        )
    ) else (
        call :generate_hexid
    )
    echo.
)

echo ========================================
echo        SETUP COMPLETADO
echo ========================================
echo.
pause
exit /b 0

REM ------------------------------------------------------------
REM  Subrutina: generar el hexid
REM ------------------------------------------------------------
:generate_hexid

REM --- Buscar un directorio donde correr el registro (libs + config + serverNames.xml) ---
set "REG_DIR="
if defined SETUP_REGISTER_DIR if exist "%SETUP_REGISTER_DIR%\libs\l2jserver.jar" set "REG_DIR=%SETUP_REGISTER_DIR%"
if not defined REG_DIR if exist "C:\server\login\libs\l2jserver.jar" if exist "C:\server\login\config\loginserver.properties" if exist "C:\server\login\serverNames.xml" set "REG_DIR=C:\server\login"
if not defined REG_DIR call :build_register_dir
if not defined REG_DIR (
    echo   [ERROR] No se encontro un directorio de registro ^(necesita libs\l2jserver.jar,
    echo           config\loginserver.properties y serverNames.xml^).
    echo           Ejecuta primero compile.bat o configura SETUP_REGISTER_DIR.
    exit /b 1
)
echo   Directorio de registro: %REG_DIR%

REM --- Ejecutar el registro (no interactivo: responde el id y luego "exit") ---
set "JAVA_EXE=java"
if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"

pushd "%REG_DIR%"
echo.
if %FORCE%==1 (
    echo   ^(force^) Limpiando servidores registrados y registrando id %SERVER_ID%...
    (echo cleanall & echo y & echo %SERVER_ID% & echo exit) | "%JAVA_EXE%" -cp "libs\*" net.sf.l2j.gsregistering.GameServerRegister
) else (
    (echo %SERVER_ID% & echo exit) | "%JAVA_EXE%" -cp "libs\*" net.sf.l2j.gsregistering.GameServerRegister
)
popd

REM --- Copiar el hexid generado a las carpetas de config ---
set "GEN_FILE=%REG_DIR%\hexid(server %SERVER_ID%).txt"
if not exist "%GEN_FILE%" (
    echo.
    echo   [ERROR] No se genero el archivo hexid.
    echo           Probablemente el id %SERVER_ID% ya esta registrado en la base.
    echo           Reintenta con:  setup.bat -force
    exit /b 1
)

echo.
echo   Copiando hexid.txt a las carpetas de config...
copy /y "%GEN_FILE%" "%GS_CONFIG%\hexid.txt" >nul
echo    + %GS_CONFIG%\hexid.txt
if exist "%DEPLOY_GS_CONFIG%" (
    copy /y "%GEN_FILE%" "%DEPLOY_GS_CONFIG%\hexid.txt" >nul
    echo    + %DEPLOY_GS_CONFIG%\hexid.txt
)
echo.
echo   [OK] hexid.txt generado y copiado. Reinicia los servidores para aplicarlo.
exit /b

REM ------------------------------------------------------------
REM  Subrutina: armar un directorio de registro desde el repo
REM ------------------------------------------------------------
:build_register_dir
set "REG_DIR=%SETUP_ROOT%aCis_gameserver\build\register"
mkdir "%REG_DIR%\libs" 2>nul
mkdir "%REG_DIR%\config" 2>nul

if not exist "%REG_DIR%\libs\l2jserver.jar" if exist "%SETUP_ROOT%aCis_gameserver\build\l2jserver.jar" copy /y "%SETUP_ROOT%aCis_gameserver\build\l2jserver.jar" "%REG_DIR%\libs\" >nul
if not exist "%REG_DIR%\libs\mariadb-java-client-3.1.4.jar" if exist "%SETUP_ROOT%aCis_gameserver\lib\mariadb-java-client-3.1.4.jar" copy /y "%SETUP_ROOT%aCis_gameserver\lib\mariadb-java-client-3.1.4.jar" "%REG_DIR%\libs\" >nul
if not exist "%REG_DIR%\config\loginserver.properties" if exist "%SETUP_ROOT%aCis_gameserver\config\loginserver.properties" copy /y "%SETUP_ROOT%aCis_gameserver\config\loginserver.properties" "%REG_DIR%\config\" >nul
if not exist "%REG_DIR%\serverNames.xml" if exist "%SETUP_ROOT%aCis_datapack\data\serverNames.xml" copy /y "%SETUP_ROOT%aCis_datapack\data\serverNames.xml" "%REG_DIR%\" >nul

REM Validar que quedo completo; si falta algo, descartar el directorio
if not exist "%REG_DIR%\libs\l2jserver.jar" set "REG_DIR="
if not exist "%REG_DIR%\config\loginserver.properties" set "REG_DIR="
if not exist "%REG_DIR%\serverNames.xml" set "REG_DIR="
exit /b

REM ------------------------------------------------------------
REM  Ayuda
REM ------------------------------------------------------------
:show_help
echo.
echo  aCis SETUP - automatiza la puesta a punto del servidor.
echo.
echo  Uso:
echo    setup.bat              crea la DB, importa los SQL y genera el hexid si falta
echo    setup.bat -sql         solo crea la DB e importa los SQL
echo    setup.bat -hexid       solo genera/copia el hexid.txt
echo    setup.bat -force       ademas REGENERA el hexid aunque ya exista
echo    setup.bat -h           muestra esta ayuda
echo.
echo  Variables de entorno opcionales:
echo    SETUP_MYSQL        ruta al mysql.exe
echo    SETUP_DB_NAME      nombre de la base (default: acis)
echo    SETUP_DB_USER      usuario (default: root)
echo    SETUP_DB_PASS      password (default: vacio)
echo    SETUP_SQL_DIR      carpeta con los .sql
echo    SETUP_SERVER_ID    id del server (default: 1)
echo    SETUP_REGISTER_DIR carpeta con libs + config + serverNames.xml
echo.
pause
exit /b 0
