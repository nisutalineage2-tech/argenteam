package net.sf.l2j.gameserver.event;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;

public class HuntingGroundsEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(HuntingGroundsEvent.class.getName());
	
	private int _bowId;
	
	public HuntingGroundsEvent(EventConfig.EventData data)
	{
		super(data);
		_bowId = getData().getCustomInt("BowId", 9999);
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
			p.getInventory().addItem(_bowId, 1);
			p.setTitle("[Hunt] " + p.getName());
			p.broadcastTitleInfo();
			p.sendMessage("[Hunt] Un disparo, una muerte. Usa tu arco.");
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
			killer.getPlayer().setTitle("[Hunt] " + killer.getKills() + " bajas");
			killer.getPlayer().broadcastTitleInfo();
		}
		
		broadcastToPlayers("[Hunt] " + killer.getName() + " disparo a " + victim.getName() + ".");
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		player.sendMessage("[Hunt] Moriste. Reviviendo en " + getData().getRespawnDelay() + " segundos...");
		
		player.disableAllSkills();
		player.setIsImmobilized(true);
		player.startAbnormalEffect(AbnormalEffect.HOLD_1);
		
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
		}, getData().getRespawnDelay() * 1000L);
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
		return "[Hunt] Azul: " + teams.get(0).getScore() + " - Rojo: " + teams.get(1).getScore();
	}
}
