package net.sf.l2j.gameserver.event;

import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;

public class LuckyChestsEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(LuckyChestsEvent.class.getName());
	
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
				ep.getPlayer().setTitle("[Chest] " + ep.getKills() + " abiertos");
				ep.getPlayer().broadcastTitleInfo();
				ep.getPlayer().sendMessage("[Chests] Encuentra y abre cofres para obtener recompensas. Pero cuidado con los que explotan.");
			}
		}
		
		_spawnTask = ThreadPool.scheduleAtFixedRate(this::spawnChest, 5000, _chestInterval * 1000L);
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
		player.sendMessage("[Chests] Moriste. Reviviendo en " + getData().getRespawnDelay() + " segundos...");
		
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
		_chests.clear();
	}
	
	private void spawnChest()
	{
		if (getState() != State.RUNNING)
			return;
		
		if (_chests.size() >= _maxChests)
			return;
		
		final Location center = getData().getPositionAll();
		if (center == null)
			return;
		
		final int x = center.getX() + Rnd.get(-500, 500);
		final int y = center.getY() + Rnd.get(-500, 500);
		
		_chests.add(new Chest(x, y, center.getZ()));
		broadcastToPlayers("[Chests] Aparecio un cofre. Ve a buscarlo.");
	}
	
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
		
		if (Rnd.get(100) < _explodeChance)
		{
			player.doDie(player);
			broadcastToPlayers("[Chests] " + ep.getName() + " abrio un cofre bomba y murio.");
		}
		else
		{
			ep.addKill();
			player.getInventory().addItem(_chestRewardId, _chestRewardCount);
			if (ep.isOnline())
			{
				ep.getPlayer().setTitle("[Chest] " + ep.getKills() + " abiertos");
				ep.getPlayer().broadcastTitleInfo();
			}
			player.sendMessage("[Chests] Obtuviste " + _chestRewardCount + " recompensas del cofre.");
			broadcastToPlayers("[Chests] " + ep.getName() + " abrio un cofre. (" + ep.getKills() + " en total)");
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
		return "[Chests] Lider: " + (top != null ? top.getName() + " (" + top.getKills() + ")" : "-") + " | Cofres: " + _chests.size();
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
