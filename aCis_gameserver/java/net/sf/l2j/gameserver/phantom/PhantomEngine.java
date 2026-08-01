package net.sf.l2j.gameserver.phantom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.Config;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.data.xml.NewbieBuffData;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.factionwar.FactionWarRegistry;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
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
		final List<Integer> ids = PhantomConfig.getPhantomIds();
		if (ids.isEmpty())
			return 0;
		
		final List<Integer> snapshot = new ArrayList<>(ids);
		ThreadPool.execute(() ->
		{
			int loaded = 0;
			for (int objectId : snapshot)
			{
				if (load(objectId, gm, PhantomConfig.spawnAtGm() && !PhantomConfig.rememberLastLocation()) != null)
					loaded++;
			}
			LOGGER.info("Background phantom load complete: {}/{} phantoms.", loaded, snapshot.size());
		});
		
		return 0;
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
		
		// If no faction and faction system is enabled, assign a random one
		if (Config.ENABLE_FACTION_SYSTEM && phantom.getFactionId() <= 0)
		{
			final int[] factionIds = FactionData.getInstance().getFactionIds();
			if (factionIds.length > 0)
			{
				final int randomFactionId = factionIds[Rnd.get(factionIds.length)];
				phantom.setFactionId(randomFactionId);
				FactionData.getInstance().storeData(phantom);
				PhantomLog.info("Assigned random faction " + randomFactionId + " to phantom " + phantom.getName() + " (" + objectId + ").");
			}
		}
		
		// Apply faction visuals (name color, title color) ONLY - skip the delayed neutral-zone
		// teleport that onPlayerEnter() schedules for real players, otherwise a freshly
		// spawned/bridged phantom would "disappear" 3s later.
		if (Config.ENABLE_FACTION_SYSTEM)
			FactionData.getInstance().applyFactionVisuals(phantom);
		
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
		
		// If faction war is running, teleport this phantom to the war map
		if (Config.ENABLE_FACTION_SYSTEM && FactionWarManager.getInstance().isRunning())
		{
			final int factionId = phantom.getFactionId();
			if (factionId > 0)
			{
				final Location warSpawn = FactionWarManager.getInstance().getFactionSpawn(factionId);
				if (warSpawn != null)
				{
					phantom.teleportTo(warSpawn, 20);
					if (phantom.isTeleporting())
						phantom.onTeleported();
					PhantomLog.info("Phantom " + phantom.getName() + " teleported to faction war on load.");
				}
			}
		}
		
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
	
	public static int resurrectAll()
	{
		int revived = 0;
		for (Player phantom : ACTIVE_PHANTOMS.values())
		{
			if (phantom == null || !phantom.isOnline())
				continue;
			
			if (phantom.isDead())
			{
				phantom.doRevive();
				phantom.getStatus().setHp(phantom.getStatus().getMaxHp());
				phantom.getStatus().setMp(phantom.getStatus().getMaxMp());
				phantom.broadcastUserInfo();
				PhantomAI.clearDeathFlag(phantom.getObjectId());
				revived++;
			}
		}
		return revived;
	}
	
	public static int deleteAll()
	{
		int deleted = 0;
		for (int objectId : new ArrayList<>(ACTIVE_PHANTOMS.keySet()))
		{
			final Player phantom = ACTIVE_PHANTOMS.remove(objectId);
			if (phantom != null)
			{
				PhantomAI.stop(objectId);
				NEXT_BUFFS.remove(objectId);
				phantom.deleteMe();
				deleted++;
			}
		}
		PhantomAI.stopAll();
		NEXT_BUFFS.clear();
		ACTIVE_PHANTOMS.clear();
		PhantomLog.warn("Admin deleted ALL phantoms: " + deleted + ".");
		return deleted;
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
		return bringFaction(gm, 0);
	}
	
	public static int bringFaction(Player gm, int factionId)
	{
		if (gm == null)
			return 0;
		
		int moved = 0;
		for (Player phantom : ACTIVE_PHANTOMS.values())
		{
			if (phantom == null || !phantom.isOnline() || phantom.isDead())
				continue;
			
			if (factionId > 0 && phantom.getFactionId() != factionId)
				continue;
			
			final int x = gm.getX() + Rnd.get(-180, 180);
			final int y = gm.getY() + Rnd.get(-180, 180);
			phantom.teleportTo(x, y, gm.getZ(), 20);
			
			// Force the teleport to complete for phantom clients (no real client to send Appearing).
			if (phantom.isTeleporting())
				phantom.onTeleported();
			
			// Re-register the region + zones and reset transient stuck/idle tracking so the
			// phantom stays visible and stable at the destination instead of vanishing after a second.
			phantom.revalidateZone(true);
			phantom.broadcastUserInfo();
			PhantomAI.clearStuckState(phantom.getObjectId());
			
			PhantomAI.setHome(phantom);
			phantom.store();
			moved++;
		}
		return moved;
	}
	
	public static int startAi()
	{
		// Resume the runtime AI loop for every active phantom, bypassing the config gate
		// so the admin can force the AI on at any time without a server restart.
		PhantomAI.setAiPaused(false);
		
		int started = 0;
		for (Player phantom : ACTIVE_PHANTOMS.values())
		{
			if (phantom == null)
				continue;
			
			// Only (re)create the task if it isn't already running - otherwise just resume
			// the existing loop via the pause flag (avoids resetting homes/teleporting on
			// every "AI On" click).
			if (!PhantomAI.hasTask(phantom.getObjectId()))
				PhantomAI.start(phantom, true);
			
			started++;
		}
		return started;
	}
	
	public static int stopAi()
	{
		// Pause the runtime AI loop WITHOUT cancelling tasks or wiping homes/targets state,
		// so toggling AI back on resumes exactly where it left off (no desync).
		PhantomAI.setAiPaused(true);
		return ACTIVE_PHANTOMS.size();
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
	
	/**
	 * Teleports all active phantoms with valid faction IDs to the faction war map.
	 * First simulates travel by walking toward the neutral zone and saying war phrases,
	 * then after a short delay teleports them to the war map.
	 */
	public static int teleportPhantomsToWar()
	{
		int moved = 0;
		for (Player phantom : ACTIVE_PHANTOMS.values())
		{
			if (phantom == null || !phantom.isOnline())
				continue;
			
			final int factionId = phantom.getFactionId();
			if (factionId <= 0)
				continue;
			
			final FactionWarManager fwm = FactionWarManager.getInstance();
			if (!fwm.isRunning())
				continue;
			
			final Location spawn = fwm.getFactionSpawn(factionId);
			if (spawn == null)
				continue;
			
			// Revive dead phantoms so they can participate in war
			if (phantom.isDead())
			{
				phantom.doRevive();
				phantom.getStatus().setHp(phantom.getStatus().getMaxHp());
				phantom.getStatus().setMp(phantom.getStatus().getMaxMp());
				PhantomAI.clearDeathFlag(phantom.getObjectId());
			}
			
			// Say a war battle phrase before traveling
			PhantomSocial.sayWarPhrase(phantom);
			
			// Walk toward the neutral zone to simulate traveling to war
			final Location neutralLoc = net.sf.l2j.gameserver.factionwar.FactionWarConfig.getNeutralSpawnLoc();
			if (neutralLoc != null && phantom.distance3D(neutralLoc) > 300)
			{
				final int walkX = neutralLoc.getX() + Rnd.get(-200, 200);
				final int walkY = neutralLoc.getY() + Rnd.get(-200, 200);
				phantom.getAI().tryToMoveTo(new Location(walkX, walkY, neutralLoc.getZ()), null);
			}
			
			// Schedule delayed teleport to war map (simulates travel time)
			final int fId = factionId;
			final Location warSpawn = spawn;
			final Player p = phantom;
			final boolean alreadyMoving = neutralLoc != null && p.distance3D(neutralLoc) > 300;
			final int delay = alreadyMoving ? Rnd.get(4000, 10000) : Rnd.get(2000, 5000);
			
			ThreadPool.schedule(() ->
			{
				try
				{
					if (p == null || !p.isOnline())
						return;
					
					// Don't teleport if war ended while walking
					if (!FactionWarManager.getInstance().isRunning())
						return;
					
					// Stop any movement and teleport
					p.getMove().stop();
					
					final int rx = warSpawn.getX() + Rnd.get(-250, 250);
					final int ry = warSpawn.getY() + Rnd.get(-250, 250);
					p.teleportTo(rx, ry, warSpawn.getZ(), 20);
					if (p.isTeleporting())
						p.onTeleported();
					
					FactionWarRegistry.getInstance().register(p);
					PhantomAI.setHome(p);
					
					// Say arrival phrase
					PhantomSocial.sayWarPhrase(p);
				}
				catch (Exception e)
				{
					PhantomLog.warn("War teleport failed for " + (p == null ? "null" : p.getName()) + ": " + e.getMessage());
				}
			}, delay);
			
			moved++;
		}
		
		// Auto-party phantoms by faction after teleporting to war
		if (moved > 1)
		{
			ThreadPool.schedule(PhantomAI::ensureFactionParties, 12000);
		}
		
		return moved;
	}
	
	/**
	 * Returns all phantoms from the war map to their faction home locations.
	 * Dead phantoms are revived first.
	 */
	public static int returnPhantomsFromWar()
	{
		int moved = 0;
		for (Player phantom : ACTIVE_PHANTOMS.values())
		{
			if (phantom == null || !phantom.isOnline())
				continue;
			
			final int factionId = phantom.getFactionId();
			if (factionId <= 0)
				continue;
			
			final Faction faction = FactionData.getInstance().getFaction(factionId);
			if (faction == null || faction.getHomeLocation() == null)
				continue;
			
			// Revive dead phantoms before returning them
			if (phantom.isDead())
			{
				phantom.doRevive();
				phantom.getStatus().setHp(phantom.getStatus().getMaxHp());
				phantom.getStatus().setMp(phantom.getStatus().getMaxMp());
				PhantomAI.clearDeathFlag(phantom.getObjectId());
			}
			
			phantom.teleportTo(faction.getHomeLocation(), 20);
			if (phantom.isTeleporting())
				phantom.onTeleported();
			
			PhantomAI.setHome(phantom);
			moved++;
		}
		return moved;
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
