@echo off
title aCis Gameserver JAR Compiler

REM ============================================================
REM  aCis Gameserver JAR — compila el Gameserver y copia solo
REM  l2jserver.jar a C:\server\gameserver\libs
REM  Requiere JDK 21 (javac, jar)
REM ============================================================

set "PROJECT_DIR=%~dp0"
set "GS_DIR=%PROJECT_DIR%aCis_gameserver"
set "TARGET_DIR=C:\server\gameserver\libs"

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

REM Compilar
echo Buscando archivos .java...
dir /s /b "%GS_DIR%\java\*.java" > "%GS_DIR%\build\_files.txt"
for /f %%a in ('type "%GS_DIR%\build\_files.txt" ^| find /c /v ""') do set FILE_COUNT=%%a
echo Encontrados %FILE_COUNT% archivos. Compilando...

"%JAVA_HOME%\bin\javac" -encoding UTF-8 -source 21 -target 21 -cp "%CLASSPATH%" -d "%GS_DIR%\build\classes" -sourcepath "%GS_DIR%\java" @"%GS_DIR%\build\_files.txt"
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Fallo la compilacion del Gameserver.
    pause
    exit /b 1
)
del "%GS_DIR%\build\_files.txt"
echo Gameserver compilado exitosamente.

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

REM ============================================================
REM  COPIA SOLO DEL JAR A C:\server\gameserver\libs
REM ============================================================
echo.
echo ========================================
echo     COPIANDO l2jserver.jar A C:\server\gameserver\libs
echo ========================================
echo.

if not exist "%TARGET_DIR%" (
    echo AVISO: No existe %TARGET_DIR% - creandolo...
    mkdir "%TARGET_DIR%"
    if errorlevel 1 (
        echo ERROR: No se pudo crear %TARGET_DIR%
        pause
        exit /b 1
    )
)

copy /y "%GS_DIR%\build\l2jserver.jar" "%TARGET_DIR%\l2jserver.jar"
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Fallo al copiar l2jserver.jar.
    pause
    exit /b 1
)

echo l2jserver.jar copiado a %TARGET_DIR%\l2jserver.jar
echo.
echo ========================================
echo     COMPILACION COMPLETA
echo ========================================
echo.
pause
