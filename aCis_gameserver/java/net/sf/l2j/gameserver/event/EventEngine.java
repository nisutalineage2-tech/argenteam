package net.sf.l2j.gameserver.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.Config;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.model.actor.Player;

/**
 * EventEngine — always active scheduler that alternates between Faction War and events.
 * The system runs permanently: Faction War → Event → Faction War → Event, etc.
 * 
 * Alternance logic:
 * - onFactionWarEnded() → sets flag so next is an event
 * - onEventEnded() → sets flag so next is Faction War (if enabled)
 * - Scheduler runs every 10 seconds; when nothing is active it starts the appropriate mode.
 */
public final class EventEngine
{
	private static final CLogger LOGGER = new CLogger(EventEngine.class.getName());
	
	private final List<AbstractEvent> _events = new ArrayList<>();
	private ScheduledFuture<?> _schedulerTask;
	private boolean _initialized;
	
	/**
	 * Alternance flag: true → Faction War should start next (if enabled).
	 * false → an event should start next.
	 */
	private volatile boolean _alternanceExpectsFw = true;
	
	private EventEngine()
	{
	}
	
	private static class SingletonHolder
	{
		protected static final EventEngine INSTANCE = new EventEngine();
	}
	
	public static EventEngine getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	public void init()
	{
		_alternanceExpectsFw = true;
		_events.clear();
		
		for (EventConfig.EventData data : EventConfig.getEvents())
		{
			if (!data.isEnabled())
				continue;
			
			final AbstractEvent event = createEvent(data);
			if (event != null)
			{
				_events.add(event);
				LOGGER.info("Registered event: {} (id={}).", data.getEventName(), data.getId());
			}
		}
		
		_initialized = true;
		LOGGER.info("EventEngine initialized with {} events. Alternance: FW ↔ Events.", _events.size());
		
		// Start alternance scheduler
		startScheduler();
	}
	
	private AbstractEvent createEvent(EventConfig.EventData data)
	{
		switch (data.getId())
		{
			case 1: return new TvTEvent(data);
			case 2: return new DMEvent(data);
			case 3: return new CTFEvent(data);
			case 4: return new BattlefieldEvent(data);
			case 5: return new BombFightEvent(data);
			case 6: return new LMSEvent(data);
			case 7: return new LuckyChestsEvent(data);
			case 8: return new DominationEvent(data);
			case 9: return new DoubleDominationEvent(data);
			case 10: return new MutantEvent(data);
			case 11: return new RussianRouletteEvent(data);
			case 12: return new SimonSaysEvent(data);
			case 13: return new ZombieEvent(data);
			case 14: return new HuntingGroundsEvent(data);
			case 15: return new KoreanTvTEvent(data);
			case 16: return new RaidInTheMiddleEvent(data);
			case 17: return new TreasureHuntEvent(data);
			default:
				LOGGER.warn("Unknown event type: {}. Skipping.", data.getId());
				return null;
		}
	}
	
	/**
	 * Scheduler runs every 10 seconds and alternates between FW and events.
	 * - If anything is running → skip
	 * - If FW expected (Flag true) and FW enabled → start FW
	 * - If event expected (Flag false) → start first idle event
	 * - Fallback: if all events exhausted, start FW (if enabled) or cycle again
	 */
	private void startScheduler()
	{
		_schedulerTask = ThreadPool.scheduleAtFixedRate(this::runScheduler, 15000, 10000);
		LOGGER.info("Alternance scheduler started (10-second interval): FW ↔ Events.");
	}
	
	private void runScheduler()
	{
		try
		{
			// If anything is already running (FW or event), do nothing
			if (isAnyEventActive())
				return;
			
			if (FactionWarManager.getInstance().isRunning())
				return;
			
			// Nothing running — decide what to start based on alternance flag
			if (Config.ENABLE_FACTION_SYSTEM && FactionWarConfig.isEnabled() && _alternanceExpectsFw)
			{
				LOGGER.info("Alternance: starting Faction War vote phase (next: events).");
				FactionWarManager.getInstance().startVotePhase();
				return;
			}
			
			// Find an idle event to start
			for (AbstractEvent event : _events)
			{
				if (event.getState() == AbstractEvent.State.IDLE)
				{
					LOGGER.info("Alternance: starting event: {} (next: FW).", event.getData().getEventName());
					event.startRegistering();
					return;
				}
			}
			
			// All events exhausted — if FW is enabled, start vote phase; otherwise restart cycle
			if (Config.ENABLE_FACTION_SYSTEM && FactionWarConfig.isEnabled())
			{
				LOGGER.info("Alternance: events exhausted, starting Faction War vote phase.");
				FactionWarManager.getInstance().startVotePhase();
			}
			else
			{
				// FW not enabled — wait for events to become IDLE again and restart cycle
				LOGGER.debug("Alternance: nothing to start. Waiting for events to reset.");
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Error in alternance scheduler.", e);
		}
	}
	
	/**
	 * Called when a Faction War ends. Sets flag so the next mode is an event.
	 */
	public void onFactionWarEnded()
	{
		_alternanceExpectsFw = false;
		LOGGER.debug("Alternance: FW ended, expecting event next.");
	}
	
	/**
	 * Called when an event ends (via endMatch() or stop()).
	 * Sets flag so the next mode is Faction War (if enabled).
	 */
	public void onEventEnded()
	{
		_alternanceExpectsFw = true;
		LOGGER.debug("Alternance: event ended, expecting FW next.");
	}
	
	public boolean isInitialized() { return _initialized; }
	
	public boolean isAnyEventActive()
	{
		for (AbstractEvent event : _events)
		{
			final AbstractEvent.State state = event.getState();
			if (state == AbstractEvent.State.REGISTER || state == AbstractEvent.State.STARTING || state == AbstractEvent.State.RUNNING)
				return true;
		}
		return false;
	}
	
	public AbstractEvent getActiveEvent()
	{
		for (AbstractEvent event : _events)
		{
			final AbstractEvent.State state = event.getState();
			if (state == AbstractEvent.State.REGISTER || state == AbstractEvent.State.STARTING || state == AbstractEvent.State.RUNNING)
				return event;
		}
		return null;
	}
	
	public AbstractEvent getEvent(int id)
	{
		for (AbstractEvent event : _events)
		{
			if (event.getData().getId() == id)
				return event;
		}
		return null;
	}
	
	public AbstractEvent getEventByName(String name)
	{
		for (AbstractEvent event : _events)
		{
			if (event.getData().getShortName().equalsIgnoreCase(name))
				return event;
		}
		return null;
	}
	
	public List<AbstractEvent> getActiveEvents()
	{
		final List<AbstractEvent> active = new ArrayList<>();
		for (AbstractEvent event : _events)
		{
			if (event.getState() != AbstractEvent.State.IDLE)
				active.add(event);
		}
		return active;
	}
	
	public List<AbstractEvent> getAllEvents()
	{
		return new ArrayList<>(_events);
	}
	
	public AbstractEvent getEventForPlayer(int playerId)
	{
		for (AbstractEvent event : _events)
		{
			if (event.isParticipating(playerId))
				return event;
		}
		return null;
	}
	
	public boolean isPlayerInAnyEvent(int playerId)
	{
		return getEventForPlayer(playerId) != null;
	}
	
	public void onPlayerKill(Player killer, Player victim)
	{
		for (AbstractEvent event : _events)
		{
			if (event.getState() == AbstractEvent.State.RUNNING && event.isParticipating(victim.getObjectId()))
			{
				event.onKill(killer.getObjectId(), victim.getObjectId());
				break;
			}
		}
	}
	
	public void onPlayerDie(Player victim, Player killer)
	{
		for (AbstractEvent event : _events)
		{
			if (event.getState() == AbstractEvent.State.RUNNING && event.isParticipating(victim.getObjectId()))
			{
				event.onDie(victim.getObjectId(), killer != null ? killer.getObjectId() : -1);
				break;
			}
		}
	}
	
	public void reload()
	{
		if (_schedulerTask != null && !_schedulerTask.isDone())
			_schedulerTask.cancel(false);
		
		for (AbstractEvent event : _events)
		{
			if (event.getState() != AbstractEvent.State.IDLE)
				event.stop();
		}
		
		EventConfig.load();
		init();
	}
	
	public void stopAllEvents()
	{
		for (AbstractEvent event : _events)
		{
			if (event.getState() != AbstractEvent.State.IDLE)
				event.stop();
		}
	}
}
