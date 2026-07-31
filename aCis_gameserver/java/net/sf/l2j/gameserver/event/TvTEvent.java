package net.sf.l2j.gameserver.event;

import java.util.List;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;

public class TvTEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(TvTEvent.class.getName());
	private static final int SCORE_LIMIT = 50;
	
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
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			
			final String teamName = ep.getTeamId() == 0 ? getData().getTeam1Name() : getData().getTeam2Name();
			ep.getPlayer().setTitle("[" + teamName + "] Bajas: 0");
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
			victimTeam.subScore(1);
		
		// Update killer's title with current kill count
		if (killer.isOnline())
		{
			final String teamName = killer.getTeamId() == 0 ? getData().getTeam1Name() : getData().getTeam2Name();
			killer.getPlayer().setTitle("[" + teamName + "] Bajas: " + killer.getKills());
			killer.getPlayer().broadcastTitleInfo();
		}
		
		// Kill streak rewards
		if (getData().isKillStreakMilestone(killer.getKillStreak()) && killer.isOnline())
		{
			final String rewardStr = getData().getKillStreakReward();
			if (!rewardStr.isEmpty())
			{
				grantReward(killer.getPlayer(), rewardStr);
				killer.getPlayer().sendPacket(new ExShowScreenMessage("[TvT] ¡Racha de bajas x" + killer.getKillStreak() + "! ¡Recompensa recibida!", 5000, ExShowScreenMessage.SMPOS.MIDDLE_CENTER, true));
			}
			broadcastEvent("[TvT] " + killer.getName() + " alcanzó " + killer.getKillStreak() + " bajas consecutivas!");
		}
		
		for (EventTeam team : getTeams())
		{
			team.broadcast("[TvT] " + killer.getName() + " mató a " + victim.getName() + "!");
		}
		
		// Check score limit
		if (killerTeam != null && killerTeam.getScore() >= SCORE_LIMIT)
		{
			broadcastEvent("[TvT] " + killerTeam.getName() + " alcanzó " + SCORE_LIMIT + " bajas! ¡Victoria!");
			endMatch();
		}
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		
		player.sendMessage("[TvT] ¡Moriste! Reviviendo en " + getData().getRespawnDelay() + " segundos...");
		
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
			player.sendMessage("[TvT] ¡Has sido revivido y curado!");
		}, getData().getRespawnDelay() * 1000L);
	}
	
	@Override
	protected void onStop()
	{
	}
	
	@Override
	protected String getScorebar()
	{
		final List<EventTeam> teams = getTeams();
		if (teams.size() < 2)
			return null;
		
		final EventTeam blue = teams.get(0);
		final EventTeam red = teams.get(1);
		return blue.getName() + ": " + blue.getScore() + " - " + red.getName() + ": " + red.getScore() + " | Meta: " + SCORE_LIMIT;
	}
}
