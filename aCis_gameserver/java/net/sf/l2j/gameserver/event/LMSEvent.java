package net.sf.l2j.gameserver.event;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.gameserver.model.actor.Player;

public class LMSEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(LMSEvent.class.getName());
	
	public LMSEvent(EventConfig.EventData data)
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
			ep.getPlayer().setTitle("[LMS] Alive");
			ep.getPlayer().broadcastTitleInfo();
		}
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
		if (killer == null)
			return;
		
		if (killer.isOnline())
		{
			killer.getPlayer().setTitle("[LMS] " + killer.getKills() + " kills");
			killer.getPlayer().broadcastTitleInfo();
		}
		
		broadcastToPlayers("[LMS] " + killer.getName() + " killed " + (victim != null ? victim.getName() : "someone") + "!");
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		// No respawn in LMS - turn into observer
		final Player player = victim.getPlayer();
		player.setIsImmobilized(true);
		player.setIsParalyzed(true);
		player.setTitle("[LMS] Dead");
		player.broadcastTitleInfo();
		player.sendMessage("[LMS] You are dead! Wait for the event to end.");
		
		// Check if only one player remains alive
		checkLastAlive();
	}
	
	@Override
	protected void onStop()
	{
	}
	
	@Override
	protected String getScorebar()
	{
		int alive = 0;
		EventPlayer leader = null;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			final Player p = ep.getPlayer();
			if (!p.isDead() && !p.isAlikeDead())
			{
				alive++;
				if (leader == null || ep.getKills() > leader.getKills())
					leader = ep;
			}
		}
		return "[LMS] Alive: " + alive + " | Leader: " + (leader != null ? leader.getName() + " (" + leader.getKills() + ")" : "-");
	}
	
	private void checkLastAlive()
	{
		int alive = 0;
		EventPlayer lastAlive = null;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			final Player p = ep.getPlayer();
			if (!p.isDead() && !p.isAlikeDead())
			{
				alive++;
				lastAlive = ep;
			}
		}
		
		if (alive <= 1 && lastAlive != null)
		{
			broadcastToPlayers("[LMS] " + lastAlive.getName() + " is the LAST MAN STANDING! (" + lastAlive.getKills() + " kills)");
			endMatch();
		}
		else if (alive == 0)
		{
			broadcastToPlayers("[LMS] Everyone is dead! It's a draw!");
			endMatch();
		}
	}
}
