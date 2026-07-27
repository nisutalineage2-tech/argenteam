package net.sf.l2j.gameserver.event;

import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;

public class EventPlayer
{
	private final Player _player;
	private int _teamId;
	private int _kills;
	private int _deaths;
	private Location _originalLocation;
	
	public EventPlayer(Player player)
	{
		_player = player;
		_teamId = -1;
		_kills = 0;
		_deaths = 0;
		_originalLocation = new Location(player.getX(), player.getY(), player.getZ());
	}
	
	public Player getPlayer() { return _player; }
	public int getObjectId() { return _player.getObjectId(); }
	public String getName() { return _player.getName(); }
	
	public int getTeamId() { return _teamId; }
	public void setTeamId(int teamId) { _teamId = teamId; }
	
	public int getKills() { return _kills; }
	public void addKill() { _kills++; }
	
	public int getDeaths() { return _deaths; }
	public void addDeath() { _deaths++; }
	
	public Location getOriginalLocation() { return _originalLocation; }
	
	public boolean isOnline() { return _player != null && _player.isOnline(); }
	
	public void restoreLocation()
	{
		if (_player == null || !_player.isOnline())
			return;
		
		if (_player.getAccessLevel().getLevel() < 1)
		{
			_player.enableAllSkills();
			_player.setIsImmobilized(false);
			_player.setIsParalyzed(false);
		}
		
		// Teleport back to original location
		if (_originalLocation != null)
			_player.teleportTo(_originalLocation.getX(), _originalLocation.getY(), _originalLocation.getZ(), 0);
		
		_player.broadcastUserInfo();
	}
}
