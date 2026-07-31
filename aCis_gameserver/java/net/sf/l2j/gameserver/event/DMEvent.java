package net.sf.l2j.gameserver.event;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;

public class DMEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(DMEvent.class.getName());
	
	public DMEvent(EventConfig.EventData data)
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
			
			ep.getPlayer().setTitle("[DM] Bajas: 0");
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
			killer.getPlayer().setTitle("[DM] Bajas: " + killer.getKills());
			killer.getPlayer().broadcastTitleInfo();
		}
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (ep.isOnline())
			{
				final CreatureSay cs = new CreatureSay(0, SayType.ALL, "DM", killer.getName() + " mató a " + (victim != null ? victim.getName() : "alguien") + "! (" + killer.getKills() + " bajas)");
				ep.getPlayer().sendPacket(cs);
			}
		}
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		
		player.sendMessage("[DM] ¡Moriste! Reviviendo en " + getData().getRespawnDelay() + " segundos...");
		
		// Disable skills and immobilize during respawn delay
		player.disableAllSkills();
		player.setIsImmobilized(true);
		player.startAbnormalEffect(AbnormalEffect.HOLD_1);
		
		// Schedule respawn with revive + full heal
		final EventTeam team = getTeam(victim.getTeamId());
		final int respawnX = (team != null && team.getSpawnLocation() != null) ? team.getSpawnLocation().getX() : player.getX();
		final int respawnY = (team != null && team.getSpawnLocation() != null) ? team.getSpawnLocation().getY() : player.getY();
		final int respawnZ = (team != null && team.getSpawnLocation() != null) ? team.getSpawnLocation().getZ() : player.getZ();
		
		ThreadPool.schedule(() ->
		{
			if (player == null || !player.isOnline())
				return;
			
			if (player.isDead())
				player.doRevive();
			
			player.getStatus().setCpHpMp(player.getStatus().getMaxCp(), player.getStatus().getMaxHp(), player.getStatus().getMaxMp());
			
			player.stopAbnormalEffect(AbnormalEffect.HOLD_1);
			player.enableAllSkills();
			player.setIsImmobilized(false);
			
			player.teleportTo(respawnX, respawnY, respawnZ, 0);
			player.sendMessage("[DM] ¡Has sido revivido y curado!");
		}, getData().getRespawnDelay() * 1000L);
	}
	
	@Override
	protected void onStop()
	{
	}
	
	@Override
	protected String getScorebar()
	{
		EventPlayer top = null;
		int maxKills = 0;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			if (ep.getKills() > maxKills)
			{
				maxKills = ep.getKills();
				top = ep;
			}
		}
		return "[DM] Líder: " + (top != null ? top.getName() + " (" + maxKills + ")" : "-") + " | Jugadores: " + getAllPlayers().size();
	}
}
