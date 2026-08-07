package net.sf.l2j.gameserver.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.enums.GaugeColor;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ConfirmDlg;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.SetupGauge;	import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
	import net.sf.l2j.gameserver.phantom.PhantomEngine;
	import net.sf.l2j.gameserver.phantom.PhantomLog;
	import net.sf.l2j.gameserver.factionwar.FactionWarManager;

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
	private ScheduledFuture<?> _scorebarTask;
	private ScheduledFuture<?> _roundTask;
	private final List<ScheduledFuture<?>> _countdownTasks = new ArrayList<>();
	private long _matchStartTime;
	private long _registerStartTime;
	private long _matchDurationMs;
	private int _currentRound;
	private int _totalRounds;
	private long _roundDurationMs;
	private boolean _roundActive;
	
	/** Crest id for event team 1 (Argentina country flag), file data/crests/Crest_90001.dds. */
	private static final int EVENT_CREST_TEAM1 = 90001;
	/** Crest id for event team 2 (Brasil country flag), file data/crests/Crest_90002.dds. */
	private static final int EVENT_CREST_TEAM2 = 90002;
	
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
	public final int getCurrentRound() { return _currentRound; }
	public final int getTotalRounds() { return _totalRounds; }
	public final boolean isRoundActive() { return _roundActive; }
	
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
		_registerStartTime = System.currentTimeMillis();
		_allPlayers.clear();
		_teams.clear();
		
		onStartRegistering();
		
		broadcastEvent("[Event] " + _data.getEventName() + " inscripcion abierta. Visita al Event Manager o usa .eventjoin " + _data.getId() + " para unirte.");
		sendEventPopup();
		autoRegisterPhantoms();
		
		_registerTask = ThreadPool.schedule(this::startCountdown, EventConfig.getRegisterTime() * 60000L);
		
		LOGGER.info("Event {} registration opened.", _data.getEventName());
	}
	
	private void startCountdown()
	{
		if (_state != State.REGISTER)
			return;
		
		// Never let an event start while the Faction War is running: the alternance owns the
		// battlefield. This guards manual admin restarts that force a war during an open event.
		if (FactionWarManager.getInstance().isRunning())
		{
			broadcastEvent("[Event] " + _data.getEventName() + " cancelado - la guerra de facciones esta en curso.");
			stop();
			return;
		}
		
		if (_allPlayers.size() < _data.getMinPlayers())
		{
			broadcastEvent("[Event] " + _data.getEventName() + " cancelado - jugadores insuficientes (" + _allPlayers.size() + "/" + _data.getMinPlayers() + ").");
			stop();
			return;
		}
		
		_state = State.STARTING;
		broadcastEvent("[Event] " + _data.getEventName() + " comienza en 10 segundos.");
		
		_startTask = ThreadPool.schedule(this::startMatch, 10000);
	}
	
	private void startMatch()
	{
		if (_state != State.STARTING)
			return;
		
		_state = State.RUNNING;
		
		// Record match start time for timer display
		_matchStartTime = System.currentTimeMillis();
		_matchDurationMs = _data.getMatchTime() * 60000L;
		
		// Initialize round system
		_totalRounds = _data.getRounds();
		_currentRound = 0;
		
		assignTeams();
		teleportPlayers();
		onStartMatch();
		
		// Apply event buffs to all participants
		if (EventConfig.isEventBufferEnabled())
		{
			for (EventPlayer ep : _allPlayers)
			{
				if (ep.isOnline())
					EventBuffer.getInstance().applyBuffs(ep.getPlayer());
			}
		}
		
		broadcastEvent("[Event] " + _data.getEventName() + " ha comenzado. Buena suerte.");
		
		// Send SetupGauge bar to each player + start scorebar updates every 10 seconds
		if (_matchDurationMs > 0)
		{
			for (EventPlayer ep : _allPlayers)
			{
				if (ep.isOnline())
					ep.getPlayer().sendPacket(new SetupGauge(GaugeColor.CYAN, (int) _matchDurationMs));
			}
		}
		_scorebarTask = ThreadPool.scheduleAtFixedRate(this::broadcastScorebar, 10000, 10000);
		
		// Schedule countdown announcements
		scheduleCountdowns();
		
		if (_data.getMatchTime() > 0)
			_matchTask = ThreadPool.schedule(this::endMatch, _matchDurationMs);
		
		// Start round system if configured
		if (_totalRounds > 0)
		{
			_roundDurationMs = _matchDurationMs / _totalRounds;
			startNextRound();
		}
		
		LOGGER.info("Event {} started with {} players ({} rounds).", _data.getEventName(), _allPlayers.size(), _totalRounds);
	}
	
	public final void stop()
	{
		cancelTask(_registerTask);
		cancelTask(_matchTask);
		cancelTask(_startTask);
		cancelTask(_scorebarTask);
		cancelTask(_roundTask);
		cancelCountdowns();
		
		_roundActive = false;
		restoreAllPlayers();
		
		_state = State.IDLE;
		_allPlayers.clear();
		_teams.clear();
		
		onStop();
		
		LOGGER.info("Event {} stopped.", _data.getEventName());
		
		EventEngine.getInstance().onEventEnded();
	}
	
	protected final void endMatch()
	{
		if (_state != State.RUNNING)
			return;
		
		cancelTask(_scorebarTask);
		cancelTask(_roundTask);
		cancelCountdowns();
		
		_roundActive = false;
		_state = State.ENDED;
		
		final EventTeam winner = determineWinner();
		
		// Persist stats to database
		final int winnerTeamId = (winner != null) ? winner.getId() : -1;
		EventStats.getInstance().onEventEnd(_data.getId(), _allPlayers, winnerTeamId);
		
		// Send ranking before clearing
		final String ranking = buildRanking();
		broadcastToPlayers(ranking);
		broadcastEvent("[Event] " + _data.getEventName() + " termino. Ganador: " + (winner != null ? winner.getName() + "." : "Empate."));
		
		if (winner != null)
			rewardPlayers(winner);
		else
			rewardAllPlayers();
		
		restoreAllPlayers();
		
		_state = State.IDLE;
		_allPlayers.clear();
		_teams.clear();
		
		onStop();
		
		LOGGER.info("Event {} ended.", _data.getEventName());
		
		EventEngine.getInstance().onEventEnded();
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
		
		// Remove faction during event for neutrality (save original in EventPlayer)
		if (player.getFactionId() > 0)
		{
			player.setFactionId(0);
			player.broadcastUserInfo();
		}
		
		player.sendMessage("[Event] Te uniste a " + _data.getEventName() + ". (" + _allPlayers.size() + " jugadores)");
		broadcastEvent("[Event] " + player.getName() + " se unio a " + _data.getEventName() + ". (" + _allPlayers.size() + " jugadores)");
		
		return true;
	}
	
	/**
	 * Automatically registers all eligible phantoms (bots) to this event.
	 * Phantoms are neutral during events (no faction), their original faction
	 * is restored when the event ends via EventPlayer.restoreLocation().
	 */
	private void autoRegisterPhantoms()
	{
		int registered = 0;
		for (Player phantom : PhantomEngine.getActivePhantoms())
		{
			if (phantom == null || !phantom.isOnline() || phantom.isDead())
				continue;
			
			if (isParticipating(phantom.getObjectId()))
				continue;
			
			// Respect event level requirements
			final int level = phantom.getStatus().getLevel();
			if (level < _data.getMinLvl() || level > _data.getMaxLvl())
				continue;
			
			// Create EventPlayer (saves original faction, location, title)
			final EventPlayer ep = new EventPlayer(phantom);
			_allPlayers.add(ep);
			
			// Remove faction during event for neutrality
			if (phantom.getFactionId() > 0)
			{
				phantom.setFactionId(0);
				phantom.broadcastUserInfo();
			}
			
			registered++;
		}
		
		if (registered > 0)
			LOGGER.info("Auto-registered {} phantoms to event {}.", registered, _data.getEventName());
	}
	
	public final boolean unregisterPlayer(int objectId)
	{
		if (_state != State.REGISTER)
			return false;
		
		final EventPlayer ep = getEventPlayer(objectId);
		if (ep == null)
			return false;
		
		_allPlayers.remove(ep);
		ep.getPlayer().sendMessage("[Event] Saliste de " + _data.getEventName() + ".");
		broadcastEvent("[Event] " + ep.getName() + " abandono " + _data.getEventName() + ". (" + _allPlayers.size() + " jugadores)");
		
		return true;
	}
	
	public final void onKill(int killerId, int victimId)
	{
		final EventPlayer killer = getEventPlayer(killerId);
		final EventPlayer victim = getEventPlayer(victimId);
		
		if (killer != null)
			killer.addKill();
		
		onEventKill(killer, victim);
		
		// Phantom diagnostic: makes event kills by phantoms verifiable in log/phantoms.log.
		// Logged AFTER onEventKill so the team score already includes this kill (matches the scorebar).
		if (killer != null && killer.isOnline() && PhantomEngine.isPhantom(killerId))
		{
			final EventTeam team = killer.getTeamId() >= 0 ? getTeam(killer.getTeamId()) : null;
			PhantomLog.info("Event " + _data.getEventName() + " | phantom " + killer.getName() + " killed " + (victim != null ? victim.getName() : "?") + " | kills=" + killer.getKills() + (team != null ? " teamScore=" + team.getScore() : ""));
		}
	}
	
	public final void onDie(int victimId, int killerId)
	{
		final EventPlayer victim = getEventPlayer(victimId);
		
		if (victim != null)
			victim.addDeath();
		
		// Phantom diagnostic: makes event deaths by phantoms verifiable in log/phantoms.log.
		// Note: reviving events (TvT/DM/CTF) respawn the phantom at its team spawn;
		// elimination events (LMS/SimonSays/BombFight...) keep it dead until the match ends.
		if (victim != null && victim.isOnline() && PhantomEngine.isPhantom(victimId))
			PhantomLog.info("Event " + _data.getEventName() + " | phantom " + victim.getName() + " died | deaths=" + victim.getDeaths() + " (event controls respawn)");
		
		onEventDie(victim, getEventPlayer(killerId));
	}
	
	protected void assignTeams()
	{
		final List<EventPlayer> shuffled = new ArrayList<>(_allPlayers);
		Collections.shuffle(shuffled);
		
		for (int i = 0; i < shuffled.size(); i++)
		{
			final int teamId = i % 2;
			EventTeam team = getTeam(teamId);
			if (team == null)
			{
				// Team 0 = Argentina (celeste), team 1 = Brasil (verde), each with its country crest.
				final boolean isTeam0 = teamId == 0;
				team = new EventTeam(teamId, isTeam0 ? _data.getTeam1Name() : _data.getTeam2Name(), isTeam0 ? 102 : 0, isTeam0 ? 204 : 156, isTeam0 ? 255 : 59, isTeam0 ? _data.getPositionBlue() != null ? _data.getPositionBlue() : _data.getPositionAll() : _data.getPositionRed() != null ? _data.getPositionRed() : _data.getPositionAll());
				team.setCrestId(isTeam0 ? EVENT_CREST_TEAM1 : EVENT_CREST_TEAM2);
				_teams.add(team);
			}
			team.addPlayer(shuffled.get(i));
		}
	}
	
	protected void teleportPlayers()
	{
		for (EventTeam team : _teams)
		{
			final Location loc = team.getSpawnLocation();
			final int radius = (loc != null) ? _data.getPositionRadius() : 300;
			
			for (EventPlayer ep : team.getPlayers())
			{
				if (!ep.isOnline())
					continue;
				
				if (loc != null)
				{
					final int x = loc.getX() + Rnd.get(-radius, radius);
					final int y = loc.getY() + Rnd.get(-radius, radius);
					// Validate the destination is walkable before teleporting
					final Location validatedLoc = GeoEngine.getInstance().getValidLocation(
							loc.getX(), loc.getY(), loc.getZ(), x, y, loc.getZ(), null);
					if (!validatedLoc.equals(new Location(x, y, loc.getZ())))
					{
						LOGGER.info("Event position adjusted for walkability: ({},{},{}) -> ({},{},{})",
								x, y, loc.getZ(),
								validatedLoc.getX(), validatedLoc.getY(), validatedLoc.getZ());
					}
					ep.getPlayer().teleportTo(validatedLoc.getX(), validatedLoc.getY(), validatedLoc.getZ(), 0);
				}
				
				team.setColors(ep.getPlayer());
			}
		}
	}
	
	protected void broadcastScorebar()
	{
		if (_state != State.RUNNING)
			return;
		
		// Build remaining time string
		String timeStr = "";
		if (_matchDurationMs > 0 && _matchStartTime > 0)
		{
			final long elapsed = System.currentTimeMillis() - _matchStartTime;
			final long remaining = Math.max(0, _matchDurationMs - elapsed);
			final int mins = (int) (remaining / 60000);
			final int secs = (int) ((remaining % 60000) / 1000);
			timeStr = String.format(" | Time: %d:%02d", mins, secs);
		}
		
		// Get score string from subclass
		final String score = getScorebar();
		
		// Build the on-screen message
		final String eventName = _data.getEventName();
		final String screenMsg;
		if (score != null && !score.isEmpty())
			screenMsg = "[ " + eventName + " ] " + score + timeStr;
		else
			screenMsg = "[ " + eventName + " ]" + timeStr;
		
		// Send ExShowScreenMessage to all event participants
		for (EventPlayer ep : _allPlayers)
		{
			if (ep.isOnline())
			{
				ep.getPlayer().sendPacket(new ExShowScreenMessage(screenMsg, 10000, ExShowScreenMessage.SMPOS.TOP_LEFT, false));
			}
		}
	}
	
	/** Override in subclasses to return a live score string (e.g. "Blue: 5 - Red: 3 | Time: 45:30") */
	protected String getScorebar()
	{
		return null;
	}
	
	protected String buildRanking()
	{
		final List<EventPlayer> sorted = new ArrayList<>(_allPlayers);
		sorted.removeIf(ep -> !ep.isOnline());
		sorted.sort((a, b) -> Integer.compare(b.getKills(), a.getKills()));
		
		final StringBuilder sb = new StringBuilder();
		sb.append("[Event] Top de Bajas:");
		int rank = 1;
		for (EventPlayer ep : sorted)
		{
			if (rank > 5)
				break;
			sb.append(" #").append(rank).append(" ").append(ep.getName()).append(" (").append(ep.getKills()).append("k/").append(ep.getDeaths()).append("d)");
			rank++;
		}
		return sb.toString();
	}
	
	protected void restoreAllPlayers()
	{
		for (EventPlayer ep : _allPlayers)
		{
			if (!ep.isOnline())
				continue;
			
			ep.restoreLocation();
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
	
	protected void grantReward(Player player, String rewardStr)
	{
		if (rewardStr == null || rewardStr.isEmpty())
			return;
		
		// Support multiple rewards separated by semicolon: "57,1000;1835,10;729,1"
		final String[] rewards = rewardStr.split(";");
		for (String single : rewards)
		{
			final String[] parts = single.split(",");
			if (parts.length >= 2)
			{
				try
				{
					final int itemId = Integer.parseInt(parts[0].trim());
					final int count = Integer.parseInt(parts[1].trim());
					player.getInventory().addItem(itemId, count);
					player.sendMessage("[Event] Recibiste " + count + "x " + itemId + ".");
				}
				catch (NumberFormatException e)
				{
					LOGGER.warn("Invalid reward entry: {}.", single);
				}
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
	
	protected void broadcastToPlayers(String msg)
	{
		for (EventPlayer ep : _allPlayers)
		{
			if (ep.isOnline())
				ep.getPlayer().sendMessage(msg);
		}
	}
	
	private void sendEventPopup()
	{
		final ConfirmDlg dlg = new ConfirmDlg(SystemMessageId.EVENT);
		dlg.addString(_data.getEventName() + " - Quieres registrarte?");
		dlg.addZoneName(_data.getPositionAll());
		dlg.addTime(45000);
		dlg.addRequesterId(_data.getId());
		
		for (Player player : World.getInstance().getPlayers())
		{
			if (player == null || !player.isOnline())
				continue;
			
			// Check if already in an event
			if (EventEngine.getInstance().isPlayerInAnyEvent(player.getObjectId()))
				continue;
			
			// Check level requirements
			final int level = player.getStatus().getLevel();
			if (level < _data.getMinLvl() || level > _data.getMaxLvl())
				continue;
			
			// Skip players in special states
			if (player.isInOlympiadMode() || player.isInObserverMode() || player.isCursedWeaponEquipped())
				continue;
			
			player.sendPacket(dlg);
		}
	}
	
	protected void cancelTask(ScheduledFuture<?> task)
	{
		if (task != null && !task.isDone())
			task.cancel(false);
	}
	
	/**
	 * Schedules countdown announcements at key time milestones during the match.
	 * Announces at: 10min, 5min, 3min, 2min, 1min, 30s, 10s, 5s, 4, 3, 2, 1
	 */
	private void scheduleCountdowns()
	{
		if (_matchDurationMs <= 0)
			return;
		
		final int matchSeconds = (int) (_matchDurationMs / 1000);
		final int[] milestones = {600, 300, 180, 120, 60, 30, 10, 5, 4, 3, 2, 1};
		
		for (int sec : milestones)
		{
			if (sec >= matchSeconds)
				continue;
			
			final long delayMs = (_matchDurationMs - (sec * 1000L));
			final int announcementSec = sec;
			final String timeStr = sec >= 60 ? (sec / 60) + " minuto(s)" : sec + " segundo(s)";
			
			_countdownTasks.add(ThreadPool.schedule(() ->
			{
				if (_state != State.RUNNING)
					return;
				broadcastEvent("[Event] " + _data.getEventName() + ": " + timeStr + " restantes.");
			}, delayMs));
		}
	}
	
	private void cancelCountdowns()
	{
		for (ScheduledFuture<?> task : _countdownTasks)
		{
			if (task != null && !task.isDone())
				task.cancel(false);
		}
		_countdownTasks.clear();
	}
	
	/**
	 * @return remaining match time in seconds, or 0 if not running.
	 */
	public final int getRemainingMatchTime()
	{
		if (_state != State.RUNNING || _matchDurationMs <= 0 || _matchStartTime <= 0)
			return 0;
		
		final long elapsed = System.currentTimeMillis() - _matchStartTime;
		final long remaining = Math.max(0, _matchDurationMs - elapsed);
		return (int) (remaining / 1000);
	}
	
	/**
	 * @return remaining registration time in seconds, or 0 if not in REGISTER state.
	 */
	public final int getRemainingRegisterTime()
	{
		if (_state != State.REGISTER || _registerStartTime <= 0)
			return 0;
		
		final long registerDurationMs = EventConfig.getRegisterTime() * 60000L;
		final long elapsed = System.currentTimeMillis() - _registerStartTime;
		final long remaining = Math.max(0, registerDurationMs - elapsed);
		return (int) (remaining / 1000);
	}
	
	protected abstract void onStartRegistering();
	protected abstract void onStartMatch();
	protected abstract void onEventKill(EventPlayer killer, EventPlayer victim);
	protected abstract void onEventDie(EventPlayer victim, EventPlayer killer);
	protected abstract void onStop();
	
	protected void startNextRound()
	{
		if (_totalRounds <= 0 || _state != State.RUNNING)
			return;
		
		_currentRound++;
		_roundActive = true;
		
		if (_currentRound > _totalRounds)
		{
			endMatch();
			return;
		}
		
		broadcastEvent("[Event] Ronda " + _currentRound + "/" + _totalRounds + " comienza.");
		
		// Teleport players back to team spawns for new round
		for (EventTeam team : _teams)
		{
			final Location loc = team.getSpawnLocation();
			if (loc == null)
				continue;
			
			final int radius = _data.getPositionRadius();
			for (EventPlayer ep : team.getPlayers())
			{
				if (!ep.isOnline())
					continue;
				
				final Player player = ep.getPlayer();
				
				// Revive dead players
				if (player.isDead())
					player.doRevive();
				
				// Full heal
				player.getStatus().setCpHpMp(player.getStatus().getMaxCp(), player.getStatus().getMaxHp(), player.getStatus().getMaxMp());
				
				// Teleport to spawn
				final int x = loc.getX() + Rnd.get(-radius, radius);
				final int y = loc.getY() + Rnd.get(-radius, radius);
				// Validate the destination is walkable before teleporting
				final Location validatedLoc = GeoEngine.getInstance().getValidLocation(
						loc.getX(), loc.getY(), loc.getZ(), x, y, loc.getZ(), null);
				if (!validatedLoc.equals(new Location(x, y, loc.getZ())))
				{
					LOGGER.info("Event position adjusted for walkability: ({},{},{}) -> ({},{},{})",
							x, y, loc.getZ(),
							validatedLoc.getX(), validatedLoc.getY(), validatedLoc.getZ());
				}
				player.teleportTo(validatedLoc.getX(), validatedLoc.getY(), validatedLoc.getZ(), 0);
				
				// Enable skills
				player.enableAllSkills();
				player.setIsImmobilized(false);
				player.stopAbnormalEffect(AbnormalEffect.HOLD_1);
			}
		}
		
		onRoundStart(_currentRound);
		
		// Schedule round end
		cancelTask(_roundTask);
		_roundTask = ThreadPool.schedule(this::endRound, _roundDurationMs);
	}
	
	protected void endRound()
	{
		if (!_roundActive || _state != State.RUNNING)
			return;
		
		_roundActive = false;
		
		onRoundEnd(_currentRound);
		
		// Check if match should end early (score limit)
		if (checkScoreLimit())
		{
			endMatch();
			return;
		}
		
		// Start next round or end match
		if (_currentRound < _totalRounds)
			startNextRound();
		else
			endMatch();
	}
	
	protected boolean checkScoreLimit()
	{
		return false;
	}
	
	protected void onRoundStart(int round)
	{
	}
	
	protected void onRoundEnd(int round)
	{
	}
}
