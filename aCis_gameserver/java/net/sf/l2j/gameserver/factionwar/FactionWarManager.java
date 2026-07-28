package net.sf.l2j.gameserver.factionwar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

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
	private boolean _startedOnce;
	private int _currentMapIndex;
	private final Map<Integer, Integer> _scores = new HashMap<>();
	
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
	private long _startTime;
	private long _durationMs;
	
	private boolean _votingActive;
	private final Map<Integer, Integer> _mapVotes = new HashMap<>();
	private final java.util.Set<Integer> _votedPlayers = new java.util.HashSet<>();
	private java.util.List<FactionWarConfig.WarMap> _currentVoteMaps;
	
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
		_startedOnce = true;
		_scores.clear();
		_scores.put(FactionWarConfig.getGoodFactionId(), 0);
		_scores.put(FactionWarConfig.getEvilFactionId(), 0);
		_currentMapIndex = 0;
		
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
		
		teleportFactionPlayersToNeutral();
		
		final int teleported = net.sf.l2j.gameserver.phantom.PhantomEngine.teleportPhantomsToWar();
		
		if (FactionWarConfig.isAnnounceStart())
		{
			final FactionWarConfig.WarMap firstMap = FactionWarConfig.getMaps().get(_currentMapIndex);
			broadcast("[Faction War] La guerra ha comenzado! Habla con el Registrador para unirte. Mapa: " + firstMap.getName() + " | Score: " + scoreToWin + (durationMinutes > 0 ? " | " + durationMinutes + "min" : ""));
		}
		
		LOGGER.info("Faction War started. Score: {}. Teleported {} phantoms.", scoreToWin, teleported);
	}
	
	public void stop()
	{
		if (!_running)
			return;
		
		_running = false;
		
		cancelTask(_mapRotationTask);
		cancelTask(_mapVoteTask);
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
		
		teleportFactionPlayersToNeutral();
		
		LOGGER.info("Faction War stopped. Returned {} phantoms.", returned);
	}
	
	public void onFlagKilled(int killerFactionId)
	{
		if (!_running || !FactionWarConfig.isEnabled())
			return;
		
		final int points = FactionWarConfig.getPointsPerFlagKill();
		_scores.merge(killerFactionId, points, Integer::sum);
		
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
			player.teleportTo(factionLoc, 50);
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
		// Registrar ALWAYS spawns in the neutral zone (Aden), not on the war map
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
		_guardRespawnTask = ThreadPool.schedule(() ->
		{
			if (_running)
				spawnGuards();
		}, FactionWarConfig.getGuardRespawnDelay());
	}
	
	private void startMapVote()
	{
		if (!_running || FactionWarConfig.getMaps().size() <= 1)
			return;
		
		_votingActive = true;
		_mapVotes.clear();
		_votedPlayers.clear();
		_currentVoteMaps = FactionWarConfig.getVoteMaps();
		
		for (FactionWarConfig.WarMap map : _currentVoteMaps)
			_mapVotes.put(_currentVoteMaps.indexOf(map), 0);
		
		final int voteSeconds = FactionWarConfig.getMapVoteSeconds();
		
		broadcast("[Faction War] ¡Vota por el próximo mapa! Tienes " + voteSeconds + " segundos.");
		
		sendVotePopup();
		
		_mapVoteTask = ThreadPool.schedule(this::applyMapVote, voteSeconds * 1000L);
	}
	
	private void sendVotePopup()
	{
		if (_currentVoteMaps == null || _registrarNpc == null)
			return;
		
		final int npcId = _registrarNpc.getObjectId();
		
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
		
		for (Player player : World.getInstance().getPlayers())
		{
			if (player == null || !player.isOnline())
				continue;
			if (player.getFactionId() == FactionWarConfig.getGoodFactionId() || player.getFactionId() == FactionWarConfig.getEvilFactionId())
			{
				final net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage html = new net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage(npcId);
				html.setFile("data/html/script/factionwar/WarRegistrar/war_registrar_map_vote.htm");
				html.replace("%MAPS%", mapsHtml.toString());
				html.replace("%SECONDS%", String.valueOf(FactionWarConfig.getMapVoteSeconds()));
				player.sendPacket(html);
				
				player.sendPacket(new ExShowScreenMessage("Vota por el siguiente mapa!", 10000, ExShowScreenMessage.SMPOS.TOP_CENTER, false));
			}
		}
	}
	
	public void onPlayerVote(Player player, int mapIndex)
	{
		if (!_votingActive || _currentVoteMaps == null)
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
		
		if (bestVotes == 0)
		{
			bestIndex = Rnd.get(_currentVoteMaps.size());
			broadcast("[Faction War] ¡Nadie votó! Mapa aleatorio seleccionado.");
		}
		
		final FactionWarConfig.WarMap chosen = _currentVoteMaps.get(bestIndex);
		
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
		
		despawnFlag();
		despawnGuards();
		despawnRegistrar();
		_checkpoints.despawn();
		
		_currentMapIndex = newIndex;
		
		spawnFlag();
		spawnGuards();
		spawnRegistrar();
		_checkpoints.spawn(_currentMapIndex);
		
		final FactionWarConfig.WarMap map = FactionWarConfig.getMaps().get(_currentMapIndex);
		broadcast("[Faction War] ¡Mapa votado: " + map.getName() + "! (" + bestVotes + " votos)");
		
		_mapVotes.clear();
		_votedPlayers.clear();
		_currentVoteMaps = null;
		
		if (_running && FactionWarConfig.getMapRotationMinutes() > 0 && FactionWarConfig.getMaps().size() > 1)
		{
			_mapRotationTask = ThreadPool.schedule(this::startMapVote, FactionWarConfig.getMapRotationMinutes() * 60000L);
		}
	}
	
	private void rotateMap()
	{
		if (!_running || FactionWarConfig.getMaps().size() <= 1)
			return;
		
		despawnFlag();
		despawnGuards();
		despawnRegistrar();
		_checkpoints.despawn();
		
		int newIndex;
		do
		{
			newIndex = Rnd.get(FactionWarConfig.getMaps().size());
		}
		while (newIndex == _currentMapIndex && FactionWarConfig.getMaps().size() > 1);
		
		_currentMapIndex = newIndex;
		
		spawnFlag();
		spawnGuards();
		spawnRegistrar();
		_checkpoints.spawn(_currentMapIndex);
		
		final FactionWarConfig.WarMap map = FactionWarConfig.getMaps().get(_currentMapIndex);
		if (FactionWarConfig.isAnnounceMapSwitch())
			broadcast("[Faction War] Mapa: " + map.getName());
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
	
	private void cancelTask(ScheduledFuture<?> task)
	{
		if (task != null && !task.isDone())
			task.cancel(false);
	}
}
