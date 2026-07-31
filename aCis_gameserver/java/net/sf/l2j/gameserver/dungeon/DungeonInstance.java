package net.sf.l2j.gameserver.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.data.manager.SpawnManager;
import net.sf.l2j.gameserver.data.xml.NpcData;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * A single running dungeon instance.
 * Manages stage progression, mob spawning, timer, and rewards.
 */
public class DungeonInstance
{
	private static final CLogger LOGGER = new CLogger(DungeonInstance.class.getName());
	
	private final int _id;
	private final DungeonTemplate _template;
	private final List<Player> _players;
	private final CopyOnWriteArrayList<Npc> _mobs;
	private DungeonStage _currentStage;
	private long _stageBeginTime;
	private ScheduledFuture<?> _stageTimer;
	private ScheduledFuture<?> _timerBroadcastTask;
	private ScheduledFuture<?> _nextTask;
	private boolean _cancelled;
	
	public DungeonInstance(int id, DungeonTemplate template, List<Player> players)
	{
		_id = id;
		_template = template;
		_players = new CopyOnWriteArrayList<>(players);
		_mobs = new CopyOnWriteArrayList<>();
		
		for (Player p : _players)
			DungeonEngine.getInstance().setPlayerDungeon(p, this);
		
		broadcastScreenMessage("You will be teleported in 10 seconds!", 3);
		_nextTask = ThreadPool.schedule(this::beginFirstStage, 10000);
	}
	
	public int getId() { return _id; }
	public DungeonTemplate getTemplate() { return _template; }
	public List<Player> getPlayers() { return _players; }
	
	public boolean containsPlayer(int objectId)
	{
		for (Player p : _players)
		{
			if (p.getObjectId() == objectId)
				return true;
		}
		return false;
	}
	
	// --- Stage lifecycle ------------------------------------------
	
	private void beginFirstStage()
	{
		getNextStage();
		if (_currentStage == null)
		{
			cancelDungeon("No stages defined.");
			return;
		}
		
		teleportToStage();
		broadcastScreenMessage("Stage " + _currentStage.getOrder() + " begins in 10 seconds!", 5);
		_nextTask = ThreadPool.schedule(this::beginStage, 10000);
	}
	
	private void beginStage()
	{
		if (_cancelled)
			return;
		
		for (Entry<Integer, List<Location>> entry : _currentStage.getMobs().entrySet())
		{
			final int npcId = entry.getKey();
			final List<Location> locs = entry.getValue();
			spawnAll(npcId, locs);
		}
		
		_stageBeginTime = System.currentTimeMillis();
		_timerBroadcastTask = ThreadPool.scheduleAtFixedRate(this::broadcastTimer, 5000, 1000);
		
		final long timeout = _currentStage.getMinutes() * 60000L;
		_stageTimer = ThreadPool.schedule(() -> cancelDungeon("Time ran out on stage " + _currentStage.getOrder() + "!"), timeout);
		
		broadcastScreenMessage("Stage " + _currentStage.getOrder() + " started! Time: " + _currentStage.getMinutes() + " minutes.", 5);
		_nextTask = null;
	}
	
	public synchronized void onMobKill(Npc npc)
	{
		if (_cancelled || !_mobs.remove(npc))
			return;
		
		if (_mobs.isEmpty())
		{
			cancelTasks();
			
			for (Player p : _players)
			{
				if (p.isDead())
					p.doRevive();
			}
			
			getNextStage();
			if (_currentStage == null)
			{
				rewardPlayers();
				broadcastScreenMessage("Dungeon complete! Congratulations!", 5);
				teleportOut();
				DungeonEngine.getInstance().removeDungeon(this);
			}
			else
			{
				broadcastScreenMessage("Stage " + (_currentStage.getOrder() - 1) + " cleared! Next stage in 10 seconds.", 5);
				teleportToStage();
				_nextTask = ThreadPool.schedule(this::beginStage, 10000);
			}
		}
	}
	
	public void onPlayerDeath(Player player)
	{
		if (_cancelled || !_players.contains(player))
			return;
		
		if (_players.size() == 1)
			ThreadPool.schedule(() -> cancelDungeon("All party members have fallen!"), 5000);
		else
			player.sendMessage("You will be resurrected if your team completes this stage.");
	}
	
	// --- Reward ---------------------------------------------------
	
	private void rewardPlayers()
	{
		for (Player player : _players)
		{
			if (!player.isOnline())
				continue;
			
			DungeonEngine.getInstance().setLastEntry(player, _template.getId());
			
			for (Entry<Integer, Integer> item : _template.getRewards().entrySet())
				player.addItem(item.getKey(), item.getValue(), true);
		}
		
		final String rewardHtm = _template.getRewardHtm();
		if (rewardHtm != null && !rewardHtm.isEmpty() && !rewardHtm.equals("NULL"))
		{
			final NpcHtmlMessage htm = new NpcHtmlMessage(0);
			htm.setFile(rewardHtm);
			for (Player player : _players)
			{
				if (player.isOnline())
					player.sendPacket(htm);
			}
		}
	}
	
	// --- Helper methods -------------------------------------------
	
	private void teleportToStage()
	{
		if (_currentStage == null || !_currentStage.teleport())
			return;
		
		final Location loc = _currentStage.getLocation();
		for (Player player : _players)
		{
			if (player.isOnline())
				player.teleportTo(loc.getX(), loc.getY(), loc.getZ(), 25);
		}
	}
	
	private void teleportOut()
	{
		for (Player player : _players)
		{
			if (!player.isOnline())
				continue;
			
			DungeonEngine.getInstance().setPlayerDungeon(player, null);
			DungeonEngine.getInstance().getParticipants().remove(player.getObjectId());
			player.teleportTo(DungeonEngine.getInstance().getSpawnX(), DungeonEngine.getInstance().getSpawnY(), DungeonEngine.getInstance().getSpawnZ(), 25);
		}
	}
	
	private void cancelDungeon(String reason)
	{
		if (_cancelled)
			return;
		_cancelled = true;
		
		cancelTasks();
		
		for (Player player : _players)
		{
			if (!player.isOnline())
				continue;
			
			if (player.isDead())
				player.doRevive();
		}
		
		broadcastScreenMessage(reason + " Teleporting back in 10 seconds.", 5);
		
		for (Npc mob : _mobs)
		{
			if (mob != null && mob.getSpawn() != null)
			{
				final var sp = mob.getSpawn();
				if (sp instanceof Spawn spawn)
					SpawnManager.getInstance().deleteSpawn(spawn);
			}
			if (mob != null)
				mob.deleteMe();
		}
		_mobs.clear();
		
		ThreadPool.schedule(this::teleportOut, 10000);
		DungeonEngine.getInstance().removeDungeon(this);
	}
	
	private void getNextStage()
	{
		_currentStage = (_currentStage == null)
			? _template.getStages().get(1)
			: _template.getStages().get(_currentStage.getOrder() + 1);
	}
	
	private void spawnAll(int npcId, List<Location> locations)
	{
		final var template = NpcData.getInstance().getTemplate(npcId);
		if (template == null)
		{
			LOGGER.warn("Dungeon: NPC template {} not found for mob spawn.", npcId);
			return;
		}
		
		for (Location loc : locations)
		{
			try
			{
				final Spawn spawn = new Spawn(template);
				spawn.setLoc(loc.getX(), loc.getY(), loc.getZ(), 0);
				spawn.setRespawnDelay(0);
				spawn.doSpawn(false);
				
				final Npc npc = spawn.getNpc();
				if (npc != null)
				{
					npc.setTitle("[Dungeon]");
					_mobs.add(npc);
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Failed to spawn dungeon mob {} at {},{}", npcId, loc.getX(), loc.getY(), e);
			}
		}
	}
	
	private void broadcastTimer()
	{
		if (_cancelled || _currentStage == null)
			return;
		
		final long elapsed = System.currentTimeMillis() - _stageBeginTime;
		final long remaining = Math.max(0, (_currentStage.getMinutes() * 60000L) - elapsed);
		final int mins = (int) (remaining / 60000);
		final int secs = (int) ((remaining % 60000) / 1000);
		
		final ExShowScreenMessage packet = new ExShowScreenMessage(
			String.format("%02d:%02d", mins, secs),
			1010, ExShowScreenMessage.SMPOS.BOTTOM_RIGHT, false);
		
		for (Player player : _players)
		{
			if (player.isOnline())
				player.sendPacket(packet);
		}
	}
	
	private void broadcastScreenMessage(String msg, int seconds)
	{
		final ExShowScreenMessage packet = new ExShowScreenMessage(msg, seconds * 1000, ExShowScreenMessage.SMPOS.TOP_CENTER, false);
		for (Player player : _players)
		{
			if (player.isOnline())
				player.sendPacket(packet);
		}
	}
	
	private void cancelTasks()
	{
		if (_stageTimer != null && !_stageTimer.isDone())
			_stageTimer.cancel(false);
		if (_timerBroadcastTask != null && !_timerBroadcastTask.isDone())
			_timerBroadcastTask.cancel(false);
		if (_nextTask != null && !_nextTask.isDone())
			_nextTask.cancel(false);
	}
	
	public void broadcastEvent(String msg)
	{
		final var cs = new net.sf.l2j.gameserver.network.serverpackets.CreatureSay(0, SayType.ALL, "Dungeon", msg);
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.isOnline())
				player.sendPacket(cs);
		}
	}
}
