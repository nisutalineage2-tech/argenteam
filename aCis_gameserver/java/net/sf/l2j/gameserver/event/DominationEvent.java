package net.sf.l2j.gameserver.event;

import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;

public class DominationEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(DominationEvent.class.getName());
	
	private Location _zoneCenter;
	private int _zoneRadius = 300;
	private int _pointsPerTick = 1;
	private int _tickInterval = 5;
	private ScheduledFuture<?> _zoneTask;
	
	public DominationEvent(EventConfig.EventData data)
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
		_zoneCenter = getData().getPositionAll();
		if (_zoneCenter == null)
			_zoneCenter = new Location(-54478, -69506, -3371);
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (ep.isOnline())
			{
				ep.getPlayer().setTitle("[Dom] Fight!");
				ep.getPlayer().broadcastTitleInfo();
			}
		}
		
		// Start zone scoring task
		_zoneTask = ThreadPool.scheduleAtFixedRate(this::tickZone, _tickInterval * 1000L, _tickInterval * 1000L);
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
		// Kills don't directly score in Domination
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		player.sendMessage("[Dom] You died! Respawning...");
		player.teleportTo(_zoneCenter.getX() + net.sf.l2j.commons.random.Rnd.get(-200, 200), _zoneCenter.getY() + net.sf.l2j.commons.random.Rnd.get(-200, 200), _zoneCenter.getZ(), 0);
	}
	
	@Override
	protected void onStop()
	{
		cancelTask(_zoneTask);
	}
	
	private void tickZone()
	{
		if (getState() != State.RUNNING)
			return;
		
		int blueInZone = 0, redInZone = 0;
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			final Player p = ep.getPlayer();
			if (p.isDead() || p.isAlikeDead())
				continue;
			
			if (isInZone(p))
			{
				if (ep.getTeamId() == 0)
					blueInZone++;
				else if (ep.getTeamId() == 1)
					redInZone++;
			}
		}
		
		if (blueInZone > redInZone)
		{
			final EventTeam blue = getTeam(0);
			if (blue != null)
			{
				blue.addScore(_pointsPerTick);
				broadcastToPlayers("[Dom] Blue scores! (" + blueInZone + " vs " + redInZone + " in zone)");
			}
		}
		else if (redInZone > blueInZone)
		{
			final EventTeam red = getTeam(1);
			if (red != null)
			{
				red.addScore(_pointsPerTick);
				broadcastToPlayers("[Dom] Red scores! (" + redInZone + " vs " + blueInZone + " in zone)");
			}
		}
	}
	
	private boolean isInZone(Player player)
	{
		if (_zoneCenter == null)
			return false;
		return player.isIn3DRadius(_zoneCenter, _zoneRadius);
	}
	
	@Override
	protected String getScorebar()
	{
		final java.util.List<EventTeam> teams = getTeams();
		if (teams.size() < 2)
			return null;
		return "[Dom] Blue: " + teams.get(0).getScore() + " - Red: " + teams.get(1).getScore();
	}
}
