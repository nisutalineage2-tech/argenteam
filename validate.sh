#!/bin/bash
# ================================================================
#  Script de validación — prueba todos los cambios recientes
# ================================================================
# Verifica: migración 9999→90007, HTML rediseñados, compilación Java
# ================================================================

PASS=0
FAIL=0
ERRORS=""

check() {
    local desc="$1"
    local cond="$2"
    if eval "$cond" 2>/dev/null; then
        echo "  ✅ $desc"
        ((PASS++))
    else
        echo "  ❌ $desc"
        ((FAIL++))
        ERRORS="$ERRORS\n    - $desc"
    fi
}

echo ""
echo "================================================"
echo "  🧪 VALIDACIÓN DE CAMBIOS — FactionWars + Eventos"
echo "================================================"
echo ""

# ── 1. Migración 9999 → 90007 ──────────────────────
echo "── 1. Migración NPC Event Manager 9999 → 90007 ──"

check "EventConfig.java default cambiado a 90007" \
    "grep -q '_managerNpcId = 90007' aCis_gameserver/java/net/sf/l2j/gameserver/event/EventConfig.java"

check "events.properties ManagerNpcId = 90007" \
    "grep -q 'ManagerNpcId = 90007' aCis_gameserver/config/events.properties"

check "90000-90999.xml template 9999→90007" \
    "grep -q 'id=\"90007\".*EventManagerNpc' aCis_datapack/data/xml/npcs/90000-90999.xml"

check "No queda template 9999 EventManager en xml" \
    "! grep -q 'id=\"9999\".*EventManagerNpc' aCis_datapack/data/xml/npcs/90000-90999.xml"

check "faction_neutral_zone.xml spawn 90007" \
    "grep -q 'id=\"90007\"' aCis_datapack/data/xml/spawnlist/faction_neutral_zone.xml"

check "faction_neutral_zone.xml NO spawn 9999" \
    "! grep -q 'id=\"9999\"' aCis_datapack/data/xml/spawnlist/faction_neutral_zone.xml"

check "event/90007.htm existe (renombrado)" \
    "test -f aCis_datapack/data/html/mods/event/90007.htm"

check "event/9999.htm NO existe (eliminado)" \
    "test ! -f aCis_datapack/data/html/mods/event/9999.htm"

# ── 2. HTML files ──────────────────────────────────
echo ""
echo "── 2. Archivos HTML ──"

FACTION_HTML=( "0.htm" "90004.htm" )
for f in "${FACTION_HTML[@]}"; do
    check "faction/$f existe" "test -f aCis_datapack/data/html/mods/faction/$f"
done

EVENT_HTML=( "90007.htm" "50003.htm" )
for f in "${EVENT_HTML[@]}"; do
    check "event/$f existe" "test -f aCis_datapack/data/html/mods/event/$f"
done

for i in $(seq 1 18); do
    check "event/$i.htm existe" "test -f aCis_datapack/data/html/mods/event/$i.htm"
done

# ── 3. Compilación Java ────────────────────────────
echo ""
echo "── 3. Compilación Java ──"

JAVA_FILES=(
    "aCis_gameserver/java/net/sf/l2j/gameserver/event/EventConfig.java"
    "aCis_gameserver/java/net/sf/l2j/gameserver/factionwar/FactionWarCheckpoint.java"
    "aCis_gameserver/java/net/sf/l2j/gameserver/factionwar/FactionWarManager.java"
    "aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/instance/FactionWarNpc.java"
    "aCis_gameserver/java/net/sf/l2j/gameserver/model/actor/instance/EventManagerNpc.java"
    "aCis_gameserver/java/net/sf/l2j/gameserver/event/AbstractEvent.java"
)

cd aCis_gameserver || exit 1
for jf in "${JAVA_FILES[@]}"; do
    REL="${jf#aCis_gameserver/}"
    if javac -encoding UTF-8 -source 21 -target 21 -cp "lib/*" -d build/classes -g -sourcepath java "$REL" 2>/dev/null; then
        echo "  ✅ $REL"
        ((PASS++))
    else
        echo "  ❌ $REL (ERROR de compilación)"
        ((FAIL++))
        ERRORS="$ERRORS\n    - $REL (compilación fallida)"
    fi
done
cd ..

# ── 4. Resumen ─────────────────────────────────────
echo ""
echo "================================================"
echo "  📊 RESULTADO: $PASS pasaron, $FAIL fallaron"
echo "================================================"

if [ "$FAIL" -gt 0 ]; then
    echo ""
    echo "  ERRORES:$ERRORS"
    echo ""
    exit 1
else
    echo ""
    echo "  ✅ ¡TODO OK! Todos los cambios están correctos."
    echo ""
    exit 0
fi
