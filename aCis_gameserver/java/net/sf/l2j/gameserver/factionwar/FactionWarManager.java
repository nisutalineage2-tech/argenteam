package net.sf.l2j.gameserver.factionwar;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.gameserver.enums.GaugeColor;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.SetupGauge;
import net.sf.l2j.gameserver.model.World;

public class FactionWarManager
{
	private static final CLogger LOGGER = new CLogger(FactionWarManager.class.getName());
	
	private boolean _running;
	private int _currentMapIndex;
	private final Map<Integer, Integer> _scores = new HashMap<>();
	
	private Spawn _flagSpawn;
	private Npc _flagNpc;
	private Spawn _goodGuardSpawn;
	private Npc _goodGuardNpc;
	private Spawn _evilGuardSpawn;
	private Npc _evilGuardNpc;
	private Spawn _registrarSpawn;
	private Npc _registrarNpc;
	
	private final FactionWarCheckpoint _checkpoints = new FactionWarCheckpoint();
	
	private ScheduledFuture<?> _mapRotationTask;
	private ScheduledFuture<?> _flagRespawnTask;
	private ScheduledFuture<?> _guardRespawnTask;
	private ScheduledFuture<?> _eventEndTask;
	private ScheduledFuture<?> _scoreboardTask;
	private long _startTime;
	private long _durationMs;
	
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
	
	public void start(Player player)
	{
		start(FactionWarConfig.getScoreToWin(), 0);
	}
	
	public void start(int scoreToWin, int durationMinutes)
	{
		if (_running)
		{
			LOGGER.warn("Faction War already running.");
			return;
		}
		
		FactionWarConfig.load();
		
		_running = true;
		_scores.clear();
		_scores.put(FactionWarConfig.getGoodFactionId(), 0);
		_scores.put(FactionWarConfig.getEvilFactionId(), 0);
		_currentMapIndex = 0;
		
		// Record start time for timer display
		_startTime = System.currentTimeMillis();
		_durationMs = durationMinutes * 60000L;
		
		spawnFlag();
		spawnGuards();
		spawnRegistrar();
		_checkpoints.spawn(_currentMapIndex);
		
		if (FactionWarConfig.getMapRotationMinutes() > 0 && FactionWarConfig.getMaps().size() > 1)
		{
			_mapRotationTask = ThreadPool.scheduleAtFixedRate(this::rotateMap, FactionWarConfig.getMapRotationMinutes() * 60000L, FactionWarConfig.getMapRotationMinutes() * 60000L);
		}
		
		if (durationMinutes > 0)
		{
			_eventEndTask = ThreadPool.schedule(() -> stop(), _durationMs);
		}
		
		// Send gauge bar to all faction players and start periodic scoreboard
		sendGaugeToAllPlayers();
		_scoreboardTask = ThreadPool.scheduleAtFixedRate(this::broadcastScoreboardWithTime, 15000, 15000);
		
		final int teleported = net.sf.l2j.gameserver.phantom.PhantomEngine.teleportPhantomsToWar();
		
		if (FactionWarConfig.isAnnounceStart())
			broadcast("[Faction War] La guerra ha comenzado! Score to win: " + scoreToWin + (durationMinutes > 0 ? " | Duration: " + durationMinutes + "min" : ""));
		
		LOGGER.info("Faction War started. Score to win: {}. Teleported {} phantoms to war.", scoreToWin, teleported);
	}
	
	public void stop()
	{
		if (!_running)
			return;
		
		_running = false;
		
		cancelTask(_mapRotationTask);
		cancelTask(_flagRespawnTask);
		cancelTask(_guardRespawnTask);
		cancelTask(_eventEndTask);
		cancelTask(_scoreboardTask);
		
		despawnFlag();
		despawnGuards();
		despawnRegistrar();
		_checkpoints.despawn();
		
		final int returned = net.sf.l2j.gameserver.phantom.PhantomEngine.returnPhantomsFromWar();
		
		if (FactionWarConfig.isAnnounceEnd())
		{
			final int goodScore = getScore(FactionWarConfig.getGoodFactionId());
			final int evilScore = getScore(FactionWarConfig.getEvilFactionId());
			String winner;
			if (goodScore > evilScore)
				winner = "GOOD WINS!";
			else if (evilScore > goodScore)
				winner = "EVIL WINS!";
			else
				winner = "EMPATE!";
			
			broadcast("[Faction War] La guerra ha terminado! " + winner + " [" + goodScore + " - " + evilScore + "]");
		}
		
		LOGGER.info("Faction War stopped. Returned {} phantoms to faction bases.", returned);
	}
	
	public void onFlagKilled(int killerFactionId)
	{
		if (!_running || !FactionWarConfig.isEnabled())
			return;
		
		final int points = FactionWarConfig.getPointsPerFlagKill();
		_scores.merge(killerFactionId, points, Integer::sum);
		
		if (FactionWarConfig.isAnnounceFlagKill())
			broadcast("[Faction War] Faction " + killerFactionId + " destruyo la bandera! +" + points + " pts");
		
		if (FactionWarConfig.isAnnounceScore())
			broadcast("[Faction War] " + getScoreboard());
		
		checkWinner();
		
		if (_running)
			scheduleFlagRespawn();
	}
	
	public void onPvpKill(int killerFactionId, int victimFactionId)
	{
		if (!_running || !FactionWarConfig.isEnabled() || killerFactionId == victimFactionId)
			return;
		
		final int points = FactionWarConfig.getPointsPerPvpKill();
		if (points <= 0)
			return;
		
		_scores.merge(killerFactionId, points, Integer::sum);
		
		if (FactionWarConfig.isAnnouncePvpKill())
			broadcast("[Faction War] PvP kill! Faction " + killerFactionId + " +" + points + " pts");
		
		checkWinner();
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
		_flagRespawnTask = ThreadPool.schedule(() ->
		{
			if (_running)
				spawnFlag();
		}, FactionWarConfig.getFlagRespawnDelay());
	}
	
	private void spawnGuards()
	{
		try
		{
			final Location goodLoc = FactionWarConfig.getGoodGuardLoc();
			_goodGuardSpawn = new Spawn(FactionWarConfig.getGuardNpcId(), true);
			_goodGuardSpawn.setLoc(goodLoc.getX(), goodLoc.getY(), goodLoc.getZ(), 0);
			_goodGuardNpc = _goodGuardSpawn.doSpawn(false);
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to spawn good guard.", e);
		}
		
		try
		{
			final Location evilLoc = FactionWarConfig.getEvilGuardLoc();
			_evilGuardSpawn = new Spawn(FactionWarConfig.getGuardNpcId(), true);
			_evilGuardSpawn.setLoc(evilLoc.getX(), evilLoc.getY(), evilLoc.getZ(), 0);
			_evilGuardNpc = _evilGuardSpawn.doSpawn(false);
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to spawn evil guard.", e);
		}
	}
	
	private void despawnGuards()
	{
		despawnNpc(_goodGuardNpc, _goodGuardSpawn);
		_goodGuardNpc = null;
		_goodGuardSpawn = null;
		despawnNpc(_evilGuardNpc, _evilGuardSpawn);
		_evilGuardNpc = null;
		_evilGuardSpawn = null;
	}
	
	private void despawnNpc(Npc npc, Spawn spawn)
	{
		if (npc != null)
			npc.deleteMe();
		if (spawn != null)
			spawn.doDelete();
	}
	
	private void spawnRegistrar()
	{
		if (FactionWarConfig.getMaps().isEmpty())
			return;
		
		try
		{
			final FactionWarConfig.WarMap map = FactionWarConfig.getMaps().get(_currentMapIndex);
			_registrarSpawn = new Spawn(FactionWarConfig.getWarRegistrarNpcId(), true);
			_registrarSpawn.setLoc(map.getX(), map.getY() + 200, map.getZ(), 0);
			_registrarNpc = _registrarSpawn.doSpawn(false);
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to spawn War Registrar.", e);
		}
	}
	
	private void despawnRegistrar()
	{
		despawnNpc(_registrarNpc, _registrarSpawn);
		_registrarNpc = null;
		_registrarSpawn = null;
	}
	
	public void onGuardDied()
	{
		if (!_running)
			return;
		
		scheduleGuardRespawn();
	}
	
	private void scheduleGuardRespawn()
	{
		_guardRespawnTask = ThreadPool.schedule(() ->
		{
			if (_running)
				spawnGuards();
		}, FactionWarConfig.getGuardRespawnDelay());
	}
	
	private void rotateMap()
	{
		if (!_running || FactionWarConfig.getMaps().size() <= 1)
			return;
		
		despawnFlag();
		despawnGuards();
		despawnRegistrar();
		_checkpoints.despawn();
		
		_currentMapIndex = (_currentMapIndex + 1) % FactionWarConfig.getMaps().size();
		
		spawnFlag();
		spawnGuards();
		spawnRegistrar();
		_checkpoints.spawn(_currentMapIndex);
		
		if (FactionWarConfig.isAnnounceMapSwitch())
			broadcast("[Faction War] El mapa ha cambiado a: " + FactionWarConfig.getMaps().get(_currentMapIndex).getName());
	}
	
	public int getCurrentMapIndex()
	{
		return _currentMapIndex;
	}
	
	public Location getFactionSpawn(int factionId)
	{
		if (factionId == FactionWarConfig.getGoodFactionId())
			return FactionWarConfig.getGoodSpawnLoc();
		if (factionId == FactionWarConfig.getEvilFactionId())
			return FactionWarConfig.getEvilSpawnLoc();
		return null;
	}
	
	/**
	 * Returns remaining time string, or empty if no duration set.
	 */
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
	
	/**
	 * Sends a SetupGauge bar to all online players showing the war duration.
	 */
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
	
	/**
	 * Periodic scoreboard broadcast with score + remaining time, sent via ExShowScreenMessage.
	 */
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
	
	private void cancelTask(ScheduledFuture<?> task)
	{
		if (task != null && !task.isDone())
			task.cancel(false);
	}
}
