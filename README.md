# aCis — Faction War & Phantom System

Servidor privado de **Lineage 2 Interlude (C4)** basado en **aCis** (fork de L2J), modificado con un modo de juego persistente de **Faction Wars**, un sistema de **phantoms** (bots que pueblan el mundo), un motor de **eventos** con 16 modos y varios NPCs y sistemas custom en la zona neutral.

Diseñado para operar en LAN (IP de ejemplo: `192.168.100.141`) con jugadores reales y phantoms coexistiendo.

---

## Features Custom (resumen)

| Sistema | Descripción | Config principal |
|---|---|---|
| **Faction Wars** | Guerra por tiempo entre 2 facciones; gana la que tiene la bandera al terminar | `factionwar.properties`, `faction_flags.xml`, `faction.xml` |
| **Eventos** | 16 eventos automáticos que alternan con la Faction War | `events.properties` |
| **Phantoms** | Bots con IA completa: farmean, pelean, participan de eventos y guerra | `phantoms.properties` |
| **Tiendas offline** | Venta/buy offline persistente + phantoms con tienda abierta | tablas `character_offline_trade*` |
| **NPC Buffer** | Buffs con esquemas por clase | `bufferSkills.xml`, `newbieBuffs.xml` |
| **GM Shop (General Trader)** | Multisells de equipo B/A/S y scrolls en zona neutral | `data/xml/multisell/trader_*.xml`, `buyLists.xml` |
| **Cassino, Bingo, Guild Missions, Dungeon, Grand Boss TP** | NPCs de minijuegos y utilidades en la zona neutral | `guildMissions.xml` |
| **Encantamiento de héroe** | Cañas y armas de héroe (6611-6621) encantables | `players.properties` |
| **Sistema de quests custom** | Motor de quests propio (ext.mods) | `ext/mods/quests` |

---

## 1. Sistema de Faction War

### Reglas

- La guerra es **por tiempo** (`WarDurationMinutes`). No hay puntaje para ganar: gana la facción que **mantiene la bandera principal** cuando termina el evento.
- Al terminar, los personajes quedan **congelados** durante `EndFreezeSeconds` y luego son teletransportados a la zona neutral (Gludin Village).
- Cada facción tiene **1 bandera principal** (NPC 90000) que defender y capturar.
- Hay **4 checkpoints** (NPC 90003) por mapa, neutrales y disputables: cualquier facción puede tomarlos para que su equipo respawnee más cerca del frente.
- Al morir, el respawn es configurable (`DeathRespawnMode`): última bandera capturada / base de la facción / zona neutral.
- El scoreboard se muestra arriba de todo con el tiempo del evento, actualizado cada 3 segundos y con el sufijo `[Faction War]`.
- Las facciones bloquean interacción entre sí: no hay PM, trade, duelo, party ni invitaciones a clan entre facciones rivales.

### Ciclo de vida

1. **Votación** — El War Registrar (NPC 90002) aparece en la zona neutral y los jugadores votan el mapa (`.votemap 1-4` o popup). Si nadie vota, se usa el **primer mapa** de la config.
2. **Inicio** — Se spawnean bandera, guardias y checkpoints; los participantes (jugadores y phantoms) son teletransportados a los spawns de su facción.
3. **Combate** — PvP entre facciones, captura de bandera y checkpoints, guardias defendiendo bases.
4. **Rotación** — Cada `MapRotationMinutes` se vota un mapa nuevo.
5. **Fin** — Congelamiento, anuncio del ganador, premios y retorno a zona neutral (jugadores y phantoms).

### Mapas

- Los **mapas de guerra** se definen en `Maps =` de `factionwar.properties` (formato `Nombre,x,y,z,radio,goodX,goodY,goodZ,evilX,evilY,evilZ`).
- Cada mapa se vincula a su set de banderas/checkpoints con `MapFlagIds = Nombre,mapId` (los mapIds viven en `faction_flags.xml`, que hoy cubre 12 zonas: Gludio, Dion, Giran, Oren, Aden, Goddard, Rune, Schuttgart y más).
- Si un mapa no tiene spawns propios, usa los globales `GoodSpawn` / `EvilSpawn`.

### NPCs de la guerra

| NPC | Id | Rol |
|---|---|---|
| `FactionWarFlag` | 90000 | Bandera principal, destruible y capturable |
| `FactionWarGuard` | 90001 | Guardia de base; respawnea individualmente al morir |
| `WarRegistrar` | 90002 | Registro, voto de mapas, lista de banderas capturadas y teleport a la última bandera |
| `FactionWarCpFlag` | 90003 | Checkpoint neutral disputable |

### BBS

El Community Board tiene una sección de Faction War con el **Top 10** de kills, puntos y facción (`FactionWarBBSManager`), accesible desde el menú principal del BBS.

---

## 2. Sistema de Eventos

Los eventos están **siempre activos** y alternan con la Faction War (EventEngine: FW -> Evento -> FW). El NPC **Event Manager** (90007) de la zona neutral lista los eventos, muestra el tiempo de inicio y permite registrarse.

### Registro

- Comando de jugador: `.eventjoin` (al evento abierto) o `.eventjoin <id>`; `.eventleave` para salir.
- Los **phantoms** también se registran: participan del evento con roles por modo (en Domination van a la zona de captura, en Lucky Chests priorizan cofres, en Raid in the Middle van al boss, etc.).
- Sus kills cuentan para el score del evento y al morir reviven en el spawn de su equipo.

### Modos (16 habilitados en `events.properties`)

| # | Nombre | # | Nombre |
|---|---|---|---|
| 1 | Team vs Team (TvT) | 10 | Mutant |
| 2 | Deathmatch | 11 | Ruleta Rusa |
| 3 | Capture the Flag | 12 | Simon Dice |
| 4 | Battlefield | 13 | Zombies vs Humanos |
| 5 | Bomb Fight | 14 | Caceria |
| 6 | Last Man Standing | 15 | Korean TvT (desactivado) |
| 7 | Lucky Chests | 16 | Raid en el Centro |
| 8 | Domination | 17 | Caza del Tesoro |
| 9 | Double Domination | 18-20 | Reservados (desactivados) |

Los equipos de los eventos se llaman **Argentina** (celeste) y **Brasil** (verde), con crest de país.

---

## 3. Sistema de Phantoms

Bots que simulan jugadores reales. Su IA está **siempre activa** (no existe opción de apagarla).

### Arquitectura

| Componente | Propósito |
|---|---|
| `PhantomEngine` | Singleton central: carga, spawn y gestión |
| `PhantomAI` | IA principal: pathfinding, combate, party, social, eventos |
| `PhantomCombat` | Combate: selección de target, skills, rage |
| `PhantomSocial` | Chat social y frases de guerra |
| `PhantomState` | Máquina de estados (idle, combat, follow...) |
| `PhantomInventory` / `PhantomEquipment` | Inventario y equipo según clase/nivel |
| `PhantomProgression` | Progresión: niveles, skills, equipo |
| `PhantomLog` / `PhantomChat` | Logging y chat |

### Comportamiento

- Pueblan las ciudades y zonas neutrales; algunos usan **tienda offline** para simular jugadores con shop abierto.
- **Faction Wars:** se auto-registran vía War Registrar si no tienen facción, el sistema elige participantes al inicio (`WarParticipationChance`, `WarMaxPerFaction`, `WarNearbyOnlyRange`), pelean en el mapa, respawnean en su base/checkpoint y al terminar **vuelven a la zona neutral**.
- **Eventos:** se registran solos, cumplen el rol del modo activo y esperan junto al Event Manager mientras el evento no arranca.
- Panel de admin `//phantom` con control por facción, listas online, stats de guerra y creación de phantoms.

### Configuración clave (`phantoms.properties`)

| Propiedad | Descripción |
|---|---|
| Activación | El sistema se inicia con el comando admin `//phantom start` (carga los `PhantomIds`); la IA queda siempre activa |
| `PhantomIds` | IDs guardados que carga `//phantom start` |
| `SpawnAtGm` / `PersistCreated` | Spawn cerca del GM y persistencia |
| `WarParticipationChance` / `WarMaxPerFaction` / `WarNearbyOnlyRange` | Qué phantoms van a la guerra |
| Niveles, equipo, chat social | Rango de niveles, grade de equipo, frases y delays |

---

## 4. Tiendas Offline

- Sistema de venta/compra offline con persistencia en SQL:
  - `character_offline_trade` (charId, time, type, title)
  - `character_offline_trade_items` (charId, item, count, price)
- Al cerrar sesión con la tienda abierta, el personaje queda como vendedor offline.
- Algunos **phantoms** usan este modo para simular tiendas en la zona neutral.
- Comando de admin `//offline` para listar, ver y cerrar tiendas activas.

---

## 5. NPC Buffer

- **Faction Buffer** (NPC 90005) en la zona neutral: buffs con esquemas por clase (guardados por jugador).
- **SchemeBuffer** (NPC 50002, `data/html/mods/buffer/`): esquemas de buffs custom.
- Los eventos tienen su propio **EventBuffer** (`EventBufferEnabled`, `NpcBufferId`) que buffea a los participantes con la lista de `events.properties` (`AllowedBuffsList`, `MaxBuffNum`).

---

## 6. GM Shop (General Trader)

- NPC **General Trader** (90012) en la zona neutral con multisells de equipo **grado B, A y S**:
  - `data/xml/multisell/trader_b_armor.xml`, `trader_b_weapons.xml`, `trader_a_*.xml`, `trader_s_*.xml`
  - Incluye **scrolls de encantado S** (959, 6577, 961) para aprovechar el encantamiento de armas de héroe.
- `buyLists.xml`: los buylists de GM shop (`npcId="-1"`, items 67-99) tienen `price="0"` para equipo B-grade gratis a personajes nuevos.

---

## 7. Otros NPCs y features de la zona neutral

| NPC | Id | Descripción |
|---|---|---|
| Faction Manager | 90004 | Info de facción y registro |
| Dungeon Manager | 90006 | Acceso a dungeons |
| Guild Mission Manager | 90008 | Misiones de clan (XML `guildMissions.xml`) |
| Bingo Manager | 90009 | Minijuego de bingo |
| Cassino | 90010 | Tragamonedas 3x3 con animación |
| Grand Boss Teleporter | 90011 | Teleport a raid bosses |
| General Trader | 90012 | GM Shop (multisells B/A/S) |

### Otras modificaciones

- **Encantamiento de héroe:** `EnchantHeroWeapons` (cañas y armas 6611-6621) + `EnchantHeroWeaponsMaxLevel` para limitar el nivel máximo.
- **Duración de skills:** `SkillDurationList` en `players.properties` (buffs de 3h: 264-277, 304-311, etc.).
- **Anuncios de raid boss:** al spawnear y al morir se anuncian con facción/clan del killer (`[Raid Boss] ...`).
- **AutoLearnSkills** y **Newbie System** (items iniciales por clase al primer login), personajes nuevos nivel 76.
- **Geodata optimizada:** solo se cargan **18 regiones** (`geoengine.properties`) que cubren las zonas del juego (mapas de guerra, eventos y zona neutral), reduciendo el uso de RAM.
- **Quest system custom** (`ext/mods/quests`): motor de quests propio con objetivos, recompensas por clase y bypass de voz.
- **GM por defecto:** `GMStartupInvulnerable/Invisible/BlockAll = True`, sin aura de héroe y sin auto-listado.

---

## Estructura del proyecto

```
acis_public/
├── compile.bat                  <- Compila Gameserver + Datapack y copia a C:\server
├── aCis_gameserver/
│   ├── java/net/sf/l2j/         <- Código fuente (Config, gameserver, commons)
│   │   └── gameserver/
│   │       ├── factionwar/      <- FactionWarManager, Config, Checkpoint, Registry
│   │       ├── phantom/         <- PhantomEngine, PhantomAI, PhantomCombat, ...
│   │       ├── event/           <- EventEngine, EventConfig + 17 modos de evento
│   │       ├── bingo/           <- BingoManager
│   │       ├── guildmission/    <- GuildMissionManager
│   │       ├── communitybbs/    <- CommunityBoard + FactionWarBBSManager
│   │       └── model/actor/instance/  <- NPCs custom (FactionWarFlag, CassinoNpc, ...)
│   ├── java/ext/mods/           <- Quest system custom (ext.mods.quests) + voiced handlers
│   ├── config/                  <- Todas las .properties del server
│   ├── lib/                     <- Librerías (jars)
│   └── dist/                    <- Scripts de arranque (bat/sh)
├── aCis_datapack/
│   ├── data/
│   │   ├── html/                <- HTMLs de NPCs (mods/, script/, admin/, ...)
│   │   ├── xml/                 <- Datos: items, npcs (90000-90999.xml), multisell/,
│   │   │                           buyLists.xml, faction.xml, faction_flags.xml,
│   │   │                           guildMissions.xml, bufferSkills.xml, ...
│   │   └── ...
│   ├── sql/                     <- Scripts SQL de la base `acis`
│   └── build/                   <- Salida del build (datapack compilado)
└── .gitignore                   <- Excluye .freebuff/, .agents/, .claude/, logs, ...
```

### Estructura desplegada (`C:\server`, generada por `compile.bat`)

```
C:\server\
├── gameserver\    <- JARs, configs, scripts, data (HTML, XML)
├── login\         <- Login server (JARs, configs, scripts)
├── sql\           <- Scripts SQL
└── tools\         <- Herramientas
```

---

## Compilar y levantar el server

### Requisitos

- **JDK 21** (64 bits) con `JAVA_HOME` definido.
- **MariaDB/MySQL** con la base de datos `acis` creada.
- Cliente **Lineage 2 Interlude (C4)** apuntando a la IP del server.

### 1. Base de datos

1. Crear la base `acis`.
2. Importar los scripts de `aCis_datapack/build/sql/` (todos los `.sql`; los custom incluyen `events.sql`, `mods_faction.sql`, `mods_factionwar.sql`, `alter_add_factionPoints.sql` y las tablas de tiendas offline `character_offline_trade` / `character_offline_trade_items`).

### 2. Compilar

```bat
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12
compile.bat
```

- Compila el Gameserver (todos los `.java`, ~2400 archivos), genera `l2jserver.jar` y copia todo a `C:\server\`.

### 3. Levantar (en orden)

1. **Login server** — `C:\server\login\startLoginServer.bat` (escucha en `*:2106`).
2. **Registrar el gameserver** — `C:\server\login\RegisterGameServer.bat` genera `hexid.txt` y lo copia a `C:\server\gameserver\config\`. (En el primer arranque se crean las cuentas automáticamente al registrarse.)
3. **Gameserver** — `C:\server\gameserver\startGameServer.bat` (escucha en `*:9014` y se conecta al login en la IP de `loginserver.properties`).

### 4. Cliente

Apuntar el cliente L2 a la IP del server (ej. `192.168.100.141`) con puerto de login `2106`.

---

## Configuración

| Archivo | Contenido |
|---|---|
| `factionwar.properties` | Guerra: facciones, mapa por defecto, duración, freeze, checkpoints, respawn al morir, premios |
| `faction_flags.xml` | Banderas y checkpoints por mapa (12 mapIds) |
| `faction.xml` | Definición de facciones (id, nombre, colores) |
| `events.properties` | 16 eventos: nombres, niveles, tiempos, posiciones, buffer del evento |
| `phantoms.properties` | Phantoms: cantidad, niveles, equipo, chat, participación en guerra |
| `players.properties` | AutoLearnSkills, nivel inicial, Newbie System, `EnchantHeroWeapons*`, `SkillDurationList` |
| `geoengine.properties` | Geodata: ruta, formato y las 18 regiones cargadas |
| `loginserver.properties` / `server.properties` | Puertos, IPs, GM defaults |
| `npcs.properties`, `clans.properties`, etc. | Resto del server estándar aCis |

---

## Comandos

### Admin

| Comando | Descripción |
|---|---|
| `//factionwar start [duracion]` / `stop` | Inicia / detiene la Faction War |
| `//factionwar register [player]` / `registerall` | Registra jugadores |
| `//factionwar score` / `reload` | Scoreboard / recarga config |
| `//phantom` | Panel de control de phantoms (estado, stats, crear, facciones, guerra) |
| `//phantom start` / `home` | Carga phantoms guardados / los devuelve a su base |
| `//phantom warforcejoin <1|2>` / `warforceleave` | Asigna / quita facción a phantoms |
| `//phantom warteleportin` / `warteleportout` | Lleva / saca phantoms del mapa de guerra |
| `//phantom warteleportin` | Marca participantes y los manda al mapa |
| `//offline` | Lista, ve y cierra tiendas offline |

### Jugador

| Comando | Descripción |
|---|---|
| `.eventjoin` / `.eventjoin <id>` | Registrarse al evento abierto / a uno específico |
| `.eventleave` | Salir del evento |
| `.votemap 1-4` | Votar mapa en la fase de votación de la guerra |
| `.charge`, `.finfo`, `.fhelp` | Info de facción y ayuda |

---

## Higiene del repositorio

Estas rutas son **locales y NO se suben a GitHub** (definidas en `.gitignore`):

| Ruta | Motivo |
|---|---|
| `.freebuff/`, `.agents/`, `.claude/` | Estado local de herramientas de IA (sesiones, skills, permisos) |
| `.idea`, `.metadata`, `.settings` | Archivos de IDE |
| `*.log`, `hs_err_pid*.log` | Logs y crash dumps |
| `*.class`, `build/` | Clases compiladas |
| `*.db`, `*.db-shm`, `*.db-wal` | Bases SQLite locales |

**Reglas al commitear:**

- Nunca usar `git add -A` ni `git add .` — agregar solo los archivos del cambio.
- No commitear `.freebuff/`, `.agents/` ni `.claude/`.
- Si se clona el repo en otra PC, copiar las skills de `.agents/` y `.claude/` manualmente (no vienen con el clone).

---

## Créditos

Basado en **aCis** (fork de L2J Server). Modificado por la comunidad para funcionar como servidor de Faction War con phantoms, eventos y sistemas custom.
