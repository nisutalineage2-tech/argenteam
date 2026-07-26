package net.sf.l2j.gameserver.factionwar;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.spawn.Spawn;
import net.sf.l2j.gameserver.model.actor.Npc;

public class FactionWarCheckpoint
{
	private static final CLogger LOGGER = new CLogger(FactionWarCheckpoint.class.getName());
	
	private final List<Spawn> _spawns = new ArrayList<>();
	private final List<Npc> _npcs = new ArrayList<>();
	private final List<Location> _locations = new ArrayList<>();
	
	public void spawn(int mapIndex)
	{
		despawn();
		
		if (!FactionWarConfig.isEnabled())
			return;
		
		final int count = FactionWarConfig.getCheckpointCount();
		final int radius = FactionWarConfig.getCheckpointRadius();
		final int npcId = FactionWarConfig.getCheckpointNpcId();
		
		final List<FactionWarConfig.WarMap> maps = FactionWarConfig.getMaps();
		if (mapIndex < 0 || mapIndex >= maps.size())
			return;
		
		final FactionWarConfig.WarMap map = maps.get(mapIndex);
		final int centerX = map.getX();
		final int centerY = map.getY();
		final int centerZ = map.getZ();
		
		for (int i = 0; i < count; i++)
		{
			final double angle = (2 * Math.PI * i) / count;
			final int x = centerX + (int) (radius * Math.cos(angle));
			final int y = centerY + (int) (radius * Math.sin(angle));
			final int z = centerZ;
			
			final Location loc = new Location(x, y, z);
			_locations.add(loc);
			
			try
			{
				final Spawn spawn = new Spawn(npcId, true);
				spawn.setLoc(x, y, z, 0);
				final Npc npc = spawn.doSpawn(false);
				if (npc != null)
				{
					npc.setIsImmobilized(true);
					_spawns.add(spawn);
					_npcs.add(npc);
				}
			}
			catch (Exception e)
			{
				LOGGER.error("Failed to spawn checkpoint {} at ({}, {}, {}).", e, i, x, y, z);
			}
		}
		
		LOGGER.info("Spawned {} checkpoints for map index {}.", _npcs.size(), mapIndex);
	}
	
	public void despawn()
	{
		for (Npc npc : _npcs)
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
	}
	
	public List<Location> getLocations()
	{
		return _locations;
	}
	
	public int size()
	{
		return _locations.size();
	}
}
