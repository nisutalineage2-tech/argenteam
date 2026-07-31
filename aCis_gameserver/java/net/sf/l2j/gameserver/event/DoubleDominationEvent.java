package net.sf.l2j.gameserver.event;

import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;

public class DoubleDominationEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(DoubleDominationEvent.class.getName());
	
	private Location _zone1, _zone2;
	private int _zoneRadius = 200;
	private int _pointsPerTick = 1;
	private int _tickInterval = 5;
	private ScheduledFuture<?> _zoneTask;
	
	public DoubleDominationEvent(EventConfig.EventData data)
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
		_zone1 = getData().getPositionAll();
		_zone2 = getData().getPositionBlue();
		
		if (_zone1 == null) _zone1 = new Location(-54500, -69600, -3371);
		if (_zone2 == null) _zone2 = new Location(-54400, -69400, -3371);
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (ep.isOnline())
			{
				ep.getPlayer().setTitle("[2Dom] ¡Pelea!");
				ep.getPlayer().broadcastTitleInfo();
			}
		}
		
		_zoneTask = ThreadPool.scheduleAtFixedRate(this::tickZones, _tickInterval * 1000L, _tickInterval * 1000L);
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		player.sendMessage("[2Dom] ¡Moriste! Reviviendo en " + getData().getRespawnDelay() + " segundos...");
		
		player.disableAllSkills();
		player.setIsImmobilized(true);
		player.startAbnormalEffect(AbnormalEffect.HOLD_1);
		
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
			
			final int x = _zone1.getX() + net.sf.l2j.commons.random.Rnd.get(-200, 200);
			final int y = _zone1.getY() + net.sf.l2j.commons.random.Rnd.get(-200, 200);
			player.teleportTo(x, y, _zone1.getZ(), 0);
		}, getData().getRespawnDelay() * 1000L);
	}
	
	@Override
	protected void onStop()
	{
		cancelTask(_zoneTask);
	}
	
	private void tickZones()
	{
		if (getState() != State.RUNNING)
			return;
		
		int blueZone1 = 0, redZone1 = 0, blueZone2 = 0, redZone2 = 0;
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			final Player p = ep.getPlayer();
			if (p.isDead() || p.isAlikeDead())
				continue;
			
			if (isInZone1(p))
			{
				if (ep.getTeamId() == 0) blueZone1++;
				else if (ep.getTeamId() == 1) redZone1++;
			}
			if (isInZone2(p))
			{
				if (ep.getTeamId() == 0) blueZone2++;
				else if (ep.getTeamId() == 1) redZone2++;
			}
		}
		
		boolean blueDominates = blueZone1 > redZone1 && blueZone2 > redZone2;
		boolean redDominates = redZone1 > blueZone1 && redZone2 > blueZone2;
		
		if (blueDominates)
		{
			final EventTeam blue = getTeam(0);
			if (blue != null)
			{
				blue.addScore(_pointsPerTick);
				broadcastToPlayers("[2Dom] ¡Azul domina ambas zonas! +" + _pointsPerTick);
			}
		}
		else if (redDominates)
		{
			final EventTeam red = getTeam(1);
			if (red != null)
			{
				red.addScore(_pointsPerTick);
				broadcastToPlayers("[2Dom] ¡Rojo domina ambas zonas! +" + _pointsPerTick);
			}
		}
	}
	
	private boolean isInZone1(Player player) { return player.isIn3DRadius(_zone1, _zoneRadius); }
	private boolean isInZone2(Player player) { return player.isIn3DRadius(_zone2, _zoneRadius); }
	
	@Override
	protected String getScorebar()
	{
		final java.util.List<EventTeam> teams = getTeams();
		if (teams.size() < 2)
			return null;
		return "[2Dom] Azul: " + teams.get(0).getScore() + " - Rojo: " + teams.get(1).getScore();
	}
}
