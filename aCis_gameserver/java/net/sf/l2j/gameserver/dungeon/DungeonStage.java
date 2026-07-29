package net.sf.l2j.gameserver.dungeon;

import java.util.List;
import java.util.Map;

import net.sf.l2j.gameserver.model.location.Location;

/**
 * A single stage within a dungeon template.
 * Defines spawn locations for mobs, time limit, and teleport destination.
 */
public class DungeonStage
{
	private final int _order;
	private final Location _location;
	private final boolean _teleport;
	private final int _minutes;
	private final Map<Integer, List<Location>> _mobs;
	
	public DungeonStage(int order, Location location, boolean teleport, int minutes, Map<Integer, List<Location>> mobs)
	{
		_order = order;
		_location = location;
		_teleport = teleport;
		_minutes = minutes;
		_mobs = mobs;
	}
	
	public int getOrder() { return _order; }
	public Location getLocation() { return _location; }
	public boolean teleport() { return _teleport; }
	public int getMinutes() { return _minutes; }
	public Map<Integer, List<Location>> getMobs() { return _mobs; }
	
	/** Total seconds for this stage's time limit. */
	public int getTotalSeconds() { return _minutes * 60; }
}
