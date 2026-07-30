package net.sf.l2j.gameserver.factionwar;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.l2j.commons.config.ExProperties;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.FactionFlag;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class FactionWarConfig
{
	private static final CLogger LOGGER = new CLogger(FactionWarConfig.class.getName());

	private static boolean _enabled;
	private static int _goodFactionId;
	private static int _evilFactionId;
	private static String _goodFactionName;
	private static String _evilFactionName;
	private static int _goodFactionColor;
	private static int _evilFactionColor;
	private static int _scoreToWin;
	private static int _pointsPerFlagKill;
	private static int _pointsPerPvpKill;
	private static int _flagNpcId;
	private static long _flagRespawnDelay;
	private static int _guardNpcId;
	private static int _guardsPerBase;
	private static int _guardCircleRadius;
	private static long _guardRespawnDelay;
	private static int _warRegistrarNpcId;
	private static int _checkpointNpcId;
	private static int _checkpointCount;
	private static int _checkpointRadius;
	private static int _mapRotationMinutes;
	private static int _mapVoteSeconds;
	private static final List<WarMap> _maps = new ArrayList<>();
	private static final Map<Integer, List<net.sf.l2j.gameserver.model.FactionFlag>> _xmlFlags = new HashMap<>();
	private static final Map<String, Integer> _mapFlagIds = new HashMap<>();
	private static Location _goodSpawnLoc;
	private static Location _evilSpawnLoc;
	private static Location _neutralSpawnLoc;
	private static boolean _announceStart;
	private static boolean _announceEnd;
	private static boolean _announceFlagKill;
	private static boolean _announcePvpKill;
	private static boolean _announceScore;
	private static boolean _announceMapSwitch;
	private static int _killRewardPoints;
	private static boolean _townRestriction;
	private static int _townRestrictionRadius;
	private static int _neutralZoneRadius;
	private static int _warDurationMinutes;
	
	// End freeze duration in seconds
	private static int _endFreezeSeconds;
	
	// Unstuck delay in seconds
	private static int _unstuckDelaySeconds;
	
	// Rewards
	private static int _rewardItemId;
	private static final int[] _topRewardAmounts = new int[3];
	private static int _winningFactionReward;
	private static int _pvpAdenaReward;
	private static final java.util.List<int[]> _spoilItems = new java.util.ArrayList<>();
	
	// Gameplay
	private static int _startingLevel;
	private static int _startingLevelSubClass;
	private static int _priceForFactionChange;
	private static boolean _enableAutoTeleportToBase;
	private static boolean _enableFactionGuards;
	private static boolean _enablePlayersBalanceLogin;
	private static boolean _enableMapVoting;
	private static boolean _enableFactionGuardsSpawn;
	
	// Anti-farm
	private static boolean _enableProtectionIP;
	private static boolean _enableProtectionClan;
	private static boolean _enableProtectionAlly;
	private static boolean _enableProtectionArmour;
	private static int _protectionArmourAmount;
	private static boolean _enableProtectionSamePlayer;
	
	// Class & equipment
	private static boolean _enableClassBalance;
	private static boolean _enableAntiHeavySystem;
	private static int _killsForSpecShop;
	private static boolean _forbidUseItem;
	private static boolean _enableAGradeLimit;
	private static int _killsForAGradeItem;
	
	// PvP EXP
	private static boolean _enablePvpExpReward;
	private static int _pvpExpRewardFirst;
	private static int _pvpExpRewardSecond;
	private static int _pvpExpRewardThird;
	
	// PvP items
	private static boolean _enablePvpItemReward;
	private static int _pvpItemRewardId;
	private static int _pvpItemRewardCount;
	
	// Party rewards
	private static boolean _enablePartyPvpReward;
	private static boolean _partyRewardOnlySupportClass;
	private static int _partyRewardChance;
	private static int _killerPartyBonus;
	
	// Faction points
	private static int _pointsRewardFlag;
	private static int _pointsRewardPvp;
	private static boolean _losePointsOnDeath;
	private static int _pointsLoseAmount;
	
	// Checkpoint capture
	private static long _checkpointRespawnDelay;
	private static int _checkpointScoreInterval;
	private static int _checkpointPointsPerTick;
	
	// Flag rewards
	private static boolean _enableFlagSpItemReward;
	private static int _flagSpRewardFirst;
	private static int _flagSpRewardSecond;
	private static int _flagSpRewardThird;
	private static final java.util.List<int[]> _flagItemReward1 = new java.util.ArrayList<>();
	private static final java.util.List<int[]> _flagItemReward2 = new java.util.ArrayList<>();
	private static final java.util.List<int[]> _flagItemReward3 = new java.util.ArrayList<>();
	
	// Castle multiplier
	private static boolean _enableCastleRewardMultiplier;
	private static int _castleRewardAden;
	private static int _castleRewardDion;
	private static int _castleRewardGludio;
	
	// Round end
	private static int _roundEndSpFirstPlace;
	private static int _roundEndAdenaFirstPlace;
	private static int _roundEndAdenaSecondPlace;
	private static int _roundEndAdenaThirdPlace;
	private static boolean _enableRoundEndAaReward;
	private static int _roundEndAaFirstPlace;
	private static boolean _enableTopPlayerItemReward;
	private static int _topPlayerItemRewardId;
	private static int _topPlayerItemRewardAmount;
	private static boolean _enableWinFactionItemReward;
	private static int _winFactionItemRewardId;
	private static int _winFactionItemRewardAmount;
	
	// Enchant
	private static String _enchantMode;
	private static int _enchantScrollDropChance;
	private static int _maxItemEnchant;
	private static int _killsForEnchantB;
	private static int _killsForEnchantA;
	private static int _killsForEnchantS;
	
	// Class balance
	private static int _classBalanceMcrit;
	private static double _classBalanceMAtk;
	
	// Say texts
	private static boolean _enableSayTexts;
	private static String _goodNpcText;
	private static String _goodPlayerText;
	private static String _evilNpcText;
	private static String _evilPlayerText;
	
	// Chaos event
	private static boolean _enableChaosEvent;
	private static int _chaosSuperHasteLvl;
	private static int _chaosEventDuration;
	private static int _chaosEventInterval;
	private static int _chaosEventRewardId;
	private static int _chaosEventRewardAmount;
	private static boolean _enableAuraTeam;
	
	private static Location _newbieSpawnLoc;
	
	public static void load()
	{
		final ExProperties props = Config.initProperties("./config/factionwar.properties");
		
		_enabled = props.getProperty("Enabled", false);
		_goodFactionId = props.getProperty("GoodFactionId", 1);
		_evilFactionId = props.getProperty("EvilFactionId", 2);
		_goodFactionName = props.getProperty("GoodFactionName", "Good");
		_evilFactionName = props.getProperty("EvilFactionName", "Evil");
		_goodFactionColor = Integer.parseUnsignedInt(props.getProperty("GoodFactionColor", "0000E0"), 16);
		_evilFactionColor = Integer.parseUnsignedInt(props.getProperty("EvilFactionColor", "F4FA58"), 16);
		_scoreToWin = props.getProperty("ScoreToWin", 100);
		_pointsPerFlagKill = props.getProperty("PointsPerFlagKill", 1);
		_pointsPerPvpKill = props.getProperty("PointsPerPvpKill", 1);
		_flagNpcId = props.getProperty("FlagNpcId", 90000);
		_flagRespawnDelay = props.getProperty("FlagRespawnDelay", 30) * 1000L;
		_guardNpcId = props.getProperty("GuardNpcId", 90001);
		_guardsPerBase = props.getProperty("GuardsPerBase", 3);
		_guardCircleRadius = props.getProperty("GuardCircleRadius", 100);
		_guardRespawnDelay = props.getProperty("GuardRespawnDelay", 60) * 1000L;
		_warRegistrarNpcId = props.getProperty("WarRegistrarNpcId", 90002);
		_checkpointNpcId = props.getProperty("CheckpointNpcId", 90003);
		_checkpointCount = props.getProperty("CheckpointCount", 3);
		_checkpointRadius = props.getProperty("CheckpointRadius", 2000);
		_checkpointRespawnDelay = props.getProperty("CheckpointRespawnDelay", 15) * 1000L;
		_checkpointScoreInterval = props.getProperty("CheckpointScoreInterval", 30);
		_checkpointPointsPerTick = props.getProperty("CheckpointPointsPerTick", 1);
		_mapRotationMinutes = props.getProperty("MapRotationMinutes", 30);
		_mapVoteSeconds = props.getProperty("MapVoteSeconds", 30);
		_neutralSpawnLoc = parseLoc(props.getProperty("NeutralSpawnLoc", "147300,25750,-2000"));
		_announceStart = props.getProperty("AnnounceStart", true);
		_announceEnd = props.getProperty("AnnounceEnd", true);
		_announceFlagKill = props.getProperty("AnnounceFlagKill", true);
		_announcePvpKill = props.getProperty("AnnouncePvpKill", false);
		_announceScore = props.getProperty("AnnounceScore", true);
		_announceMapSwitch = props.getProperty("AnnounceMapSwitch", true);
		_killRewardPoints = props.getProperty("KillRewardPoints", 1);
		_townRestriction = props.getProperty("TownRestriction", false);
		_townRestrictionRadius = props.getProperty("TownRestrictionRadius", 1500);
		_neutralZoneRadius = props.getProperty("NeutralZoneRadius", 2000);
		_warDurationMinutes = props.getProperty("WarDurationMinutes", 120);
		_endFreezeSeconds = props.getProperty("EndFreezeSeconds", 5);
		_unstuckDelaySeconds = props.getProperty("UnstuckDelaySeconds", 10);
		_rewardItemId = props.getProperty("RewardItemId", 57);
		_topRewardAmounts[0] = props.getProperty("Top1Reward", 500000);
		_topRewardAmounts[1] = props.getProperty("Top2Reward", 300000);
		_topRewardAmounts[2] = props.getProperty("Top3Reward", 100000);
		_winningFactionReward = props.getProperty("WinningFactionReward", 50000);
		_pvpAdenaReward = props.getProperty("PvpAdenaReward", 5000);
		
		// Gameplay
		_startingLevel = props.getProperty("StartingLevel", 76);
		_startingLevelSubClass = props.getProperty("StartingLevelSubClass", 76);
		_priceForFactionChange = props.getProperty("PriceForFactionChange", 1000);
		_enableAutoTeleportToBase = props.getProperty("EnableAutoTeleportToBase", true);
		_enableFactionGuardsSpawn = props.getProperty("EnableFactionGuards", true);
		_enablePlayersBalanceLogin = props.getProperty("EnablePlayersBalanceLogin", true);
		_enableMapVoting = props.getProperty("EnableMapVoting", true);
		
		// Anti-farm
		_enableProtectionIP = props.getProperty("EnableProtectionIP", true);
		_enableProtectionClan = props.getProperty("EnableProtectionClan", false);
		_enableProtectionAlly = props.getProperty("EnableProtectionAlly", false);
		_enableProtectionArmour = props.getProperty("EnableProtectionArmour", true);
		_protectionArmourAmount = props.getProperty("ProtectionArmourAmount", 300);
		_enableProtectionSamePlayer = props.getProperty("EnableProtectionSamePlayer", false);
		
		// Class & equipment
		_enableClassBalance = props.getProperty("EnableClassBalance", true);
		_enableAntiHeavySystem = props.getProperty("EnableAntiHeavySystem", true);
		_killsForSpecShop = props.getProperty("KillsForSpecShop", 300);
		_forbidUseItem = props.getProperty("ForbidUseItem", true);
		_enableAGradeLimit = props.getProperty("EnableAGradeLimit", true);
		_killsForAGradeItem = props.getProperty("KillsForAGradeItem", 100);
		
		// PvP EXP
		_enablePvpExpReward = props.getProperty("EnablePvpExpReward", true);
		_pvpExpRewardFirst = props.getProperty("PvpExpRewardFirst", 1000000);
		_pvpExpRewardSecond = props.getProperty("PvpExpRewardSecond", 2800000);
		_pvpExpRewardThird = props.getProperty("PvpExpRewardThird", 120000);
		
		// PvP items
		_enablePvpItemReward = props.getProperty("EnablePvpItemReward", true);
		_pvpItemRewardId = props.getProperty("PvpItemRewardId", 57);
		_pvpItemRewardCount = props.getProperty("PvpItemRewardCount", 2);
		
		// Party rewards
		_enablePartyPvpReward = props.getProperty("EnablePartyPvpReward", true);
		_partyRewardOnlySupportClass = props.getProperty("PartyRewardOnlySupportClass", false);
		_partyRewardChance = props.getProperty("PartyRewardChance", 50);
		_killerPartyBonus = props.getProperty("KillerPartyBonus", 2);
		
		// Faction points
		_pointsRewardFlag = props.getProperty("PointsRewardFlag", 2);
		_pointsRewardPvp = props.getProperty("PointsRewardPvp", 3);
		_losePointsOnDeath = props.getProperty("LosePointsOnDeath", true);
		_pointsLoseAmount = props.getProperty("PointsLoseAmount", 1);
		
		// Flag rewards
		_enableFlagSpItemReward = props.getProperty("EnableFlagSpItemReward", true);
		_flagSpRewardFirst = props.getProperty("FlagSpRewardFirst", 157865);
		_flagSpRewardSecond = props.getProperty("FlagSpRewardSecond", 100875);
		_flagSpRewardThird = props.getProperty("FlagSpRewardThird", 75405);
		parseItemList(props, "FlagItemReward1", "57,6", _flagItemReward1);
		parseItemList(props, "FlagItemReward2", "57,3", _flagItemReward2);
		parseItemList(props, "FlagItemReward3", "57,2", _flagItemReward3);
		
		// Castle multiplier
		_enableCastleRewardMultiplier = props.getProperty("EnableCastleRewardMultiplier", true);
		_castleRewardAden = props.getProperty("CastleRewardAden", 4);
		_castleRewardDion = props.getProperty("CastleRewardDion", 3);
		_castleRewardGludio = props.getProperty("CastleRewardGludio", 2);
		
		// Round end
		_roundEndSpFirstPlace = props.getProperty("RoundEndSpFirstPlace", 200000);
		_roundEndAdenaFirstPlace = props.getProperty("RoundEndAdenaFirstPlace", 60);
		_roundEndAdenaSecondPlace = props.getProperty("RoundEndAdenaSecondPlace", 25);
		_roundEndAdenaThirdPlace = props.getProperty("RoundEndAdenaThirdPlace", 10);
		_enableRoundEndAaReward = props.getProperty("EnableRoundEndAaReward", true);
		_roundEndAaFirstPlace = props.getProperty("RoundEndAaFirstPlace", 3);
		_enableTopPlayerItemReward = props.getProperty("EnableTopPlayerItemReward", true);
		_topPlayerItemRewardId = props.getProperty("TopPlayerItemRewardId", 5575);
		_topPlayerItemRewardAmount = props.getProperty("TopPlayerItemRewardAmount", 8);
		_enableWinFactionItemReward = props.getProperty("EnableWinFactionItemReward", true);
		_winFactionItemRewardId = props.getProperty("WinFactionItemRewardId", 5575);
		_winFactionItemRewardAmount = props.getProperty("WinFactionItemRewardAmount", 5);
		
		// Enchant
		_enchantMode = props.getProperty("EnchantMode", "PVPSCROLLS");
		_enchantScrollDropChance = props.getProperty("EnchantScrollDropChance", 34);
		_maxItemEnchant = props.getProperty("MaxItemEnchant", 12);
		_killsForEnchantB = props.getProperty("KillsForEnchantB", 25);
		_killsForEnchantA = props.getProperty("KillsForEnchantA", 20);
		_killsForEnchantS = props.getProperty("KillsForEnchantS", 40);
		
		// Class balance
		_classBalanceMcrit = props.getProperty("ClassBalanceMcrit", 1);
		_classBalanceMAtk = props.getProperty("ClassBalanceMAtk", 0.8);
		
		// Say texts
		_enableSayTexts = props.getProperty("EnableSayTexts", true);
		_goodNpcText = props.getProperty("GoodNpcText", "Thanks for Register in %faction% %n!");
		_goodPlayerText = props.getProperty("GoodPlayerText", "I feel the power !!!");
		_evilNpcText = props.getProperty("EvilNpcText", "Thanks for Register in %faction% %n!");
		_evilPlayerText = props.getProperty("EvilPlayerText", "I feel the dark power !!");
		
		// Chaos event
		_enableChaosEvent = props.getProperty("EnableChaosEvent", false);
		_chaosSuperHasteLvl = props.getProperty("ChaosSuperHasteLvl", 2);
		_chaosEventDuration = props.getProperty("ChaosEventDuration", 20);
		_chaosEventInterval = props.getProperty("ChaosEventInterval", 360);
		_chaosEventRewardId = props.getProperty("ChaosEventRewardId", 57);
		_chaosEventRewardAmount = props.getProperty("ChaosEventRewardAmount", 99);
		_enableAuraTeam = props.getProperty("EnableAuraTeam", true);
		
		_newbieSpawnLoc = parseLoc(props.getProperty("NewbieSpawnLoc", "45346,49026,-3061"));
		
		_spoilItems.clear();
		parseItemList(props, "SpoilItems", "57,1000", _spoilItems);
		
		_goodSpawnLoc = parseLoc(props.getProperty("GoodSpawn", "-84318,244579,-792"));
		_evilSpawnLoc = parseLoc(props.getProperty("EvilSpawn", "82218,148561,-3472"));
		
		_maps.clear();
		final String[] mapEntries = props.getProperty("Maps", "").split(";");
		for (String entry : mapEntries)
		{
			final String[] parts = entry.split(",");
			if (parts.length >= 4)
			{
				try
				{
					final String name = parts[0].trim();
					final int x = Integer.parseInt(parts[1].trim());
					final int y = Integer.parseInt(parts[2].trim());
					final int z = Integer.parseInt(parts[3].trim());
					final int radius = parts.length > 4 ? Integer.parseInt(parts[4].trim()) : 3000;
					
					Location goodMapSpawn = null;
					Location evilMapSpawn = null;
					if (parts.length >= 11)
					{
						goodMapSpawn = new Location(Integer.parseInt(parts[5].trim()), Integer.parseInt(parts[6].trim()), Integer.parseInt(parts[7].trim()));
						evilMapSpawn = new Location(Integer.parseInt(parts[8].trim()), Integer.parseInt(parts[9].trim()), Integer.parseInt(parts[10].trim()));
					}
					
					_maps.add(new WarMap(name, x, y, z, radius, goodMapSpawn, evilMapSpawn));
				}
				catch (NumberFormatException e)
				{
				}
			}
		}
		
		// Parse map <-> flagId mappings
		_mapFlagIds.clear();
		final String[] mapFlagEntries = props.getProperty("MapFlagIds", "Gludio,2;Giran,3").split(";");
		for (String entry : mapFlagEntries)
		{
			final String[] parts = entry.trim().split(",");
			if (parts.length >= 2)
			{
				try
				{
					_mapFlagIds.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
				}
				catch (NumberFormatException e) { }
			}
		}
		
		// Load faction_flags.xml
		loadFactionFlagsXml();
		
		if (_maps.isEmpty())
		{
			_maps.add(new WarMap("Gludio", -14300, 123700, -3100, 3000, null, null));
			_maps.add(new WarMap("Giran", 83400, 148000, -3400, 3000, null, null));
		}
		
		Collections.shuffle(_maps);
	}
	
	private static Location parseLoc(String s)
	{
		if (s == null || s.isEmpty())
			return null;
		final String[] p = s.split(",");
		if (p.length < 3)
		{
			LOGGER.warn("Malformed location: '{}' — expected x,y,z", s);
			return null;
		}
		try
		{
			return new Location(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
		}
		catch (NumberFormatException e)
		{
			LOGGER.warn("Invalid number in location: '{}'", s);
			return null;
		}
	}
	
	private static void loadFactionFlagsXml()
	{
		_xmlFlags.clear();
		
		final Path path = Path.of("./data/xml/faction_flags.xml");
		if (!path.toFile().exists())
		{
			LOGGER.warn("faction_flags.xml not found at {}. Skipping XML flag loading.", path);
			return;
		}
		
		final java.io.File xmlFile = path.toFile();
		if (!xmlFile.exists())
			return;
		
		try
		{
			final javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
			dbf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
			dbf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			final javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
			final Document doc = db.parse(xmlFile);
			
			final Node listNode = doc.getFirstChild();
			if (listNode == null)
				return;
			
			final org.w3c.dom.NodeList flagNodes = listNode.getChildNodes();
			for (int i = 0; i < flagNodes.getLength(); i++)
			{
				final Node flagNode = flagNodes.item(i);
				if (flagNode.getNodeType() != Node.ELEMENT_NODE || !flagNode.getNodeName().equals("flag"))
					continue;
				
				final NamedNodeMap attrs = flagNode.getAttributes();
				final int mapId = Integer.parseInt(attrs.getNamedItem("mapId").getNodeValue());
				final String flagName = attrs.getNamedItem("flag_name").getNodeValue();
				final int factionId = Integer.parseInt(attrs.getNamedItem("faction_id").getNodeValue());
				final boolean isCapturable = Boolean.parseBoolean(attrs.getNamedItem("isCapturable").getNodeValue());
				final int x = Integer.parseInt(attrs.getNamedItem("x").getNodeValue());
				final int y = Integer.parseInt(attrs.getNamedItem("y").getNodeValue());
				final int z = Integer.parseInt(attrs.getNamedItem("z").getNodeValue());
				final String flagType = attrs.getNamedItem("flag_type") != null ? attrs.getNamedItem("flag_type").getNodeValue() : "default";
				
				final FactionFlag flag = new FactionFlag(mapId, flagName, flagType, factionId, isCapturable, new Location(x, y, z));
				_xmlFlags.computeIfAbsent(mapId, k -> new ArrayList<>()).add(flag);
			}
			LOGGER.info("Loaded {} flags from faction_flags.xml ({} map groups).", _xmlFlags.values().stream().mapToInt(List::size).sum(), _xmlFlags.size());
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to parse faction_flags.xml: {}", e.getMessage());
		}
	}
	
	/**
	 * Gets the XML-defined flags for a specific map name (looked up via MapFlagIds).
	 */
	public static List<FactionFlag> getXmlFlagsForMap(String mapName)
	{
		final Integer flagMapId = _mapFlagIds.get(mapName);
		if (flagMapId == null)
			return Collections.emptyList();
		
		final List<FactionFlag> flags = _xmlFlags.get(flagMapId);
		return flags != null ? flags : Collections.emptyList();
	}
	
	/**
	 * Gets the flag map ID for a map name.
	 */
	public static int getFlagMapId(String mapName)
	{
		return _mapFlagIds.getOrDefault(mapName, -1);
	}
	
	private static void parseItemList(ExProperties props, String key, String defaultVal, java.util.List<int[]> list)
	{
		list.clear();
		final String[] entries = props.getProperty(key, defaultVal).split(";");
		for (String entry : entries)
		{
			final String[] parts = entry.trim().split(",");
			if (parts.length >= 1)
			{
				try
				{
					final int itemId = Integer.parseInt(parts[0].trim());
					final int count = (parts.length >= 2) ? Integer.parseInt(parts[1].trim()) : 1;
					list.add(new int[]{itemId, count});
				}
				catch (NumberFormatException e) { }
			}
		}
	}
	
	public static boolean isEnabled() { return Config.ENABLE_FACTION_SYSTEM && _enabled; }
	public static int getGoodFactionId() { return _goodFactionId; }
	public static int getEvilFactionId() { return _evilFactionId; }
	public static String getGoodFactionName() { return _goodFactionName; }
	public static String getEvilFactionName() { return _evilFactionName; }
	public static int getGoodFactionColor() { return _goodFactionColor; }
	public static int getEvilFactionColor() { return _evilFactionColor; }
	public static int getScoreToWin() { return _scoreToWin; }
	public static int getPointsPerFlagKill() { return _pointsPerFlagKill; }
	public static int getPointsPerPvpKill() { return _pointsPerPvpKill; }
	public static int getFlagNpcId() { return _flagNpcId; }
	public static long getFlagRespawnDelay() { return _flagRespawnDelay; }
	public static int getGuardNpcId() { return _guardNpcId; }
	public static int getGuardsPerBase() { return _guardsPerBase; }
	public static int getGuardCircleRadius() { return _guardCircleRadius; }
	public static long getGuardRespawnDelay() { return _guardRespawnDelay; }
	public static int getWarRegistrarNpcId() { return _warRegistrarNpcId; }
	public static int getCheckpointNpcId() { return _checkpointNpcId; }
	public static int getCheckpointCount() { return _checkpointCount; }
	public static int getCheckpointRadius() { return _checkpointRadius; }
	public static long getCheckpointRespawnDelay() { return _checkpointRespawnDelay; }
	public static int getCheckpointScoreInterval() { return _checkpointScoreInterval; }
	public static int getCheckpointPointsPerTick() { return _checkpointPointsPerTick; }
	public static int getMapRotationMinutes() { return _mapRotationMinutes; }
	public static int getMapVoteSeconds() { return _mapVoteSeconds; }
	public static List<WarMap> getMaps() { return _maps; }
	
	/** Returns a random subset of maps for voting (max 4). */
	public static List<WarMap> getVoteMaps()
	{
		final List<WarMap> copy = new ArrayList<>(_maps);
		Collections.shuffle(copy);
		final int count = Math.min(4, copy.size());
		final List<WarMap> result = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
			result.add(copy.get(i));
		return result;
	}
	
	public static Location getGoodSpawnLoc() { return _goodSpawnLoc; }
	public static Location getEvilSpawnLoc() { return _evilSpawnLoc; }
	public static Location getNeutralSpawnLoc() { return _neutralSpawnLoc; }
	public static boolean isAnnounceStart() { return _announceStart; }
	public static boolean isAnnounceEnd() { return _announceEnd; }
	public static boolean isAnnounceFlagKill() { return _announceFlagKill; }
	public static boolean isAnnouncePvpKill() { return _announcePvpKill; }
	public static boolean isAnnounceScore() { return _announceScore; }
	public static boolean isAnnounceMapSwitch() { return _announceMapSwitch; }
	public static int getKillRewardPoints() { return _killRewardPoints; }
	public static boolean isTownRestriction() { return _townRestriction; }
	public static int getTownRestrictionRadius() { return _townRestrictionRadius; }
	public static int getWarDurationMinutes() { return _warDurationMinutes; }
	public static int getEndFreezeSeconds() { return _endFreezeSeconds; }
	public static int getUnstuckDelaySeconds() { return _unstuckDelaySeconds; }
	public static int getNeutralZoneRadius() { return _neutralZoneRadius; }
	public static int getRewardItemId() { return _rewardItemId; }
	public static int[] getTopRewardAmounts() { return _topRewardAmounts; }
	public static int getWinningFactionReward() { return _winningFactionReward; }
	public static int getTop1Reward() { return _topRewardAmounts.length > 0 ? _topRewardAmounts[0] : 0; }
	public static int getTop2Reward() { return _topRewardAmounts.length > 1 ? _topRewardAmounts[1] : 0; }
	public static int getTop3Reward() { return _topRewardAmounts.length > 2 ? _topRewardAmounts[2] : 0; }
	public static int getPvpAdenaReward() { return _pvpAdenaReward; }
	public static java.util.List<int[]> getSpoilItems() { return _spoilItems; }
	
	// Gameplay getters
	public static int getStartingLevel() { return _startingLevel; }
	public static int getStartingLevelSubClass() { return _startingLevelSubClass; }
	public static int getPriceForFactionChange() { return _priceForFactionChange; }
	public static boolean isEnableAutoTeleportToBase() { return _enableAutoTeleportToBase; }
	public static boolean isEnableFactionGuardsSpawn() { return _enableFactionGuardsSpawn; }
	public static boolean isEnablePlayersBalanceLogin() { return _enablePlayersBalanceLogin; }
	public static boolean isEnableMapVoting() { return _enableMapVoting; }
	
	// Anti-farm getters
	public static boolean isEnableProtectionIP() { return _enableProtectionIP; }
	public static boolean isEnableProtectionClan() { return _enableProtectionClan; }
	public static boolean isEnableProtectionAlly() { return _enableProtectionAlly; }
	public static boolean isEnableProtectionArmour() { return _enableProtectionArmour; }
	public static int getProtectionArmourAmount() { return _protectionArmourAmount; }
	public static boolean isEnableProtectionSamePlayer() { return _enableProtectionSamePlayer; }
	
	// Class & equipment getters
	public static boolean isEnableClassBalance() { return _enableClassBalance; }
	public static boolean isEnableAntiHeavySystem() { return _enableAntiHeavySystem; }
	public static int getKillsForSpecShop() { return _killsForSpecShop; }
	public static boolean isForbidUseItem() { return _forbidUseItem; }
	public static boolean isEnableAGradeLimit() { return _enableAGradeLimit; }
	public static int getKillsForAGradeItem() { return _killsForAGradeItem; }
	
	// PvP EXP getters
	public static boolean isEnablePvpExpReward() { return _enablePvpExpReward; }
	public static int getPvpExpRewardFirst() { return _pvpExpRewardFirst; }
	public static int getPvpExpRewardSecond() { return _pvpExpRewardSecond; }
	public static int getPvpExpRewardThird() { return _pvpExpRewardThird; }
	
	// PvP item getters
	public static boolean isEnablePvpItemReward() { return _enablePvpItemReward; }
	public static int getPvpItemRewardId() { return _pvpItemRewardId; }
	public static int getPvpItemRewardCount() { return _pvpItemRewardCount; }
	
	// Party reward getters
	public static boolean isEnablePartyPvpReward() { return _enablePartyPvpReward; }
	public static boolean isPartyRewardOnlySupportClass() { return _partyRewardOnlySupportClass; }
	public static int getPartyRewardChance() { return _partyRewardChance; }
	public static int getKillerPartyBonus() { return _killerPartyBonus; }
	
	// Faction point getters
	public static int getPointsRewardFlag() { return _pointsRewardFlag; }
	public static int getPointsRewardPvp() { return _pointsRewardPvp; }
	public static boolean isLosePointsOnDeath() { return _losePointsOnDeath; }
	public static int getPointsLoseAmount() { return _pointsLoseAmount; }
	
	// Flag reward getters
	public static boolean isEnableFlagSpItemReward() { return _enableFlagSpItemReward; }
	public static int getFlagSpRewardFirst() { return _flagSpRewardFirst; }
	public static int getFlagSpRewardSecond() { return _flagSpRewardSecond; }
	public static int getFlagSpRewardThird() { return _flagSpRewardThird; }
	public static java.util.List<int[]> getFlagItemReward1() { return _flagItemReward1; }
	public static java.util.List<int[]> getFlagItemReward2() { return _flagItemReward2; }
	public static java.util.List<int[]> getFlagItemReward3() { return _flagItemReward3; }
	
	// Castle multiplier getters
	public static boolean isEnableCastleRewardMultiplier() { return _enableCastleRewardMultiplier; }
	public static int getCastleRewardAden() { return _castleRewardAden; }
	public static int getCastleRewardDion() { return _castleRewardDion; }
	public static int getCastleRewardGludio() { return _castleRewardGludio; }
	
	// Round end getters
	public static int getRoundEndSpFirstPlace() { return _roundEndSpFirstPlace; }
	public static int getRoundEndAdenaFirstPlace() { return _roundEndAdenaFirstPlace; }
	public static int getRoundEndAdenaSecondPlace() { return _roundEndAdenaSecondPlace; }
	public static int getRoundEndAdenaThirdPlace() { return _roundEndAdenaThirdPlace; }
	public static boolean isEnableRoundEndAaReward() { return _enableRoundEndAaReward; }
	public static int getRoundEndAaFirstPlace() { return _roundEndAaFirstPlace; }
	public static boolean isEnableTopPlayerItemReward() { return _enableTopPlayerItemReward; }
	public static int getTopPlayerItemRewardId() { return _topPlayerItemRewardId; }
	public static int getTopPlayerItemRewardAmount() { return _topPlayerItemRewardAmount; }
	public static boolean isEnableWinFactionItemReward() { return _enableWinFactionItemReward; }
	public static int getWinFactionItemRewardId() { return _winFactionItemRewardId; }
	public static int getWinFactionItemRewardAmount() { return _winFactionItemRewardAmount; }
	
	// Enchant getters
	public static String getEnchantMode() { return _enchantMode; }
	public static int getEnchantScrollDropChance() { return _enchantScrollDropChance; }
	public static int getMaxItemEnchant() { return _maxItemEnchant; }
	public static int getKillsForEnchantB() { return _killsForEnchantB; }
	public static int getKillsForEnchantA() { return _killsForEnchantA; }
	public static int getKillsForEnchantS() { return _killsForEnchantS; }
	
	// Class balance getters
	public static int getClassBalanceMcrit() { return _classBalanceMcrit; }
	public static double getClassBalanceMAtk() { return _classBalanceMAtk; }
	
	// Say text getters
	public static boolean isEnableSayTexts() { return _enableSayTexts; }
	public static String getGoodNpcText() { return _goodNpcText; }
	public static String getGoodPlayerText() { return _goodPlayerText; }
	public static String getEvilNpcText() { return _evilNpcText; }
	public static String getEvilPlayerText() { return _evilPlayerText; }
	
	// Chaos event getters
	public static boolean isEnableChaosEvent() { return _enableChaosEvent; }
	public static int getChaosSuperHasteLvl() { return _chaosSuperHasteLvl; }
	public static int getChaosEventDuration() { return _chaosEventDuration; }
	public static int getChaosEventInterval() { return _chaosEventInterval; }
	public static int getChaosEventRewardId() { return _chaosEventRewardId; }
	public static int getChaosEventRewardAmount() { return _chaosEventRewardAmount; }
	public static boolean isEnableAuraTeam() { return _enableAuraTeam; }
	
	public static Location getNewbieSpawnLoc() { return _newbieSpawnLoc; }
	
	public static boolean isInNeutralZone(net.sf.l2j.gameserver.model.location.Location loc)
	{
		if (_neutralSpawnLoc == null || loc == null)
			return false;
		return _neutralSpawnLoc.distance3D(loc) <= _neutralZoneRadius;
	}
	
	static
	{
		load();
	}
	
	public static class WarMap
	{
		private final String _name;
		private final int _x;
		private final int _y;
		private final int _z;
		private final int _radius;
		private final Location _goodMapSpawn;
		private final Location _evilMapSpawn;
		
		public WarMap(String name, int x, int y, int z, int radius, Location goodMapSpawn, Location evilMapSpawn)
		{
			_name = name;
			_x = x;
			_y = y;
			_z = z;
			_radius = radius;
			_goodMapSpawn = goodMapSpawn;
			_evilMapSpawn = evilMapSpawn;
		}
		
		public String getName() { return _name; }
		public int getX() { return _x; }
		public int getY() { return _y; }
		public int getZ() { return _z; }
		public int getRadius() { return _radius; }
		public Location getCenter() { return new Location(_x, _y, _z); }
		
		public Location getGoodSpawn() { return _goodMapSpawn != null ? _goodMapSpawn : _goodSpawnLoc; }
		public Location getEvilSpawn() { return _evilMapSpawn != null ? _evilMapSpawn : _evilSpawnLoc; }
	}
}
