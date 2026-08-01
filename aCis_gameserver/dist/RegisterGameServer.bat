@echo off
title aCis gameserver registration console

REM ---------------------------------------------------------------
REM  Tras registrar el server ID, copia el hexid generado a
REM  config/hexid.txt del gameserver.
REM  Si tu estructura de carpetas difiere, ajusta GS_CONFIG.
REM  Orden detectado: ..\gameserver\config (desplegado),
REM  ..\config (repo/dist).
REM ---------------------------------------------------------------
set "GS_CONFIG="
if exist "..\gameserver\config" set "GS_CONFIG=..\gameserver\config"
if not defined GS_CONFIG if exist "..\config" set "GS_CONFIG=..\config"

@java -Djava.util.logging.config.file=config/console.cfg -cp ./libs/*; net.sf.l2j.gsregistering.GameServerRegister

REM ---------------------------------------------------------------
REM  Busca el hexid mas reciente y lo copia al gameserver
REM ---------------------------------------------------------------
set "HEXID_FILE="
for /f "delims=" %%f in ('dir /b /o-d "hexid*.txt" 2^>nul') do (
    if not defined HEXID_FILE if /i not "%%f" == "hexid.txt" set "HEXID_FILE=%%f"
)
if not defined HEXID_FILE if exist "hexid.txt" set "HEXID_FILE=hexid.txt"

if defined HEXID_FILE (
    echo.
    copy /y "%HEXID_FILE%" "hexid.txt" >nul
    echo [Register] hexid.txt generado desde "%HEXID_FILE%".
    if defined GS_CONFIG (
        copy /y "hexid.txt" "%GS_CONFIG%\hexid.txt" >nul
        echo [Register] hexid.txt copiado a %GS_CONFIG%\hexid.txt
    ) else (
        echo [Register] No se detecto la carpeta config del gameserver.
        echo [Register] Copia hexid.txt manualmente a la carpeta config del gameserver.
    )
) else (
    echo.
    echo [Register] No se encontro un archivo hexid. Registra un server ID con el menu anterior.
)

@pause