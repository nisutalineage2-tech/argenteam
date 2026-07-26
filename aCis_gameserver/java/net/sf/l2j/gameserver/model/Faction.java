package net.sf.l2j.gameserver.model;

import net.sf.l2j.commons.data.StatSet;

import net.sf.l2j.gameserver.model.location.Location;

public class Faction
{
	private final int _id;
	private final String _name;
	private final int _nameColor;
	private final int _titleColor;
	private final Location _homeLocation;
	
	public Faction(StatSet set)
	{
		_id = set.getInteger("id");
		_name = set.getString("name");
		_nameColor = Integer.decode("0x" + set.getString("nameColor", "FFFFFF"));
		_titleColor = Integer.decode("0x" + set.getString("titleColor", "FFFF77"));
		_homeLocation = new Location(set.getInteger("homeX"), set.getInteger("homeY"), set.getInteger("homeZ"));
	}
	
	public int getId()
	{
		return _id;
	}
	
	public String getName()
	{
		return _name;
	}
	
	public int getNameColor()
	{
		return _nameColor;
	}
	
	public int getTitleColor()
	{
		return _titleColor;
	}
	
	public Location getHomeLocation()
	{
		return _homeLocation;
	}
}
