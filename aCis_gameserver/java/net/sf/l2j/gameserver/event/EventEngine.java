package net.sf.l2j.gameserver.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.model.actor.Player;

public final class EventEngine
{
	private static final CLogger LOGGER = new CLogger(EventEngine.class.getName());
	
	private static final List<AbstractEvent> _events = new ArrayList<>();
	private ScheduledFuture<?> _schedulerTask;
	private boolean _enabled;
	
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
		_enabled = EventConfig.isEnabled();
		if (!_enabled)
		{
			LOGGER.info("EventEngine disabled by config.");
			return;
		}
		
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
		
		if (EventConfig.isSchedulerEnabled() && !_events.isEmpty())
		{
			startScheduler();
		}
		
		LOGGER.info("EventEngine initialized with {} events.", _events.size());
	}
	
	private AbstractEvent createEvent(EventConfig.EventData data)
	{
		switch (data.getId())
		{
			case 1: return new TvTEvent(data);
			case 2: return new DMEvent(data);
			case 3: return new CTFEvent(data);
			default:
				LOGGER.warn("Unknown event type: {}. Skipping.", data.getId());
				return null;
		}
	}
	
	private void startScheduler()
	{
		_schedulerTask = ThreadPool.scheduleAtFixedRate(this::runScheduler, 60000, 60000);
		LOGGER.info("Event scheduler started (1-minute interval).");
	}
	
	private void runScheduler()
	{
		try
		{
			for (AbstractEvent event : _events)
			{
				if (event.getState() == AbstractEvent.State.IDLE)
				{
					LOGGER.info("Auto-starting event: {}", event.getData().getEventName());
					event.startRegistering();
					break;
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Error in event scheduler.", e);
		}
	}
	
	public boolean isEnabled() { return _enabled; }
	
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
}
