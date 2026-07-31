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
			ep.getPlayer().setTitle("[LMS] Vivo");
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
			killer.getPlayer().setTitle("[LMS] " + killer.getKills() + " bajas");
			killer.getPlayer().broadcastTitleInfo();
		}
		
		broadcastToPlayers("[LMS] " + killer.getName() + " mató a " + (victim != null ? victim.getName() : "alguien") + "!");
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
		player.setTitle("[LMS] Muerto");
		player.broadcastTitleInfo();
		player.sendMessage("[LMS] ¡Estás muerto! Espera a que termine el evento.");
		
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
		return "[LMS] Vivos: " + alive + " | Líder: " + (leader != null ? leader.getName() + " (" + leader.getKills() + ")" : "-");
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
			broadcastToPlayers("[LMS] " + lastAlive.getName() + " es el ÚLTIMO HOMBRE EN PIE! (" + lastAlive.getKills() + " bajas)");
			endMatch();
		}
		else if (alive == 0)
		{
			broadcastToPlayers("[LMS] ¡Todos están muertos! ¡Es un empate!");
			endMatch();
		}
	}
}
