package net.sf.l2j.gameserver.event;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.commons.logging.CLogger;

import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;

public class CTFEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(CTFEvent.class.getName());
	
	private final List<CTFFlag> _flags = new ArrayList<>();
	
	public CTFEvent(EventConfig.EventData data)
	{
		super(data);
	}
	
	@Override
	protected void onStartRegistering()
	{
	}
	
	@Override
	protected void onStartMatch()
	{
		_flags.clear();
		
		for (EventTeam team : getTeams())
		{
			final Location flagLoc = team.getSpawnLocation();
			if (flagLoc != null)
			{
				final CTFFlag flag = new CTFFlag(team.getId(), flagLoc);
				_flags.add(flag);
			}
		}
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (ep.isOnline())
			{
				ep.getPlayer().sendMessage("[CTF] Flags are placed! Capture the enemy flag!");
			}
		}
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
		if (killer == null || victim == null)
			return;
		
		final EventTeam killerTeam = getTeam(killer.getTeamId());
		final EventTeam victimTeam = getTeam(victim.getTeamId());
		
		if (killerTeam != null)
			killerTeam.addScore(1);
		if (victimTeam != null)
			victimTeam.addScore(-1);
		
		for (EventTeam team : getTeams())
		{
			team.broadcast("[CTF] " + killer.getName() + " killed " + victim.getName() + "!");
		}
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		
		player.sendMessage("[CTF] You died! Respawning...");
		
		final EventTeam team = getTeam(victim.getTeamId());
		if (team != null && team.getSpawnLocation() != null)
		{
			player.teleportTo(team.getSpawnLocation().getX(), team.getSpawnLocation().getY(), team.getSpawnLocation().getZ(), 0);
		}
		
		player.startAbnormalEffect(AbnormalEffect.HOLD_1);
		
		victim.setTeleported(true);
	}
	
	@Override
	protected void onStop()
	{
		_flags.clear();
	}
	
	public void captureFlag(int teamId, Player captor)
	{
		final EventPlayer ep = getEventPlayer(captor.getObjectId());
		if (ep == null)
			return;
		
		final EventTeam flagTeam = getTeam(teamId);
		
		if (flagTeam != null)
			flagTeam.addScore(-10);
		
		final EventTeam captorTeam = getTeam(ep.getTeamId());
		if (captorTeam != null)
			captorTeam.addScore(10);
		
		for (EventTeam team : getTeams())
		{
			team.broadcast("[CTF] " + captor.getName() + " captured " + flagTeam.getName() + "'s flag!");
		}
	}
	
	private static class CTFFlag
	{
		private final int _teamId;
		private final Location _location;
		
		public CTFFlag(int teamId, Location location)
		{
			_teamId = teamId;
			_location = location;
		}
		
		public int getTeamId() { return _teamId; }
		public Location getLocation() { return _location; }
	}
}
