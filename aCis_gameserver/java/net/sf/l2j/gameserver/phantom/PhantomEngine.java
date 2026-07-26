package net.sf.l2j.gameserver.phantom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.data.xml.NewbieBuffData;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.records.NewbieBuff;
import net.sf.l2j.gameserver.scripting.Quest;

public final class PhantomEngine
{
	private static final CLogger LOGGER = new CLogger(PhantomEngine.class.getName());
	private static final Map<Integer, Player> ACTIVE_PHANTOMS = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> NEXT_BUFFS = new ConcurrentHashMap<>();
	
	private PhantomEngine()
	{
	}
	
	public static int startConfigured(Player gm)
	{
		PhantomConfig.load();
		
		int loaded = 0;
		for (int objectId : PhantomConfig.getPhantomIds())
		{
			if (load(objectId, gm, PhantomConfig.spawnAtGm() && !PhantomConfig.rememberLastLocation()) != null)
				loaded++;
		}
		return loaded;
	}
	
	public static Player load(int objectId, Player gm, boolean spawnAtGm)
	{
		final Player active = ACTIVE_PHANTOMS.get(objectId);
		if (active != null)
			return active;
		
		final Player online = World.getInstance().getPlayer(objectId);
		if (online != null)
		{
			LOGGER.warn("Refused to load phantom {} because the player is already online.", objectId);
			PhantomLog.warn("Refused to load phantom " + objectId + " because the player is already online.");
			return null;
		}
		
		final Player phantom = Player.restore(objectId);
		if (phantom == null)
		{
			LOGGER.warn("Couldn't restore phantom character {}.", objectId);
			PhantomLog.warn("Couldn't restore phantom character " + objectId + ".");
			return null;
		}
		
		phantom.setOnlineStatus(true, true);
		phantom.setRunning(true);
		phantom.setStanding(true);
		
		if (spawnAtGm && gm != null)
		{
			final int x = gm.getX() + Rnd.get(-120, 120);
			final int y = gm.getY() + Rnd.get(-120, 120);
			phantom.spawnMe(x, y, gm.getZ(), gm.getHeading());
		}
		else
		{
			phantom.spawnMe();
		}
		
		applyStartupFeatures(phantom);
		phantom.broadcastUserInfo();
		ACTIVE_PHANTOMS.put(objectId, phantom);
		PhantomState.register(objectId);
		PhantomAI.start(phantom);
		LOGGER.info("Loaded phantom {} ({}) at {}, {}, {}.", phantom.getName(), objectId, phantom.getX(), phantom.getY(), phantom.getZ());
		PhantomLog.info("Loaded phantom " + phantom.getName() + " (" + objectId + ") at " + phantom.getX() + "," + phantom.getY() + "," + phantom.getZ() + ".");
		return phantom;
	}
	
	private static void applyStartupFeatures(Player phantom)
	{
		if (phantom == null)
			return;
		
		if (PhantomConfig.autoEquip())
			PhantomEquipment.equip(phantom);
		
		if (PhantomConfig.autoBuff())
		{
			applyNewbieBuffs(phantom);
			NEXT_BUFFS.put(phantom.getObjectId(), System.currentTimeMillis() + PhantomConfig.autoBuffIntervalMs());
		}
	}
	
	public static void applyTimedBuffs(Player phantom)
	{
		if (phantom == null || !PhantomConfig.autoBuff() || phantom.isDead())
			return;
		
		final long now = System.currentTimeMillis();
		final long next = NEXT_BUFFS.getOrDefault(phantom.getObjectId(), 0L);
		if (now < next)
			return;
		
		applyNewbieBuffs(phantom);
		NEXT_BUFFS.put(phantom.getObjectId(), now + PhantomConfig.autoBuffIntervalMs());
		PhantomLog.info("AutoBuff applied to " + phantom.getName() + ".");
	}
	
	private static void applyNewbieBuffs(Player phantom)
	{
		if (phantom.isCursedWeaponEquipped())
			return;
		
		final boolean isMage = phantom.isMageClass() && phantom.getClassId() != ClassId.ORC_MYSTIC && phantom.getClassId() != ClassId.ORC_SHAMAN;
		final int level = phantom.getStatus().getLevel();
		
		for (NewbieBuff buff : NewbieBuffData.getInstance().getValidBuffs(isMage, level))
			Quest.callSkill(phantom, phantom, buff.getSkill());
	}
	
	public static int stopAll()
	{
		int stopped = 0;
		for (Player phantom : ACTIVE_PHANTOMS.values())
		{
			if (phantom != null)
			{
				PhantomAI.stop(phantom.getObjectId());
				NEXT_BUFFS.remove(phantom.getObjectId());
				phantom.deleteMe();
				stopped++;
			}
		}
		ACTIVE_PHANTOMS.clear();
		NEXT_BUFFS.clear();
		PhantomAI.stopAll();
		return stopped;
	}
	
	public static boolean stop(int objectId)
	{
		final Player phantom = ACTIVE_PHANTOMS.remove(objectId);
		if (phantom == null)
			return false;
		
		PhantomAI.stop(objectId);
		NEXT_BUFFS.remove(objectId);
		phantom.deleteMe();
		return true;
	}
	
	public static boolean kill(int objectId)
	{
		final Player phantom = ACTIVE_PHANTOMS.get(objectId);
		if (phantom == null)
			return false;
		
		PhantomLog.warn("Admin killed phantom " + phantom.getName() + " (" + objectId + ")");
		phantom.doDie(null);
		return true;
	}
	
	public static boolean deleteConfigured(int objectId)
	{
		final Player phantom = ACTIVE_PHANTOMS.remove(objectId);
		if (phantom != null)
		{
			PhantomAI.stop(objectId);
			NEXT_BUFFS.remove(objectId);
			phantom.deleteMe();
		}
		
		final boolean removed = PhantomConfig.removePhantomId(objectId);
		PhantomLog.warn("Admin deleted phantom config id " + objectId + ", active=" + (phantom != null) + ", removedId=" + removed);
		return phantom != null || removed;
	}
	
	public static List<Player> getActivePhantomsSorted()
	{
		final List<Player> phantoms = new ArrayList<>(ACTIVE_PHANTOMS.values());
		phantoms.removeIf(phantom -> phantom == null);
		phantoms.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
		return phantoms;
	}
	
	public static int bringAll(Player gm)
	{
		if (gm == null)
			return 0;
		
		int moved = 0;
		for (Player phantom : ACTIVE_PHANTOMS.values())
		{
			if (phantom == null || !phantom.isOnline() || phantom.isDead())
				continue;
			
			final int x = gm.getX() + Rnd.get(-180, 180);
			final int y = gm.getY() + Rnd.get(-180, 180);
			phantom.teleportTo(x, y, gm.getZ(), 20);
			PhantomAI.setHome(phantom);
			phantom.store();
			moved++;
		}
		return moved;
	}
		public static int startAi()
	{
		int started = 0;
		for (Player phantom : ACTIVE_PHANTOMS.values())
		{
			if (phantom != null)
			{
				PhantomAI.start(phantom);
				started++;
			}
		}
		return started;
	}
	
	public static int stopAi()
	{
		final int stopped = ACTIVE_PHANTOMS.size();
		PhantomAI.stopAll();
		return stopped;
	}
	
	public static int setHomes()
	{
		int updated = 0;
		for (Player phantom : ACTIVE_PHANTOMS.values())
		{
			if (phantom != null)
			{
				PhantomAI.setHome(phantom);
				updated++;
			}
		}
		return updated;
	}
	
	public static int size()
	{
		return ACTIVE_PHANTOMS.size();
	}
	
	public static boolean isPhantom(int objectId)
	{
		return ACTIVE_PHANTOMS.containsKey(objectId);
	}
	
	public static Player getActivePhantom(int objectId)
	{
		return ACTIVE_PHANTOMS.get(objectId);
	}

	public static Player getActivePhantom(String name)
	{
		if (name == null || name.isBlank())
			return null;
		
		for (Player phantom : ACTIVE_PHANTOMS.values())
		{
			if (phantom != null && phantom.getName().equalsIgnoreCase(name))
				return phantom;
		}
		return null;
	}
		public static Collection<Player> getActivePhantoms()
	{
		return ACTIVE_PHANTOMS.values();
	}
}
