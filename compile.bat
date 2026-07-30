@echo off
title aCis Compiler

REM ============================================================
REM  aCis Compiler — compila Gameserver + Datapack con Ant
REM  Requiere: Ant y JDK 21
REM ============================================================

set "PROJECT_DIR=%~dp0"
set "GS_DIR=%PROJECT_DIR%aCis_gameserver"
set "DP_DIR=%PROJECT_DIR%aCis_datapack"

REM Verificar Ant
where ant >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Ant no encontrado. Instala Ant o agrega 'ant' al PATH.
    pause
    exit /b 1
)

echo.
echo ========================================
echo     COMPILANDO aCis GAMESERVER (Ant)
echo ========================================
echo.
pushd "%GS_DIR%"
call ant clean dist
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Fallo la compilacion del Gameserver.
    pause
    popd
    exit /b 1
)
popd
echo Gameserver compilado exitosamente.
echo.

echo.
echo ========================================
echo     COMPILANDO aCis DATAPACK (Ant)
echo ========================================
echo.
pushd "%DP_DIR%"
call ant build
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Fallo la compilacion del Datapack.
    pause
    popd
    exit /b 1
)
popd
echo Datapack compilado exitosamente.
echo.

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

xcopy /e /i /y "%GS_DIR%\build\dist\*" "C:\server\" >nul
xcopy /e /i /y "%DP_DIR%\build\*" "C:\server\" >nul

echo Copiado exitosamente a C:\server\
echo.

pause