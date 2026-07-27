package net.sf.l2j.gameserver.event;

import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;

public class LuckyChestsEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(LuckyChestsEvent.class.getName());
	
	// Chest NPC template ID - a visible chest NPC
	private static final int CHEST_NPC_ID = 90010;
	
	private int _maxChests = 5;
	private int _chestInterval = 15;
	private int _explodeChance = 30;
	private int _chestRewardId = 57;
	private int _chestRewardCount = 100;
	
	private final java.util.List<Chest> _chests = new java.util.ArrayList<>();
	private ScheduledFuture<?> _spawnTask;
	
	public LuckyChestsEvent(EventConfig.EventData data)
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
				ep.getPlayer().setTitle("[Chests] " + ep.getKills() + " opened");
				ep.getPlayer().broadcastTitleInfo();
				ep.getPlayer().sendMessage("[Chests] Find and open chests for rewards! But beware of exploding ones!");
			}
		}
		
		// Start spawning chests
		_spawnTask = ThreadPool.scheduleAtFixedRate(this::spawnChest, 5000, _chestInterval * 1000L);
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
		// No PvP scoring - chests are what matter
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		player.sendMessage("[Chests] You died! Respawning...");
		player.teleportTo(getData().getPositionAll().getX(), getData().getPositionAll().getY(), getData().getPositionAll().getZ(), 0);
	}
	
	@Override
	protected void onStop()
	{
		cancelTask(_spawnTask);
		_chests.clear();
	}
	
	private void spawnChest()
	{
		if (getState() != State.RUNNING)
			return;
		
		if (_chests.size() >= _maxChests)
			return;
		
		// Spawn at random position within 500 radius of center
		final Location center = getData().getPositionAll();
		if (center == null)
			return;
		
		final int x = center.getX() + Rnd.get(-500, 500);
		final int y = center.getY() + Rnd.get(-500, 500);
		
		_chests.add(new Chest(x, y, center.getZ()));
		broadcastToPlayers("[Chests] A chest appeared! Go find it!");
	}
	
	// Called when a player clicks/interacts with a chest NPC
	public void openChest(int chestIndex, Player player)
	{
		if (getState() != State.RUNNING)
			return;
		
		if (chestIndex < 0 || chestIndex >= _chests.size())
			return;
		
		final Chest chest = _chests.get(chestIndex);
		if (chest.isOpened())
			return;
		
		chest.setOpened(true);
		
		final EventPlayer ep = getEventPlayer(player.getObjectId());
		if (ep == null)
			return;
		
		// Random explode or reward
		if (Rnd.get(100) < _explodeChance)
		{
			// BOOM!
			player.doDie(player);
			broadcastToPlayers("[Chests] " + ep.getName() + " opened a bomb chest and died!");
		}
		else
		{
			// Reward!
			ep.addKill(); // Track chests opened
			player.getInventory().addItem(_chestRewardId, _chestRewardCount);
			if (ep.isOnline())
			{
				ep.getPlayer().setTitle("[Chests] " + ep.getKills() + " opened");
				ep.getPlayer().broadcastTitleInfo();
			}
			player.sendMessage("[Chests] You got " + _chestRewardCount + " rewards from the chest!");
			broadcastToPlayers("[Chests] " + ep.getName() + " opened a chest! (" + ep.getKills() + " total)");
		}
		
		_chests.remove(chestIndex);
	}
	
	@Override
	protected String getScorebar()
	{
		EventPlayer top = null;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			if (top == null || ep.getKills() > top.getKills())
				top = ep;
		}
		return "[Chests] Leader: " + (top != null ? top.getName() + " (" + top.getKills() + ")" : "-") + " | Chests: " + _chests.size();
	}
	
	public java.util.List<Chest> getChests() { return _chests; }
	
	public static class Chest
	{
		private final int _x, _y, _z;
		private boolean _opened;
		
		public Chest(int x, int y, int z)
		{
			_x = x; _y = y; _z = z;
		}
		
		public int getX() { return _x; }
		public int getY() { return _y; }
		public int getZ() { return _z; }
		public boolean isOpened() { return _opened; }
		public void setOpened(boolean b) { _opened = b; }
	}
}
