package net.sf.l2j.gameserver.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;

public class KoreanTvTEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(KoreanTvTEvent.class.getName());
	
	private int _playersPerRound = 0;
	private final List<EventPlayer> _eliminated = new ArrayList<>();
	private List<EventPlayer> _activeFighters = new ArrayList<>();
	private ScheduledFuture<?> _roundStartTask;
	private boolean _roundActive;
	
	public KoreanTvTEvent(EventConfig.EventData data)
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
			if (ep.isOnline())
			{
				ep.getPlayer().setTitle("[KTvT] Waiting");
				ep.getPlayer().broadcastTitleInfo();
				// Spectator mode for waiting players
				ep.getPlayer().setIsImmobilized(true);
				ep.getPlayer().setIsParalyzed(true);
			}
		}
		
		startNewRound();
	}
	
	private void startNewRound()
	{
		_roundActive = false;
		
		// Calculate players per round (at least 1v1)
		int totalAlive = 0;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline() || _eliminated.contains(ep))
				continue;
			totalAlive++;
		}
		
		if (totalAlive < 2)
		{
			endMatch();
			return;
		}
		
		int perTeam = _playersPerRound > 0 ? _playersPerRound : Math.max(1, totalAlive / 4);
		perTeam = Math.min(perTeam, totalAlive / 2);
		if (perTeam < 1) perTeam = 1;
		
		// Select fighters from each team
		List<EventPlayer> blueAvailable = new ArrayList<>();
		List<EventPlayer> redAvailable = new ArrayList<>();
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline() || _eliminated.contains(ep))
				continue;
			if (ep.getTeamId() == 0) blueAvailable.add(ep);
			else if (ep.getTeamId() == 1) redAvailable.add(ep);
		}
		
		Collections.shuffle(blueAvailable);
		Collections.shuffle(redAvailable);
		
		_activeFighters = new ArrayList<>();
		for (int i = 0; i < perTeam && i < blueAvailable.size(); i++)
			_activeFighters.add(blueAvailable.get(i));
		for (int i = 0; i < perTeam && i < redAvailable.size(); i++)
			_activeFighters.add(redAvailable.get(i));
		
		if (_activeFighters.size() < 2)
		{
			endMatch();
			return;
		}
		
		// Teleport fighters to arena, spectate others
		broadcastToPlayers("[KTvT] --- NEW ROUND! " + perTeam + "v" + perTeam + " ---");
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline() || _eliminated.contains(ep))
				continue;
			
			final Player p = ep.getPlayer();
			if (_activeFighters.contains(ep))
			{
				p.setIsImmobilized(false);
				p.setIsParalyzed(false);
				ep.getPlayer().setTitle("[KTvT] Fighting!");
				final EventTeam team = getTeam(ep.getTeamId());
				if (team != null && team.getSpawnLocation() != null)
					p.teleportTo(team.getSpawnLocation().getX(), team.getSpawnLocation().getY(), team.getSpawnLocation().getZ(), 0);
			}
			else
			{
				p.setIsImmobilized(true);
				p.setIsParalyzed(true);
				ep.getPlayer().setTitle("[KTvT] Watching");
				p.teleportTo(getData().getPositionAll().getX(), getData().getPositionAll().getY(), getData().getPositionAll().getZ(), 0);
			}
			p.broadcastTitleInfo();
		}
		
		_roundActive = true;
		
		// Schedule round end if no one dies
		_roundStartTask = ThreadPool.schedule(() -> {
			if (_roundActive)
			{
				_roundActive = false;
				checkRoundWinner();
			}
		}, getData().getMatchTime() * 60000L / 4); // Round lasts 1/4 of match time
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
		if (killer == null || victim == null)
			return;
		
		if (!_activeFighters.contains(killer) || !_activeFighters.contains(victim))
			return;
		
		// Eliminate the victim from this round
		_activeFighters.remove(victim);
		
		// If victim is dead, put them in spectator
		if (victim.isOnline())
		{
			victim.getPlayer().setTitle("[KTvT] Dead");
			victim.getPlayer().broadcastTitleInfo();
		}
		
		broadcastToPlayers("[KTvT] " + killer.getName() + " defeated " + victim.getName() + "!");
		
		// Check if round is over
		checkRoundWinner();
	}
	
	private void checkRoundWinner()
	{
		if (!_roundActive)
			return;
		
		int blueRemaining = 0, redRemaining = 0;
		for (EventPlayer ep : _activeFighters)
		{
			if (!ep.isOnline())
				continue;
			final Player p = ep.getPlayer();
			if (p.isDead() || p.isAlikeDead())
				continue;
			if (ep.getTeamId() == 0) blueRemaining++;
			else if (ep.getTeamId() == 1) redRemaining++;
		}
		
		if (blueRemaining == 0 && redRemaining > 0)
		{
			_roundActive = false;
			cancelTask(_roundStartTask);
			broadcastToPlayers("[KTvT] Red wins the round!");
			
			// Eliminate all blue fighters
			eliminateTeam(0);
			checkMatchEnd();
		}
		else if (redRemaining == 0 && blueRemaining > 0)
		{
			_roundActive = false;
			cancelTask(_roundStartTask);
			broadcastToPlayers("[KTvT] Blue wins the round!");
			
			eliminateTeam(1);
			checkMatchEnd();
		}
	}
	
	private void eliminateTeam(int teamId)
	{
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline() || _eliminated.contains(ep))
				continue;
			if (ep.getTeamId() == teamId)
			{
				_eliminated.add(ep);
				final Player p = ep.getPlayer();
				p.setIsImmobilized(true);
				p.setIsParalyzed(true);
				p.setTitle("[KTvT] Out!");
				p.broadcastTitleInfo();
			}
		}
	}
	
	private void checkMatchEnd()
	{
		int blueAlive = 0, redAlive = 0;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline() || _eliminated.contains(ep))
				continue;
			if (ep.getTeamId() == 0) blueAlive++;
			else if (ep.getTeamId() == 1) redAlive++;
		}
		
		if (blueAlive == 0 && redAlive > 0)
		{
			broadcastToPlayers("[KTvT] Red team wins the match!");
			endMatch();
		}
		else if (redAlive == 0 && blueAlive > 0)
		{
			broadcastToPlayers("[KTvT] Blue team wins the match!");
			endMatch();
		}
		else if (blueAlive > 0 && redAlive > 0)
		{
			// Start next round
			startNewRound();
		}
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		// No respawn in Korean TvT
	}
	
	@Override
	protected void onStop()
	{
		cancelTask(_roundStartTask);
		_activeFighters.clear();
		_eliminated.clear();
		_roundActive = false;
	}
	
	@Override
	protected String getScorebar()
	{
		int blueAlive = 0, redAlive = 0;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline() || _eliminated.contains(ep))
				continue;
			if (ep.getTeamId() == 0) blueAlive++;
			else if (ep.getTeamId() == 1) redAlive++;
		}
		return "[KTvT] Blue: " + blueAlive + " | Red: " + redAlive + " | Fighting: " + _activeFighters.size();
	}
}
