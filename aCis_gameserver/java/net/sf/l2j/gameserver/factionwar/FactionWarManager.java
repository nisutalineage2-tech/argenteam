package net.sf.l2j.gameserver.factionwar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.enums.GaugeColor;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.SetupGauge;
import net.sf.l2j.gameserver.model.World;

public class FactionWarManager
{
	private static final CLogger LOGGER = new CLogger(FactionWarManager.class.getName());
	
	private volatile boolean _running;
	private boolean _startedOnce;
	private volatile boolean _votingPhaseActive;
	private volatile int _currentMapIndex;
	private final Map<Integer, Integer> _scores = new ConcurrentHashMap<>();
	
	// Per-player stats tracking: playerId -> FactionWarStats
	private final Map<Integer, FactionWarStats> _playerStats = new ConcurrentHashMap<>();
	
	private Spawn _flagSpawn;
	private Npc _flagNpc;
	
	private final List<Spawn> _goodGuardSpawns = new ArrayList<>();
	private final List<Npc> _goodGuardNpcs = new ArrayList<>();
	private final List<Spawn> _evilGuardSpawns = new ArrayList<>();
	private final List<Npc> _evilGuardNpcs = new ArrayList<>();
	
	private Spawn _registrarSpawn;
	private Npc _registrarNpc;
	
	private final FactionWarCheckpoint _checkpoints = new FactionWarCheckpoint();
	
	private ScheduledFuture<?> _mapRotationTask;
	private ScheduledFuture<?> _mapVoteTask;
	private ScheduledFuture<?> _flagRespawnTask;
	private ScheduledFuture<?> _guardRespawnTask;
	private ScheduledFuture<?> _eventEndTask;
	private ScheduledFuture<?> _scoreboardTask;
	private ScheduledFuture<?> _countdownTask;
	private volatile int _winningFaction;
	private volatile long _startTime;
	private volatile long _durationMs;
	
	private volatile boolean _votingActive;
	private final Map<Integer, Integer> _mapVotes = new HashMap<>();
	private final java.util.Set<Integer> _votedPlayers = new java.util.HashSet<>();
	private volatile java.util.List<FactionWarConfig.WarMap> _currentVoteMaps;
	
	private static class SingletonHolder
	{
		protected static final FactionWarManager INSTANCE = new FactionWarManager();
	}
	
	public static FactionWarManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private FactionWarManager()
	{
		FactionWarConfig.load();
	}
	
	public boolean isRunning()
	{
		return _running;
	}
	
	public boolean isStartedOnce()
	{
		return _startedOnce;
	}
	
	public boolean isVotingPhaseActive()
	{
		return _votingPhaseActive;
	}
	
	public int getScore(int factionId)
	{
		return _scores.getOrDefault(factionId, 0);
	}
	
	public Map<Integer, Integer> getAllScores()
	{
		return new HashMap<>(_scores);
	}
	
	public FactionWarCheckpoint getCheckpoints()
	{
		return _checkpoints;
	}
	
	/**
	 * Gets the top N players sorted by points (kills + flag captures).
	 */
	public List<FactionWarStats> getTopPlayers(int limit)
	{
		return _playerStats.values().stream()
			.sorted((a, b) -> Integer.compare(b.points, a.points))
			.limit(limit)
			.collect(java.util.stream.Collectors.toList());
	}
	
	/**
	 * Gets all player stats for display on the Community Board.
	 */
	public Map<Integer, FactionWarStats> getAllPlayerStats()
	{
		return new HashMap<>(_playerStats);
	}
	
	public int getWinningFaction()
	{
		return _winningFaction;
	}
	
	public String getScoreboard()
	{
		final StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, Integer> entry : _scores.entrySet())
		{
			if (sb.length() > 0)
				sb.append(" vs ");
			sb.append("Faction ").append(entry.getKey()).append(": ").append(entry.getValue());
		}
		return sb.toString();
	}
	
	/**
	 * Starts the Faction War voting phase. During this phase, players vote for the map.
	 * After voting ends, a 10-second countdown begins, then the war starts.
	 */
	public void startVotePhase()
	{
		if (_running || _votingPhaseActive)
		{
			LOGGER.warn("Faction War already running or in voting phase.");
			return;
		}
		
		FactionWarConfig.load();
		
		_votingPhaseActive = true;
		_scores.clear();
		_scores.put(FactionWarConfig.getGoodFactionId(), 0);
		_scores.put(FactionWarConfig.getEvilFactionId(), 0);
		
		// Spawn registrar in neutral zone so players can register during voting
		spawnRegistrar();
		
		// Pick random maps for voting
		_currentVoteMaps = FactionWarConfig.getVoteMaps();
		_mapVotes.clear();
		_votedPlayers.clear();
		
		for (int i = 0; i < _currentVoteMaps.size(); i++)
			_mapVotes.put(i, 0);
		
		final int voteSeconds = FactionWarConfig.getMapVoteSeconds();
		
		broadcast("[Faction War] ¡La guerra de facciones se acerca! Vota por el mapa: \" .votemap \"");
		
		// Send voting HTML to all faction players
		sendVotePopup();
		
		// Schedule vote end
		_mapVoteTask = ThreadPool.schedule(this::applyInitialVote, voteSeconds * 1000L);
		
		LOGGER.info("Faction War voting phase started ({} seconds). {} maps available.", voteSeconds, _currentVoteMaps.size());
	}
	
	/**
	 * Applies the initial vote result, announces winner, then starts the war after 10 seconds.
	 */
	private void applyInitialVote()
	{
		if (!_votingPhaseActive)
			return;
		
		_votingActive = false;
		// IMPORTANT: do NOT set _votingPhaseActive = false here.
		// It stays true until start() is called, so stop() can cancel the countdown.
		
		// Find winner
		int bestIndex = 0;
		int bestVotes = 0;
		for (Map.Entry<Integer, Integer> entry : _mapVotes.entrySet())
		{
			if (entry.getValue() > bestVotes)
			{
				bestVotes = entry.getValue();
				bestIndex = entry.getKey();
			}
		}
		
		// Match vote map index to actual map index
		final FactionWarConfig.WarMap chosen;
		if (bestVotes == 0 || _currentVoteMaps == null)
		{
			chosen = FactionWarConfig.getMaps().get(Rnd.get(FactionWarConfig.getMaps().size()));
			broadcast("[Faction War] ¡Nadie votó! Mapa aleatorio: " + chosen.getName());
		}
		else
		{
			chosen = _currentVoteMaps.get(bestIndex);
			broadcast("[Faction War] ¡Mapa elegido: " + chosen.getName() + " (" + bestVotes + " votos)! La guerra comienza en 10 segundos...");
		}
		
		// Find the map index in the full map list
		int mapIndex = 0;
		for (int i = 0; i < FactionWarConfig.getMaps().size(); i++)
		{
			if (FactionWarConfig.getMaps().get(i).getName().equals(chosen.getName()))
			{
				mapIndex = i;
				break;
			}
		}
		
		final int finalMapIndex = mapIndex;
		
		// 10-second countdown
		_countdownTask = ThreadPool.schedule(() ->
		{
			// If voting phase was cancelled (e.g. by stop()), don't start
			if (!_votingPhaseActive)
				return;
			
			start(FactionWarConfig.getScoreToWin(), FactionWarConfig.getWarDurationMinutes(), finalMapIndex);
			
		}, 10000);
	}
	
	/**
	 * Start the war (called from admin command or after vote phase).
	 */
	public void start(Player player)
	{
		start(FactionWarConfig.getScoreToWin(), FactionWarConfig.getWarDurationMinutes(), -1);
	}
	
	/**
	 * Start the war with given score and duration, random map.
	 */
	public void start(int scoreToWin, int durationMinutes)
	{
		start(scoreToWin, durationMinutes, -1);
	}
	
	/**
	 * Start the war with given score, duration, and a specific map index.
	 * @param mapIndex Use -1 for random.
	 */
	public void start(int scoreToWin, int durationMinutes, int mapIndex)
	{
		if (_running)
		{
			LOGGER.warn("Faction War already running.");
			return;
		}
		
		FactionWarConfig.load();
		
		_running = true;
		_startedOnce = true;
		_votingPhaseActive = false;
		_scores.clear();
		_scores.put(FactionWarConfig.getGoodFactionId(), 0);
		_scores.put(FactionWarConfig.getEvilFactionId(), 0);
		_playerStats.clear();
		_winningFaction = 0;
		
		_currentMapIndex = (mapIndex >= 0 && mapIndex < FactionWarConfig.getMaps().size()) ? mapIndex : Rnd.get(FactionWarConfig.getMaps().size());
		
		_startTime = System.currentTimeMillis();
		_durationMs = durationMinutes * 60000L;
		
		spawnFlag();
		spawnGuards();
		spawnRegistrar();
		_checkpoints.spawn(_currentMapIndex);
		
		if (FactionWarConfig.getMapRotationMinutes() > 0 && FactionWarConfig.getMaps().size() > 1)
		{
			_mapRotationTask = ThreadPool.schedule(this::startMapVote, FactionWarConfig.getMapRotationMinutes() * 60000L);
		}
		
		if (durationMinutes > 0)
		{
			_eventEndTask = ThreadPool.schedule(() -> stop(), _durationMs);
		}
		
		sendGaugeToAllPlayers();
		_scoreboardTask = ThreadPool.scheduleAtFixedRate(this::broadcastScoreboardWithTime, 15000, 15000);
		
		final FactionWarConfig.WarMap firstMap = FactionWarConfig.getMaps().get(_currentMapIndex);
		
		if (FactionWarConfig.isAnnounceStart())
		{
			broadcast("[Faction War] ¡La guerra ha comenzado! Mapa: " + firstMap.getName() + " | Puntuación: " + scoreToWin + (durationMinutes > 0 ? " | " + durationMinutes + "min" : ""));
		}
		
		// Refresh faction visuals for all players (colors, titles)
		broadcastFactionVisuals();
		
		// Auto-teleport all phantoms to war
		final int teleportedPhantoms = net.sf.l2j.gameserver.phantom.PhantomEngine.teleportPhantomsToWar();
		
		// Players must go to the Teleport Manager or the Registrar NPC to enter the war
		broadcast("[Faction War] ¡La guerra ha comenzado! Ve al Teleport Manager o al Registrador de Guerra en la zona neutral para unirte a la batalla. Mapa: " + firstMap.getName());
		
		LOGGER.info("Faction War started. Map: {} (index: {}). Score: {}. Duration: {}min. Teleported {} phantoms.", 
			firstMap.getName(), _currentMapIndex, scoreToWin, durationMinutes, teleportedPhantoms);
	}
	
	public void stop()
	{
		if (!_running && !_votingPhaseActive)
			return;
		
		// Determine winner BEFORE clearing state
		final int goodScore = getScore(FactionWarConfig.getGoodFactionId());
		final int evilScore = getScore(FactionWarConfig.getEvilFactionId());
		_winningFaction = 0;
		if (goodScore > evilScore)
			_winningFaction = FactionWarConfig.getGoodFactionId();
		else if (evilScore > goodScore)
			_winningFaction = FactionWarConfig.getEvilFactionId();
		
		_running = false;
		_votingPhaseActive = false;
		
		cancelTask(_mapRotationTask);
		cancelTask(_mapVoteTask);
		cancelTask(_flagRespawnTask);
		cancelTask(_guardRespawnTask);
		cancelTask(_eventEndTask);
		cancelTask(_scoreboardTask);
		cancelTask(_countdownTask);
		
		despawnFlag();
		despawnGuards();
		despawnRegistrar();
		_checkpoints.despawn();
		
		final int returned = net.sf.l2j.gameserver.phantom.PhantomEngine.returnPhantomsFromWar();
		
		if (FactionWarConfig.isAnnounceEnd())
		{
			final String winnerName;
			if (_winningFaction == FactionWarConfig.getGoodFactionId())
				winnerName = "¡LOS BUENOS GANAN!";
			else if (_winningFaction == FactionWarConfig.getEvilFactionId())
				winnerName = "¡LOS MALVADOS GANAN!";
			else
				winnerName = "¡EMPATE!";
			
			// Announce top 3 players
			final List<FactionWarStats> top3 = getTopPlayers(3);
			final StringBuilder topMsg = new StringBuilder();
			topMsg.append("[Faction War] La guerra ha terminado! ").append(winnerName).append(" [").append(goodScore).append(" - ").append(evilScore).append("]");
			if (!top3.isEmpty())
			{
				topMsg.append(" | Top 3:");
				for (int i = 0; i < top3.size(); i++)
				{
					final FactionWarStats s = top3.get(i);
					topMsg.append(" #").append(i + 1).append(" ").append(s.playerName).append(" (").append(s.points).append("pts)");
				}
			}
			broadcast(topMsg.toString());
			
			// Give rewards to top 3 and winning faction
			giveRewards(top3);
		}
		
		teleportFactionPlayersToNeutral();
		
		LOGGER.info("Faction War stopped. Returned {} phantoms.", returned);
		
		// Notify EventEngine that FW ended (alternance: FW → event)
		net.sf.l2j.gameserver.event.EventEngine.getInstance().onFactionWarEnded();
	}
	
	/**
	 * Gives rewards to top 3 players and winning faction members.
	 */
	private void giveRewards(List<FactionWarStats> top3)
	{
		final int rewardItemId = FactionWarConfig.getRewardItemId();
		final int[] topRewards = FactionWarConfig.getTopRewardAmounts();
		final int winReward = FactionWarConfig.getWinningFactionReward();
		
		if (rewardItemId <= 0)
			return;
		
		// Top 3 individual rewards
		for (int i = 0; i < top3.size() && i < topRewards.length; i++)
		{
			final FactionWarStats stats = top3.get(i);
			if (topRewards[i] <= 0)
				continue;
			
			final Player player = World.getInstance().getPlayer(stats.playerId);
			if (player != null && player.isOnline())
			{					player.addItem(rewardItemId, topRewards[i], true);
				broadcast("[Faction War] ¡" + stats.playerName + " queda #" + (i + 1) + " y recibe " + topRewards[i] + "x " + getItemName(rewardItemId) + "!");
			}
		}
		
		// Winning faction reward (all members)
		if (_winningFaction > 0)
		{
			int count = 0;
			for (Player player : World.getInstance().getPlayers())
			{
				if (player != null && player.isOnline() && player.getFactionId() == _winningFaction)
				{
					player.addItem(rewardItemId, winReward, true);
					count++;
				}
			}
			broadcast("[Faction War] ¡La facción ganadora recibe " + winReward + "x " + getItemName(rewardItemId) + "! (" + count + " miembros premiados)");
		}
	}
	
	private String getItemName(int itemId)
	{
		final net.sf.l2j.gameserver.data.xml.ItemData itemData = net.sf.l2j.gameserver.data.xml.ItemData.getInstance();
		if (itemData != null)
		{
			final net.sf.l2j.gameserver.model.item.kind.Item item = itemData.getTemplate(itemId);
			if (item != null)
				return item.getName();
		}
		return "Item";
	}
	
	/**
	 * Backward-compatible single-arg version. Calls the two-arg version with killerId=0 (no per-player tracking).
	 */
	public void onFlagKilled(int killerFactionId)
	{
		onFlagKilled(killerFactionId, 0);
	}
	
	public void onPvpKill(int killerFactionId, int victimFactionId, int killerId, int victimId)
	{
		if (!_running || !FactionWarConfig.isEnabled() || killerFactionId == victimFactionId)
			return;
		
		final int points = FactionWarConfig.getPointsPerPvpKill();
		if (points <= 0)
			return;
		
		_scores.merge(killerFactionId, points, Integer::sum);
		
		// Track per-player stats
		FactionWarStats killerStats = _playerStats.get(killerId);
		if (killerStats == null)
		{
			killerStats = new FactionWarStats();
			killerStats.playerId = killerId;
			final net.sf.l2j.gameserver.model.actor.Player killer = net.sf.l2j.gameserver.model.World.getInstance().getPlayer(killerId);
			killerStats.playerName = (killer != null) ? killer.getName() : "Unknown";
			killerStats.factionId = killerFactionId;
			_playerStats.put(killerId, killerStats);
		}
		killerStats.kills++;
		killerStats.points += points;
		
		FactionWarStats victimStats = _playerStats.get(victimId);
		if (victimStats == null)
		{
			victimStats = new FactionWarStats();
			victimStats.playerId = victimId;
			final net.sf.l2j.gameserver.model.actor.Player victim = net.sf.l2j.gameserver.model.World.getInstance().getPlayer(victimId);
			victimStats.playerName = (victim != null) ? victim.getName() : "Unknown";
			victimStats.factionId = victimFactionId;
			_playerStats.put(victimId, victimStats);
		}
		victimStats.deaths++;
		
		if (FactionWarConfig.isAnnouncePvpKill())
			broadcast("[Faction War] PvP kill! Faction " + killerFactionId + " +" + points + " pts");
		
		// Give adena reward to the killer
		final int adenaReward = FactionWarConfig.getPvpAdenaReward();
		if (adenaReward > 0)
		{
			final net.sf.l2j.gameserver.model.actor.Player killer = net.sf.l2j.gameserver.model.World.getInstance().getPlayer(killerId);
			if (killer != null && killer.isOnline())
			{
				killer.addItem(57, adenaReward, true);
				
				// Random spoil drop to the killer's party
				giveRandomSpoilToParty(killer);
			}
		}
		
		checkWinner();
	}
	
	/**
	 * Gives a random spoil item to the killer's party members.
	 */
	private void giveRandomSpoilToParty(net.sf.l2j.gameserver.model.actor.Player killer)
	{
		final java.util.List<int[]> spoilItems = FactionWarConfig.getSpoilItems();
		if (spoilItems == null || spoilItems.isEmpty())
			return;
		
		final int[] chosen = spoilItems.get(net.sf.l2j.commons.random.Rnd.get(spoilItems.size()));
		final int itemId = chosen[0];
		final int count = chosen.length > 1 ? chosen[1] : 1;
		
		if (itemId <= 0)
			return;
		
		final net.sf.l2j.gameserver.model.group.Party party = killer.getParty();
		if (party != null)
		{
			// Distribute to all party members in range
			for (net.sf.l2j.gameserver.model.actor.Player member : party.getMembers())
			{
				if (member != null && member.isOnline() && !member.isDead() && member.distance3D(killer) <= 1500)
					member.addItem(itemId, count, true);
			}
		}
		else
		{
			killer.addItem(itemId, count, true);
		}
	}
	
	/**
	 * Track a flag capture for a specific player.
	 */
	public void onFlagKilled(int killerFactionId, int killerId)
	{
		if (!_running || !FactionWarConfig.isEnabled())
			return;
		
		final int points = FactionWarConfig.getPointsPerFlagKill();
		_scores.merge(killerFactionId, points, Integer::sum);
		
		// Track per-player stats for flag killer
		FactionWarStats stats = _playerStats.get(killerId);
		if (stats == null)
		{
			stats = new FactionWarStats();
			stats.playerId = killerId;
			final net.sf.l2j.gameserver.model.actor.Player player = net.sf.l2j.gameserver.model.World.getInstance().getPlayer(killerId);
			stats.playerName = (player != null) ? player.getName() : "Unknown";
			stats.factionId = killerFactionId;
			_playerStats.put(killerId, stats);
		}
		stats.points += points;
		
		final int loserFactionId = (killerFactionId == FactionWarConfig.getGoodFactionId())
			? FactionWarConfig.getEvilFactionId()
			: FactionWarConfig.getGoodFactionId();
		
		teleportFactionToBase(loserFactionId);
		
		if (FactionWarConfig.isAnnounceFlagKill())
			broadcast("[Faction War] Faction " + killerFactionId + " destruyo la bandera! +" + points + " pts. Faction " + loserFactionId + " regresa a su base!");
		
		if (FactionWarConfig.isAnnounceScore())
			broadcast("[Faction War] " + getScoreboard());
		
		checkWinner();
		
		if (_running)
			scheduleFlagRespawn();
	}
	
	private void checkWinner()
	{
		final int target = FactionWarConfig.getScoreToWin();
		for (int factionId : _scores.keySet())
		{
			if (_scores.get(factionId) >= target)
			{
				stop();
				return;
			}
		}
	}
	
	private void teleportFactionToBase(int factionId)
	{
		final Location baseLoc = getFactionSpawn(factionId);
		if (baseLoc == null)
			return;
		
		for (Player player : World.getInstance().getPlayers())
		{
			if (player == null || !player.isOnline() || player.isDead())
				continue;
			
			if (player.getFactionId() == factionId)
				player.teleportTo(baseLoc, 50);
		}
	}
	
	public void teleportFactionPlayersToNeutral()
	{
		final Location neutralLoc = FactionWarConfig.getNeutralSpawnLoc();
		if (neutralLoc == null)
			return;
		
		for (Player player : World.getInstance().getPlayers())
		{
			if (player == null || !player.isOnline())
				continue;
			
			if (player.getFactionId() != 0)
				player.teleportTo(neutralLoc, 50);
		}
	}
	
	public void teleportToWarMap(Player player)
	{
		if (player == null || !_running)
			return;
		
		final Location factionLoc = getFactionSpawn(player.getFactionId());
		if (factionLoc != null)
		{
			// Add randomization to spread players around spawn
			final int rx = factionLoc.getX() + Rnd.get(-300, 300);
			final int ry = factionLoc.getY() + Rnd.get(-300, 300);
			player.teleportTo(rx, ry, factionLoc.getZ(), 50);
		}
	}
	
	private void deregisterAll()
	{
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.isOnline() && FactionWarRegistry.getInstance().isRegistered(player))
				FactionWarRegistry.getInstance().unregister(player);
		}
	}
	
	private void spawnFlag()
	{
		try
		{
			final FactionWarConfig.WarMap map = FactionWarConfig.getMaps().get(_currentMapIndex);
			_flagSpawn = new Spawn(FactionWarConfig.getFlagNpcId(), true);
			_flagSpawn.setLoc(map.getX(), map.getY(), map.getZ(), 0);
			_flagNpc = _flagSpawn.doSpawn(false);
			if (_flagNpc != null)
			{
				_flagNpc.setInvul(false);
				_flagNpc.setIsImmobilized(true);
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to spawn flag.", e);
		}
	}
	
	private void despawnFlag()
	{
		if (_flagNpc != null)
		{
			_flagNpc.deleteMe();
			_flagNpc = null;
		}
		if (_flagSpawn != null)
		{
			_flagSpawn.doDelete();
			_flagSpawn = null;
		}
	}
	
	private void scheduleFlagRespawn()
	{
		if (_flagRespawnTask != null && !_flagRespawnTask.isDone())
			_flagRespawnTask.cancel(false);
		_flagRespawnTask = ThreadPool.schedule(() ->
		{
			if (_running)
				spawnFlag();
		}, FactionWarConfig.getFlagRespawnDelay());
	}
	
	private void spawnGuards()
	{
		if (FactionWarConfig.getMaps().isEmpty())
			return;
		
		final FactionWarConfig.WarMap map = FactionWarConfig.getMaps().get(_currentMapIndex);
		final int count = FactionWarConfig.getGuardsPerBase();
		final int radius = FactionWarConfig.getGuardCircleRadius();
		
		spawnGuardGroup(map.getGoodSpawn(), count, radius, _goodGuardSpawns, _goodGuardNpcs);
		spawnGuardGroup(map.getEvilSpawn(), count, radius, _evilGuardSpawns, _evilGuardNpcs);
	}
	
	private void spawnGuardGroup(Location baseLoc, int count, int radius, List<Spawn> spawns, List<Npc> npcs)
	{
		for (int i = 0; i < count; i++)
		{
			final double angle = (2 * Math.PI * i) / count;
			final int x = baseLoc.getX() + (int) (radius * Math.cos(angle));
			final int y = baseLoc.getY() + (int) (radius * Math.sin(angle));
			final int z = baseLoc.getZ();
			
			try
			{
				final Spawn spawn = new Spawn(FactionWarConfig.getGuardNpcId(), true);
				spawn.setLoc(x, y, z, 0);
				final Npc npc = spawn.doSpawn(false);
				if (npc != null)
				{
					spawns.add(spawn);
					npcs.add(npc);
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Failed to spawn guard at ({}, {}, {}).", e, x, y, z);
			}
		}
	}
	
	private void despawnGuards()
	{
		despawnGuardGroup(_goodGuardSpawns, _goodGuardNpcs);
		despawnGuardGroup(_evilGuardSpawns, _evilGuardNpcs);
	}
	
	private void despawnGuardGroup(List<Spawn> spawns, List<Npc> npcs)
	{
		for (Npc npc : npcs)
		{
			if (npc != null)
				npc.deleteMe();
		}
		npcs.clear();
		
		for (Spawn spawn : spawns)
		{
			if (spawn != null)
				spawn.doDelete();
		}
		spawns.clear();
	}
	
	private void spawnRegistrar()
	{
		final Location neutralLoc = FactionWarConfig.getNeutralSpawnLoc();
		if (neutralLoc == null)
			return;
		
		try
		{
			_registrarSpawn = new Spawn(FactionWarConfig.getWarRegistrarNpcId(), true);
			_registrarSpawn.setLoc(neutralLoc.getX(), neutralLoc.getY(), neutralLoc.getZ(), 0);
			_registrarNpc = _registrarSpawn.doSpawn(false);
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to spawn War Registrar at neutral zone.", e);
		}
	}
	
	private void despawnRegistrar()
	{
		if (_registrarNpc != null)
		{
			_registrarNpc.deleteMe();
			_registrarNpc = null;
		}
		if (_registrarSpawn != null)
		{
			_registrarSpawn.doDelete();
			_registrarSpawn = null;
		}
	}
	
	public void onGuardDied()
	{
		if (!_running)
			return;
		
		scheduleGuardRespawn();
	}
	
	private void scheduleGuardRespawn()
	{
		if (_guardRespawnTask != null && !_guardRespawnTask.isDone())
			_guardRespawnTask.cancel(false);
		_guardRespawnTask = ThreadPool.schedule(() ->
		{
			if (_running)
				spawnGuards();
		}, FactionWarConfig.getGuardRespawnDelay());
	}
	
	/**
	 * Starts a map rotation vote (mid-war map change).
	 */
	private void startMapVote()
	{
		if (!_running || FactionWarConfig.getMaps().size() <= 1)
			return;
		
		_votingActive = true;
		_mapVotes.clear();
		_votedPlayers.clear();
		_currentVoteMaps = FactionWarConfig.getVoteMaps();
		
		for (int i = 0; i < _currentVoteMaps.size(); i++)
			_mapVotes.put(i, 0);
		
		final int voteSeconds = FactionWarConfig.getMapVoteSeconds();
		
		broadcast("[Faction War] ¡Vota por el próximo mapa! Tienes " + voteSeconds + " segundos. Usa .votemap o habla con el Registrador.");
		
		sendVotePopup();
		
		_mapVoteTask = ThreadPool.schedule(this::applyMapVote, voteSeconds * 1000L);
	}
	
	/**
	 * Sends the voting HTML to all faction players.
	 */
	private void sendVotePopup()
	{
		if (_currentVoteMaps == null || _registrarNpc == null)
			return;
		
		final int npcId = _registrarNpc.getObjectId();
		
		for (Player player : World.getInstance().getPlayers())
		{
			if (player == null || !player.isOnline())
				continue;
			if (player.getFactionId() == FactionWarConfig.getGoodFactionId() || player.getFactionId() == FactionWarConfig.getEvilFactionId())
			{
				sendVoteHtml(player, npcId);
			}
		}
	}
	
	/**
	 * Sends the voting HTML to a specific player.
	 */
	public void sendVoteHtml(Player player)
	{
		if (_currentVoteMaps == null || _registrarNpc == null)
			return;
		sendVoteHtml(player, _registrarNpc.getObjectId());
	}
	
	private void sendVoteHtml(Player player, int npcId)
	{
		final StringBuilder mapsHtml = new StringBuilder();
		for (int i = 0; i < _currentVoteMaps.size(); i++)
		{
			final FactionWarConfig.WarMap map = _currentVoteMaps.get(i);
			mapsHtml.append("<table width=270><tr>");
			mapsHtml.append("<td width=30 align=center><font color=LEVEL>").append(i + 1).append("</font></td>");
			mapsHtml.append("<td width=240><a action=\"bypass -h npc_").append(npcId).append("_fwVote_").append(i).append("\">").append(map.getName()).append("</a></td>");
			mapsHtml.append("</tr></table>");
			mapsHtml.append("<img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\">");
		}
		
		final NpcHtmlMessage html = new NpcHtmlMessage(npcId);
		html.setFile("data/html/script/factionwar/WarRegistrar/war_registrar_map_vote.htm");
		html.replace("%MAPS%", mapsHtml.toString());
		html.replace("%SECONDS%", String.valueOf(FactionWarConfig.getMapVoteSeconds()));
		player.sendPacket(html);
		
		player.sendPacket(new ExShowScreenMessage("¡Vota por el mapa de la guerra!", 10000, ExShowScreenMessage.SMPOS.TOP_CENTER, false));
	}
	
	public void onPlayerVote(Player player, int mapIndex)
	{
		if (!_votingActive && !_votingPhaseActive)
			return;
		if (_currentVoteMaps == null)
			return;
		if (mapIndex < 0 || mapIndex >= _currentVoteMaps.size())
			return;
		if (_votedPlayers.contains(player.getObjectId()))
		{
			player.sendMessage("[Faction War] Ya has votado.");
			return;
		}
		
		_votedPlayers.add(player.getObjectId());
		_mapVotes.merge(mapIndex, 1, Integer::sum);
		
		player.sendMessage("[Faction War] ¡Voto registrado por " + _currentVoteMaps.get(mapIndex).getName() + "!");
	}
	
	/**
	 * Applies a mid-war map rotation vote.
	 */
	private void applyMapVote()
	{
		if (!_running)
			return;
		
		_votingActive = false;
		
		int bestIndex = 0;
		int bestVotes = 0;
		for (Map.Entry<Integer, Integer> entry : _mapVotes.entrySet())
		{
			if (entry.getValue() > bestVotes)
			{
				bestVotes = entry.getValue();
				bestIndex = entry.getKey();
			}
		}
		
		FactionWarConfig.WarMap chosen;
		if (bestVotes == 0 || _currentVoteMaps == null)
		{
			chosen = FactionWarConfig.getMaps().get(Rnd.get(FactionWarConfig.getMaps().size()));
			broadcast("[Faction War] ¡Nadie votó! Mapa aleatorio seleccionado: " + chosen.getName());
		}
		else
		{
			chosen = _currentVoteMaps.get(bestIndex);
			broadcast("[Faction War] ¡Mapa votado: " + chosen.getName() + "! (" + bestVotes + " votos)");
		}
		
		int newIndex = -1;
		for (int i = 0; i < FactionWarConfig.getMaps().size(); i++)
		{
			if (FactionWarConfig.getMaps().get(i).getName().equals(chosen.getName()))
			{
				newIndex = i;
				break;
			}
		}
		
		if (newIndex == -1)
			newIndex = Rnd.get(FactionWarConfig.getMaps().size());
		
		// Respawn everything on new map
		despawnFlag();
		despawnGuards();
		_checkpoints.despawn();
		
		_currentMapIndex = newIndex;
		
		spawnFlag();
		spawnGuards();
		_checkpoints.spawn(_currentMapIndex);
		
		_mapVotes.clear();
		_votedPlayers.clear();
		_currentVoteMaps = null;
		
		if (_running && FactionWarConfig.getMapRotationMinutes() > 0 && FactionWarConfig.getMaps().size() > 1)
		{
			_mapRotationTask = ThreadPool.schedule(this::startMapVote, FactionWarConfig.getMapRotationMinutes() * 60000L);
		}
	}
	
	/**
	 * Checks if the player is in the neutral zone.
	 */
	public boolean isInNeutralZone(Player player)
	{
		if (player == null)
			return false;
		return FactionWarConfig.isInNeutralZone(new Location(player.getX(), player.getY(), player.getZ()));
	}
	
	public int getCurrentMapIndex()
	{
		return _currentMapIndex;
	}
	
	public Location getFactionSpawn(int factionId)
	{
		if (FactionWarConfig.getMaps().isEmpty())
			return null;
		
		final FactionWarConfig.WarMap map = FactionWarConfig.getMaps().get(_currentMapIndex);
		
		if (factionId == FactionWarConfig.getGoodFactionId())
			return map.getGoodSpawn();
		if (factionId == FactionWarConfig.getEvilFactionId())
			return map.getEvilSpawn();
		return null;
	}
	
	public String getRemainingTimeStr()
	{
		if (_durationMs <= 0 || _startTime <= 0)
			return "";
		
		final long elapsed = System.currentTimeMillis() - _startTime;
		final long remaining = Math.max(0, _durationMs - elapsed);
		final int hours = (int) (remaining / 3600000);
		final int mins = (int) ((remaining % 3600000) / 60000);
		final int secs = (int) ((remaining % 60000) / 1000);
		
		if (hours > 0)
			return String.format("%d:%02d:%02d", hours, mins, secs);
		return String.format("%d:%02d", mins, secs);
	}
	
	private void sendGaugeToAllPlayers()
	{
		if (_durationMs <= 0)
			return;
		
		final SetupGauge gauge = new SetupGauge(GaugeColor.RED, (int) _durationMs);
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.isOnline())
				player.sendPacket(gauge);
		}
	}
	
	public void broadcastScoreboardWithTime()
	{
		if (!_running)
			return;
		
		final int goodScore = getScore(FactionWarConfig.getGoodFactionId());
		final int evilScore = getScore(FactionWarConfig.getEvilFactionId());
		final String timeStr = getRemainingTimeStr();
		
		final StringBuilder sb = new StringBuilder();
		sb.append("[ Faction War ] Good: ").append(goodScore).append(" vs Evil: ").append(evilScore);
		sb.append(" | Win: ").append(FactionWarConfig.getScoreToWin());
		if (!timeStr.isEmpty())
			sb.append(" | Time: ").append(timeStr);
		
		final String msg = sb.toString();
		final ExShowScreenMessage screenMsg = new ExShowScreenMessage(msg, 15000, ExShowScreenMessage.SMPOS.TOP_LEFT, false);
		
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.isOnline())
				player.sendPacket(screenMsg);
		}
	}
	
	public void broadcast(String msg)
	{
		final CreatureSay cs = new CreatureSay(0, SayType.ALL, "FactionWar", msg);
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.isOnline())
				player.sendPacket(cs);
		}
	}
	
	/**
	 * Refreshes faction visuals (name color, title, title color) for all online players.
	 * Uses inline faction lookup to avoid calling onPlayerEnter() which would teleport players.
	 */
	public void broadcastFactionVisuals()
	{
		if (!Config.ENABLE_FACTION_SYSTEM)
			return;
		
		final net.sf.l2j.gameserver.data.xml.FactionData factionData = net.sf.l2j.gameserver.data.xml.FactionData.getInstance();
		
		for (Player player : World.getInstance().getPlayers())
		{
			if (player == null || !player.isOnline())
				continue;
			
			final int factionId = player.getFactionId();
			if (factionId <= 0)
				continue;
			
			final net.sf.l2j.gameserver.model.Faction faction = factionData.getFaction(factionId);
			if (faction == null)
				continue;
			
			player.getAppearance().setNameColor(faction.getNameColor());
			player.getAppearance().setTitleColor(faction.getTitleColor());
			player.setTitle(faction.getName());
			player.broadcastUserInfo();
		}
		LOGGER.info("Broadcasted faction visuals to all online players.");
	}
	
	private void cancelTask(ScheduledFuture<?> task)
	{
		if (task != null && !task.isDone())
			task.cancel(false);
	}
	
	/**
	 * Per-player statistics for the current Faction War.
	 */
	public static class FactionWarStats
	{
		public int playerId;
		public String playerName;
		public int factionId;
		public int kills;
		public int deaths;
		public int points;
	}
}
