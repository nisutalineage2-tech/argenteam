#!/bin/sh

# Tras registrar el server ID, copia el hexid generado a config/hexid.txt del gameserver.
# Si tu estructura de carpetas difiere, ajusta GS_CONFIG manualmente.
# Orden detectado: ../gameserver/config (desplegado), ../config (repo/dist).
GS_CONFIG=""
if [ -d "../gameserver/config" ]; then
    GS_CONFIG="../gameserver/config"
elif [ -d "../config" ]; then
    GS_CONFIG="../config"
fi

java -Djava.util.logging.config.file=config/console.cfg -cp ./libs/*:l2jserver.jar net.sf.l2j.gsregistering.GameServerRegister

HEXID_FILE=$(ls -t hexid*.txt 2>/dev/null | grep -v '^hexid.txt$' | head -1)
if [ -z "$HEXID_FILE" ] && [ -f "hexid.txt" ]; then
    HEXID_FILE="hexid.txt"
fi

if [ -n "$HEXID_FILE" ]; then
    echo ""
    cp -f "$HEXID_FILE" hexid.txt
    echo "[Register] hexid.txt generado desde $HEXID_FILE."
    if [ -n "$GS_CONFIG" ]; then
        cp -f hexid.txt "$GS_CONFIG/hexid.txt"
        echo "[Register] hexid.txt copiado a $GS_CONFIG/hexid.txt"
    else
        echo "[Register] No se detecto la carpeta config del gameserver."
        echo "[Register] Copia hexid.txt manualmente a la carpeta config del gameserver."
    fi
else
    echo ""
    echo "[Register] No se encontro un archivo hexid. Registra un server ID con el menu anterior."
fi
