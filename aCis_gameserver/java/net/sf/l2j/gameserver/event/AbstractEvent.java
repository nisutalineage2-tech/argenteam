package net.sf.l2j.gameserver.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.world.World;

public abstract class AbstractEvent
{
	protected static final CLogger LOGGER = new CLogger(AbstractEvent.class.getName());
	
	public enum State { IDLE, REGISTER, STARTING, RUNNING, ENDED }
	
	private State _state;
	private final EventConfig.EventData _data;
	private final List<EventPlayer> _allPlayers;
	private final List<EventTeam> _teams;
	private ScheduledFuture<?> _registerTask;
	private ScheduledFuture<?> _matchTask;
	private ScheduledFuture<?> _startTask;
	
	public AbstractEvent(EventConfig.EventData data)
	{
		_data = data;
		_state = State.IDLE;
		_allPlayers = new ArrayList<>();
		_teams = new ArrayList<>();
	}
	
	public final State getState() { return _state; }
	protected final void setState(State state) { _state = state; }
	public final EventConfig.EventData getData() { return _data; }
	public final List<EventPlayer> getAllPlayers() { return _allPlayers; }
	public final List<EventTeam> getTeams() { return _teams; }
	
	public final boolean isParticipating(int objectId)
	{
		for (EventPlayer ep : _allPlayers)
		{
			if (ep.getObjectId() == objectId)
				return true;
		}
		return false;
	}
	
	public final EventPlayer getEventPlayer(int objectId)
	{
		for (EventPlayer ep : _allPlayers)
		{
			if (ep.getObjectId() == objectId)
				return ep;
		}
		return null;
	}
	
	public final boolean areTeammates(int objectId1, int objectId2)
	{
		EventPlayer ep1 = getEventPlayer(objectId1);
		EventPlayer ep2 = getEventPlayer(objectId2);
		if (ep1 == null || ep2 == null)
			return false;
		return ep1.getTeamId() == ep2.getTeamId() && ep1.getTeamId() >= 0;
	}
	
	public final void startRegistering()
	{
		if (_state != State.IDLE)
			return;
		
		_state = State.REGISTER;
		_allPlayers.clear();
		_teams.clear();
		
		onStartRegistering();
		
		broadcastEvent("[Event] " + _data.getEventName() + " registration open! Use .event join " + _data.getId() + " to join.");
		
		_registerTask = ThreadPool.schedule(this::startCountdown, EventConfig.getRegisterTime() * 60000L);
		
		LOGGER.info("Event {} registration opened.", _data.getEventName());
	}
	
	private void startCountdown()
	{
		if (_state != State.REGISTER)
			return;
		
		if (_allPlayers.size() < _data.getMinPlayers())
		{
			broadcastEvent("[Event] " + _data.getEventName() + " cancelled - not enough players (" + _allPlayers.size() + "/" + _data.getMinPlayers() + ").");
			stop();
			return;
		}
		
		_state = State.STARTING;
		broadcastEvent("[Event] " + _data.getEventName() + " starting in 10 seconds!");
		
		_startTask = ThreadPool.schedule(this::startMatch, 10000);
	}
	
	private void startMatch()
	{
		if (_state != State.STARTING)
			return;
		
		_state = State.RUNNING;
		
		assignTeams();
	_teleportPlayers();
	 onStartMatch();
		
		broadcastEvent("[Event] " + _data.getEventName() + " has started! Good luck!");
		
		if (_data.getMatchTime() > 0)
			_matchTask = ThreadPool.schedule(this::endMatch, _data.getMatchTime() * 60000L);
		
		LOGGER.info("Event {} started with {} players.", _data.getEventName(), _allPlayers.size());
	}
	
	public final void stop()
	{
		cancelTask(_registerTask);
		cancelTask(_matchTask);
		cancelTask(_startTask);
		
		teleportAllBack();
		restoreAllPlayers();
		
		_state = State.IDLE;
		_allPlayers.clear();
		_teams.clear();
		
		onStop();
		
		LOGGER.info("Event {} stopped.", _data.getEventName());
	}
	
	protected final void endMatch()
	{
		if (_state != State.RUNNING)
			return;
		
		_state = State.ENDED;
		
		final EventTeam winner = determineWinner();
		broadcastEvent("[Event] " + _data.getEventName() + " ended! Winner: " + (winner != null ? winner.getName() + "!" : "Draw!"));
		
		if (winner != null)
			rewardPlayers(winner);
		else
			rewardAllPlayers();
		
		teleportAllBack();
		restoreAllPlayers();
		
		_state = State.IDLE;
		_allPlayers.clear();
		_teams.clear();
		
		onStop();
		
		LOGGER.info("Event {} ended.", _data.getEventName());
	}
	
	public final boolean registerPlayer(Player player)
	{
		if (_state != State.REGISTER)
			return false;
		
		if (isParticipating(player.getObjectId()))
			return false;
		
		if (player.getStatus().getLevel() < _data.getMinLvl() || player.getStatus().getLevel() > _data.getMaxLvl())
			return false;
		
		final EventPlayer ep = new EventPlayer(player);
		_allPlayers.add(ep);
		
		player.sendMessage("[Event] You joined " + _data.getEventName() + "! (" + _allPlayers.size() + " players)");
		broadcastEvent("[Event] " + player.getName() + " joined " + _data.getEventName() + "! (" + _allPlayers.size() + " players)");
		
		return true;
	}
	
	public final boolean unregisterPlayer(int objectId)
	{
		if (_state != State.REGISTER)
			return false;
		
		final EventPlayer ep = getEventPlayer(objectId);
		if (ep == null)
			return false;
		
		_allPlayers.remove(ep);
		ep.getPlayer().sendMessage("[Event] You left " + _data.getEventName() + ".");
		broadcastEvent("[Event] " + ep.getName() + " left " + _data.getEventName() + ". (" + _allPlayers.size() + " players)");
		
		return true;
	}
	
	public final void onKill(int killerId, int victimId)
	{
		final EventPlayer killer = getEventPlayer(killerId);
		final EventPlayer victim = getEventPlayer(victimId);
		
		if (killer != null)
			killer.addKill();
		if (victim != null)
			victim.addDeath();
		
		onEventKill(killer, victim);
	}
	
	public final void onDie(int victimId, int killerId)
	{
		final EventPlayer victim = getEventPlayer(victimId);
		
		if (victim != null)
			victim.addDeath();
		
		onEventDie(victim, getEventPlayer(killerId));
	}
	
	protected void assignTeams()
	{
		final List<EventPlayer> shuffled = new ArrayList<>(_allPlayers);
		Rnd.shuffle(shuffled);
		
		for (int i = 0; i < shuffled.size(); i++)
		{
			final int teamId = i % 2;
			EventTeam team = getTeam(teamId);
			if (team == null)
			{
				final boolean isBlue = teamId == 0;
				team = new EventTeam(teamId, isBlue ? "Blue" : "Red", isBlue ? 0 : 255, isBlue ? 0 : 0, isBlue ? 255 : 0, isBlue ? _data.getPositionBlue() != null ? _data.getPositionBlue() : _data.getPositionAll() : _data.getPositionRed() != null ? _data.getPositionRed() : _data.getPositionAll());
				_teams.add(team);
			}
			team.addPlayer(shuffled.get(i));
		}
	}
	
	protected void teleportPlayers()
	{
		for (EventTeam team : _teams)
		{
			for (EventPlayer ep : team.getPlayers())
			{
				if (!ep.isOnline())
					continue;
				
				final Location loc = team.getSpawnLocation();
				if (loc != null)
				{
					final int x = loc.getX() + Rnd.get(-300, 300);
					final int y = loc.getY() + Rnd.get(-300, 300);
					ep.getPlayer().teleportTo(x, y, loc.getZ(), 0);
				}
				
				team.setColors(ep.getPlayer());
			}
		}
	}
	
	protected void teleportAllBack()
	{
		for (EventPlayer ep : _allPlayers)
		{
			if (!ep.isOnline())
				continue;
			
			if (ep.isTeleported())
				continue;
			
			ep.getPlayer().teleportTo(0, 0, 0, 0);
		}
	}
	
	protected void restoreAllPlayers()
	{
		for (EventPlayer ep : _allPlayers)
		{
			if (!ep.isOnline())
				continue;
			
			ep.restoreLocation();
			ep.getPlayer().broadcastUserInfo();
		}
	}
	
	protected void rewardPlayers(EventTeam winner)
	{
		for (EventPlayer ep : _allPlayers)
		{
			if (!ep.isOnline())
				continue;
			
			final boolean isWinner = winner.containsPlayer(ep.getObjectId());
			final String rewardStr = isWinner ? _data.getRewardWinner() : _data.getRewardLoser();
			grantReward(ep.getPlayer(), rewardStr);
		}
	}
	
	protected void rewardAllPlayers()
	{
		for (EventPlayer ep : _allPlayers)
		{
			if (!ep.isOnline())
				continue;
			grantReward(ep.getPlayer(), _data.getRewardWinner());
		}
	}
	
	private void grantReward(Player player, String rewardStr)
	{
		if (rewardStr == null || rewardStr.isEmpty())
			return;
		
		final String[] parts = rewardStr.split(",");
		if (parts.length >= 2)
		{
			try
			{
				final int itemId = Integer.parseInt(parts[0].trim());
				final int count = Integer.parseInt(parts[1].trim());
				player.getInventory().addItem("EventReward", itemId, count, player, null);
				player.sendMessage("[Event] Received " + count + "x item " + itemId + ".");
			}
			catch (NumberFormatException e)
			{
			}
		}
	}
	
	protected EventTeam getTeam(int id)
	{
		for (EventTeam team : _teams)
		{
			if (team.getId() == id)
				return team;
		}
		return null;
	}
	
	protected EventTeam determineWinner()
	{
		EventTeam best = null;
		for (EventTeam team : _teams)
		{
			if (best == null || team.getScore() > best.getScore())
				best = team;
			else if (team.getScore() == best.getScore() && team.getSize() > best.getSize())
				best = team;
		}
		return best;
	}
	
	public final boolean canAttack(int attackerId, int targetId)
	{
		if (!isParticipating(attackerId) || !isParticipating(targetId))
			return true;
		
		return !areTeammates(attackerId, targetId);
	}
	
	protected void broadcastEvent(String msg)
	{
		final CreatureSay cs = new CreatureSay(0, SayType.ALL, "Event", msg);
		for (Player player : World.getInstance().getPlayers())
		{
			if (player != null && player.isOnline())
				player.sendPacket(cs);
		}
	}
	
	protected void cancelTask(ScheduledFuture<?> task)
	{
		if (task != null && !task.isDone())
			task.cancel(false);
	}
	
	protected abstract void onStartRegistering();
	protected abstract void onStartMatch();
	protected abstract void onEventKill(EventPlayer killer, EventPlayer victim);
	protected abstract void onEventDie(EventPlayer victim, EventPlayer killer);
	protected abstract void onStop();
}
