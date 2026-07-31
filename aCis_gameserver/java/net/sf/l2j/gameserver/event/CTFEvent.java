package net.sf.l2j.gameserver.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;

public class CTFEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(CTFEvent.class.getName());
	
	private final List<CTFFlag> _flags = new ArrayList<>();
	private ScheduledFuture<?> _flagCheckTask;
	private static final int FLAG_CAPTURE_RANGE = 200;
	
	public CTFEvent(EventConfig.EventData data)
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
		_flags.clear();
		
		for (EventTeam team : getTeams())
		{
			final Location flagLoc = team.getSpawnLocation();
			if (flagLoc != null)
			{
				final CTFFlag flag = new CTFFlag(team.getId(), flagLoc);
				_flags.add(flag);
			}
		}
		
		// Start flag proximity check every 2 seconds
		_flagCheckTask = ThreadPool.scheduleAtFixedRate(this::checkFlagProximity, 2000, 2000);
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (ep.isOnline())
			{
				ep.getPlayer().sendMessage("[CTF] Las banderas estan colocadas! Captura la bandera enemiga!");
			}
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
		
		for (EventTeam team : getTeams())
		{
			team.broadcast("[CTF] " + killer.getName() + " mato a " + victim.getName() + "!");
		}
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		
		player.sendMessage("[CTF] Moriste! Reviviendo en " + getData().getRespawnDelay() + " segundos...");
		
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
			player.sendMessage("[CTF] Has sido revivido y curado!");
		}, getData().getRespawnDelay() * 1000L);
	}
	
	@Override
	protected void onStop()
	{
		if (_flagCheckTask != null)
		{
			_flagCheckTask.cancel(false);
			_flagCheckTask = null;
		}
		_flags.clear();
	}
	
	private void checkFlagProximity()
	{
		if (!isRunning())
			return;
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			
			final Player player = ep.getPlayer();
			if (player.isDead())
				continue;
			
			for (CTFFlag flag : _flags)
			{
				// Skip own team's flag
				if (flag.getTeamId() == ep.getTeamId())
					continue;
				
				// Check if flag is already captured
				if (flag.isCaptured())
					continue;
				
				// Check distance to flag
				if (player.getPosition().distance3D(flag.getLocation()) <= FLAG_CAPTURE_RANGE)
				{
					captureFlag(flag.getTeamId(), player);
					flag.setCaptured(true);
					
					// Respawn flag after 30 seconds
					final CTFFlag capturedFlag = flag;
					ThreadPool.schedule(() ->
					{
						capturedFlag.setCaptured(false);
						for (EventPlayer p : getAllPlayers())
						{
							if (p.isOnline())
								p.getPlayer().sendMessage("[CTF] La bandera de " + getTeam(capturedFlag.getTeamId()).getName() + " ha reaparecido!");
						}
					}, 30000);
					break;
				}
			}
		}
	}
	
	private boolean isRunning()
	{
		return getState() == State.RUNNING;
	}
	
	public void captureFlag(int teamId, Player captor)
	{
		final EventPlayer ep = getEventPlayer(captor.getObjectId());
		if (ep == null)
			return;
		
		final EventTeam flagTeam = getTeam(teamId);
		
		if (flagTeam != null)
			flagTeam.subScore(10);
		
		final EventTeam captorTeam = getTeam(ep.getTeamId());
		if (captorTeam != null)
			captorTeam.addScore(10);
		
		for (EventTeam team : getTeams())
		{
			team.broadcast("[CTF] " + captor.getName() + " capturo la bandera de " + flagTeam.getName() + "! +10 puntos!");
		}
		
		captor.sendMessage("[CTF] Capturaste la bandera enemiga!");
	}
	
	private static class CTFFlag
	{
		private final int _teamId;
		private final Location _location;
		private boolean _captured;
		
		public CTFFlag(int teamId, Location location)
		{
			_teamId = teamId;
			_location = location;
			_captured = false;
		}
		
		public int getTeamId() { return _teamId; }
		public Location getLocation() { return _location; }
		public boolean isCaptured() { return _captured; }
		public void setCaptured(boolean captured) { _captured = captured; }
	}
}
