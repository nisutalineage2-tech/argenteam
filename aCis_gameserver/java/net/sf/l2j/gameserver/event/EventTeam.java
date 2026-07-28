package net.sf.l2j.gameserver.event;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;

public class EventTeam
{
	private final int _id;
	private final String _name;
	private final int _r;
	private final int _g;
	private final int _b;
	private final Location _spawnLocation;
	private final List<EventPlayer> _players;
	private int _score;
	
	public EventTeam(int id, String name, int r, int g, int b, Location spawnLocation)
	{
		_id = id;
		_name = name;
		_r = r;
		_g = g;
		_b = b;
		_spawnLocation = spawnLocation;
		_players = new ArrayList<>();
		_score = 0;
	}
	
	public int getId() { return _id; }
	public String getName() { return _name; }
	public int getR() { return _r; }
	public int getG() { return _g; }
	public int getB() { return _b; }
	public Location getSpawnLocation() { return _spawnLocation; }
	public List<EventPlayer> getPlayers() { return _players; }
	public int getScore() { return _score; }
	public void setScore(int score) { _score = score; }
	public void addScore(int amount) { _score += amount; }
	public void subScore(int amount) { _score = Math.max(0, _score - amount); }
	
	public void addPlayer(EventPlayer player)
	{
		_players.add(player);
		player.setTeamId(_id);
	}
	
	public void removePlayer(EventPlayer player)
	{
		_players.remove(player);
	}
	
	public int getSize()
	{
		return _players.size();
	}
	
	public boolean containsPlayer(int objectId)
	{
		for (EventPlayer ep : _players)
		{
			if (ep.getObjectId() == objectId)
				return true;
		}
		return false;
	}
	
	public void broadcast(String msg)
	{
		final CreatureSay cs = new CreatureSay(0, SayType.ALL, _name, msg);
		for (EventPlayer ep : _players)
		{
			if (ep.isOnline())
				ep.getPlayer().sendPacket(cs);
		}
	}
	
	public void setColors(Player player)
	{
		player.getAppearance().setNameColor((_r << 16) + (_g << 8) + _b);
		player.broadcastUserInfo();
	}
	
	public void clearColors(Player player)
	{
		player.getAppearance().setNameColor(0xFFFFFF);
		player.broadcastUserInfo();
	}
	
	public EventPlayer getMostKills()
	{
		EventPlayer best = null;
		for (EventPlayer ep : _players)
		{
			if (best == null || ep.getKills() > best.getKills())
				best = ep;
		}
		return best;
	}
}
