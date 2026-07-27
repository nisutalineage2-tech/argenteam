package net.sf.l2j.gameserver.event;

import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;

public class RussianRouletteEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(RussianRouletteEvent.class.getName());
	
	private int _chestCount = 5;
	private int _roundTime = 15;
	
	private final java.util.List<Chest> _currentChests = new java.util.ArrayList<>();
	private final java.util.List<EventPlayer> _eliminated = new java.util.ArrayList<>();
	private int _currentChestIndex = -1; // -1 = picking phase
	private ScheduledFuture<?> _roundTask;
	
	public RussianRouletteEvent(EventConfig.EventData data)
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
				ep.getPlayer().setTitle("[Roulette] Alive");
				ep.getPlayer().broadcastTitleInfo();
				ep.getPlayer().sendMessage("[Roulette] Choose a chest! One will explode!");
			}
		}
		
		startNewRound();
	}
	
	private void startNewRound()
	{
		_currentChests.clear();
		_currentChestIndex = -1;
		
		// Spawn chests in a circle around center
		final Location center = getData().getPositionAll();
		if (center == null) return;
		
		for (int i = 0; i < _chestCount; i++)
		{
			final double angle = (Math.PI * 2 / _chestCount) * i;
			final int x = center.getX() + (int)(Math.cos(angle) * 200);
			final int y = center.getY() + (int)(Math.sin(angle) * 200);
			_currentChests.add(new Chest(i, x, y, center.getZ()));
		}
		
		broadcastToPlayers("[Roulette] Round " + (_eliminated.size() + 1) + "! Choose a chest! (" + _roundTime + "s)");
		
		_roundTask = ThreadPool.schedule(this::endRound, _roundTime * 1000L);
	}
	
	private void endRound()
	{
		if (getState() != State.RUNNING)
			return;
		
		// Pick a random chest to explode
		final int boomIndex = Rnd.get(_currentChests.size());
		final Chest boomChest = _currentChests.get(boomIndex);
		boomChest.setExploded(true);
		
		broadcastToPlayers("[Roulette] Chest " + (boomIndex + 1) + " EXPLODED!");
		
		// Find who chose that chest and eliminate them
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline() || _eliminated.contains(ep))
				continue;
			
			if (ep.getTeamId() == boomIndex)
			{
				// This player chose the exploding chest
				_eliminated.add(ep);
				final Player p = ep.getPlayer();
				p.setIsImmobilized(true);
				p.setIsParalyzed(true);
				p.setTitle("[Roulet] Out R" + (_eliminated.size()));
				p.broadcastTitleInfo();
				p.sendMessage("[Roulette] You chose the wrong chest! You're out!");
				broadcastToPlayers("[Roulette] " + ep.getName() + " was eliminated!");
			}
		}
		
		// Check if only one player remains
		int alive = 0;
		EventPlayer winner = null;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline() || _eliminated.contains(ep))
				continue;
			alive++;
			winner = ep;
		}
		
		if (alive <= 1 && winner != null)
		{
			broadcastToPlayers("[Roulette] " + winner.getName() + " wins Russian Roulette!");
			endMatch();
		}
		else if (alive <= 0)
		{
			broadcastToPlayers("[Roulette] Everyone died! It's a draw!");
			endMatch();
		}
		else
		{
			// Reset team IDs for chest choices
			for (EventPlayer ep : getAllPlayers())
			{
				if (!_eliminated.contains(ep))
					ep.setTeamId(-1); // Reset choice
			}
			
			startNewRound();
		}
	}
	
	// Called when a player chooses a chest (via NPC interaction)
	public void chooseChest(int chestIndex, Player player)
	{
		if (getState() != State.RUNNING)
			return;
		
		final EventPlayer ep = getEventPlayer(player.getObjectId());
		if (ep == null || _eliminated.contains(ep))
			return;
		
		if (chestIndex < 0 || chestIndex >= _currentChests.size())
			return;
		
		ep.setTeamId(chestIndex); // Store chest choice in team ID
		player.sendMessage("[Roulette] You chose Chest " + (chestIndex + 1) + "!");
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
	}
	
	@Override
	protected void onStop()
	{
		cancelTask(_roundTask);
		_currentChests.clear();
		_eliminated.clear();
	}
	
	@Override
	protected String getScorebar()
	{
		int alive = getAllPlayers().size() - _eliminated.size();
		return "[Roulette] Alive: " + alive + " | Round: " + (_eliminated.size() + 1);
	}
	
	public java.util.List<Chest> getCurrentChests() { return _currentChests; }
	
	public static class Chest
	{
		private final int _id, _x, _y, _z;
		private boolean _exploded;
		
		public Chest(int id, int x, int y, int z)
		{
			_id = id; _x = x; _y = y; _z = z;
		}
		
		public int getId() { return _id; }
		public int getX() { return _x; }
		public int getY() { return _y; }
		public int getZ() { return _z; }
		public boolean isExploded() { return _exploded; }
		public void setExploded(boolean b) { _exploded = b; }
	}
}
