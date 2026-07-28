package net.sf.l2j.gameserver.event;

import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;

public class TreasureHuntEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(TreasureHuntEvent.class.getName());
	
	private int _chestInterval = 20;
	private int _totalChests = 10;
	private int _spawnRadius = 500;
	private int _chestRewardId = 57;
	private int _chestRewardCount = 500;
	
	private final java.util.List<TreasureChest> _chests = new java.util.ArrayList<>();
	private int _chestsSpawned;
	private int _chestsFound;
	private EventPlayer _firstFinder;
	private ScheduledFuture<?> _spawnTask;
	
	public TreasureHuntEvent(EventConfig.EventData data)
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
				ep.getPlayer().setTitle("[Find] 0fd");
				ep.getPlayer().broadcastTitleInfo();
				ep.getPlayer().sendMessage("[Treasure] Find treasure chests hidden in the ruins! First finder wins big!");
			}
		}
		
		_chestsSpawned = 0;
		_chestsFound = 0;
		_firstFinder = null;
		
		_spawnTask = ThreadPool.scheduleAtFixedRate(this::spawnTreasure, 5000, _chestInterval * 1000L);
		
		for (int i = 0; i < 3 && _chestsSpawned < _totalChests; i++)
			spawnTreasure();
	}
	
	private void spawnTreasure()
	{
		if (getState() != State.RUNNING)
			return;
		
		if (_chestsSpawned >= _totalChests)
		{
			cancelTask(_spawnTask);
			return;
		}
		
		final Location center = getData().getPositionAll();
		if (center == null) return;
		
		final int x = center.getX() + Rnd.get(-_spawnRadius, _spawnRadius);
		final int y = center.getY() + Rnd.get(-_spawnRadius, _spawnRadius);
		
		_chests.add(new TreasureChest(x, y, center.getZ()));
		_chestsSpawned++;
		broadcastToPlayers("[Treasure] A treasure chest appeared! (" + _chestsSpawned + "/" + _totalChests + ")");
	}
	
	public void findTreasure(int chestIndex, Player player)
	{
		if (getState() != State.RUNNING)
			return;
		
		if (chestIndex < 0 || chestIndex >= _chests.size())
			return;
		
		final TreasureChest chest = _chests.get(chestIndex);
		if (chest.isFound())
			return;
		
		chest.setFound(true);
		
		final EventPlayer ep = getEventPlayer(player.getObjectId());
		if (ep == null) return;
		
		ep.addKill();
		player.getInventory().addItem(_chestRewardId, _chestRewardCount);
		
		if (_firstFinder == null)
		{
			_firstFinder = ep;
			player.sendMessage("[Treasure] YOU found the first treasure! Bonus reward at the end!");
			broadcastToPlayers("[Treasure] " + ep.getName() + " found the first treasure!");
		}
		else
		{
			player.sendMessage("[Treasure] You found a treasure! +" + _chestRewardCount + " reward!");
			broadcastToPlayers("[Treasure] " + ep.getName() + " found a treasure! (" + ep.getKills() + " total)");
		}
		
		_chestsFound++;
		_chests.remove(chestIndex);
		
		if (ep.isOnline())
		{
			ep.getPlayer().setTitle("[Find] " + ep.getKills() + "fd");
			ep.getPlayer().broadcastTitleInfo();
		}
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		player.sendMessage("[Treasure] You died! Respawning in " + getData().getRespawnDelay() + " seconds...");
		
		player.disableAllSkills();
		player.setIsImmobilized(true);
		player.startAbnormalEffect(AbnormalEffect.HOLD_1);
		
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
			
			final Location center = getData().getPositionAll();
			if (center != null)
				player.teleportTo(center.getX(), center.getY(), center.getZ(), 0);
		}, getData().getRespawnDelay() * 1000L);
	}
	
	@Override
	protected void onStop()
	{
		cancelTask(_spawnTask);
		
		if (_firstFinder != null && _firstFinder.isOnline())
		{
			_firstFinder.getPlayer().getInventory().addItem(_chestRewardId, _chestRewardCount * 2);
			_firstFinder.getPlayer().sendMessage("[Treasure] Bonus reward for being the first finder!");
		}
		
		_chests.clear();
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
		return "[Treasure] Leader: " + (top != null ? top.getName() + " (" + top.getKills() + ")" : "-") + " | Chests: " + _chestsFound + "/" + _chestsSpawned;
	}
	
	public java.util.List<TreasureChest> getChests() { return _chests; }
	
	public static class TreasureChest
	{
		private final int _x, _y, _z;
		private boolean _found;
		
		public TreasureChest(int x, int y, int z)
		{
			_x = x; _y = y; _z = z;
		}
		
		public int getX() { return _x; }
		public int getY() { return _y; }
		public int getZ() { return _z; }
		public boolean isFound() { return _found; }
		public void setFound(boolean b) { _found = b; }
	}
}
