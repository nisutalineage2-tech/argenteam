package net.sf.l2j.gameserver.phantom;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.l2j.commons.config.ExProperties;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.model.location.Location;

public final class PhantomConfig
{
	private static final CLogger LOGGER = new CLogger(PhantomConfig.class.getName());
	private static final String CONFIG_FILE = "./config/phantoms.properties";
	
	private static List<Integer> _phantomIds = Collections.emptyList();
	private static List<LevelZone> _levelZones = Collections.emptyList();
	private static int[] _cleanupItemIds = new int[0];
	private static int[] _cleanupKeepItemIds = new int[0];
	
	private static boolean _spawnAtGm = true;
	private static boolean _aiEnabled = true;
	private static int _aiTickMs = 3500;
	private static int _aggroRange = 900;
	private static int _wanderRange = 280;
	private static boolean _persistCreated = true;
	private static boolean _rememberLastLocation = true;
	private static boolean _mageUseSkills = true;
	private static int _mageSkillChance = 85;
	private static int _minMageMpPercent = 15;
	private static boolean _fighterUseSkills = false;
	private static boolean _autoEquip = false;
	private static boolean _autoBuff = false;
	private static int _autoBuffIntervalMs = 3600000;
	private static boolean _useLevelZones = false;
	private static int _levelZoneRadius = 650;
	private static int _levelZoneTeleportChance = 20;
	private static boolean _initialLevelZoneTeleport = false;
	private static boolean _autoMoveToFarmZones = true;
	private static int _autoMoveToFarmZonesDelayMs = 30000;
	private static int _farmZoneMoveStep = 10;
	private static boolean _farmZoneUseNpcSpawns = true;
	private static int _farmZoneNpcSearchRadius = 5000;
	private static int _farmZoneNpcLevelTolerance = 8;
	private static int _farmZoneNpcSpread = 350;
	private static boolean _autoLootDrops = false;
	private static int _autoLootRange = 350;
	private static boolean _lootOwnDropsOnly = true;
	private static int _lootOwnDropRadius = 350;
	private static int _lootOwnDropMemoryMs = 20000;
	private static int _lootClaimMs = 10000;
	private static boolean _autoCleanupInventory = false;
	private static int _cleanupInventoryLoadPercent = 40;
	private static int _cleanupIntervalTicks = 8;
	private static boolean _cleanupAllNonEquipable = false;
	private static boolean _autoEquipRefresh = false;
	private static int _equipmentIncompleteChance = 22;
	private static int _equipmentMixedChance = 28;
	private static String _equipGrade = "AS";
	private static boolean _respawnInTown = true;
	private static int _respawnDelayMs = 7000;
	private static boolean _returnToLevelZoneAfterDeath = true;
	private static int _returnToLevelZoneDelayMs = 12000;
	private static boolean _patrolEnabled = true;
	private static int _patrolRadius = 900;
	private static int _patrolWaypointReach = 140;
	private static boolean _avoidClaimedTargets = true;
	private static int _targetClaimMs = 12000;
	private static boolean _pvpEnabled = false;
	private static int _pvpRetaliationHits = 2;
	private static int _pvpHitMemoryMs = 15000;
	private static int _pvpResponseRange = 900;
	private static boolean _pkEnabled = false;
	private static int _pkRagePercent = 1;
	private static int _pkSearchRange = 900;
	private static int _pkKarma = 720;
	private static boolean _pkAttackRealPlayers = true;
	private static boolean _pkRageTransfersOnDeath = true;
	private static boolean _attackOnlyEnemyFaction = true;
	private static boolean _bossHuntEnabled = true;
	private static int _bossHuntMaxPerBoss = 4;
	
	private static boolean _useGeoPathing = true;
	private static boolean _stuckCheckEnabled = true;
	private static int _stuckCheckDistance = 40;
	private static int _stuckCheckTicks = 2;
	private static int _stuckEscapeRadius = 450;
	private static boolean _stuckTeleportToFarmZone = true;
	private static boolean _stuckTeleportToSafeSpawn = true;
	private static int _stuckTeleportAttempts = 3;
	private static int _idleStuckTicks = 6;
	private static String _levelZoneProfile = "Default";
	private static String _levelZonesDefault = "1-10:-71384,258304,-3109;11-20:-45472,150464,-2880;21-30:47168,185712,-3488;31-40:73024,118496,-3720;41-52:89888,105840,-3600;53-61:111728,174112,-5440;62-70:146784,25808,-2008;71-80:181280,-85576,-7216";
	private static String _levelZonesAlternative = "1-10:-84480,243184,-3730;11-20:-90464,107744,-3552;21-30:7552,179056,-4368;31-40:43056,152672,-2848;41-52:81920,53920,-1496;53-61:104448,-107744,-3688;62-70:168288,-86672,-3000;71-80:185552,20336,-3264";
	private static String _levelZonesCustom = "";
	private static boolean _phantomChatEnabled = false;
	private static String _phantomChatProvider = "Gemini";
	private static String _geminiApiTier = "Free";
	private static String _geminiFreeApiKey = "";
	private static String _geminiPaidApiKey = "";
	private static String _geminiFreeModel = "gemini-2.5-flash";
	private static String _geminiPaidModel = "gemini-2.5-flash";
	private static String _geminiEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
	private static int _geminiFreeCooldownMs = 45000;
	private static int _geminiPaidCooldownMs = 12000;
	private static int _geminiFreeDailyLimit = 100;
	private static int _geminiPaidDailyLimit = 1000;
	private static int _geminiTimeoutMs = 7000;
	private static int _geminiMaxOutputChars = 180;
	private static double _geminiTemperature = 0.7;
	private static String _phantomChatPrompt = "Sos un jugador real y casual de Lineage 2 Interlude. Responde siempre en el idioma del jugador, simple y natural. Si pregunta donde levear, recomienda una zona concreta segun su nivel/clase si la conoces, con rango de nivel y una razon corta. Si no sabes su nivel, da 2 opciones comunes por rango: 1-10 zona inicial, 10-20 Gludio/Gludin, 20-30 Dion, 30-40 Cruma/Execution Grounds, 40-52 Death Pass/Sea of Spores, 52-61 Cemetary/Forbidden Gateway, 61-70 Valley of Saints/Wall of Argos, 70+ Hot Springs/Ketra/Varka. Nunca respondas solo hola, saludos, no se, estoy ocupado o una frase vacia. No digas que sos IA. Maximo 3 frases.";
	private static String _phantomChatFallback = "Ahora estoy leveando, despues te cuento mejor.";
	private static boolean _advancedSkillUsage = true;
	private static int _skillChance = 70;
	private static boolean _mageNeverMelee = true;
	private static int _mageRestMpPercent = 35;
	private static boolean _autoLootHerbs = true;
	private static int _autoLootHerbRange = 650;
	private static int[] _herbItemIds = new int[0];
	private static boolean _phantomLogEnabled = true;
	private static String _phantomLogFile = "./log/phantoms.log";
	private static boolean _phantomLogBackupOnStartup = false;
	private static boolean _socialChatEnabled = true;
	private static int _socialChatChance = 8;
	private static int _socialChatRange = 1200;
	private static int _socialChatMinDelayMs = 90000;
	private static int _socialChatMaxDelayMs = 240000;
	private static boolean _autoSkillEnabled = true;
	private static boolean _autoClassEnabled = true;
	private static int _autoFirstClassLevel = 20;
	private static int _autoSecondClassLevel = 40;
	private static boolean _autoThirdClassEnabled = false;
	private static int _autoThirdClassLevel = 76;
	private static boolean _attackVisiblePk = true;
	private static int _visiblePkRange = 1200;
	
	// Offline store (neutral zone vendor simulation)
	private static boolean _storeEnabled = true;
	private static int _storeChance = 30;
	
	// Town NPC interaction simulation (walk up to a known NPC and "talk" to it while idle)
	private static boolean _npcInteractionEnabled = true;
	private static int _npcInteractionChance = 30;
	
	// FactionWar participation control (avoids emptying the cities during the war)
	private static int _warParticipationChance = 100;
	private static int _warMaxPerFaction = 0;
	private static int _warNearbyOnlyRange = 0;
	private static int _warRespawnDelayMs = 12000;
	private static int _warRespawnRandomMs = 8000;
	private static int[] _storeItemIds = new int[]
	{
		1835, 1836, 1837, 2509, 2510, 2511, 1061, 1062, 1064, 1073, 736
	};
	private static int _storeItemListMin = 2;
	private static int _storeItemListMax = 5;
	private static int _storeItemCountMin = 10;
	private static int _storeItemCountMax = 50;
	private static double _storePriceMultiplier = 1.0;
	private static String _storeTitle = "Vendo barato";
	private static int _storeDurationMinMs = 60000;
	private static int _storeDurationMaxMs = 180000;
	private PhantomConfig()
	{
	}
	
	public static void load()
	{
		final File file = new File(CONFIG_FILE);
		if (!file.exists())
		{
			LOGGER.warn("Phantom config file {} doesn't exist. No configured phantoms will be loaded.", CONFIG_FILE);
			setDefaults();
			return;
		}
		
		try
		{
			final ExProperties props = new ExProperties();
			props.load(file);
			
			_phantomLogEnabled = props.getProperty("PhantomLogEnabled", true);
			_phantomLogFile = props.getProperty("PhantomLogFile", "./log/phantoms.log");
			_phantomLogBackupOnStartup = props.getProperty("PhantomLogBackupOnStartup", false);
			PhantomLog.init();
			
			try
			{
				_phantomIds = parseUniqueIds(props.getProperty("PhantomIds", new int[0]));
			}
			catch (NumberFormatException e)
			{
				LOGGER.warn("Invalid PhantomIds value, resetting to empty.");
				_phantomIds = Collections.emptyList();
			}
			_spawnAtGm = props.getProperty("SpawnAtGm", true);
			// The phantom AI is always active by design; the AIEnabled property is ignored
			// so no config or admin toggle can ever leave phantoms static.
			_aiEnabled = true;
			_aiTickMs = Math.max(1000, props.getProperty("AITickMs", 3500));
			_aggroRange = Math.max(100, props.getProperty("AggroRange", 900));
			_wanderRange = Math.max(50, props.getProperty("WanderRange", 280));
			_persistCreated = props.getProperty("PersistCreated", true);
			_rememberLastLocation = props.getProperty("RememberLastLocation", true);
			_mageUseSkills = props.getProperty("MageUseSkills", true);
			_mageSkillChance = clampPercent(props.getProperty("MageSkillChance", 85));
			_minMageMpPercent = clampPercent(props.getProperty("MinMageMpPercent", 15));
			_fighterUseSkills = props.getProperty("FighterUseSkills", false);
			_autoEquip = props.getProperty("AutoEquip", false);
			_autoBuff = props.getProperty("AutoBuff", false);
			_autoBuffIntervalMs = Math.max(60000, props.getProperty("AutoBuffIntervalMs", 3600000));
			_useLevelZones = props.getProperty("UseLevelZones", false);
			_levelZoneRadius = Math.max(100, props.getProperty("LevelZoneRadius", 650));
			_levelZoneTeleportChance = clampPercent(props.getProperty("LevelZoneTeleportChance", 20));
			_initialLevelZoneTeleport = props.getProperty("InitialLevelZoneTeleport", false);
			_autoMoveToFarmZones = props.getProperty("AutoMoveToFarmZones", true);
			_autoMoveToFarmZonesDelayMs = Math.max(0, props.getProperty("AutoMoveToFarmZonesDelayMs", 30000));
			_farmZoneMoveStep = Math.max(1, props.getProperty("FarmZoneMoveStep", 10));
			_farmZoneUseNpcSpawns = props.getProperty("FarmZoneUseNpcSpawns", true);
			_farmZoneNpcSearchRadius = Math.max(500, props.getProperty("FarmZoneNpcSearchRadius", 5000));
			_farmZoneNpcLevelTolerance = Math.max(0, props.getProperty("FarmZoneNpcLevelTolerance", 8));
			_farmZoneNpcSpread = Math.max(0, props.getProperty("FarmZoneNpcSpread", 350));
			_levelZoneProfile = props.getProperty("LevelZoneProfile", "Default");
			_levelZonesDefault = props.getProperty("LevelZonesDefault", "1-10:-71384,258304,-3109;11-20:-45472,150464,-2880;21-30:47168,185712,-3488;31-40:73024,118496,-3720;41-52:89888,105840,-3600;53-61:111728,174112,-5440;62-70:146784,25808,-2008;71-80:181280,-85576,-7216");
			_levelZonesAlternative = props.getProperty("LevelZonesAlternative", "1-10:-84480,243184,-3730;11-20:-90464,107744,-3552;21-30:7552,179056,-4368;31-40:43056,152672,-2848;41-52:81920,53920,-1496;53-61:104448,-107744,-3688;62-70:168288,-86672,-3000;71-80:185552,20336,-3264");
			_levelZonesCustom = props.getProperty("LevelZonesCustom", "");
			_levelZones = parseLevelZones(selectLevelZones(props.getProperty("LevelZones", "")));
			_autoLootDrops = props.getProperty("AutoLootDrops", false);
			_autoLootRange = Math.max(80, props.getProperty("AutoLootRange", 350));
			_lootOwnDropsOnly = props.getProperty("LootOwnDropsOnly", true);
			_lootOwnDropRadius = Math.max(80, props.getProperty("LootOwnDropRadius", 350));
			_lootOwnDropMemoryMs = Math.max(1000, props.getProperty("LootOwnDropMemoryMs", 20000));
			_lootClaimMs = Math.max(1000, props.getProperty("LootClaimMs", 10000));
			_autoCleanupInventory = props.getProperty("AutoCleanupInventory", false);
			_cleanupInventoryLoadPercent = clampPercent(props.getProperty("CleanupInventoryLoadPercent", 40));
			_cleanupIntervalTicks = Math.max(1, props.getProperty("CleanupIntervalTicks", 8));
			_cleanupItemIds = parseOptionalIds(props.getProperty("CleanupItemIds", ""));
			_cleanupKeepItemIds = parseOptionalIds(props.getProperty("CleanupKeepItemIds", ""));
			_cleanupAllNonEquipable = props.getProperty("CleanupAllNonEquipable", false);
			_autoEquipRefresh = props.getProperty("AutoEquipRefresh", false);
			_equipmentIncompleteChance = clampPercent(props.getProperty("EquipmentIncompleteChance", 22));
			_equipmentMixedChance = clampPercent(props.getProperty("EquipmentMixedChance", 28));
			_equipGrade = props.getProperty("EquipmentGrade", "AS").trim().toUpperCase();
			_respawnInTown = props.getProperty("RespawnInTown", true);
			_respawnDelayMs = Math.max(1000, props.getProperty("RespawnDelayMs", 7000));
			_returnToLevelZoneAfterDeath = props.getProperty("ReturnToLevelZoneAfterDeath", true);
			_returnToLevelZoneDelayMs = Math.max(1000, props.getProperty("ReturnToLevelZoneDelayMs", 12000));
			_patrolEnabled = props.getProperty("PatrolEnabled", true);
			_patrolRadius = Math.max(100, props.getProperty("PatrolRadius", 900));
			_patrolWaypointReach = Math.max(50, props.getProperty("PatrolWaypointReach", 140));
			_avoidClaimedTargets = props.getProperty("AvoidClaimedTargets", true);
			_targetClaimMs = Math.max(1000, props.getProperty("TargetClaimMs", 12000));
			_pvpEnabled = props.getProperty("PvpEnabled", false);
			_pvpRetaliationHits = Math.max(1, props.getProperty("PvpRetaliationHits", 2));
			_pvpHitMemoryMs = Math.max(1000, props.getProperty("PvpHitMemoryMs", 15000));
			_pvpResponseRange = Math.max(100, props.getProperty("PvpResponseRange", 900));
			_pkEnabled = props.getProperty("PkEnabled", false);
			_pkRagePercent = clampPercent(props.getProperty("PkRagePercent", 1));
			_pkSearchRange = Math.max(100, props.getProperty("PkSearchRange", 900));
			_pkKarma = Math.max(0, props.getProperty("PkKarma", 720));
			_pkAttackRealPlayers = props.getProperty("PkAttackRealPlayers", true);
			_pkRageTransfersOnDeath = props.getProperty("PkRageTransfersOnDeath", true);
			_attackOnlyEnemyFaction = props.getProperty("AttackOnlyEnemyFaction", true);
			_bossHuntEnabled = props.getProperty("BossHuntEnabled", true);
			_bossHuntMaxPerBoss = Math.max(1, props.getProperty("BossHuntMaxPerBoss", 4));
			_useGeoPathing = props.getProperty("UseGeoPathing", true);
			_stuckCheckEnabled = props.getProperty("StuckCheckEnabled", true);
			_stuckCheckDistance = Math.max(5, props.getProperty("StuckCheckDistance", 40));
			_stuckCheckTicks = Math.max(1, props.getProperty("StuckCheckTicks", 2));
			_stuckEscapeRadius = Math.max(100, props.getProperty("StuckEscapeRadius", 450));
			_stuckTeleportToFarmZone = props.getProperty("StuckTeleportToFarmZone", true);
			_stuckTeleportToSafeSpawn = props.getProperty("StuckTeleportToSafeSpawn", true);
			_stuckTeleportAttempts = Math.max(1, props.getProperty("StuckTeleportAttempts", 3));
			_idleStuckTicks = Math.max(1, props.getProperty("IdleStuckTicks", 6));
			_phantomChatEnabled = props.getProperty("PhantomChatEnabled", false);
			_phantomChatProvider = props.getProperty("PhantomChatProvider", "Gemini");
			_geminiApiTier = props.getProperty("GeminiApiTier", "Free");
			_geminiFreeApiKey = props.getProperty("GeminiFreeApiKey", "").trim();
			_geminiPaidApiKey = props.getProperty("GeminiPaidApiKey", "").trim();
			_geminiFreeModel = props.getProperty("GeminiFreeModel", "gemini-2.5-flash");
			_geminiPaidModel = props.getProperty("GeminiPaidModel", "gemini-2.5-flash");
			_geminiEndpoint = props.getProperty("GeminiEndpoint", "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent");
			_geminiFreeCooldownMs = Math.max(1000, props.getProperty("GeminiFreeCooldownMs", 45000));
			_geminiPaidCooldownMs = Math.max(1000, props.getProperty("GeminiPaidCooldownMs", 12000));
			_geminiFreeDailyLimit = Math.max(0, props.getProperty("GeminiFreeDailyLimit", 100));
			_geminiPaidDailyLimit = Math.max(0, props.getProperty("GeminiPaidDailyLimit", 1000));
			_geminiTimeoutMs = Math.max(1000, props.getProperty("GeminiTimeoutMs", 7000));
			_geminiMaxOutputChars = Math.max(40, props.getProperty("GeminiMaxOutputChars", 180));
			_geminiTemperature = Math.max(0.0, Math.min(2.0, parseDouble(props.getProperty("GeminiTemperature", "0.7"), 0.7)));
			_phantomChatPrompt = props.getProperty("PhantomChatPrompt", _phantomChatPrompt);
			_phantomChatFallback = props.getProperty("PhantomChatFallback", _phantomChatFallback);
			_advancedSkillUsage = props.getProperty("AdvancedSkillUsage", true);
			_skillChance = clampPercent(props.getProperty("SkillChance", 70));
			_mageNeverMelee = props.getProperty("MageNeverMelee", true);
			_mageRestMpPercent = clampPercent(props.getProperty("MageRestMpPercent", 35));
			_autoLootHerbs = props.getProperty("AutoLootHerbs", true);
			_autoLootHerbRange = Math.max(80, props.getProperty("AutoLootHerbRange", 650));
			_herbItemIds = parseOptionalIds(props.getProperty("HerbItemIds", ""));
			
			// Offline store (neutral zone vendor simulation)
			_storeEnabled = props.getProperty("StoreEnabled", true);
			_storeChance = clampPercent(props.getProperty("StoreChance", 30));
			_storeItemIds = parseOptionalIds(props.getProperty("StoreItemIds", "1835,1836,1837,2509,2510,2511,1061,1062,1064,1073,736"));
			_storeItemListMin = Math.max(1, props.getProperty("StoreItemListMin", 2));
			_storeItemListMax = Math.max(_storeItemListMin, props.getProperty("StoreItemListMax", 5));
			_storeItemCountMin = Math.max(1, props.getProperty("StoreItemCountMin", 10));
			_storeItemCountMax = Math.max(_storeItemCountMin, props.getProperty("StoreItemCountMax", 50));
			_storePriceMultiplier = Math.max(0.1, parseDouble(props.getProperty("StorePriceMultiplier", "1.0"), 1.0));
			_storeTitle = props.getProperty("StoreTitle", "Vendo barato");
			_storeDurationMinMs = Math.max(5000, props.getProperty("StoreDurationMinMs", 60000));
			_storeDurationMaxMs = Math.max(_storeDurationMinMs, props.getProperty("StoreDurationMaxMs", 180000));
			
			// Town NPC interaction simulation
			_npcInteractionEnabled = props.getProperty("NpcInteractionEnabled", true);
			_npcInteractionChance = clampPercent(props.getProperty("NpcInteractionChance", 30));
			
			// FactionWar participation control
			_warParticipationChance = clampPercent(props.getProperty("WarParticipationChance", 100));
			_warMaxPerFaction = Math.max(0, props.getProperty("WarMaxPerFaction", 0));
			_warNearbyOnlyRange = Math.max(0, props.getProperty("WarNearbyOnlyRange", 0));
			_warRespawnDelayMs = Math.max(1000, props.getProperty("WarRespawnDelayMs", 12000));
			_warRespawnRandomMs = Math.max(0, props.getProperty("WarRespawnRandomMs", 8000));
			LOGGER.info("Loaded {} configured phantom ids and {} level zones.", _phantomIds.size(), _levelZones.size());
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to load {}.", e, CONFIG_FILE);
			setDefaults();
			PhantomLog.init();
			PhantomLog.error("Failed to load phantom config", e);
		}
	}
	
	private static void setDefaults()
	{
		_phantomIds = Collections.emptyList();
		_levelZones = Collections.emptyList();
		_cleanupItemIds = new int[0];
		_cleanupKeepItemIds = new int[0];
		_spawnAtGm = true;
		_aiEnabled = true;
		_aiTickMs = 3500;
		_aggroRange = 900;
		_wanderRange = 280;
		_persistCreated = true;
		_rememberLastLocation = true;
		_mageUseSkills = true;
		_mageSkillChance = 85;
		_minMageMpPercent = 15;
		_fighterUseSkills = false;
		_autoEquip = false;
		_autoBuff = false;
		_autoBuffIntervalMs = 3600000;
		_useLevelZones = false;
		_levelZoneRadius = 650;
		_levelZoneTeleportChance = 20;
		_autoMoveToFarmZones = true;
		_autoMoveToFarmZonesDelayMs = 30000;
		_farmZoneMoveStep = 10;
		_farmZoneUseNpcSpawns = true;
		_farmZoneNpcSearchRadius = 5000;
		_farmZoneNpcLevelTolerance = 8;
		_farmZoneNpcSpread = 350;
		_autoLootDrops = false;
		_autoLootRange = 350;
		_lootOwnDropsOnly = true;
		_lootOwnDropRadius = 350;
		_lootOwnDropMemoryMs = 20000;
		_lootClaimMs = 10000;
		_autoCleanupInventory = false;
		_cleanupInventoryLoadPercent = 40;
		_cleanupIntervalTicks = 8;
		_cleanupAllNonEquipable = false;
		_autoEquipRefresh = false;
		_equipmentIncompleteChance = 22;
		_equipmentMixedChance = 28;
		_respawnInTown = true;
		_respawnDelayMs = 7000;
		_returnToLevelZoneAfterDeath = true;
		_returnToLevelZoneDelayMs = 12000;
		_patrolEnabled = true;
		_patrolRadius = 900;
		_patrolWaypointReach = 140;
		_avoidClaimedTargets = true;
		_targetClaimMs = 12000;
		_pvpEnabled = false;
		_pvpRetaliationHits = 2;
		_pvpHitMemoryMs = 15000;
		_pvpResponseRange = 900;
		_pkEnabled = false;
		_pkRagePercent = 1;
		_pkSearchRange = 900;
		_pkKarma = 720;
		_pkAttackRealPlayers = true;
		_pkRageTransfersOnDeath = true;
		_bossHuntEnabled = true;
		_bossHuntMaxPerBoss = 4;
		_useGeoPathing = true;
		_stuckCheckEnabled = true;
		_stuckCheckDistance = 40;
		_stuckCheckTicks = 2;
		_stuckEscapeRadius = 450;
		_stuckTeleportToFarmZone = true;
		_stuckTeleportToSafeSpawn = true;
		_stuckTeleportAttempts = 3;
		_idleStuckTicks = 6;
		_levelZoneProfile = "Default";
		_levelZonesDefault = "1-10:-71384,258304,-3109;11-20:-45472,150464,-2880;21-30:47168,185712,-3488;31-40:73024,118496,-3720;41-52:89888,105840,-3600;53-61:111728,174112,-5440;62-70:146784,25808,-2008;71-80:181280,-85576,-7216";
		_levelZonesAlternative = "1-10:-84480,243184,-3730;11-20:-90464,107744,-3552;21-30:7552,179056,-4368;31-40:43056,152672,-2848;41-52:81920,53920,-1496;53-61:104448,-107744,-3688;62-70:168288,-86672,-3000;71-80:185552,20336,-3264";
		_levelZonesCustom = "";
		_phantomChatEnabled = false;
		_phantomChatProvider = "Gemini";
		_geminiApiTier = "Free";
		_geminiFreeApiKey = "";
		_geminiPaidApiKey = "";
		_geminiFreeModel = "gemini-2.5-flash";
		_geminiPaidModel = "gemini-2.5-flash";
		_geminiEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
		_geminiFreeCooldownMs = 45000;
		_geminiPaidCooldownMs = 12000;
		_geminiFreeDailyLimit = 100;
		_geminiPaidDailyLimit = 1000;
		_geminiTimeoutMs = 7000;
		_geminiMaxOutputChars = 180;
		_geminiTemperature = 0.7;
		_phantomChatPrompt = "Sos un jugador real y casual de Lineage 2 Interlude. Responde siempre en el idioma del jugador, simple y natural. Si pregunta donde levear, recomienda una zona concreta segun su nivel/clase si la conoces, con rango de nivel y una razon corta. Si no sabes su nivel, da 2 opciones comunes por rango: 1-10 zona inicial, 10-20 Gludio/Gludin, 20-30 Dion, 30-40 Cruma/Execution Grounds, 40-52 Death Pass/Sea of Spores, 52-61 Cemetary/Forbidden Gateway, 61-70 Valley of Saints/Wall of Argos, 70+ Hot Springs/Ketra/Varka. Nunca respondas solo hola, saludos, no se, estoy ocupado o una frase vacia. No digas que sos IA. Maximo 3 frases.";
		_phantomChatFallback = "Ahora estoy leveando, despues te cuento mejor.";
		_advancedSkillUsage = true;
		_skillChance = 70;
		_mageNeverMelee = true;
		_mageRestMpPercent = 35;
		_autoLootHerbs = true;
		_autoLootHerbRange = 650;
		_herbItemIds = new int[0];
		_storeEnabled = true;
		_storeChance = 30;
		_storeItemIds = new int[]
		{
			1835, 1836, 1837, 2509, 2510, 2511, 1061, 1062, 1064, 1073, 736
		};
		_storeItemListMin = 2;
		_storeItemListMax = 5;
		_storeItemCountMin = 10;
		_storeItemCountMax = 50;
		_storePriceMultiplier = 1.0;
		_storeTitle = "Vendo barato";
		_storeDurationMinMs = 60000;
		_storeDurationMaxMs = 180000;
		_npcInteractionEnabled = true;
		_npcInteractionChance = 30;
		_warParticipationChance = 100;
		_warMaxPerFaction = 0;
		_warNearbyOnlyRange = 0;
		_warRespawnDelayMs = 12000;
		_warRespawnRandomMs = 8000;
		_phantomLogEnabled = true;
		_phantomLogFile = "./log/phantoms.log";
		_phantomLogBackupOnStartup = false;
	}
	
	private static List<Integer> parseUniqueIds(int[] ids)
	{
		final List<Integer> parsedIds = new ArrayList<>();
		for (int id : ids)
		{
			if (id > 0 && !parsedIds.contains(id))
				parsedIds.add(id);
		}
		return Collections.unmodifiableList(parsedIds);
	}
	
	private static String selectLevelZones(String legacyZones)
	{
		if ("Custom".equalsIgnoreCase(_levelZoneProfile) && !_levelZonesCustom.isBlank())
			return _levelZonesCustom;
		
		if ("Alternative".equalsIgnoreCase(_levelZoneProfile) && !_levelZonesAlternative.isBlank())
			return _levelZonesAlternative;
		
		if (!_levelZonesDefault.isBlank())
			return _levelZonesDefault;
		
		return legacyZones;
	}
	private static List<LevelZone> parseLevelZones(String value)
	{
		if (value == null || value.isBlank())
			return Collections.emptyList();
		
		final List<LevelZone> zones = new ArrayList<>();
		for (String token : value.split(";"))
		{
			final String[] parts = token.trim().split(":");
			if (parts.length != 2)
				continue;
			
			final String[] levels = parts[0].split("-");
			final String[] loc = parts[1].split(",");
			if (levels.length != 2 || loc.length != 3)
				continue;
			
			try
			{
				zones.add(new LevelZone(Integer.parseInt(levels[0].trim()), Integer.parseInt(levels[1].trim()), new Location(Integer.parseInt(loc[0].trim()), Integer.parseInt(loc[1].trim()), Integer.parseInt(loc[2].trim()))));
			}
			catch (NumberFormatException e)
			{
				LOGGER.warn("Invalid Phantom LevelZones entry: {}.", token);
			}
		}
		return Collections.unmodifiableList(zones);
	}
	
	private static int clampPercent(int value)
	{
		return Math.max(0, Math.min(100, value));
	}
	
		private static int[] parseOptionalIds(String value)
	{
		if (value == null || value.isBlank())
			return new int[0];
		
		final List<Integer> ids = new ArrayList<>();
		for (String token : value.split("[,; ]+"))
		{
			if (token == null || token.isBlank())
				continue;
			
			try
			{
				ids.add(Integer.parseInt(token.trim()));
			}
			catch (NumberFormatException e)
			{
				LOGGER.warn("Invalid Phantom item id: {}.", token);
				PhantomLog.warn("Invalid Phantom item id: " + token);
			}
		}
		
		final int[] parsed = new int[ids.size()];
		for (int i = 0; i < ids.size(); i++)
			parsed[i] = ids.get(i);
		
		return parsed;
	}
	
	private static double parseDouble(String value, double defaultValue)
	{
		if (value == null || value.isBlank())
			return defaultValue;
		
		try
		{
			return Double.parseDouble(value.trim());
		}
		catch (NumberFormatException e)
		{
			LOGGER.warn("Invalid Phantom decimal value: {}.", value);
			PhantomLog.warn("Invalid Phantom decimal value: " + value);
			return defaultValue;
		}
	}
	public static synchronized void addPhantomId(int objectId)
	{
		if (!_persistCreated || objectId <= 0 || _phantomIds.contains(objectId))
			return;
		
		final List<Integer> ids = new ArrayList<>(_phantomIds);
		ids.add(objectId);
		_phantomIds = Collections.unmodifiableList(ids);
		saveIds(ids);
	}
	
	public static synchronized boolean removePhantomId(int objectId)
	{
		if (objectId <= 0 || !_phantomIds.contains(objectId))
			return false;
		
		final List<Integer> ids = new ArrayList<>(_phantomIds);
		ids.remove(Integer.valueOf(objectId));
		_phantomIds = Collections.unmodifiableList(ids);
		saveIds(ids);
		return true;
	}
	
	private static void saveIds(List<Integer> ids)
	{
		try
		{
			final File file = new File(CONFIG_FILE);
			
			// Don't write empty PhantomIds to avoid NumberFormatException on next load
			if (ids.isEmpty())
			{
				if (file.exists())
				{
					final List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
					lines.removeIf(l -> l.trim().startsWith("PhantomIds"));
					java.nio.file.Files.write(file.toPath(), lines);
				}
				return;
			}
			
			final StringBuilder sb = new StringBuilder();
			for (int id : ids)
			{
				if (sb.length() > 0)
					sb.append(',');
				sb.append(id);
			}
			
			final String line = "PhantomIds = " + sb;
			if (!file.exists())
			{
				try (FileOutputStream fos = new FileOutputStream(file))
				{
					fos.write((line + System.lineSeparator()).getBytes());
				}
				return;
			}
			
			final List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
			boolean updated = false;
			for (int i = 0; i < lines.size(); i++)
			{
				if (lines.get(i).trim().startsWith("PhantomIds"))
				{
					lines.set(i, line);
					updated = true;
					break;
				}
			}
			
			if (!updated)
				lines.add(line);
			
			java.nio.file.Files.write(file.toPath(), lines);
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to persist phantom ids into {}.", e, CONFIG_FILE);
			PhantomLog.error("Failed to persist phantom ids", e);
		}
	}
		public static Location getLevelZone(int level)
	{
		final List<LevelZone> matches = new ArrayList<>();
		for (LevelZone zone : _levelZones)
		{
			if (level >= zone.minLevel() && level <= zone.maxLevel())
				matches.add(zone);
		}
		return matches.isEmpty() ? null : matches.get(Rnd.get(matches.size())).location();
	}
	
	public static boolean contains(int[] ids, int itemId)
	{
		for (int id : ids)
		{
			if (id == itemId)
				return true;
		}
		return false;
	}
	
	public static List<Integer> getPhantomIds()
	{
		return _phantomIds;
	}
	
	public static boolean spawnAtGm()
	{
		return _spawnAtGm;
	}
	
	public static boolean aiEnabled()
	{
		return _aiEnabled;
	}
	
	public static int aiTickMs()
	{
		return _aiTickMs;
	}
	
	public static int aggroRange()
	{
		return _aggroRange;
	}
	
	public static int wanderRange()
	{
		return _wanderRange;
	}
	
	public static boolean persistCreated()
	{
		return _persistCreated;
	}
	
	public static boolean rememberLastLocation()
	{
		return _rememberLastLocation;
	}
	
	public static boolean mageUseSkills()
	{
		return _mageUseSkills;
	}
	
	public static int mageSkillChance()
	{
		return _mageSkillChance;
	}
	
	public static int minMageMpPercent()
	{
		return _minMageMpPercent;
	}
	
	public static boolean fighterUseSkills()
	{
		return _fighterUseSkills;
	}
	
	public static boolean autoEquip()
	{
		return _autoEquip;
	}
	
	public static boolean autoBuff()
	{
		return _autoBuff;
	}
	
	public static int autoBuffIntervalMs()
	{
		return _autoBuffIntervalMs;
	}
	
	public static boolean useLevelZones()
	{
		return _useLevelZones;
	}
	
	public static int levelZoneRadius()
	{
		return _levelZoneRadius;
	}
	
	public static int levelZoneTeleportChance()
	{
		return _levelZoneTeleportChance;
	}
	
	public static boolean initialLevelZoneTeleport()
	{
		return _initialLevelZoneTeleport;
	}
	
	public static boolean autoMoveToFarmZones()
	{
		return _autoMoveToFarmZones;
	}
	
	public static int autoMoveToFarmZonesDelayMs()
	{
		return _autoMoveToFarmZonesDelayMs;
	}
	
	public static int farmZoneMoveStep()
	{
		return _farmZoneMoveStep;
	}
	
	public static boolean farmZoneUseNpcSpawns()
	{
		return _farmZoneUseNpcSpawns;
	}
	
	public static int farmZoneNpcSearchRadius()
	{
		return _farmZoneNpcSearchRadius;
	}
	
	public static int farmZoneNpcLevelTolerance()
	{
		return _farmZoneNpcLevelTolerance;
	}
	
	public static int farmZoneNpcSpread()
	{
		return _farmZoneNpcSpread;
	}
	
	public static boolean autoLootDrops()
	{
		return _autoLootDrops;
	}
	
	public static int autoLootRange()
	{
		return _autoLootRange;
	}
	
	public static boolean lootOwnDropsOnly()
	{
		return _lootOwnDropsOnly;
	}
	
	public static int lootOwnDropRadius()
	{
		return _lootOwnDropRadius;
	}
	
	public static int lootOwnDropMemoryMs()
	{
		return _lootOwnDropMemoryMs;
	}
	
	public static int lootClaimMs()
	{
		return _lootClaimMs;
	}
	
	public static boolean autoCleanupInventory()
	{
		return _autoCleanupInventory;
	}
	
	public static int cleanupInventoryLoadPercent()
	{
		return _cleanupInventoryLoadPercent;
	}
	
	public static int cleanupIntervalTicks()
	{
		return _cleanupIntervalTicks;
	}
	
	public static int[] cleanupItemIds()
	{
		return _cleanupItemIds;
	}
	
	public static int[] cleanupKeepItemIds()
	{
		return _cleanupKeepItemIds;
	}
	
	public static boolean cleanupAllNonEquipable()
	{
		return _cleanupAllNonEquipable;
	}
	
	public static boolean autoEquipRefresh()
	{
		return _autoEquipRefresh;
	}

	public static int equipmentIncompleteChance()
	{
		return _equipmentIncompleteChance;
	}
	
	public static int equipmentMixedChance()
	{
		return _equipmentMixedChance;
	}
	
	public static String equipGrade()
	{
		return _equipGrade;
	}
	
	public static boolean respawnInTown()
	{
		return _respawnInTown;
	}
	
	public static int respawnDelayMs()
	{
		return _respawnDelayMs;
	}
	
	public static boolean returnToLevelZoneAfterDeath()
	{
		return _returnToLevelZoneAfterDeath;
	}
	
	public static int returnToLevelZoneDelayMs()
	{
		return _returnToLevelZoneDelayMs;
	}
	
	public static boolean patrolEnabled()
	{
		return _patrolEnabled;
	}
	
	public static int patrolRadius()
	{
		return _patrolRadius;
	}
	
	public static int patrolWaypointReach()
	{
		return _patrolWaypointReach;
	}
	
	public static boolean avoidClaimedTargets()
	{
		return _avoidClaimedTargets;
	}
	
	public static int targetClaimMs()
	{
		return _targetClaimMs;
	}
	
	public static boolean pvpEnabled()
	{
		return _pvpEnabled;
	}
	
	public static int pvpRetaliationHits()
	{
		return _pvpRetaliationHits;
	}
	
	public static int pvpHitMemoryMs()
	{
		return _pvpHitMemoryMs;
	}
	
	public static int pvpResponseRange()
	{
		return _pvpResponseRange;
	}
	
	public static boolean pkEnabled()
	{
		return _pkEnabled;
	}
	
	public static int pkRagePercent()
	{
		return _pkRagePercent;
	}
	
	public static int pkSearchRange()
	{
		return _pkSearchRange;
	}
	
	public static int pkKarma()
	{
		return _pkKarma;
	}
	
	public static boolean pkAttackRealPlayers()
	{
		return _pkAttackRealPlayers;
	}
	
	public static boolean pkRageTransfersOnDeath()
	{
		return _pkRageTransfersOnDeath;
	}
	
	public static boolean attackOnlyEnemyFaction()
	{
		return _attackOnlyEnemyFaction;
	}
	
	public static boolean bossHuntEnabled()
	{
		return _bossHuntEnabled;
	}
	
	public static int bossHuntMaxPerBoss()
	{
		return _bossHuntMaxPerBoss;
	}
	
	public static boolean useGeoPathing()
	{
		return _useGeoPathing;
	}
	
	public static boolean stuckCheckEnabled()
	{
		return _stuckCheckEnabled;
	}
	
	public static int stuckCheckDistance()
	{
		return _stuckCheckDistance;
	}
	
	public static int stuckCheckTicks()
	{
		return _stuckCheckTicks;
	}
	
	public static int stuckEscapeRadius()
	{
		return _stuckEscapeRadius;
	}
	
	public static boolean stuckTeleportToFarmZone()
	{
		return _stuckTeleportToFarmZone;
	}
	
	public static boolean stuckTeleportToSafeSpawn()
	{
		return _stuckTeleportToSafeSpawn;
	}
	
	public static int stuckTeleportAttempts()
	{
		return _stuckTeleportAttempts;
	}
	
	public static int idleStuckTicks()
	{
		return _idleStuckTicks;
	}
	public static String levelZoneProfile()
	{
		return _levelZoneProfile;
	}
	
	public static boolean phantomChatEnabled()
	{
		return _phantomChatEnabled;
	}
	
	public static String phantomChatProvider()
	{
		return _phantomChatProvider;
	}
	
	public static String geminiApiTier()
	{
		return _geminiApiTier;
	}
	
	public static String geminiApiKey()
	{
		return "Paid".equalsIgnoreCase(_geminiApiTier) ? _geminiPaidApiKey : _geminiFreeApiKey;
	}
	
	public static String geminiModel()
	{
		return "Paid".equalsIgnoreCase(_geminiApiTier) ? _geminiPaidModel : _geminiFreeModel;
	}
	
	public static String geminiEndpoint()
	{
		return _geminiEndpoint;
	}
	
	public static int geminiCooldownMs()
	{
		return "Paid".equalsIgnoreCase(_geminiApiTier) ? _geminiPaidCooldownMs : _geminiFreeCooldownMs;
	}
	
	public static int geminiDailyLimit()
	{
		return "Paid".equalsIgnoreCase(_geminiApiTier) ? _geminiPaidDailyLimit : _geminiFreeDailyLimit;
	}
	
	public static int geminiTimeoutMs()
	{
		return _geminiTimeoutMs;
	}
	
	public static int geminiMaxOutputChars()
	{
		return _geminiMaxOutputChars;
	}
	
	public static double geminiTemperature()
	{
		return _geminiTemperature;
	}
	
	public static String phantomChatPrompt()
	{
		return _phantomChatPrompt;
	}
	
	public static String phantomChatFallback()
	{
		return _phantomChatFallback;
	}
		public static boolean advancedSkillUsage()
	{
		return _advancedSkillUsage;
	}
	
	public static int skillChance()
	{
		return _skillChance;
	}
	
	public static boolean mageNeverMelee()
	{
		return _mageNeverMelee;
	}
	
	public static int mageRestMpPercent()
	{
		return _mageRestMpPercent;
	}
	
	public static boolean autoLootHerbs()
	{
		return _autoLootHerbs;
	}
	
	public static int autoLootHerbRange()
	{
		return _autoLootHerbRange;
	}
	
	public static int[] herbItemIds()
	{
		return _herbItemIds;
	}
	
	public static boolean phantomLogEnabled()
	{
		return _phantomLogEnabled;
	}
	
	public static String phantomLogFile()
	{
		return _phantomLogFile;
	}
	
	public static boolean phantomLogBackupOnStartup()
	{
		return _phantomLogBackupOnStartup;
	}
		public static boolean socialChatEnabled()
	{
		return _socialChatEnabled;
	}
	
	public static int socialChatChance()
	{
		return _socialChatChance;
	}
	
	public static int socialChatRange()
	{
		return _socialChatRange;
	}
	
	public static int socialChatMinDelayMs()
	{
		return _socialChatMinDelayMs;
	}
	
	public static int socialChatMaxDelayMs()
	{
		return _socialChatMaxDelayMs;
	}
	
	public static boolean autoSkillEnabled()
	{
		return _autoSkillEnabled;
	}
	
	public static boolean autoClassEnabled()
	{
		return _autoClassEnabled;
	}
	
	public static int autoFirstClassLevel()
	{
		return _autoFirstClassLevel;
	}
	
	public static int autoSecondClassLevel()
	{
		return _autoSecondClassLevel;
	}
	
	public static boolean autoThirdClassEnabled()
	{
		return _autoThirdClassEnabled;
	}
	
	public static int autoThirdClassLevel()
	{
		return _autoThirdClassLevel;
	}
	
	public static boolean attackVisiblePk()
	{
		return _attackVisiblePk;
	}
	
	public static int visiblePkRange()
	{
		return _visiblePkRange;
	}
	
	public static boolean storeEnabled()
	{
		return _storeEnabled;
	}
	
	public static int storeChance()
	{
		return _storeChance;
	}
	
	public static int[] storeItemIds()
	{
		return _storeItemIds;
	}
	
	public static int storeItemListMin()
	{
		return _storeItemListMin;
	}
	
	public static int storeItemListMax()
	{
		return _storeItemListMax;
	}
	
	public static int storeItemCountMin()
	{
		return _storeItemCountMin;
	}
	
	public static int storeItemCountMax()
	{
		return _storeItemCountMax;
	}
	
	public static double storePriceMultiplier()
	{
		return _storePriceMultiplier;
	}
	
	public static String storeTitle()
	{
		return _storeTitle;
	}
	
	public static int storeDurationMinMs()
	{
		return _storeDurationMinMs;
	}
	
	public static int storeDurationMaxMs()
	{
		return _storeDurationMaxMs;
	}
	
	public static boolean npcInteractionEnabled()
	{
		return _npcInteractionEnabled;
	}
	
	public static int npcInteractionChance()
	{
		return _npcInteractionChance;
	}
	
	public static int warParticipationChance()
	{
		return _warParticipationChance;
	}
	
	public static int warMaxPerFaction()
	{
		return _warMaxPerFaction;
	}
	
	public static int warNearbyOnlyRange()
	{
		return _warNearbyOnlyRange;
	}
	
	public static int warRespawnDelayMs()
	{
		return _warRespawnDelayMs;
	}
	
	public static int warRespawnRandomMs()
	{
		return _warRespawnRandomMs;
	}
		private record LevelZone(int minLevel, int maxLevel, Location location)
	{
	}
}
