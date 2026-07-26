package net.sf.l2j.gameserver.event;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;

public class TvTEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(TvTEvent.class.getName());
	
	public TvTEvent(EventConfig.EventData data)
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
			team.broadcast("[TvT] " + killer.getName() + " killed " + victim.getName() + "!");
		}
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		
		player.sendMessage("[TvT] You died! Respawning...");
		
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
	}
}
