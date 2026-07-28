package net.sf.l2j.gameserver.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.l2j.commons.logging.CLogger;

/**
 * EventEngine stub — all events disabled. FactionWar is the only game mode.
 */
public final class EventEngine
{
	private static final CLogger LOGGER = new CLogger(EventEngine.class.getName());
	
	private static final List<AbstractEvent> _events = new ArrayList<>();
	
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
		LOGGER.info("EventEngine DISABLED — FactionWar is the only game mode.");
	}
	
	public boolean isEnabled() { return false; }
	
	public boolean isAnyEventActive() { return false; }
	
	public AbstractEvent getActiveEvent() { return null; }
	
	public AbstractEvent getEvent(int id) { return null; }
	
	public AbstractEvent getEventByName(String name) { return null; }
	
	public List<AbstractEvent> getActiveEvents() { return Collections.emptyList(); }
	
	public List<AbstractEvent> getAllEvents() { return Collections.emptyList(); }
	
	public AbstractEvent getEventForPlayer(int playerId) { return null; }
	
	public boolean isPlayerInAnyEvent(int playerId) { return false; }
	
	public void onPlayerKill(net.sf.l2j.gameserver.model.actor.Player killer, net.sf.l2j.gameserver.model.actor.Player victim)
	{
	}
	
	public void onPlayerDie(net.sf.l2j.gameserver.model.actor.Player victim, net.sf.l2j.gameserver.model.actor.Player killer)
	{
	}
	
	public void reload()
	{
		LOGGER.info("EventEngine reload skipped — disabled.");
	}
	
	public void stopAllEvents()
	{
	}
	
	public void onEventEnded()
	{
	}
}
