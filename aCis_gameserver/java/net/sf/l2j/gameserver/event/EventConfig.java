package net.sf.l2j.gameserver.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.l2j.commons.config.ExProperties;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.location.Location;

public final class EventConfig
{
	private static final CLogger LOGGER = new CLogger(EventConfig.class.getName());
	
	private static boolean _enabled = true;
	private static int _registerTime = 10;
	private static int _voteTime = 60;
	private static boolean _voteEnabled = false;
	private static boolean _schedulerEnabled = true;
	private static int _managerNpcId = 9999;
	private static boolean _debug = true;
	private static boolean _eventBufferEnabled = true;
	private static int _npcBufferId = 90010;
	private static boolean _friendlyFireEnabled = false;
	private static boolean _restartAllowed = false;
	private static boolean _showEscapeEffect = true;
	private static String _allowedBuffsList = "1068,1085,1086,1087";
	private static int _maxBuffNum = 2;
	private static final List<EventData> _events = new ArrayList<>();
	
	public static void load()
	{
		final ExProperties props = Config.initProperties("./config/events.properties");
		
		_enabled = true; // Events are always enabled (alternance system)
		_registerTime = props.getProperty("RegisterTime", 10);
		_voteTime = props.getProperty("VoteTime", 60);
		_voteEnabled = props.getProperty("VoteEnabled", false);
		_schedulerEnabled = props.getProperty("SchedulerEnabled", true);
		_managerNpcId = props.getProperty("ManagerNpcId", 9999);
		_debug = props.getProperty("Debug", true);
		_eventBufferEnabled = props.getProperty("EventBufferEnabled", true);
		_npcBufferId = props.getProperty("NpcBufferId", 90010);
		_friendlyFireEnabled = props.getProperty("FriendlyFireEnabled", false);
		_restartAllowed = props.getProperty("RestartAllowed", false);
		_showEscapeEffect = props.getProperty("ShowEscapeEffect", true);
		_allowedBuffsList = props.getProperty("AllowedBuffsList", "1068,1085,1086,1087");
		_maxBuffNum = props.getProperty("MaxBuffNum", 2);
		
		_events.clear();
		for (int i = 1; i <= 20; i++)
		{
			final boolean eventEnabled = props.getProperty("Event_" + i + "_Enabled", true);
			final String shortName = props.getProperty("Event_" + i + "_ShortName", "");
			final String eventName = props.getProperty("Event_" + i + "_Name", "");
			final int minLvl = props.getProperty("Event_" + i + "_MinLvl", 1);
			final int maxLvl = props.getProperty("Event_" + i + "_MaxLvl", 85);
			final int matchTime = props.getProperty("Event_" + i + "_MatchTime", 120);
			final int minPlayers = props.getProperty("Event_" + i + "_MinPlayers", 2);
			final boolean allowPotions = props.getProperty("Event_" + i + "_AllowPotions", false);
			final boolean allowMagic = props.getProperty("Event_" + i + "_AllowMagic", true);
			final boolean removeBuffs = props.getProperty("Event_" + i + "_RemoveBuffs", true);
			final String posAll = props.getProperty("Event_" + i + "_Position", "-54478,-69506,-3371,700");
			final String posBlue = props.getProperty("Event_" + i + "_PositionBlue", "");
			final String posRed = props.getProperty("Event_" + i + "_PositionRed", "");
			final String rewardWinner = props.getProperty("Event_" + i + "_RewardWinner", "57,1000");
			final String rewardLoser = props.getProperty("Event_" + i + "_RewardLoser", "57,10");
			
			final EventData data = new EventData(i, shortName, eventName, minLvl, maxLvl, matchTime, minPlayers, allowPotions, allowMagic, removeBuffs, eventEnabled);
			data.setPositionAll(parseLoc(posAll));
			data.setPositionRadius(parseLocRadius(posAll));
			if (!posBlue.isEmpty()) data.setPositionBlue(parseLoc(posBlue));
			if (!posRed.isEmpty()) data.setPositionRed(parseLoc(posRed));
			if (!rewardWinner.isEmpty()) data.setRewardWinner(rewardWinner);
			if (!rewardLoser.isEmpty()) data.setRewardLoser(rewardLoser);
			
			// Load custom properties for this event
			final String[] customKeys = {"ChestInterval","MaxChests","ExplodeChance","ChestRewardId","ChestRewardCount","ZoneRadius","PointsPerTick","TickInterval","Zone1","Zone2","MutantSkillId","ChestCount","RoundTime","InitialZombies","ZombieSkillId","HumanBowId","BowId","PlayersPerRound","BossNpcId","BossSpawnDelay","TotalChests","SpawnRadius","KillStreakReward","KillStreakThresholds","RespawnDelay","PotionsAllowed","TargetTeammatesAllowed","HealBlocked","BackCoordinates","Team1Name","Team2Name"};
			for (String key : customKeys)
			{
				final String val = props.getProperty("Event_" + i + "_" + key);
				if (val != null && !val.isEmpty())
					data.setCustom(key, val);
			}
			
			// Load rounds config
			data.setRounds(props.getProperty("Event_" + i + "_Rounds", 0));
			
			_events.add(data);
		}
		
		LOGGER.info("EventConfig loaded. {} events defined, enabled={}.", _events.size(), _enabled);
	}
	
	private static Location parseLoc(String s)
	{
		final String[] p = s.split(",");
		if (p.length < 3)
			return new Location(-54478, -69506, -3371);
		return new Location(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
	}
	
	private static int parseLocRadius(String s)
	{
		final String[] p = s.split(",");
		if (p.length < 4)
			return 300;
		try
		{
			return Math.max(0, Integer.parseInt(p[3].trim()));
		}
		catch (NumberFormatException e)
		{
			return 300;
		}
	}
	
	public static boolean isEnabled() { return _enabled; }
	public static int getRegisterTime() { return _registerTime; }
	public static int getVoteTime() { return _voteTime; }
	public static boolean isVoteEnabled() { return _voteEnabled; }
	public static boolean isSchedulerEnabled() { return _schedulerEnabled; }
	public static int getManagerNpcId() { return _managerNpcId; }
	public static boolean isDebug() { return _debug; }
	public static boolean isEventBufferEnabled() { return _eventBufferEnabled; }
	public static int getNpcBufferId() { return _npcBufferId; }
	public static boolean isFriendlyFireEnabled() { return _friendlyFireEnabled; }
	public static boolean isRestartAllowed() { return _restartAllowed; }
	public static boolean isShowEscapeEffect() { return _showEscapeEffect; }
	public static String getAllowedBuffsList() { return _allowedBuffsList; }
	public static int getMaxBuffNum() { return _maxBuffNum; }
	public static List<EventData> getEvents() { return Collections.unmodifiableList(_events); }
	
	public static EventData getEvent(int id)
	{
		for (EventData ed : _events)
		{
			if (ed.getId() == id)
				return ed;
		}
		return null;
	}
	
	public static class EventData
	{
		private final int _id;
		private final String _shortName;
		private final String _eventName;
		private final int _minLvl;
		private final int _maxLvl;
		private final int _matchTime;
		private final int _minPlayers;
		private final boolean _allowPotions;
		private final boolean _allowMagic;
		private final boolean _removeBuffs;
		private final boolean _enabled;
		private int _rounds;
		private Location _positionAll;
		private Location _positionBlue;
		private Location _positionRed;
		private int _positionRadius = 300;
		private String _rewardWinner = "57,1000";
		private String _rewardLoser = "57,10";
		private final Map<String, String> _custom = new HashMap<>();
		
		public EventData(int id, String shortName, String eventName, int minLvl, int maxLvl, int matchTime, int minPlayers, boolean allowPotions, boolean allowMagic, boolean removeBuffs, boolean enabled)
		{
			_id = id;
			_shortName = shortName;
			_eventName = eventName;
			_minLvl = minLvl;
			_maxLvl = maxLvl;
			_matchTime = matchTime;
			_minPlayers = minPlayers;
			_allowPotions = allowPotions;
			_allowMagic = allowMagic;
			_removeBuffs = removeBuffs;
			_enabled = enabled;
		}
		
		public int getId() { return _id; }
		public String getShortName() { return _shortName; }
		public String getEventName() { return _eventName; }
		public int getMinLvl() { return _minLvl; }
		public int getMaxLvl() { return _maxLvl; }
		public int getMatchTime() { return _matchTime; }
		public int getMinPlayers() { return _minPlayers; }
		public boolean isAllowPotions() { return _allowPotions; }
		public boolean isAllowMagic() { return _allowMagic; }
		public boolean isRemoveBuffs() { return _removeBuffs; }
		public boolean isEnabled() { return _enabled; }
		public int getRounds() { return _rounds; }
		public void setRounds(int rounds) { _rounds = rounds; }
		public Location getPositionAll() { return _positionAll; }
		public Location getPositionBlue() { return _positionBlue; }
		public Location getPositionRed() { return _positionRed; }
		public int getPositionRadius() { return _positionRadius; }
		public String getRewardWinner() { return _rewardWinner; }
		public String getRewardLoser() { return _rewardLoser; }
		
		public void setPositionAll(Location loc) { _positionAll = loc; }
		public void setPositionBlue(Location loc) { _positionBlue = loc; }
		public void setPositionRed(Location loc) { _positionRed = loc; }
		public void setPositionRadius(int radius) { _positionRadius = radius; }
		public void setRewardWinner(String s) { _rewardWinner = s; }
		public void setRewardLoser(String s) { _rewardLoser = s; }
		
		/** Store a custom property value. */
		public void setCustom(String key, String value) { _custom.put(key, value); }
		/** @return Custom property as String, or defaultVal if missing. */
		public String getCustom(String key, String defaultVal) { return _custom.getOrDefault(key, defaultVal); }
		/** @return Custom property as int, or defaultVal if missing/parse error. */
		public int getCustomInt(String key, int defaultVal)
		{
			final String val = _custom.get(key);
			if (val == null) return defaultVal;
			try { return Integer.parseInt(val); } catch (NumberFormatException e) { return defaultVal; }
		}
		/** @return Custom property as Location (x,y,z), or null if missing. */
		public Location getCustomLoc(String key)
		{
			final String val = _custom.get(key);
			if (val == null || val.isEmpty()) return null;
			final String[] p = val.split(",");
			if (p.length < 3) return null;
			try { return new Location(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())); }
			catch (NumberFormatException e) { return null; }
		}
		
		/** Returns the kill streak thresholds as an int array (e.g. "3,7,10" -> [3,7,10]). */
		public int[] getKillStreakThresholds()
		{
			final String val = _custom.get("KillStreakThresholds");
			if (val == null || val.isEmpty())
				return new int[0];
			final String[] parts = val.split(",");
			final int[] result = new int[parts.length];
			for (int i = 0; i < parts.length; i++)
			{
				try { result[i] = Integer.parseInt(parts[i].trim()); }
				catch (NumberFormatException e) { result[i] = 0; }
			}
			return result;
		}
		
		/** Returns the reward for a kill streak threshold (format: "itemId,count" or "itemId1,count1;itemId2,count2"). */
		public String getKillStreakReward()
		{
			return _custom.getOrDefault("KillStreakReward", "");
		}
		
		/** Returns true if the given kill streak matches one of the thresholds and should be rewarded. */
		public boolean isKillStreakMilestone(int killStreak)
		{
			if (killStreak <= 0) return false;
			for (int threshold : getKillStreakThresholds())
			{
				if (killStreak == threshold)
					return true;
			}
			return false;
		}
		
		/** Returns respawn delay in seconds. Default 5. */
		public int getRespawnDelay()
		{
			return getCustomInt("RespawnDelay", 5);
		}
		
		/** Returns true if potions are allowed during the event. */
		public boolean isPotionsAllowed()
		{
			return "true".equalsIgnoreCase(_custom.getOrDefault("PotionsAllowed", "false"));
		}
		
		/** Returns true if targeting teammates is allowed. */
		public boolean isTargetTeammatesAllowed()
		{
			return "true".equalsIgnoreCase(_custom.getOrDefault("TargetTeammatesAllowed", "true"));
		}
		
		/** Returns true if healing other players is blocked during the event. */
		public boolean isHealBlocked()
		{
			return "true".equalsIgnoreCase(_custom.getOrDefault("HealBlocked", "false"));
		}
		
		/** Returns team 1 name. */
		public String getTeam1Name()
		{
			return _custom.getOrDefault("Team1Name", "Blue");
		}
		
		/** Returns team 2 name. */
		public String getTeam2Name()
		{
			return _custom.getOrDefault("Team2Name", "Red");
		}
	}
}
