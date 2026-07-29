package net.sf.l2j.gameserver.dungeon;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.sf.l2j.commons.config.ExProperties;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ConnectionPool;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.Config;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * DungeonEngine — always-on singleton that manages PvE dungeon instances.
 * Loads dungeon templates from XML and offers them on a configurable timer.
 */
public final class DungeonEngine
{
	private static final CLogger LOGGER = new CLogger(DungeonEngine.class.getName());
	
	private static final String DUNGEON_FILE = "./data/xml/dungeon.xml";
	private static final String CONFIG_FILE = "./config/dungeon.properties";
	
	private final Map<Integer, DungeonTemplate> _templates = new ConcurrentHashMap<>();
	private final List<DungeonInstance> _running = new CopyOnWriteArrayList<>();
	private final List<Integer> _participants = new CopyOnWriteArrayList<>();
	// Cooldown: objectId -> (dungeonId -> lastEntryTime)
	private final Map<Integer, Map<Integer, Long>> _cooldowns = new ConcurrentHashMap<>();
	private final AtomicInteger _nextInstanceId = new AtomicInteger(1);
	
	// Config
	private boolean _enabled = true;
	private int _intervalMinutes = 60;
	private int _registrationMinutes = 10;
	private boolean _autoSchedule = true;
	private int _spawnX = -84100;
	private int _spawnY = 242900;
	private int _spawnZ = -3450;
	
	private ScheduledFuture<?> _schedulerTask;
	private boolean _registrationOpen;
	private boolean _initialized;
	
	private DungeonEngine()
	{
	}
	
	private static class SingletonHolder
	{
		protected static final DungeonEngine INSTANCE = new DungeonEngine();
	}
	
	public static DungeonEngine getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	// ─── Initialization ───────────────────────────────────────────
	
	public void init()
	{
		loadConfig();
		loadTemplates();
		loadCooldowns();
		
		if (_enabled && _autoSchedule)
			startScheduler();
		
		_initialized = true;
		LOGGER.info("DungeonEngine initialized with {} templates (interval: {} min).", _templates.size(), _intervalMinutes);
	}
	
	private void loadConfig()
	{
		final ExProperties props = Config.initProperties(CONFIG_FILE);
		_enabled = props.getProperty("Enabled", true);
		_intervalMinutes = props.getProperty("IntervalMinutes", 60);
		_registrationMinutes = props.getProperty("RegistrationMinutes", 10);
		_autoSchedule = props.getProperty("AutoSchedule", true);
		_spawnX = props.getProperty("SpawnX", -84100);
		_spawnY = props.getProperty("SpawnY", 242900);
		_spawnZ = props.getProperty("SpawnZ", -3450);
	}
	
	// ─── XML loading (manual, no IXmlReader) ──────────────────────
	
	private void loadTemplates()
	{
		_templates.clear();
		
		try
		{
			final File file = new File(DUNGEON_FILE);
			if (!file.exists())
			{
				LOGGER.warn("Dungeon file not found: {}", DUNGEON_FILE);
				return;
			}
			
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			final DocumentBuilder builder = factory.newDocumentBuilder();
			final Document doc = builder.parse(file);
			
			final Node listNode = doc.getFirstChild();
			if (listNode == null)
				return;
			
			for (Node dungeonNode = listNode.getFirstChild(); dungeonNode != null; dungeonNode = dungeonNode.getNextSibling())
			{
				if (!"dungeon".equals(dungeonNode.getNodeName()))
					continue;
				
				parseDungeon(dungeonNode);
			}
		}
		catch (ParserConfigurationException | SAXException | IOException e)
		{
			LOGGER.warn("Failed to load dungeon templates.", e);
		}
		
		LOGGER.info("DungeonEngine: Loaded {} dungeon templates.", _templates.size());
	}
	
	private void parseDungeon(Node dungeonNode)
	{
		final NamedNodeMap attrs = dungeonNode.getAttributes();
		final int id = Integer.parseInt(attrs.getNamedItem("id").getNodeValue());
		final String name = attrs.getNamedItem("name").getNodeValue();
		final int minPlayers = Integer.parseInt(attrs.getNamedItem("minPlayers").getNodeValue());
		final int maxPlayers = Integer.parseInt(attrs.getNamedItem("maxPlayers").getNodeValue());
		final int minLevel = Integer.parseInt(attrs.getNamedItem("minLevel").getNodeValue());
		final int maxLevel = Integer.parseInt(attrs.getNamedItem("maxLevel").getNodeValue());
		final int cooldownHours = Integer.parseInt(attrs.getNamedItem("cooldownHours").getNodeValue());
		final String rewardHtm = attrs.getNamedItem("rewardHtm").getNodeValue();
		final String enterHtm = attrs.getNamedItem("enterHtm").getNodeValue();
		
		final Map<Integer, Integer> rewards = new HashMap<>();
		final String rewardsData = attrs.getNamedItem("rewards").getNodeValue();
		if (rewardsData != null && !rewardsData.isEmpty())
		{
			for (String reward : rewardsData.split(";"))
			{
				final String[] parts = reward.split(",");
				if (parts.length >= 2)
				{
					try { rewards.put(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())); }
					catch (NumberFormatException e) { LOGGER.warn("Invalid reward entry: {}", reward); }
				}
			}
		}
		
		final Map<Integer, DungeonStage> stages = new HashMap<>();
		
		for (Node stageNode = dungeonNode.getFirstChild(); stageNode != null; stageNode = stageNode.getNextSibling())
		{
			if (!"stage".equals(stageNode.getNodeName()))
				continue;
			
			final NamedNodeMap stageAttrs = stageNode.getAttributes();
			final int order = Integer.parseInt(stageAttrs.getNamedItem("order").getNodeValue());
			final String locData = stageAttrs.getNamedItem("loc").getNodeValue();
			final String[] locParts = locData.split(",");
			final Location loc = new Location(
				Integer.parseInt(locParts[0].trim()),
				Integer.parseInt(locParts[1].trim()),
				Integer.parseInt(locParts[2].trim()));
			final boolean teleport = Boolean.parseBoolean(stageAttrs.getNamedItem("teleport").getNodeValue());
			final int minutes = Integer.parseInt(stageAttrs.getNamedItem("minutes").getNodeValue());
			
			final Map<Integer, List<Location>> mobs = new HashMap<>();
			
			for (Node mobNode = stageNode.getFirstChild(); mobNode != null; mobNode = mobNode.getNextSibling())
			{
				if (!"mob".equals(mobNode.getNodeName()))
					continue;
				
				final NamedNodeMap mobAttrs = mobNode.getAttributes();
				final int npcId = Integer.parseInt(mobAttrs.getNamedItem("npcId").getNodeValue());
				final List<Location> locs = new ArrayList<>();
				
				final String locsData = mobAttrs.getNamedItem("locs").getNodeValue();
				for (String locStr : locsData.split(";"))
				{
					final String[] p = locStr.split(",");
					if (p.length >= 3)
						locs.add(new Location(
							Integer.parseInt(p[0].trim()),
							Integer.parseInt(p[1].trim()),
							Integer.parseInt(p[2].trim())));
				}
				
				mobs.put(npcId, locs);
			}
			
			stages.put(order, new DungeonStage(order, loc, teleport, minutes, mobs));
		}
		
		_templates.put(id, new DungeonTemplate(id, name, minPlayers, maxPlayers, minLevel, maxLevel, cooldownHours, rewards, rewardHtm, enterHtm, stages));
	}
	
	// ─── Scheduler ────────────────────────────────────────────────
	
	private void startScheduler()
	{
		_schedulerTask = ThreadPool.scheduleAtFixedRate(this::runScheduler, _intervalMinutes * 60000L, _intervalMinutes * 60000L);
		LOGGER.info("Dungeon scheduler started (interval: {} minutes).", _intervalMinutes);
	}
	
	private void runScheduler()
	{
		if (_registrationOpen || !_running.isEmpty())
			return;
		
		for (DungeonTemplate template : _templates.values())
		{
			if (template.getStages().isEmpty())
				continue;
			
			openRegistration(template.getId());
			return;
		}
	}
	
	// ─── Registration ─────────────────────────────────────────────
	
	public void openRegistration(int dungeonId)
	{
		final DungeonTemplate template = _templates.get(dungeonId);
		if (template == null || _registrationOpen)
			return;
		
		_registrationOpen = true;
		
		broadcast("[Dungeon] \"" + template.getName() + "\" registration is now open! Visit the Dungeon Manager to enter.");
		LOGGER.info("Dungeon registration opened: {} ({} min).", template.getName(), _registrationMinutes);
		
		ThreadPool.schedule(() ->
		{
			if (_registrationOpen)
			{
				_registrationOpen = false;
				broadcast("[Dungeon] Registration for \"" + template.getName() + "\" has closed.");
			}
		}, _registrationMinutes * 60000L);
	}
	
	public synchronized void enterDungeon(int dungeonId, Player player)
	{
		if (!_registrationOpen)
		{
			player.sendMessage("No dungeon registration is currently open.");
			return;
		}
		
		final DungeonTemplate template = _templates.get(dungeonId);
		if (template == null)
		{
			player.sendMessage("Dungeon not found.");
			return;
		}
		
		final int level = player.getStatus().getLevel();
		if (level < template.getMinLevel() || level > template.getMaxLevel())
		{
			player.sendMessage("Your level does not meet the requirements (Lv " + template.getMinLevel() + "-" + template.getMaxLevel() + ").");
			return;
		}
		
		if (isOnCooldown(player, dungeonId))
		{
			player.sendMessage("You are on cooldown for this dungeon. Please wait.");
			return;
		}
		
		if (template.getMinPlayers() > 1)
		{
			if (!player.isInParty())
			{
				player.sendMessage("You need a party of " + template.getMinPlayers() + "-" + template.getMaxPlayers() + " players to enter.");
				return;
			}
			
			final int partySize = player.getParty().getMembers().size();
			if (partySize < template.getMinPlayers() || partySize > template.getMaxPlayers())
			{
				player.sendMessage("Your party needs " + template.getMinPlayers() + "-" + template.getMaxPlayers() + " players. Current: " + partySize + ".");
				return;
			}
		}
		
		final List<Player> players = new ArrayList<>();
		if (player.isInParty())
		{
			for (Player pm : player.getParty().getMembers())
			{
				if (!pm.isOnline() || pm.isDead())
				{
					player.sendMessage(pm.getName() + " is not available.");
					return;
				}
				if (isOnCooldown(pm, dungeonId))
				{
					player.sendMessage(pm.getName() + " is on cooldown for this dungeon.");
					return;
				}
			}
			
			for (Player pm : player.getParty().getMembers())
			{
				_participants.add(pm.getObjectId());
				players.add(pm);
			}
		}
		else
		{
			_participants.add(player.getObjectId());
			players.add(player);
		}
		
		final DungeonInstance dungeon = new DungeonInstance(_nextInstanceId.getAndIncrement(), template, players);
		_running.add(dungeon);
		
		LOGGER.info("Dungeon {} started with {} players (instance {}).", template.getName(), players.size(), dungeon.getId());
	}
	
	// ─── Status ───────────────────────────────────────────────────
	
	public boolean isRegistrationOpen() { return _registrationOpen; }
	public boolean isEnabled() { return _enabled; }
	public boolean isInitialized() { return _initialized; }
	public List<DungeonInstance> getRunning() { return _running; }
	public List<Integer> getParticipants() { return _participants; }
	
	public List<DungeonTemplate> getTemplates()
	{
		return new ArrayList<>(_templates.values());
	}
	
	public DungeonTemplate getTemplate(int id)
	{
		return _templates.get(id);
	}
	
	public void onMobKill(Npc npc)
	{
		if (npc == null || !"[Dungeon]".equals(npc.getTitle()))
			return;
		
		for (DungeonInstance di : _running)
			di.onMobKill(npc);
	}
	
	public synchronized void removeDungeon(DungeonInstance dungeon)
	{
		_running.remove(dungeon);
		_registrationOpen = false;
	}
	
	// ─── Cooldown tracking (by objectId) ─────────────────────────
	
	public boolean isOnCooldown(Player player, int dungeonId)
	{
		final Map<Integer, Long> times = _cooldowns.get(player.getObjectId());
		if (times == null)
			return false;
		
		final Long lastEntry = times.get(dungeonId);
		if (lastEntry == null)
			return false;
		
		final DungeonTemplate t = _templates.get(dungeonId);
		if (t == null)
			return false;
		
		final long elapsed = System.currentTimeMillis() - lastEntry;
		final long cooldownMs = t.getCooldownHours() * 3600000L;
		return elapsed < cooldownMs;
	}
	
	public void setLastEntry(Player player, int dungeonId)
	{
		final Map<Integer, Long> times = _cooldowns.computeIfAbsent(player.getObjectId(), k -> new ConcurrentHashMap<>());
		times.put(dungeonId, System.currentTimeMillis());
		
		saveCooldown(player, dungeonId);
	}
	
	public long getRemainingCooldownSeconds(Player player, int dungeonId)
	{
		final Map<Integer, Long> times = _cooldowns.get(player.getObjectId());
		if (times == null)
			return 0;
		
		final Long lastEntry = times.get(dungeonId);
		if (lastEntry == null)
			return 0;
		
		final DungeonTemplate t = _templates.get(dungeonId);
		if (t == null)
			return 0;
		
		final long elapsed = System.currentTimeMillis() - lastEntry;
		final long cooldownMs = t.getCooldownHours() * 3600000L;
		return Math.max(0, (cooldownMs - elapsed) / 1000);
	}
	
	// ─── Player dungeon tracking ────────────────────────────────
	
	private final Map<Integer, DungeonInstance> _playerDungeonMap = new ConcurrentHashMap<>();
	
	public void setPlayerDungeon(Player player, DungeonInstance dungeon)
	{
		if (dungeon == null)
			_playerDungeonMap.remove(player.getObjectId());
		else
			_playerDungeonMap.put(player.getObjectId(), dungeon);
	}
	
	public DungeonInstance getPlayerDungeon(Player player)
	{
		return _playerDungeonMap.get(player.getObjectId());
	}
	
	public boolean isInDungeon(Player player)
	{
		return _playerDungeonMap.containsKey(player.getObjectId());
	}
	
	public void onPlayerDeath(Player player)
	{
		final DungeonInstance di = getPlayerDungeon(player);
		if (di != null)
			di.onPlayerDeath(player);
	}
	
	// ─── NPC methods ───────────────────────────────────────────────
	
	public int getSpawnX() { return _spawnX; }
	public int getSpawnY() { return _spawnY; }
	public int getSpawnZ() { return _spawnZ; }
	public int getRegistrationMinutes() { return _registrationMinutes; }
	
	// ─── Database ─────────────────────────────────────────────────
	
	private void loadCooldowns()
	{
		try (Connection con = ConnectionPool.getConnection();
			 PreparedStatement st = con.prepareStatement("SELECT * FROM dungeon");
			 ResultSet rs = st.executeQuery())
		{
			while (rs.next())
			{
				final int dungeonId = rs.getInt("dungid");
				final int charId = rs.getInt("charId");
				final long lastJoin = rs.getLong("lastjoin");
				
				final Map<Integer, Long> times = _cooldowns.computeIfAbsent(charId, k -> new ConcurrentHashMap<>());
				times.put(dungeonId, lastJoin);
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Could not load dungeon cooldowns.", e);
		}
	}
	
	private void saveCooldown(Player player, int dungeonId)
	{
		final int charId = player.getObjectId();
		final long now = System.currentTimeMillis();
		
		ThreadPool.execute(() ->
		{
			try (Connection con = ConnectionPool.getConnection())
			{
				try (PreparedStatement st = con.prepareStatement("DELETE FROM dungeon WHERE dungid=? AND charId=?"))
				{
					st.setInt(1, dungeonId);
					st.setInt(2, charId);
					st.execute();
				}
				
				try (PreparedStatement st = con.prepareStatement("INSERT INTO dungeon (dungid, charId, lastjoin) VALUES (?,?,?)"))
				{
					st.setInt(1, dungeonId);
					st.setInt(2, charId);
					st.setLong(3, now);
					st.execute();
				}
			}
			catch (Exception e)
			{
				LOGGER.warn("Could not save dungeon cooldown.", e);
			}
		});
	}
	
	// ─── Utility ──────────────────────────────────────────────────
	
	private void broadcast(String msg)
	{
		final CreatureSay cs = new CreatureSay(0, SayType.ALL, "Dungeon", msg);
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.isOnline())
				player.sendPacket(cs);
		}
	}
}
