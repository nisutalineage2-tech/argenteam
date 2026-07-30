package net.sf.l2j.gameserver.model;

import net.sf.l2j.gameserver.model.location.Location;

/**
 * Represents a flag definition loaded from faction_flags.xml.
 * Each flag belongs to a map (by mapId) and can be a base (non-capturable)
 * or a neutral capturable flag.
 */
public class FactionFlag
{
	private final int _mapId;
	private final String _name;
	private final String _type;
	private final int _factionId;
	private final boolean _capturable;
	private final Location _location;
	
	public FactionFlag(int mapId, String name, String type, int factionId, boolean capturable, Location location)
	{
		_mapId = mapId;
		_name = name;
		_type = type;
		_factionId = factionId;
		_capturable = capturable;
		_location = location;
	}
	
	public int getMapId()
	{
		return _mapId;
	}
	
	public String getName()
	{
		return _name;
	}
	
	public String getType()
	{
		return _type;
	}
	
	public int getFactionId()
	{
		return _factionId;
	}
	
	public boolean isCapturable()
	{
		return _capturable;
	}
	
	public Location getLocation()
	{
		return _location;
	}
	
	public int getX()
	{
		return _location.getX();
	}
	
	public int getY()
	{
		return _location.getY();
	}
	
	public int getZ()
	{
		return _location.getZ();
	}
}
