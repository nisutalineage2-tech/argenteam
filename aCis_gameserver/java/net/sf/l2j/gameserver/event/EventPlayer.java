package net.sf.l2j.gameserver.event;

import net.sf.l2j.gameserver.model.actor.Player;

public class EventPlayer
{
	private final Player _player;
	private int _teamId;
	private int _kills;
	private int _deaths;
	private boolean _saved;
	private boolean _teleported;
	
	public EventPlayer(Player player)
	{
		_player = player;
		_teamId = -1;
		_kills = 0;
		_deaths = 0;
		_saved = false;
		_teleported = false;
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
	
	public boolean isSaved() { return _saved; }
	public void setSaved(boolean saved) { _saved = saved; }
	
	public boolean isTeleported() { return _teleported; }
	public void setTeleported(boolean teleported) { _teleported = teleported; }
	
	public boolean isOnline() { return _player != null && _player.isOnline(); }
	
	public void restoreLocation()
	{
		if (_saved && _player != null && _player.getAccessLevel().getLevel() < 1)
		{
			_player.enableAllSkills();
			_player.setIsImmobilized(false);
			_player.setIsParalyzed(false);
		}
	}
}
