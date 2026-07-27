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
		// Set each player's title to show initial kill count
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			
			ep.getPlayer().setTitle("[TvT] Kills: 0");
			ep.getPlayer().broadcastTitleInfo();
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
		
		// Update killer's title with current kill count
		final Player killerPlayer = killer.getPlayer();
		killerPlayer.setTitle("[TvT] Kills: " + killer.getKills());
		killerPlayer.broadcastTitleInfo();
		
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
	}
	
	@Override
	protected void onStop()
	{
	}
}
