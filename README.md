# aCis 409 — Faction War & Phantom System

## Visión General

**aCis 409** es un servidor privado de **Lineage 2 Interlude (C4)** modificado con un sistema de **Faction Wars** como único modo de juego. Reemplaza el sistema de eventos original con un conflicto persistente entre dos facciones, complementado por un sistema de phantoms (bots) que pueblan el mundo y participan en la guerra.

El servidor está diseñado para operar en un entorno LAN (IP: 192.168.100.141) con jugadores reales y phantoms coexistiendo.

---

## Sistema de Faction War

### Arquitectura

| Componente | Propósito |
|---|---|
| `FactionWarManager` | Singleton central. Gestiona el estado de la guerra (inicio, voto, puntuación, fin). |
| `FactionWarConfig` | Configuración estática (facciones, mapas, rewards, NPCs). |
| `FactionWarRegistry` | Persistencia SQL de inscripciones de jugadores. |
| `FactionWarCheckpoint` | Spawnea/despawnea NPCs checkpoint en el mapa de guerra. |
| `Flag` (NPC 90000) | Bandera destruible de cada facción. |
| `Guard` (NPC 90001) | Guardias que defienden cada base. |
| `WarRegistrar` (NPC 90002) | NPC en zona neutral para registro y voto de mapas. |
| `Checkpoint` (NPC 90003) | Puntos de control en el campo de batalla. |

### Ciclo de Vida

1. **Votación Inicial** (`startVotePhase`)
   - Se spawnear el Registrador en zona neutral.
   - Se seleccionan 4 mapas aleatorios.
   - Los jugadores votan mediante HTML popup o comando `.votemap`.
   - Tras 30 segundos, el mapa más votado gana.

2. **Inicio** (`start`)
   - Spawnea la bandera, guardias, registrador y checkpoints.
   - Todos los phantoms son teletransportados al mapa de guerra.
   - Comienza la transmisión periódica del scoreboard.

3. **Combate**
   - **PvP:** Mata enemigos de la facción contraria → puntos + recompensa en adena.
   - **Flag:** Destruye la bandera enemiga → puntos + teletransporta a la facción perdedora a su base.
   - **Guardias:** Defienden automáticamente la base, respawnean al morir.
   - **Checkpoints:** Puntos neutrales en el mapa.

4. **Rotación de Mapas**
   - Cada `MapRotationMinutes` (default: 30) se inicia una votación para cambiar de mapa.
   - Similar al voto inicial: 4 mapas aleatorios, 30 segundos para votar.

5. **Fin de Guerra** (`stop`)
   - Se determina el ganador por puntuación.
   - Se premia al Top 3 individual y a todos los miembros de la facción ganadora.
   - Los jugadores son teletransportados a zona neutral.
   - Los phantoms retornan a sus ubicaciones normales.

### Mapas de Guerra (8)

- Gludio, Dion, Giran, Oren, Aden, Goddard, Rune, Schuttgart
- Cada mapa tiene coordenadas de spawn para facción Good y Evil.
- Si un mapa no tiene spawns definidos, usa los globales `GoodSpawn` / `EvilSpawn`.

### Comandos Admin

| Comando | Descripción |
|---|---|
| `//factionwar start [score] [duration]` | Inicia la guerra |
| `//factionwar stop` | Detiene la guerra |
| `//factionwar register [player]` | Registra un jugador |
| `//factionwar registerall` | Registra todos los jugadores con facción |
| `//factionwar score` | Muestra el scoreboard |
| `//factionwar reload` | Recarga la configuración |

### Comandos de Jugador

| Comando | Descripción |
|---|---|
| `.votemap 1-4` | Vota por un mapa durante la fase de votación |

### Configuración (`factionwar.properties`)

Las propiedades clave incluyen:

| Propiedad | Default | Descripción |
|---|---|---|
| `Enabled` | `true` | Activa/desactiva el sistema de faction war |
| `GoodFactionId` / `EvilFactionId` | `1` / `2` | IDs de las facciones |
| `ScoreToWin` | `100` | Puntuación necesaria para ganar |
| `PointsPerFlagKill` | `1` | Puntos por destruir bandera |
| `PointsPerPvpKill` | `1` | Puntos por PvP |
| `FlagRespawnDelay` | `30` | Segundos para respawn de bandera |
| `GuardRespawnDelay` | `60` | Segundos para respawn de guardias |
| `MapRotationMinutes` | `30` | Minutos entre rotaciones de mapa |
| `MapVoteSeconds` | `30` | Segundos para votar |
| `WarDurationMinutes` | `120` | Duración máxima de la guerra (0 = sin límite) |
| `NeutralSpawnLoc` | — | Coordenadas de la zona neutral |
| `GoodSpawn` / `EvilSpawn` | — | Spawn global de cada facción |
| `RewardItemId` | `57` (Adena) | Item de recompensa |
| `Top1Reward` / `Top2Reward` / `Top3Reward` | — | Recompensas individuales |
| `WinningFactionReward` | — | Recompensa para miembros de facción ganadora |
| `PvpAdenaReward` | — | Adena por kill PvP |
| `SpoilItems` | — | Items de spoil aleatorio para la party del killer |

Los mapas se definen en la propiedad `Maps` con el formato:
```
Nombre,x,y,z,radio,goodSpawnX,goodSpawnY,goodSpawnZ,evilSpawnX,evilSpawnY,evilSpawnZ
```

---

## Sistema de Facciones

### Data Model

| Componente | Descripción |
|---|---|
| `Faction` | Entidad con id, nombre, color de nombre, color de título |
| `FactionData` (XML) | Carga/guarda facciones desde `faction.xml` |
| `FactionData` (DB) | Persiste en tabla `mods_faction` (char_id, factionId, factionPoints) |

### Flujo de Login

1. `EnterWorld.java` → `FactionData.onPlayerEnter()`
2. Restaura datos desde DB
3. Aplica colores de nombre/título según la facción
4. Si la war está activa, teletransporta a zona neutral con 3s de delay

### Event Engine

El `EventEngine` original fue reemplazado por un stub no-op. La alternancia entre eventos y Faction War fue eliminada. La Faction War es el único modo de juego activo.

---

## Sistema de Phantoms

### Arquitectura

| Componente | Propósito |
|---|---|
| `PhantomEngine` | Singleton central. Carga, spawn, gestión de phantoms. |
| `PhantomAI` | AI principal: pathfinding, combate, party, social. |
| `PhantomCombat` | Lógica de combate: target selection, skills, rage. |
| `PhantomSocial` | Chat social y frases de guerra de phantoms. |
| `PhantomState` | Máquina de estados (idle, combat, follow, etc). |
| `PhantomInventory` | Gestión de inventario de phantoms. |
| `PhantomEquipment` | Asignación de equipo según clase/nivel. |
| `PhantomProgression` | Progresión: subir nivel, aprender skills, mejorar equipo. |
| `PhantomLog` | Logging de phantoms. |
| `PhantomChat` | Chat de phantoms (Gemini API no utilizada por defecto). |

### Flujo de Inicio

1. `GameServer.java` inicializa `PhantomEngine.startConfigured()` en background thread.
2. Carga configuración desde `phantoms.properties`.
3. Crea instancias de phantoms y los spawnea en el mundo.
4. Los phantoms se comportan como jugadores reales: farmean, forman party, socializan.

### Participación en Faction War

- Cuando la Faction War comienza, `PhantomEngine.teleportPhantomsToWar()` teletransporta todos los phantoms con facción al mapa de guerra.
- Durante la guerra, los phantoms combaten según su facción.
- Al terminar la guerra, `returnPhantomsFromWar()` los devuelve a sus ubicaciones originales.

### Configuración (`phantoms.properties`)

| Propiedad | Descripción |
|---|---|
| `PhantomsEnabled` | Activar/desactivar phantoms |
| `PhantomCount` | Cantidad de phantoms a spawnear |
| `PhantomLevelMin/Max` | Rango de niveles |
| `SocialChatEnabled` | Activar chat social |
| `SocialChatChance` | Probabilidad de hablar (%) |
| `SocialChatMinDelayMs/MaxDelayMs` | Delay entre mensajes |

---

## Modificaciones al Engine Base

### `GameServer.java`
- Inicialización temprana de `FactionWarManager` y `PhantomEngine`.
- Agregado `Shutdown` hook para detener phantoms al apagar.

### `EnterWorld.java`
- Llamada a `FactionData.onPlayerEnter()` envuelta en try/catch.
- Logs `LOGGER.info` en puntos clave del flujo de entrada.

### `RequestGameStart.java`
- Logs de depuración para seguimiento de carga de personajes.

### `RequestBypassToServer.java`
- Bypass `fwVote` salta la verificación `canDoInteract`, permitiendo votar desde cualquier distancia.

### `Npc.java`
- Maneja bypass `fwVote_<index>` y lo redirige a `FactionWarManager.onPlayerVote()`.

### `Say2.java`
- Procesa comando `.votemap` (1-indexado, convertido a 0-indexado internamente).

### `L2GameClientPacket.java`
- Fix de logging (stack traces ahora se imprimen correctamente).

### `Player.java`, `Playable.java`
- Integración de sistema de facciones (getFactionId, setFactionId, getFactionPoints, etc.).

### `EventEngine.java`
- Stub no-op que reemplaza el sistema de eventos original.

### `compile.bat`
- Compila Gameserver + Datapack.
- Copia automáticamente a `C:\server\` con estructura: `gameserver\`, `login\`, `sql\`, `tools\`.

---

## Estructura de Directorios

```
C:\server\
├── gameserver\       <- JARs, configs, scripts, data (HTML, XML)
├── login\            <- Login server (JARs, configs, scripts)
├── sql\              <- Scripts SQL
└── tools\            <- Herramientas
```

---

## Requisitos Técnicos

- **Java 21** (JDK 21+)
- **MariaDB** (base de datos `acis`)
- **Cliente Lineage 2 Interlude (C4)** configurado para IP 192.168.100.141

---

## Créditos

Basado en **aCis** (fork de L2J Server).
Modificado por la comunidad para funcionar como servidor de Faction War con phantoms.
