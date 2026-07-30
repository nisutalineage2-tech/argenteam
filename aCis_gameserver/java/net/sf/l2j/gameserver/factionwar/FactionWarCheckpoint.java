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

import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.instance.FactionWarCpFlag;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.spawn.Spawn;

/**
 * Manages capturable checkpoints (battleground points) for the Faction War.
 * <p>
 * Each checkpoint:
 * <ul>
 *   <li>Starts neutral (owner = 0).</li>
 *   <li>Can be attacked by any faction player.</li>
 *   <li>When killed, becomes owned by the killer's faction.</li>
 *   <li>Respawns after a short delay as owned by that faction.</li>
 *   <li>Grants periodic score to the owning faction while held.</li>
 *   <li>Enemy faction can attack an owned checkpoint to capture it.</li>
 * </ul>
 */
public class FactionWarCheckpoint
{
	private static final CLogger LOGGER = new CLogger(FactionWarCheckpoint.class.getName());
	
	private final List<Spawn> _spawns = new ArrayList<>();
	private final List<FactionWarCpFlag> _npcs = new ArrayList<>();
	private final List<Location> _locations = new ArrayList<>();
	
	/** Per-checkpoint owner: index -> factionId (0 = neutral). */
	private final ConcurrentHashMap<Integer, Integer> _owners = new ConcurrentHashMap<>();
	
	private ScheduledFuture<?> _scoreTickTask;
	/** Per-CP respawn tasks: index -> ScheduledFuture. Prevents race conditions on simultaneous captures. */
	private final Map<Integer, ScheduledFuture<?>> _respawnTasks = new HashMap<>();
	
	/**
	 * Spawns the checkpoint NPCs for the given map in a circle around the map center.
	 */
	public void spawn(int mapIndex)
	{
		despawn();
		
		if (!FactionWarConfig.isEnabled())
			return;
		
		final int count = FactionWarConfig.getCheckpointCount();
		final int radius = FactionWarConfig.getCheckpointRadius();
		
		final List<FactionWarConfig.WarMap> maps = FactionWarConfig.getMaps();
		if (mapIndex < 0 || mapIndex >= maps.size())
			return;
		
		final FactionWarConfig.WarMap map = maps.get(mapIndex);
		final int centerX = map.getX();
		final int centerY = map.getY();
		final int centerZ = map.getZ();
		
		for (int i = 0; i < count; i++)
		{
			// Avoid spawning exactly on the center flag; offset positions
			final double angle = (2 * Math.PI * i) / count + (Rnd.nextDouble() * 0.6) - 0.3;
			final int x = centerX + (int) (radius * Math.cos(angle));
			final int y = centerY + (int) (radius * Math.sin(angle));
			final int z = centerZ;
			
			final Location loc = new Location(x, y, z);
			_locations.add(loc);
			_owners.put(i, 0); // neutral by default
			
			try
			{
				final Spawn spawn = new Spawn(FactionWarConfig.getCheckpointNpcId(), true);
				spawn.setLoc(x, y, z, 0);
				final Npc npc = spawn.doSpawn(false);
				if (npc instanceof FactionWarCpFlag cpFlag)
				{
					cpFlag.setIsImmobilized(true);
					cpFlag.setInvul(false);
					cpFlag.setOwnerFactionId(0); // neutral
					_spawns.add(spawn);
					_npcs.add(cpFlag);
				}
				else if (npc != null)
				{
					// Fallback: delete non-CpFlag NPCs
					npc.deleteMe();
					spawn.doDelete();
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Failed to spawn checkpoint {} at ({}, {}, {}).", e, i, x, y, z);
			}
		}
		
		// Start periodic score tick for owned checkpoints
		startScoreTick();
		
		LOGGER.info("Spawned {} capturable checkpoints for map index {}.", _npcs.size(), mapIndex);
	}
	
	/**
	 * Despawns all checkpoint NPCs and cancels tasks.
	 */
	public void despawn()
	{
		stopScoreTick();
		
		// Cancel all pending respawn tasks
		for (ScheduledFuture<?> task : _respawnTasks.values())
		{
			if (task != null && !task.isDone())
				task.cancel(false);
		}
		_respawnTasks.clear();
		
		for (FactionWarCpFlag npc : _npcs)
		{
			if (npc != null)
				npc.deleteMe();
		}
		_npcs.clear();
		
		for (Spawn spawn : _spawns)
		{
			if (spawn != null)
				spawn.doDelete();
		}
		_spawns.clear();
		_locations.clear();
		_owners.clear();
	}
	
	/**
	 * Called when a faction player kills a checkpoint NPC.
	 * Changes ownership and respawns the checkpoint as the capturing faction's.
	 *
	 * @param capturingFactionId the faction that captured it
	 * @param cpFlag             the checkpoint NPC that was killed
	 */
	public void onCapture(int capturingFactionId, FactionWarCpFlag cpFlag)
	{
		final int index = _npcs.indexOf(cpFlag);
		if (index < 0 || !_owners.containsKey(index))
			return;
		
		final int previousOwner = _owners.get(index);
		
		// If already owned by this faction, no change
		if (previousOwner == capturingFactionId)
			return;
		
		// Change ownership
		_owners.put(index, capturingFactionId);
		
		// Cancel any previous respawn task for this specific CP
		final ScheduledFuture<?> oldTask = _respawnTasks.get(index);
		if (oldTask != null && !oldTask.isDone())
			oldTask.cancel(false);
		
		// Respawn the checkpoint as owned by the capturing faction after a short delay
		final long respawnDelay = FactionWarConfig.getCheckpointRespawnDelay();
		final ScheduledFuture<?> newTask = ThreadPool.schedule(() ->
		{
			_respawnTasks.remove(index);
			
			if (!FactionWarManager.getInstance().isRunning())
				return;
			
			respawnCheckpoint(index, capturingFactionId);
		}, respawnDelay);
		_respawnTasks.put(index, newTask);
		
		if (FactionWarConfig.isAnnounceFlagKill())
		{
			final String factionName = (capturingFactionId == FactionWarConfig.getGoodFactionId())
				? FactionWarConfig.getGoodFactionName()
				: FactionWarConfig.getEvilFactionName();
			FactionWarManager.getInstance().broadcast("[Faction War] ¡" + factionName + " capturó un checkpoint! +" + FactionWarConfig.getPointsPerFlagKill() + " pts");
		}
	}
	
	/**
	 * Respawns a specific checkpoint as owned by the given faction.
	 */
	private void respawnCheckpoint(int index, int ownerFactionId)
	{
		if (index < 0 || index >= _locations.size())
			return;
		
		if (!FactionWarManager.getInstance().isRunning())
			return;
		
		// Delete old NPC if still alive
		if (index < _npcs.size() && _npcs.get(index) != null && !_npcs.get(index).isDead())
			return; // Already alive, skip
		
		final Location loc = _locations.get(index);
		try
		{
			final Spawn spawn = new Spawn(FactionWarConfig.getCheckpointNpcId(), true);
			spawn.setLoc(loc.getX(), loc.getY(), loc.getZ(), 0);
			final Npc npc = spawn.doSpawn(false);
			if (npc instanceof FactionWarCpFlag cpFlag)
			{
				cpFlag.setIsImmobilized(true);
				cpFlag.setInvul(false);
				cpFlag.setOwnerFactionId(ownerFactionId);
				
				// Replace old entries
				if (index < _spawns.size())
				{
					final Spawn oldSpawn = _spawns.get(index);
					if (oldSpawn != null)
						oldSpawn.doDelete();
					_spawns.set(index, spawn);
				}
				else
				{
					_spawns.add(spawn);
				}
				
				if (index < _npcs.size())
				{
					_npcs.set(index, cpFlag);
				}
				else
				{
					_npcs.add(cpFlag);
				}
			}
			else if (npc != null)
			{
				npc.deleteMe();
				spawn.doDelete();
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to respawn checkpoint {} at ({}, {}, {}).", e, index, loc.getX(), loc.getY(), loc.getZ());
		}
	}
	
	/**
	 * Starts a periodic task that gives score to factions for each checkpoint they own.
	 * Runs every {@code CheckpointScoreInterval} seconds.
	 */
	private void startScoreTick()
	{
		stopScoreTick();
		
		final long interval = FactionWarConfig.getCheckpointScoreInterval() * 1000L;
		if (interval <= 0)
			return;
		
		_scoreTickTask = ThreadPool.scheduleAtFixedRate(() ->
		{
			if (!FactionWarManager.getInstance().isRunning())
				return;
			
			final int pointsPerTick = FactionWarConfig.getCheckpointPointsPerTick();
			if (pointsPerTick <= 0)
				return;
			
			// Count checkpoints owned by each faction
			int goodCpCount = 0;
			int evilCpCount = 0;
			for (Integer owner : _owners.values())
			{
				if (owner == FactionWarConfig.getGoodFactionId())
					goodCpCount++;
				else if (owner == FactionWarConfig.getEvilFactionId())
					evilCpCount++;
			}
			
			final FactionWarManager fwm = FactionWarManager.getInstance();
			
			if (goodCpCount > 0)
			{
				final int goodScore = goodCpCount * pointsPerTick;
				fwm.addScore(FactionWarConfig.getGoodFactionId(), goodScore);
				fwm.broadcast("[Faction War] Checkpoint tick: " + FactionWarConfig.getGoodFactionName() + " +" + goodScore + " pts (" + goodCpCount + " CPs)");
			}
			
			if (evilCpCount > 0)
			{
				final int evilScore = evilCpCount * pointsPerTick;
				fwm.addScore(FactionWarConfig.getEvilFactionId(), evilScore);
				fwm.broadcast("[Faction War] Checkpoint tick: " + FactionWarConfig.getEvilFactionName() + " +" + evilScore + " pts (" + evilCpCount + " CPs)");
			}
			
		}, interval, interval);
	}
	
	private void stopScoreTick()
	{
		if (_scoreTickTask != null)
		{
			_scoreTickTask.cancel(false);
			_scoreTickTask = null;
		}
	}
	
	/**
	 * @return the list of checkpoint locations
	 */
	public List<Location> getLocations()
	{
		return _locations;
	}
	
	/**
	 * @return the list of checkpoint NPCs
	 */
	public List<FactionWarCpFlag> getNpcs()
	{
		return _npcs;
	}
	
	/**
	 * @return the ownership map (index -> factionId)
	 */
	public ConcurrentHashMap<Integer, Integer> getOwners()
	{
		return _owners;
	}
	
	/**
	 * @return the owner faction ID for the checkpoint at the given index, or 0 if neutral
	 */
	public int getOwner(int index)
	{
		return _owners.getOrDefault(index, 0);
	}
	
	/**
	 * @return the number of checkpoints currently alive
	 */
	public int size()
	{
		return _npcs.size();
	}
}
