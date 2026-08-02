package net.sf.l2j.gameserver.factionwar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.commons.util.SysUtil;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.enums.GaugeColor;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.model.FactionFlag;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.network.serverpackets.SetupGauge;

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
	
	// XML-defined flag spawns (from faction_flags.xml)
	private final List<Spawn> _xmlFlagSpawns = new ArrayList<>();
	private final List<Npc> _xmlFlagNpcs = new ArrayList<>();
	
	// Anti-farm tracking: killerId -> lastVictimId
	private final Map<Integer, Integer> _lastKillVictim = new ConcurrentHashMap<>();
	
	private ScheduledFuture<?> _mapRotationTask;
	private ScheduledFuture<?> _mapVoteTask;
	private ScheduledFuture<?> _flagRespawnTask;
	private ScheduledFuture<?> _guardRespawnTask;
	private ScheduledFuture<?> _eventEndTask;
	private ScheduledFuture<?> _scoreboardTask;
	private ScheduledFuture<?> _countdownTask;
	private ScheduledFuture<?> _endFreezeTask;
	private volatile int _winningFaction;
	private volatile long _startTime;
	private volatile long _durationMs;
	private volatile int _lastMainFlagKillerFaction;
	
	/** Per-faction last captured flag location: factionId -> Location (main flag or last captured checkpoint). */
	private final Map<Integer, Location> _lastCapturedFlagByFaction = new ConcurrentHashMap<>();
	
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
	
	/**
	 * @param attacker : The attacking {@link Player} (can be null).
	 * @param target : The defending {@link Player} (can be null).
	 * @return True if both players belong to different factions, the Faction War is running and
	 *         neither of them stands inside the neutral zone. Used to allow PvP between opposing
	 *         factions even inside PEACE-flagged battle maps.
	 */
	public static boolean isFactionWarPvp(Player attacker, Player target)
	{
		if (!Config.ENABLE_FACTION_SYSTEM || attacker == null || target == null)
			return false;
		
		final int attackerFaction = attacker.getFactionId();
		final int targetFaction = target.getFactionId();
		if (attackerFaction <= 0 || targetFaction <= 0 || attackerFaction == targetFaction)
			return false;
		
		final FactionWarManager manager = getInstance();
		if (!manager.isRunning() || !FactionWarConfig.isEnabled())
			return false;
		
		return !FactionWarConfig.isInNeutralZone(attacker.getPosition()) && !FactionWarConfig.isInNeutralZone(target.getPosition());
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
		
		broadcast("[Faction War] La guerra de facciones se acerca. Vota por el mapa: \" .votemap \"");
		
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
			broadcast("[Faction War] Nadie voto. Mapa aleatorio: " + chosen.getName());
		}
		else
		{
			chosen = _currentVoteMaps.get(bestIndex);
			broadcast("[Faction War] Mapa elegido: " + chosen.getName() + " (" + bestVotes + " votos). La guerra comienza en 10 segundos...");
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
			
			start(FactionWarConfig.getWarDurationMinutes(), finalMapIndex);
			
		}, 10000);
	}
	
	/**
	 * Start the war (called from admin command or after vote phase).
	 */
	public void start(Player player)
	{
		start(FactionWarConfig.getWarDurationMinutes(), -1);
	}
	
	/**
	 * Start the war with given duration, random map.
	 */
	public void start(int durationMinutes)
	{
		start(durationMinutes, -1);
	}
	
	/**
	 * Start the war with given duration and a specific map index.
	 * @param mapIndex Use -1 for random.
	 */
	public void start(int durationMinutes, int mapIndex)
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
		_lastMainFlagKillerFaction = 0;
		_lastCapturedFlagByFaction.clear();
		
		spawnFlag();
		spawnXmlFlags();
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
		_scoreboardTask = ThreadPool.scheduleAtFixedRate(this::broadcastScoreboardWithTime, 3000, 3000);
		
		final FactionWarConfig.WarMap firstMap = FactionWarConfig.getMaps().get(_currentMapIndex);
		
		if (FactionWarConfig.isAnnounceStart())
		{
			final String durationStr = (durationMinutes > 0) ? " | Duracion: " + durationMinutes + "min" : "";
			broadcast("[Faction War] La guerra ha comenzado. Mapa: " + firstMap.getName() + durationStr);
		}
		
		// Refresh faction visuals for all players (colors, titles)
		broadcastFactionVisuals();
		
		// Auto-teleport all phantoms to war
		final int teleportedPhantoms = net.sf.l2j.gameserver.phantom.PhantomEngine.teleportPhantomsToWar();
		
		// Players must go to the Teleport Manager or the Registrar NPC to enter the war
		broadcast("[Faction War] La guerra ha comenzado. Ve al Teleport Manager o al Registrador de Guerra en la zona neutral para unirte a la batalla. Mapa: " + firstMap.getName());
		
		LOGGER.info("Faction War started. Map: {} (index: {}). Duration: {}min. Teleported {} phantoms.", 
			firstMap.getName(), _currentMapIndex, durationMinutes, teleportedPhantoms);
	}
	
	public void stop()
	{
		if (!_running && !_votingPhaseActive)
			return;
		
		// Remember whether the war actually ran (vs. a voting phase being cancelled).
		// If it never ran, we skip revive/teleport of phantoms and faction players.
		final boolean warWasRunning = _running;
		
		// Cancel any pending end-freeze task (avoid double-freeze)
		cancelTask(_endFreezeTask);
		
		// 1. Determine winner - the faction that last killed the main flag
		// No score tie-breaker: if nobody captured the flag, the war ends in a draw.
		final int goodScore = getScore(FactionWarConfig.getGoodFactionId());
		final int evilScore = getScore(FactionWarConfig.getEvilFactionId());
		_winningFaction = _lastMainFlagKillerFaction;
		
		_running = false;
		_votingPhaseActive = false;
		
		cancelTask(_mapRotationTask);
		cancelTask(_mapVoteTask);
		cancelTask(_flagRespawnTask);
		cancelTask(_guardRespawnTask);
		cancelTask(_eventEndTask);
		cancelTask(_scoreboardTask);
		cancelTask(_countdownTask);
		
		// 2. Freeze all players on the battlefield (only if the war actually ran)
		if (warWasRunning)
			freezeAllPlayers();
		
		// 3. Build and broadcast winner announcement
		final String winnerMsg = buildWinnerMessage(_winningFaction, goodScore, evilScore);
		final int freezeSeconds = FactionWarConfig.getEndFreezeSeconds();
		
		if (warWasRunning && FactionWarConfig.isAnnounceEnd())
		{
			broadcast(winnerMsg);
			final String endMsg = (_winningFaction > 0)
				? "La guerra ha terminado. " + getFactionName(_winningFaction) + " gana manteniendo la bandera. [" + goodScore + " - " + evilScore + "]"
				: "La guerra ha terminado. EMPATE. Nadie capturo la bandera. [" + goodScore + " - " + evilScore + "]";
			broadcastScreenMessage(endMsg + " | Teletransportando en " + freezeSeconds + "s...", freezeSeconds * 1000);
		}
		
		// 4. Schedule delayed teleport + cleanup + unfreeze
		final int winningFaction = _winningFaction;
		_endFreezeTask = ThreadPool.schedule(() ->
		{
			try
			{
				// Give rewards (only if the war actually ran)
				if (warWasRunning && FactionWarConfig.isAnnounceEnd())
				{
					final List<FactionWarStats> top3 = getTopPlayers(3);
					announceTopPlayers(top3);
					giveRewards(top3);
				}
				
				// Despawn all war NPCs
				despawnFlag();
				despawnXmlFlags();
				despawnGuards();
				despawnRegistrar();
				_checkpoints.despawn();
				
				// Return phantoms + teleport faction players to neutral zone
				// (only if the war actually ran - otherwise players never left their spot)
				final int returned;
				if (warWasRunning)
				{
					returned = net.sf.l2j.gameserver.phantom.PhantomEngine.returnPhantomsFromWar();
					teleportFactionPlayersToNeutral();
				}
				else
					returned = 0;
				
				// Unfreeze all players
				unfreezeAllPlayers();
				
				// Notify EventEngine that FW ended (alternance: FW -> event)
				net.sf.l2j.gameserver.event.EventEngine.getInstance().onFactionWarEnded();
				
				LOGGER.info("Faction War stopped. Winner: {}. Score: {}-{}. Returned {} phantoms.", getFactionName(winningFaction), goodScore, evilScore, returned);
			}
			catch (Exception e)
			{
				LOGGER.error("Error during Faction War delayed cleanup.", e);
			}
		}, freezeSeconds * 1000L);
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
				broadcast("[Faction War] " + stats.playerName + " queda #" + (i + 1) + " y recibe " + topRewards[i] + "x " + getItemName(rewardItemId) + ".");
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
			broadcast("[Faction War] La faccion ganadora recibe " + winReward + "x " + getItemName(rewardItemId) + ". (" + count + " miembros premiados)");
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
	
	/**
	 * Adds raw score points to a faction and checks for winner.
	 */
	public void addScore(int factionId, int points)
	{
		if (!_running)
			return;
		
		_scores.merge(factionId, points, Integer::sum);
		checkWinner();
	}
	
	/**
	 * Called when a faction player captures a checkpoint (kills the CpFlag NPC).
	 * Delegates to the checkpoint manager for ownership change.
	 */
	public void onCheckpointCaptured(int capturingFactionId, net.sf.l2j.gameserver.model.actor.instance.FactionWarCpFlag cpFlag)
	{
		if (!_running || !FactionWarConfig.isEnabled())
			return;
		
		_checkpoints.onCapture(capturingFactionId, cpFlag);
		
		// Remember the last captured checkpoint location for the capturing faction.
		if (cpFlag != null)
			_lastCapturedFlagByFaction.put(capturingFactionId, new Location(cpFlag.getX(), cpFlag.getY(), cpFlag.getZ()));
		
		// Also give immediate score for the capture
		final int points = FactionWarConfig.getPointsPerFlagKill();
		if (points > 0)
		{
			_scores.merge(capturingFactionId, points, Integer::sum);
			checkWinner();
		}
		
		// On-screen flash + sound when a checkpoint is captured
		broadcastCaptureFlash("" + getFactionName(capturingFactionId) + " capturo un checkpoint.", new PlaySound("ItemSound2.race_start"));
	}
	
	public void onPvpKill(int killerFactionId, int victimFactionId, int killerId, int victimId)
	{
		if (!_running || !FactionWarConfig.isEnabled() || killerFactionId == victimFactionId)
			return;
		
		final Player killer = World.getInstance().getPlayer(killerId);
		final Player victim = World.getInstance().getPlayer(victimId);
		if (killer == null || victim == null)
			return;
		
		// Anti-PvP Farm Protection
		if (isPvpFarming(killer, victim))
		{
			killer.sendMessage("[Faction War] No obtienes recompensa por farmear PvP.");
			return;
		}
		
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
			killerStats.playerName = killer.getName();
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
			victimStats.playerName = victim.getName();
			victimStats.factionId = victimFactionId;
			_playerStats.put(victimId, victimStats);
		}
		victimStats.deaths++;
		
		// Track last kill for same-player anti-farm
		_lastKillVictim.put(killerId, victimId);
		
		if (FactionWarConfig.isAnnouncePvpKill())
			broadcast("[Faction War] PvP kill. Faction " + killerFactionId + " +" + points + " pts");
		
		// PvP EXP Reward
		if (FactionWarConfig.isEnablePvpExpReward())
		{
			final int level = killer.getStatus().getLevel();
			final int expAmount;
			if (level >= 78)
				expAmount = FactionWarConfig.getPvpExpRewardThird();
			else if (level >= 76)
				expAmount = FactionWarConfig.getPvpExpRewardSecond();
			else
				expAmount = FactionWarConfig.getPvpExpRewardFirst();
			
			if (expAmount > 0)
				killer.addExpAndSp(expAmount, 0);
		}
		
		// PvP Item Reward (with castle multiplier & party sharing)
		if (FactionWarConfig.isEnablePvpItemReward())
		{
			int itemCount = FactionWarConfig.getPvpItemRewardCount();
			if (FactionWarConfig.isEnableCastleRewardMultiplier())
				itemCount += getCastleRewardBonus(killer);
			
			if (itemCount > 0)
			{
				killer.addItem(FactionWarConfig.getPvpItemRewardId(), itemCount, true);
				
				if (FactionWarConfig.isEnablePartyPvpReward())
					givePartyReward(killer);
			}
		}
		
		// Give adena reward to the killer
		final int adenaReward = FactionWarConfig.getPvpAdenaReward();
		if (adenaReward > 0)
			killer.addItem(57, adenaReward, true);
		
		// Random spoil drop to the killer's party
		giveRandomSpoilToParty(killer);
		
		// Enchant system
		handleEnchantSystem(killer);
		
		checkWinner();
	}
	
	/**
	 * Handles the faction enchant system on PvP kill.
	 */
	private void handleEnchantSystem(Player killer)
	{
		final String mode = FactionWarConfig.getEnchantMode();
		if (mode == null || mode.equals("OFF"))
			return;
		
		if (mode.equals("PVPSCROLLS"))
		{
			if (Rnd.get(100) < FactionWarConfig.getEnchantScrollDropChance())
			{
				final int[] scrolls = {729, 730, 960, 959};
				final int scrollId = scrolls[Rnd.get(scrolls.length)];
				killer.addItem(scrollId, 1, true);
				killer.sendMessage("[Faction] Enchant scroll drop.");
			}
		}
		else if (mode.equals("PVPENCHANT"))
		{
			killer.addEnchantCnt(1);
			
			final net.sf.l2j.gameserver.model.item.instance.ItemInstance enchantItem = killer.getCurrentEnchantItem();
			if (enchantItem == null || enchantItem.isHeroItem())
				return;
			
			if (enchantItem.getEnchantLevel() >= FactionWarConfig.getMaxItemEnchant())
				return;
			
			final int crystalType = enchantItem.getItem().getCrystalType().ordinal();
			final boolean isWeapon = enchantItem.isWeapon();
			final boolean isArmor = enchantItem.isArmor();
			if (!isWeapon && !isArmor)
				return;
			
			int requiredKills = 0;
			switch (crystalType)
			{
				case 2: requiredKills = FactionWarConfig.getKillsForEnchantB(); break; // B
				case 3: requiredKills = FactionWarConfig.getKillsForEnchantA(); break; // A
				case 4: requiredKills = FactionWarConfig.getKillsForEnchantS(); break; // S
			}
			if (requiredKills <= 0)
				return;
			
			if (killer.getEnchantCnt() >= requiredKills)
			{
				enchantItem.setEnchantLevel(enchantItem.getEnchantLevel() + 1, killer);
				killer.setEnchantCnt(killer.getEnchantCnt() - requiredKills);
				killer.sendMessage("[Faction] Item enchanted to +" + enchantItem.getEnchantLevel() + ".");
				killer.sendPacket(new net.sf.l2j.gameserver.network.serverpackets.InventoryUpdate(killer));
			}
		}
	}
	
	/**
	 * Checks if this PvP kill is considered farming (no rewards).
	 */
	private boolean isPvpFarming(Player killer, Player victim)
	{
		if (killer.getClient() != null && victim.getClient() != null)
		{
			// Same IP protection
			if (FactionWarConfig.isEnableProtectionIP())
			{
				final String killerIp = killer.getClient().getConnection().getInetAddress().getHostAddress();
				final String victimIp = victim.getClient().getConnection().getInetAddress().getHostAddress();
				if (killerIp != null && killerIp.equals(victimIp))
					return true;
			}
			
			// Same clan protection
			if (FactionWarConfig.isEnableProtectionClan() && killer.getClan() != null && victim.getClan() != null && killer.getClanId() == victim.getClanId())
				return true;
			
			// Same ally protection
			if (FactionWarConfig.isEnableProtectionAlly() && killer.getAllyId() > 0 && killer.getAllyId() == victim.getAllyId())
				return true;
		}
		
		// Armor check (PDef threshold)
		if (FactionWarConfig.isEnableProtectionArmour() && victim.getStatus().getPDef(null) < FactionWarConfig.getProtectionArmourAmount())
			return true;
		
		// Same player farm protection
		if (FactionWarConfig.isEnableProtectionSamePlayer())
		{
			final Integer lastVictim = _lastKillVictim.get(killer.getObjectId());
			if (lastVictim != null && lastVictim == victim.getObjectId())
				return true;
		}
		
		return false;
	}
	
	/**
	 * Calculates the castle reward bonus for a player based on their clan's castle ownership.
	 */
	private int getCastleRewardBonus(Player player)
	{
		if (!FactionWarConfig.isEnableCastleRewardMultiplier() || player.getClan() == null || player.getClan().getCastleId() <= 0)
			return 0;
		
		final int castleId = player.getClan().getCastleId();
		switch (castleId)
		{
			case 1: return FactionWarConfig.getCastleRewardGludio();
			case 2: return FactionWarConfig.getCastleRewardDion();
			case 3: return FactionWarConfig.getCastleRewardAden();
			default: return 0;
		}
	}
	
	/**
	 * Gives party PvP rewards to the killer's party members.
	 */
	private void givePartyReward(Player killer)
	{
		final net.sf.l2j.gameserver.model.group.Party party = killer.getParty();
		if (party == null)
			return;
		
		for (Player member : party.getMembers())
		{
			if (member == null || !member.isOnline() || member == killer)
				continue;
			
			if (FactionWarConfig.isPartyRewardOnlySupportClass() && !isSupportClass(member))
				continue;
			
			if (Rnd.get(100) >= FactionWarConfig.getPartyRewardChance())
				continue;
			
			int rewardCount = FactionWarConfig.getPvpItemRewardCount();
			if (FactionWarConfig.isEnableCastleRewardMultiplier())
				rewardCount += getCastleRewardBonus(member);
			
			if (rewardCount > 0)
				member.addItem(FactionWarConfig.getPvpItemRewardId(), rewardCount, true);
		}
		
		// Extra bonus for killer when in party
		if (FactionWarConfig.getKillerPartyBonus() > 0)
			killer.addItem(FactionWarConfig.getPvpItemRewardId(), FactionWarConfig.getKillerPartyBonus(), true);
	}
	
	/**
	 * Checks if a player is a support class (healer/buffer).
	 */
	private boolean isSupportClass(Player player)
	{
		final int classId = player.getClassId().getId();
		return classId == 15 || classId == 16 || classId == 17 || classId == 29 || classId == 30 || classId == 42 || classId == 43 || classId == 97 || classId == 98 || classId == 105 || classId == 112;
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
		
		// Track which faction last killed the main flag (determines winner at timer end)
		_lastMainFlagKillerFaction = killerFactionId;
		
		// Remember the main flag location as the last captured flag for the capturing faction.
		final FactionWarConfig.WarMap currentMap = FactionWarConfig.getMaps().isEmpty() ? null : FactionWarConfig.getMaps().get(_currentMapIndex);
		if (currentMap != null)
			_lastCapturedFlagByFaction.put(killerFactionId, new Location(currentMap.getX(), currentMap.getY(), currentMap.getZ()));
		
		final int points = FactionWarConfig.getPointsPerFlagKill();
		_scores.merge(killerFactionId, points, Integer::sum);
		
		// Track per-player stats for flag killer
		FactionWarStats stats = _playerStats.get(killerId);
		final Player flagPlayer = World.getInstance().getPlayer(killerId);
		if (stats == null)
		{
			stats = new FactionWarStats();
			stats.playerId = killerId;
			stats.playerName = (flagPlayer != null) ? flagPlayer.getName() : "Unknown";
			stats.factionId = killerFactionId;
			_playerStats.put(killerId, stats);
		}
		stats.points += points;
		
		// Flag capture SP + item rewards
		if (flagPlayer != null && flagPlayer.isOnline() && FactionWarConfig.isEnableFlagSpItemReward())
		{
			final int clanLevel = (flagPlayer.getClan() != null) ? flagPlayer.getClan().getLevel() : 0;
			
			// SP reward tiered by clan level
			final int spReward;
			final java.util.List<int[]> itemReward;
			if (clanLevel >= 7)
			{
				spReward = FactionWarConfig.getFlagSpRewardThird();
				itemReward = FactionWarConfig.getFlagItemReward3();
			}
			else if (clanLevel >= 5)
			{
				spReward = FactionWarConfig.getFlagSpRewardSecond();
				itemReward = FactionWarConfig.getFlagItemReward2();
			}
			else
			{
				spReward = FactionWarConfig.getFlagSpRewardFirst();
				itemReward = FactionWarConfig.getFlagItemReward1();
			}
			
			if (spReward > 0)
				flagPlayer.addExpAndSp(0, spReward);
			
			if (itemReward != null)
			{
				for (int[] item : itemReward)
				{
					if (item.length >= 2 && item[0] > 0 && item[1] > 0)
						flagPlayer.addItem(item[0], item[1], true);
				}
			}
		}
		
		final int loserFactionId = (killerFactionId == FactionWarConfig.getGoodFactionId())
			? FactionWarConfig.getEvilFactionId()
			: FactionWarConfig.getGoodFactionId();
		
		teleportFactionToBase(loserFactionId);
		
		if (FactionWarConfig.isAnnounceFlagKill())
			broadcast("[Faction War] Faction " + killerFactionId + " destruyo la bandera. +" + points + " pts. Faction " + loserFactionId + " regresa a su base.");
		
		if (FactionWarConfig.isAnnounceScore())
			broadcast("[Faction War] " + getScoreboard());
		
		// On-screen flash + victory sound when the main flag is captured
		broadcastCaptureFlash("" + getFactionName(killerFactionId) + " capturo la BANDERA principal. +" + points + " pts", new PlaySound(1, "Siege_Victory"));
		
		checkWinner();
		
		if (_running)
			scheduleFlagRespawn();
	}
	
	/**
	 * Checks if any faction reached the score target (for display purposes only).
	 * The war is TIMED - winner is determined at the end by flag ownership.
	 */
	private void checkWinner()
	{
		// Score-based win is disabled. The war ends only when the timer expires.
		// The winner is the faction that last killed the main flag.
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
			
			// Phantoms are already handled by PhantomEngine.returnPhantomsFromWar() with the
			// phantom-safe teleport pattern (forced onTeleported + revalidateZone). A second raw
			// teleport here would leave them stuck teleporting and make them vanish.
			if (net.sf.l2j.gameserver.phantom.PhantomEngine.isPhantom(player.getObjectId()))
				continue;
			
			if (player.getFactionId() != 0)
				player.teleportTo(neutralLoc, 50);
		}
	}
	
	/**
	 * Teleports all faction players to their respective faction base spawns.
	 */
	private void teleportFactionPlayersToBase()
	{
		for (Player player : World.getInstance().getPlayers())
		{
			if (player == null || !player.isOnline() || player.getFactionId() <= 0)
				continue;
			
			final Location baseLoc = getFactionSpawn(player.getFactionId());
			if (baseLoc != null)
				player.teleportTo(baseLoc, 50);
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
	
	/**
	 * Spawns flags defined in faction_flags.xml for the current map.
	 * These are additional flag NPCs placed at strategic points around the map.
	 */
	private void spawnXmlFlags()
	{
		if (FactionWarConfig.getMaps().isEmpty())
			return;
		
		final String mapName = FactionWarConfig.getMaps().get(_currentMapIndex).getName();
		final java.util.List<FactionFlag> flags = FactionWarConfig.getXmlFlagsForMap(mapName);
		
		if (flags.isEmpty())
		{
			LOGGER.debug("No XML flags defined for map '{}'.", mapName);
			return;
		}
		
		for (FactionFlag flag : flags)
		{
			if (flag.isCapturable() || flag.getFactionId() <= 0)
			{
				// Spawn as capturable war flag NPC (use flag NPC type)
				try
				{
					final Spawn spawn = new Spawn(FactionWarConfig.getFlagNpcId(), true);
					spawn.setLoc(flag.getX(), flag.getY(), flag.getZ(), 0);
					final Npc npc = spawn.doSpawn(false);
					if (npc != null)
					{
						npc.setInvul(false);
						npc.setIsImmobilized(true);
						npc.setTitle(flag.getName());
						_xmlFlagSpawns.add(spawn);
						_xmlFlagNpcs.add(npc);
					}
				}
				catch (Exception e)
				{
					LOGGER.error("Failed to spawn XML flag '{}' at ({}, {}, {}).", e, flag.getName(), flag.getX(), flag.getY(), flag.getZ());
				}
			}
			// Non-capturable base flags (faction bases) could be decorative or used for spawn points
		}
		
		if (!_xmlFlagNpcs.isEmpty())
			LOGGER.info("Spawned {} XML flags for map '{}'.", _xmlFlagNpcs.size(), mapName);
	}
	
	/**
	 * Despawns all XML-defined flags for the current map.
	 */
	private void despawnXmlFlags()
	{
		for (Npc npc : _xmlFlagNpcs)
		{
			if (npc != null)
				npc.deleteMe();
		}
		_xmlFlagNpcs.clear();
		
		for (Spawn spawn : _xmlFlagSpawns)
		{
			if (spawn != null)
				spawn.doDelete();
		}
		_xmlFlagSpawns.clear();
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
		
		spawnGuardGroup(map.getGoodSpawn(), count, radius, _goodGuardSpawns, _goodGuardNpcs, FactionWarConfig.getGoodFactionId());
		spawnGuardGroup(map.getEvilSpawn(), count, radius, _evilGuardSpawns, _evilGuardNpcs, FactionWarConfig.getEvilFactionId());
	}
	
	private void spawnGuardGroup(Location baseLoc, int count, int radius, List<Spawn> spawns, List<Npc> npcs, int factionId)
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
				spawn.getMemo().set("factionId", factionId);
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
	
	/**
	 * Called when a FactionWarGuard dies. Only respawns THAT specific guard
	 * at its spawn position, instead of respawning ALL guards (old behavior).
	 */
	public void onGuardDied(Npc guard)
	{
		if (!_running || guard == null)
			return;
		
		final Location spawnLoc = guard.getSpawnLocation();
		if (spawnLoc == null)
			return;
		
		// Remove from active lists
		_goodGuardNpcs.removeIf(n -> n != null && n.getObjectId() == guard.getObjectId());
		_evilGuardNpcs.removeIf(n -> n != null && n.getObjectId() == guard.getObjectId());
		
		// Find and remove the matching Spawn from the parallel list
		_goodGuardSpawns.removeIf(s -> s != null && s.getNpc() != null && s.getNpc().getObjectId() == guard.getObjectId());
		_evilGuardSpawns.removeIf(s -> s != null && s.getNpc() != null && s.getNpc().getObjectId() == guard.getObjectId());
		
		// Capture the guard faction from its spawn memo (falls back to proximity-based detection).
		int factionId = 0;
		if (guard.getSpawn() != null)
			factionId = guard.getSpawn().getMemo().getInteger("factionId", 0);
		final int guardFactionId = factionId;
		
		// Schedule a single guard respawn at the exact spawn position
		final long delay = FactionWarConfig.getGuardRespawnDelay();
		ThreadPool.schedule(() ->
		{
			if (!_running)
				return;
			
			try
			{
				final Spawn spawn = new Spawn(FactionWarConfig.getGuardNpcId(), true);
				spawn.setLoc(spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), 0);
				spawn.getMemo().set("factionId", guardFactionId);
				final Npc newGuard = spawn.doSpawn(false);
				if (newGuard != null)
				{
					if (guardFactionId == FactionWarConfig.getGoodFactionId())
					{
						_goodGuardSpawns.add(spawn);
						_goodGuardNpcs.add(newGuard);
					}
					else if (guardFactionId == FactionWarConfig.getEvilFactionId())
					{
						_evilGuardSpawns.add(spawn);
						_evilGuardNpcs.add(newGuard);
					}
					else
					{
						// Fallback: determine Good or Evil base by proximity to faction spawns
						final Location goodLoc = getFactionSpawn(FactionWarConfig.getGoodFactionId());
						if (goodLoc != null && spawnLoc.distance3D(goodLoc) < 500)
						{
							_goodGuardSpawns.add(spawn);
							_goodGuardNpcs.add(newGuard);
						}
						else
						{
							_evilGuardSpawns.add(spawn);
							_evilGuardNpcs.add(newGuard);
						}
					}
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Failed to respawn single guard at ({}, {}, {}).", e, spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ());
			}
		}, delay);
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
		
		broadcast("[Faction War] Vota por el proximo mapa. Tienes " + voteSeconds + " segundos. Usa .votemap o habla con el Registrador.");
		
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
			mapsHtml.append("<td width=240><a action=\"bypass -h npc_").append(npcId).append("_fwVote_").append(i).append("\">").append(SysUtil.escapeHtml(map.getName())).append("</a></td>");
			mapsHtml.append("</tr></table>");
			mapsHtml.append("<img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\">");
		}
		
		final NpcHtmlMessage html = new NpcHtmlMessage(npcId);
		html.setFile("data/html/script/factionwar/WarRegistrar/war_registrar_map_vote.htm");
		html.replace("%MAPS%", mapsHtml.toString());
		html.replace("%SECONDS%", String.valueOf(FactionWarConfig.getMapVoteSeconds()));
		player.sendPacket(html);
		
		player.sendPacket(new ExShowScreenMessage("Vota por el mapa de la guerra.", 10000, ExShowScreenMessage.SMPOS.TOP_CENTER, false));
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
		
		player.sendMessage("[Faction War] Voto registrado por " + _currentVoteMaps.get(mapIndex).getName() + ".");
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
			broadcast("[Faction War] Nadie voto. Mapa aleatorio seleccionado: " + chosen.getName());
		}
		else
		{
			chosen = _currentVoteMaps.get(bestIndex);
			broadcast("[Faction War] Mapa votado: " + chosen.getName() + ". (" + bestVotes + " votos)");
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
		despawnXmlFlags();
		despawnGuards();
		_checkpoints.despawn();
		
		_currentMapIndex = newIndex;
		
		spawnFlag();
		spawnXmlFlags();
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
	
	/**
	 * @param factionId : The faction to check.
	 * @return The {@link Location} of the last flag captured by the given faction during the
	 *         current war (main flag or last captured checkpoint), or null if none was captured.
	 */
	public Location getLastCapturedFlag(int factionId)
	{
		return _lastCapturedFlagByFaction.get(factionId);
	}
	
	/**
	 * @param factionId : The faction to check.
	 * @return All flags currently held by the given faction during the running war: the main flag
	 *         (if that faction last killed it) plus every checkpoint owned by it, in no particular
	 *         order. Empty if the war is not running or the faction holds nothing.
	 */
	public List<CapturedFlag> getCapturedFlagsByFaction(int factionId)
	{
		final List<CapturedFlag> result = new ArrayList<>();
		if (!_running || factionId <= 0)
			return result;
		
		// Main flag: held by the faction that last killed it (its location is the map center).
		if (_lastMainFlagKillerFaction == factionId)
		{
			final FactionWarConfig.WarMap map = FactionWarConfig.getMaps().isEmpty() ? null : FactionWarConfig.getMaps().get(_currentMapIndex);
			if (map != null)
				result.add(new CapturedFlag(FactionWarConfig.getGoodFactionId() == factionId ? FactionWarConfig.getGoodFactionName() : FactionWarConfig.getEvilFactionName() + " (principal)", new Location(map.getX(), map.getY(), map.getZ())));
		}
		
		// Checkpoints owned by the faction.
		for (Map.Entry<Integer, Integer> entry : _checkpoints.getOwners().entrySet())
		{
			if (entry.getValue() == factionId)
			{
				final java.util.List<Location> locations = _checkpoints.getLocations();
				final int index = entry.getKey();
				if (index >= 0 && index < locations.size())
					result.add(new CapturedFlag("Checkpoint " + (index + 1), locations.get(index)));
			}
		}
		
		return result;
	}
	
	/** A flag captured by a faction: display name + location. */
	public record CapturedFlag(String name, Location location)
	{
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
		
		// Compact one-line message at the very top of screen, in the requested format:
		// [Faction War] | Good: X vs Evil: Y | Tiempo: <time>
		final StringBuilder sb = new StringBuilder();
		sb.append("[Faction War] | Good: ").append(goodScore).append(" vs Evil: ").append(evilScore);
		if (!timeStr.isEmpty())
			sb.append(" | Tiempo: ").append(timeStr);
		
		final String msg = sb.toString();
		
		// showEffect=true + showFading=true for a smoother look; size=1 small so it doesn't block clicks
		final ExShowScreenMessage screenMsg = new ExShowScreenMessage(1, -1, ExShowScreenMessage.SMPOS.TOP_CENTER, false, 1, 0, 0, true, 4000, true, msg);
		
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.isOnline())
			{
				player.sendPacket(screenMsg);
				
				// Real-time progress bar for the remaining time (only when the war is timed)
				if (_durationMs > 0)
				{
					final long remaining = Math.max(0, _durationMs - (System.currentTimeMillis() - _startTime));
					player.sendPacket(new SetupGauge(GaugeColor.RED, (int) remaining, (int) _durationMs));
				}
			}
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
	
	/**
	 * Freezes all faction players on the battlefield (paralyze + immobilize).
	 */
	private void freezeAllPlayers()
	{
		for (Player player : World.getInstance().getPlayers())
		{
			if (player == null || !player.isOnline() || player.isDead())
				continue;
			if (player.getFactionId() == FactionWarConfig.getGoodFactionId() || player.getFactionId() == FactionWarConfig.getEvilFactionId())
			{
				player.setIsImmobilized(true);
				player.setIsParalyzed(true);
			}
		}
	}
	
	/**
	 * Unfreezes all players (reverses freezeAllPlayers).
	 */
	private void unfreezeAllPlayers()
	{
		for (Player player : World.getInstance().getPlayers())
		{
			if (player == null || !player.isOnline())
				continue;
			player.setIsImmobilized(false);
			player.setIsParalyzed(false);
		}
	}
	
	/**
	 * Builds a winner announcement string.
	 */
	private String buildWinnerMessage(int winningFaction, int goodScore, int evilScore)
	{
		final String goodName = FactionWarConfig.getGoodFactionName();
		final String evilName = FactionWarConfig.getEvilFactionName();
		
		if (winningFaction == FactionWarConfig.getGoodFactionId())
			return "[Faction War] " + goodName + " gana manteniendo la bandera. [" + goodScore + " - " + evilScore + "]";
		else if (winningFaction == FactionWarConfig.getEvilFactionId())
			return "[Faction War] " + evilName + " gana manteniendo la bandera. [" + goodScore + " - " + evilScore + "]";
		else
			return "[Faction War] EMPATE. Nadie capturo la bandera. [" + goodScore + " - " + evilScore + "]";
	}
	
	/**
	 * Returns the faction display name by ID.
	 */
	private String getFactionName(int factionId)
	{
		if (factionId == FactionWarConfig.getGoodFactionId())
			return FactionWarConfig.getGoodFactionName();
		if (factionId == FactionWarConfig.getEvilFactionId())
			return FactionWarConfig.getEvilFactionName();
		return "Neutral";
	}
	
	/**
	 * Broadcasts an on-screen message to all players.
	 */
	private void broadcastScreenMessage(String msg, int displayMs)
	{
		final ExShowScreenMessage screenMsg = new ExShowScreenMessage(1, -1, ExShowScreenMessage.SMPOS.TOP_CENTER, false, 1, 0, 0, false, displayMs, false, msg);
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.isOnline())
				player.sendPacket(screenMsg);
		}
	}
	
	/**
	 * Broadcasts an on-screen flash (glow effect) + sound to all online players.
	 * Used to highlight main flag and checkpoint captures.
	 */
	private void broadcastCaptureFlash(String message, PlaySound sound)
	{
		final ExShowScreenMessage flash = new ExShowScreenMessage(1, -1, ExShowScreenMessage.SMPOS.MIDDLE_CENTER, false, 0, 0, 0, true, 5000, true, message);
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.isOnline())
			{
				player.sendPacket(flash);
				player.sendPacket(sound);
			}
		}
	}
	
	/**
	 * Announces the top 3 players in chat.
	 */
	private void announceTopPlayers(List<FactionWarStats> top3)
	{
		if (top3.isEmpty())
			return;
		
		final StringBuilder topMsg = new StringBuilder("[Faction War] Top 3:");
		for (int i = 0; i < top3.size(); i++)
		{
			final FactionWarStats s = top3.get(i);
			topMsg.append(" #").append(i + 1).append(" ").append(s.playerName).append(" (").append(s.points).append("pts)");
		}
		broadcast(topMsg.toString());
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
