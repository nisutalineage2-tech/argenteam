package net.sf.l2j.gameserver.event;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;

public class HuntingGroundsEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(HuntingGroundsEvent.class.getName());
	
	public HuntingGroundsEvent(EventConfig.EventData data)
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
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			final Player p = ep.getPlayer();
			p.setTitle("[Hunt] " + p.getName());
			p.broadcastTitleInfo();
			p.sendMessage("[Hunt] One shot, one kill! Use your bow!");
		}
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
		if (killer == null || victim == null)
			return;
		
		final EventTeam killerTeam = getTeam(killer.getTeamId());
		if (killerTeam != null)
			killerTeam.addScore(1);
		
		if (killer.isOnline())
		{
			killer.getPlayer().setTitle("[Hunt] " + killer.getKills() + " kills");
			killer.getPlayer().broadcastTitleInfo();
		}
		
		broadcastToPlayers("[Hunt] " + killer.getName() + " shot " + victim.getName() + "!");
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		player.sendMessage("[Hunt] You died! Respawning...");
		
		final EventTeam team = getTeam(victim.getTeamId());
		if (team != null && team.getSpawnLocation() != null)
		{
			player.teleportTo(team.getSpawnLocation().getX(), team.getSpawnLocation().getY(), team.getSpawnLocation().getZ(), 0);
			player.startAbnormalEffect(AbnormalEffect.HOLD_1);
		}
	}
	
	@Override
	protected void onStop()
	{
	}
	
	@Override
	protected String getScorebar()
	{
		final java.util.List<EventTeam> teams = getTeams();
		if (teams.size() < 2)
			return null;
		return "[Hunt] Blue: " + teams.get(0).getScore() + " - Red: " + teams.get(1).getScore();
	}
}
