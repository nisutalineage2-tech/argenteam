package net.sf.l2j.gameserver.event;

import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;

public class EventPlayer
{
	private final Player _player;
	private int _teamId;
	private int _kills;
	private int _deaths;
	private int _killStreak;
	private int _bestKillStreak;
	private Location _originalLocation;
	private final String _originalTitle;
	private final int _originalFactionId;
	
	public EventPlayer(Player player)
	{
		_player = player;
		_teamId = -1;
		_kills = 0;
		_deaths = 0;
		_killStreak = 0;
		_bestKillStreak = 0;
		_originalLocation = new Location(player.getX(), player.getY(), player.getZ());
		_originalTitle = player.getTitle();
		_originalFactionId = player.getFactionId();
	}
	
	public Player getPlayer() { return _player; }
	public int getObjectId() { return _player.getObjectId(); }
	public String getName() { return _player.getName(); }
	
	public int getTeamId() { return _teamId; }
	public void setTeamId(int teamId) { _teamId = teamId; }
	
	public int getKills() { return _kills; }
	public void addKill()
	{
		_kills++;
		_killStreak++;
		if (_killStreak > _bestKillStreak)
			_bestKillStreak = _killStreak;
	}
	
	public int getDeaths() { return _deaths; }
	public void addDeath()
	{
		_deaths++;
		_killStreak = 0;
	}
	
	public int getKillStreak() { return _killStreak; }
	public int getBestKillStreak() { return _bestKillStreak; }
	
	public Location getOriginalLocation() { return _originalLocation; }
	public String getOriginalTitle() { return _originalTitle; }
	public int getOriginalFactionId() { return _originalFactionId; }
	
	public boolean isOnline() { return _player != null && _player.isOnline(); }
	
	public void restoreLocation()
	{
		if (_player == null || !_player.isOnline())
			return;
		
		// Restore original title
		_player.setTitle(_originalTitle);
		
		// Restore original faction (removed during event registration for neutrality)
		if (_originalFactionId > 0 && _player.getFactionId() != _originalFactionId)
		{
			_player.setFactionId(_originalFactionId);
			net.sf.l2j.gameserver.data.xml.FactionData.getInstance().storeData(_player);
			net.sf.l2j.gameserver.data.xml.FactionData.getInstance().onPlayerEnter(_player);
		}
		
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
		_player.broadcastTitleInfo();
	}
}
