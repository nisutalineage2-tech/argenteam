package net.sf.l2j.gameserver.phantom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.Config;
import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.data.manager.SpawnManager;
import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.data.xml.RestartPointData;
import net.sf.l2j.gameserver.enums.RestartType;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.enums.actors.OperateType;
import net.sf.l2j.gameserver.enums.skills.SkillType;	import net.sf.l2j.gameserver.event.AbstractEvent;
	import net.sf.l2j.gameserver.event.EventEngine;
	import net.sf.l2j.gameserver.event.EventPlayer;
	import net.sf.l2j.gameserver.event.EventTeam;
	import net.sf.l2j.gameserver.event.LuckyChestsEvent;
	import net.sf.l2j.gameserver.event.RaidInTheMiddleEvent;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.factionwar.FactionWarRegistry;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.FactionWarCpFlag;
import net.sf.l2j.gameserver.model.actor.instance.FactionWarFlag;
import net.sf.l2j.gameserver.model.actor.instance.FactionWarGuard;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.spawn.MultiSpawn;
import net.sf.l2j.gameserver.model.spawn.NpcMaker;
import net.sf.l2j.gameserver.skills.L2Skill;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.Monster;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.spawn.MultiSpawn;
import net.sf.l2j.gameserver.model.spawn.NpcMaker;
import net.sf.l2j.gameserver.skills.L2Skill;

public final class PhantomAI
{
	private static final CLogger LOGGER = new CLogger(PhantomAI.class.getName());
	private static final Map<Integer, ScheduledFuture<?>> TASKS = new ConcurrentHashMap<>();
	private static final Map<Integer, Location> HOMES = new ConcurrentHashMap<>();
	private static final Map<Integer, Location> PATROL_POINTS = new ConcurrentHashMap<>();
	private static final Map<Integer, Location> LAST_TARGETS = new ConcurrentHashMap<>();
	private static final Map<Integer, String> LAST_ACTIONS = new ConcurrentHashMap<>();
	private static final Map<Integer, Boolean> DEATH_HANDLING = new ConcurrentHashMap<>();
	private static final Map<Integer, TargetClaim> TARGET_CLAIMS = new ConcurrentHashMap<>();
	private static final Map<Integer, LootMemory> LAST_LOOT_AREAS = new ConcurrentHashMap<>();
	private static final Map<Integer, ItemClaim> ITEM_CLAIMS = new ConcurrentHashMap<>();
	private static final Map<Integer, Location> LAST_POSITIONS = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> STUCK_TICKS = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> STUCK_ESCAPES = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> IDLE_STUCK_TICKS = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> FARM_ZONE_BUCKETS = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> FARM_ZONE_READY_TIMES = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> WAR_LOG_TIMES = new ConcurrentHashMap<>();
	private static final Map<Integer, Long> WAR_STRAFE_TIMES = new ConcurrentHashMap<>();
	
	/** Runtime AI pause flag (admin panel "AI On/Off"). When true, all phantom AI loops are suspended without cancelling tasks or wiping state. */
	private static volatile boolean AI_PAUSED;
	
	private PhantomAI()
	{
	}
	
	public static void setAiPaused(boolean paused)
	{
		AI_PAUSED = paused;
	}
	
	public static boolean isAiPaused()
	{
		return AI_PAUSED;
	}
	
	public static boolean hasTask(int objectId)
	{
		return TASKS.containsKey(objectId);
	}
	
	public static void start(Player phantom)
	{
		start(phantom, false);
	}
	
	/**
	 * Starts the AI loop for the given phantom.
	 * @param phantom : The phantom to start.
	 * @param force : If true, bypasses the {@link PhantomConfig#aiEnabled()} gate (used by the admin panel "AI On" button).
	 */
	public static void start(Player phantom, boolean force)
	{
		if (phantom == null)
			return;
		
		if (!force && !PhantomConfig.aiEnabled())
			return;
		
		stop(phantom.getObjectId());
		if (PhantomConfig.initialLevelZoneTeleport())
			maybeRelocateToLevelZone(phantom, true);
		HOMES.put(phantom.getObjectId(), new Location(phantom.getX(), phantom.getY(), phantom.getZ()));
		PATROL_POINTS.put(phantom.getObjectId(), nextPatrolPoint(phantom));
		LAST_ACTIONS.put(phantom.getObjectId(), "Started");
		FARM_ZONE_READY_TIMES.put(phantom.getObjectId(), System.currentTimeMillis() + PhantomConfig.autoMoveToFarmZonesDelayMs());
		TASKS.put(phantom.getObjectId(), ThreadPool.scheduleAtFixedRate(() -> think(phantom), Rnd.get(900, 2200), PhantomConfig.aiTickMs()));
	}
	
	public static void stop(int objectId)
	{
		final ScheduledFuture<?> task = TASKS.remove(objectId);
		if (task != null)
			task.cancel(false);
		
		HOMES.remove(objectId);
		PATROL_POINTS.remove(objectId);
		LAST_TARGETS.remove(objectId);
		LAST_LOOT_AREAS.remove(objectId);
		LAST_ACTIONS.remove(objectId);
		DEATH_HANDLING.remove(objectId);
		clearClaims(objectId);
		clearItemClaims(objectId);
		STUCK_ESCAPES.remove(objectId);
		IDLE_STUCK_TICKS.remove(objectId);
		FARM_ZONE_BUCKETS.remove(objectId);
		FARM_ZONE_READY_TIMES.remove(objectId);
		WAR_LOG_TIMES.remove(objectId);
	}
	
	public static void setHome(Player phantom)
	{
		if (phantom != null)
		{
			HOMES.put(phantom.getObjectId(), new Location(phantom.getX(), phantom.getY(), phantom.getZ()));
			PATROL_POINTS.put(phantom.getObjectId(), nextPatrolPoint(phantom));
			LAST_ACTIONS.put(phantom.getObjectId(), "Home updated");
		}
	}
	
	public static void stopAll()
	{
		for (ScheduledFuture<?> task : TASKS.values())
			task.cancel(false);
		
		TASKS.clear();
		HOMES.clear();
		PATROL_POINTS.clear();
		LAST_TARGETS.clear();
		LAST_ACTIONS.clear();
		DEATH_HANDLING.clear();
		TARGET_CLAIMS.clear();
		LAST_LOOT_AREAS.clear();
		ITEM_CLAIMS.clear();
		LAST_POSITIONS.clear();
		STUCK_TICKS.clear();
		STUCK_ESCAPES.clear();
		IDLE_STUCK_TICKS.clear();
		FARM_ZONE_BUCKETS.clear();
		FARM_ZONE_READY_TIMES.clear();
		WAR_LOG_TIMES.clear();
	}
	
	public static Location getLastTarget(Player phantom)
	{
		return (phantom == null) ? null : LAST_TARGETS.get(phantom.getObjectId());
	}
	
	public static String getLastAction(Player phantom)
	{
		return (phantom == null) ? "-" : LAST_ACTIONS.getOrDefault(phantom.getObjectId(), "Idle");
	}
	
	private static void think(Player phantom)
	{
		try
		{
		if (phantom == null || !phantom.isOnline() || !phantom.isVisible())
		{
			if (phantom != null)
				stop(phantom.getObjectId());
			return;
		}
		
		// Runtime pause (admin "AI Off"): suspend the loop without cancelling the task or wiping state.
		if (AI_PAUSED)
		{
			LAST_ACTIONS.put(phantom.getObjectId(), "AI paused");
			return;
		}
		
		PhantomInventory.cleanup(phantom);
			PhantomProgression.think(phantom);
			PhantomEngine.applyTimedBuffs(phantom);
			PhantomCombat.ensureRagingPk();
			PhantomSocial.think(phantom);
			
			if (phantom.isDead())
			{
				handleDeath(phantom);
				return;
			}
			
			// Early store-mode guard: while a private store is open the phantom stays seated,
			// so loot/farm/stuck-escape logic must not move or teleport it away.
			// If the faction war starts, close the store so the phantom can fight.
			if (phantom.getOperateType() != OperateType.NONE)
			{
				final boolean warStarting = PhantomEngine.canJoinWar(phantom);
				if (warStarting)
				{
					phantom.getSellList().clear();
					phantom.setOperateType(OperateType.NONE);
					phantom.standUp();
					phantom.broadcastUserInfo();
				}
				else
				{
					LAST_ACTIONS.put(phantom.getObjectId(), "Store");
					return;
				}
			}
			
			if (detectAndEscapeStuck(phantom))
				return;
			
			// War combat strafing: melee phantoms occasionally circle around their current war
			// target instead of standing still, so the battle spreads out instead of everyone
			// attacking in a straight line. Runs before the busy guard so it can break off a
			// melee swing to reposition (mages keep casting from range).
			final boolean warRunning = PhantomEngine.canJoinWar(phantom);
			if (warRunning && maybeRepositionDuringWarCombat(phantom))
				return;
			
			if (phantom.getAttack().isAttackingNow() || phantom.getCast().isCastingNow())
			{
				LAST_ACTIONS.put(phantom.getObjectId(), "Busy");
				return;
			}
			
			// === EVENT MODE: while a registered event is RUNNING, the phantom fights the event ===
			// (no farming, no patrol, no level-zone teleports - they would pull the phantom away from the arena)
			// Single lookup: getEventForPlayer() returns the event or null (avoids iterating events twice per tick).
			final AbstractEvent event = EventEngine.getInstance().getEventForPlayer(phantom.getObjectId());
			if (event != null)
			{
				if (event.getState() == AbstractEvent.State.RUNNING)
				{
					handleEventMode(phantom, event);
					return;
				}
				
				// Registered but event not started yet - behave normally until teleport.
				LAST_ACTIONS.put(phantom.getObjectId(), "Event waiting");
			}
			
			// === FACTION WAR MODE: highest priority. Runs BEFORE loot/farm/level-zone teleports,
			// otherwise maybeMoveToFarmZoneStep() would yank the phantom right back to its farm
			// zone a few seconds after it arrives on the war map (phantom would never be seen). ===
			final boolean inNeutralZone = Config.ENABLE_FACTION_SYSTEM && FactionWarConfig.isEnabled() && FactionWarConfig.isInNeutralZone(phantom.getPosition());
			
			if (warRunning)
			{
				// Still in neutral zone (war just started or the phantom missed the auto-teleport):
				// teleport it directly to this faction's spawn on the war map.
				if (inNeutralZone)
				{
					final Location warSpawn = FactionWarManager.getInstance().getFactionSpawn(phantom.getFactionId());
					if (warSpawn != null)
					{
						// Randomize slightly to avoid all phantoms stacking on the exact spawn point.
						final int rx = warSpawn.getX() + Rnd.get(-250, 250);
						final int ry = warSpawn.getY() + Rnd.get(-250, 250);
						phantom.teleportTo(rx, ry, warSpawn.getZ(), 20);
						if (phantom.isTeleporting())
							phantom.onTeleported();
						phantom.revalidateZone(true);
						phantom.broadcastUserInfo();
						PhantomAI.setHome(phantom);
						FactionWarRegistry.getInstance().register(phantom);
						LAST_ACTIONS.put(phantom.getObjectId(), "War join");
						PhantomLog.info("Phantom " + phantom.getName() + " joined the faction war from neutral zone.");
					}
					return;
				}
				
				// Priority 1: Attack enemy faction players
				final Player warTarget = findEnemyFactionPlayerInWar(phantom);
				if (warTarget != null)
				{
					attackPlayer(phantom, warTarget, "Faction war ");
					logWarMove(phantom, "attacking player " + warTarget.getName());
					PhantomSocial.sayWarAction(phantom);
					return;
				}
				
				// Priority 2: Attack war NPCs (flags, checkpoints and guards). Flags and
				// checkpoints are NEUTRAL objectives capturable by either faction, so they are
				// always valid targets. Enemy guards are only attacked when the phantom is not
				// restricted to faction players only (see findWarNpcTarget filter).
				final Monster warNpcTarget = findWarNpcTarget(phantom);
				if (warNpcTarget != null)
				{
					attackNpc(phantom, warNpcTarget, "War npc ");
					logWarMove(phantom, "attacking war npc " + warNpcTarget.getName());
					PhantomSocial.sayWarAction(phantom);
					return;
				}
				
				// Priority 3: No enemies nearby - move toward the battle area
				if (!phantom.isMoving() && !phantom.getAttack().isAttackingNow() && !phantom.getCast().isCastingNow())
				{
					moveToWarCenter(phantom);
					logWarMove(phantom, "advancing to war center");
					PhantomSocial.sayWarAction(phantom);
					return;
				}
				
				LAST_ACTIONS.put(phantom.getObjectId(), "War scanning");
				logWarMove(phantom, "scanning for targets");
				return;
			}
			
			// === GRAND BOSS HUNT MODE: phantom sent to a Grand Boss lair, focuses the boss ===
			if (PhantomEngine.isBossHunting(phantom.getObjectId()))
			{
				handleBossHuntMode(phantom);
				return;
			}
			
			if (tryLootAny(phantom))
				return;
			
			if (maybeMoveToFarmZoneStep(phantom))
				return;
			
			// === NEUTRAL ZONE BEHAVIOR: Wander or open private store (war is not running here) ===
			if (inNeutralZone)
			{
				// Store sold out: the phantom was left seated with no store, stand up and resume.
				if (phantom.isSitting() && phantom.getOperateType() == OperateType.NONE)
				{
					phantom.standUp();
					LAST_ACTIONS.put(phantom.getObjectId(), "Stand up");
					return;
				}
				
				// Sit and open a real sell store, or just wander.
				if (PhantomConfig.storeEnabled() && phantom.getOperateType() == OperateType.NONE && !phantom.isSitting() && Rnd.get(100) < PhantomConfig.storeChance())
				{
					openPrivateStore(phantom);
					return;
				}
				
				// Occasionally send some phantoms to hunt an alive Grand Boss.
				if (!phantom.isMoving() && PhantomConfig.bossHuntEnabled() && Rnd.get(100) < 8)
				{
					PhantomEngine.teleportPhantomsToBoss();
					LAST_ACTIONS.put(phantom.getObjectId(), "Boss check");
					return;
				}
				
				// Wander around the neutral zone
				if (!phantom.isMoving() && Rnd.get(100) < 60)
				{
					wander(phantom);
					return;
				}
				
				LAST_ACTIONS.put(phantom.getObjectId(), "Neutral zone");
				return;
			}
			
			// === NORMAL MODE (no faction war) ===
			
			if (tryLootAny(phantom))
				return;
			
			if (maybeMoveToFarmZoneStep(phantom))
				return;
			
			final Player pvpTarget = PhantomCombat.getRetaliationTarget(phantom);
			if (pvpTarget != null)
			{
				attackPlayer(phantom, pvpTarget, "PVP response ");
				return;
			}
			
			if (Config.ENABLE_FACTION_SYSTEM && phantom.getFactionId() > 0)
			{
				final Player factionTarget = findEnemyFactionPlayer(phantom);
				if (factionTarget != null)
				{
					attackPlayer(phantom, factionTarget, "Faction war ");
					return;
				}
			}
			
			final Player visiblePkTarget = PhantomCombat.findVisiblePk(phantom);
			if (visiblePkTarget != null)
			{
				attackPlayer(phantom, visiblePkTarget, "PK hunter ");
				return;
			}
			
			final Player pkTarget = PhantomCombat.findPkTarget(phantom);
			if (pkTarget != null)
			{
				attackPlayer(phantom, pkTarget, "PK rage ");
				return;
			}
			
			if (!PhantomConfig.attackOnlyEnemyFaction())
			{
				final Monster target = findTarget(phantom);
				if (target != null)
				{
					phantom.setTarget(target);
					LAST_TARGETS.put(phantom.getObjectId(), new Location(target.getX(), target.getY(), target.getZ()));
					rememberLootArea(phantom, target);
					claimTarget(phantom, target);
					
					if (tryUseOffensiveSkill(phantom, target, false))
						return;
					
					if (shouldRestMageMp(phantom))
					{
						restMp(phantom);
						return;
					}
					
					LAST_ACTIONS.put(phantom.getObjectId(), "Attack " + target.getName());
					phantom.getAI().tryToAttack(target, false, false);
					return;
				}
			}
			
			LAST_TARGETS.remove(phantom.getObjectId());
			if (maybeRelocateToLevelZone(phantom, false))
				return;
			
			if (PhantomConfig.patrolEnabled())
			{
				patrol(phantom);
				return;
			}
			
			if (!phantom.isMoving() && Rnd.get(100) < 45)
				wander(phantom);
			else
				LAST_ACTIONS.put(phantom.getObjectId(), "Scanning");
		}
		catch (Exception e)
		{
			LOGGER.warn("Phantom AI failed for {}.", e, phantom == null ? "null" : phantom.getName());
		}
	}
	
	/**
	 * Attacks an NPC/monster target. Used for war NPCs (flags, checkpoints, guards).
	 */
	private static void attackNpc(Player phantom, Monster target, String action)
	{
		phantom.setTarget(target);
		LAST_TARGETS.put(phantom.getObjectId(), new Location(target.getX(), target.getY(), target.getZ()));
		
		if (tryUseOffensiveSkill(phantom, target, false))
			return;
		
		if (shouldRestMageMp(phantom))
		{
			restMp(phantom);
			return;
		}
		
		LAST_ACTIONS.put(phantom.getObjectId(), action + target.getName());
		phantom.getAI().tryToAttack(target, false, false);
	}
	
	private static void attackPlayer(Player phantom, Player target, String action)
	{
		phantom.setTarget(target);
		LAST_TARGETS.put(phantom.getObjectId(), new Location(target.getX(), target.getY(), target.getZ()));
		
		if (tryUseOffensiveSkill(phantom, target, true))
			return;
		
		if (shouldRestMageMp(phantom))
		{
			restMp(phantom);
			return;
		}
		
		LAST_ACTIONS.put(phantom.getObjectId(), action + target.getName());
		phantom.getAI().tryToAttack(target, true, false);
	}
	
	/**
	 * War combat repositioning: when a melee phantom is attacking a war-relevant target
	 * (enemy faction player, flag, checkpoint or guard) at close range, it occasionally
	 * breaks off to a flanking point around the target instead of standing still. Mages
	 * keep casting from range and are not affected. Throttled so the phantom does not
	 * jitter between a strafe and the attack every tick.
	 * @return True if the phantom was told to reposition this tick.
	 */
	private static boolean maybeRepositionDuringWarCombat(Player phantom)
	{
		if (phantom == null || phantom.isMoving())
			return false;
		
		// Only during active combat.
		if (!phantom.getAttack().isAttackingNow() && !phantom.getCast().isCastingNow())
			return false;
		
		final WorldObject target = phantom.getTarget();
		if (target == null || target == phantom)
			return false;
		
		// Only reposition around war-relevant targets.
		final boolean warTarget = (target instanceof Player player && player.getFactionId() > 0 && player.getFactionId() != phantom.getFactionId() && !player.isDead())
			|| target instanceof FactionWarFlag || target instanceof FactionWarCpFlag || target instanceof FactionWarGuard;
		if (!warTarget)
			return false;
		
		// Mages keep their distance; only close-range melee strafes.
		if (phantom.distance3D(target) > 220)
			return false;
		
		// Throttle: at most one strafe per phantom every 15 seconds.
		final int objectId = phantom.getObjectId();
		final long now = System.currentTimeMillis();
		final long last = WAR_STRAFE_TIMES.getOrDefault(objectId, 0L);
		if (now - last < 15000)
			return false;
		
		// Roll a chance to reposition.
		if (Rnd.get(100) >= 30)
			return false;
		
		// Flanking point around the target at a random bearing, keeping the target ahead.
		final double angle = Math.toRadians(Rnd.get(360));
		final int radius = Rnd.get(260, 450);
		final int nx = target.getX() + (int) Math.round(Math.cos(angle) * radius);
		final int ny = target.getY() + (int) Math.round(Math.sin(angle) * radius);
		final Location destination = validateDestination(phantom, new Location(nx, ny, target.getZ()));
		
		WAR_STRAFE_TIMES.put(objectId, now);
		LAST_ACTIONS.put(objectId, "War strafe");
		moveTo(phantom, destination, "War strafe");
		return true;
	}
	
	/**
	 * Grand Boss hunt mode: the phantom was sent to a Grand Boss lair. It focuses the boss
	 * NPC (attack it when in range, move toward its spawn otherwise). When the boss is dead,
	 * the phantom returns to the neutral zone and clears the hunt flag.
	 */
	private static void handleBossHuntMode(Player phantom)
	{
		final int bossNpcId = PhantomEngine.getBossHuntTarget(phantom.getObjectId());
		if (bossNpcId <= 0)
		{
			PhantomEngine.clearBossHunt(phantom.getObjectId());
			return;
		}
		
		// Boss no longer alive -> return to neutral zone.
		if (!PhantomEngine.isGrandBossAlive(bossNpcId))
		{
			LAST_ACTIONS.put(phantom.getObjectId(), "Boss dead, returning");
			final Location neutral = FactionWarConfig.getNeutralSpawnLoc();
			if (neutral != null)
				phantom.teleportTo(neutral, 20);
			if (phantom.isTeleporting())
				phantom.onTeleported();
			phantom.revalidateZone(true);
			phantom.broadcastUserInfo();
			PhantomAI.clearStuckState(phantom.getObjectId());
			PhantomEngine.clearBossHunt(phantom.getObjectId());
			return;
		}
		
		// Boss alive: attack it (or move toward its spawn location).
		final Monster boss = findEventNpcById(phantom, bossNpcId);
		if (boss != null && !boss.isDead())
		{
			attackNpc(phantom, boss, "Grand boss ");
			return;
		}
		
		if (!phantom.isMoving() && !phantom.getAttack().isAttackingNow() && !phantom.getCast().isCastingNow())
		{
			final net.sf.l2j.gameserver.model.spawn.ASpawn spawn = SpawnManager.getInstance().getSpawn(bossNpcId);
			if (spawn != null && spawn.getNpc() != null)
			{
				final Location bossLoc = new Location(spawn.getNpc().getX(), spawn.getNpc().getY(), spawn.getNpc().getZ());
				LAST_ACTIONS.put(phantom.getObjectId(), "Move to boss");
				moveTo(phantom, bossLoc, "Move to boss");
				return;
			}
		}
		
		LAST_ACTIONS.put(phantom.getObjectId(), "Boss hunting");
	}
	
	/**
	 * Event mode: the phantom fights the running event like a real participant.
	 * Dispatches to an event-specific role when one exists, otherwise uses the
	 * generic behavior (enemies -> objective NPCs -> converge on center).
	 */
	private static void handleEventMode(Player phantom, AbstractEvent event)
	{
		switch (event.getData().getId())
		{
			case 8 -> // Domination: hold the capture zone instead of chasing far away
			{
				handleDominationRole(phantom, event);
				return;
			}
			case 7 -> // LuckyChests: open chests - prioritize them over players
			{
				handleLuckyChestsRole(phantom, event);
				return;
			}
			case 16 -> // RaidInTheMiddle: kill the boss - go for it even with enemies nearby
			{
				handleRaidRole(phantom, event);
				return;
			}
			default ->
			{
			}
		}
		
		handleGenericEventMode(phantom, event);
	}
	
	/**
	 * Generic event behavior: enemies -> objective NPCs -> converge on the center.
	 */
	private static void handleGenericEventMode(Player phantom, AbstractEvent event)
	{
		// Priority 1: enemy event participants (opposite team, or any participant for FFA)
		final Player eventTarget = findEventEnemy(phantom, event);
		if (eventTarget != null)
		{
			attackPlayer(phantom, eventTarget, "Event ");
			return;
		}
		
		// Priority 2: event objective NPCs (chests to open, boss to kill, enemy guards)
		final Monster eventNpc = findEventNpcTarget(phantom, event);
		if (eventNpc != null)
		{
			attackNpc(phantom, eventNpc, "Event npc ");
			return;
		}
		
		// Priority 3: no targets nearby - converge on the event center
		if (!phantom.isMoving() && !phantom.getAttack().isAttackingNow() && !phantom.getCast().isCastingNow())
		{
			moveToEventCenter(phantom, event);
			return;
		}
		
		LAST_ACTIONS.put(phantom.getObjectId(), "Event scanning");
	}
	
	/**
	 * Domination role: the phantom moves to the capture zone and holds it,
	 * only fighting enemies that come to contest the zone.
	 */
	private static void handleDominationRole(Player phantom, AbstractEvent event)
	{
		final Location zone = event.getData().getPositionAll();
		if (zone == null)
		{
			handleGenericEventMode(phantom, event);
			return;
		}
		
		// Fight enemies that are contesting the zone.
		final Player contestant = findEventEnemy(phantom, event);
		if (contestant != null && phantom.distance3D(zone) <= Math.max(600, event.getData().getPositionRadius() * 3))
		{
			attackPlayer(phantom, contestant, "Dom defend ");
			return;
		}
		
		// If already inside the zone, hold position.
		if (phantom.isIn3DRadius(zone, Math.max(300, event.getData().getPositionRadius())))
		{
			LAST_ACTIONS.put(phantom.getObjectId(), "Dom holding zone");
			if (phantom.isSitting())
				phantom.standUp();
			return;
		}
		
		// Move into the zone.
		if (!phantom.isMoving() && !phantom.getAttack().isAttackingNow() && !phantom.getCast().isCastingNow())
		{
			final int offsetX = Rnd.get(-150, 150);
			final int offsetY = Rnd.get(-150, 150);
			LAST_ACTIONS.put(phantom.getObjectId(), "Dom to zone");
			moveTo(phantom, new Location(zone.getX() + offsetX, zone.getY() + offsetY, zone.getZ()), "Dom to zone");
		}
	}
	
	/**
	 * LuckyChests role: the phantom heads to the nearest unopened chest.
	 * Enemies are only attacked while traveling or when directly in the way.
	 */
	private static void handleLuckyChestsRole(Player phantom, AbstractEvent event)
	{
		if (event instanceof LuckyChestsEvent chestEvent)
		{
			// Find the nearest unopened chest.
			LuckyChestsEvent.Chest nearest = null;
			double bestDistance = Double.MAX_VALUE;
			for (LuckyChestsEvent.Chest chest : chestEvent.getChests())
			{
				if (chest.isOpened())
					continue;
				final double distance = phantom.distance3D(new Location(chest.getX(), chest.getY(), chest.getZ()));
				if (distance < bestDistance)
				{
					bestDistance = distance;
					nearest = chest;
				}
			}
			
			if (nearest != null)
			{
				// On the chest - open it (interact) instead of fighting.
				if (bestDistance <= 100)
				{
					LAST_ACTIONS.put(phantom.getObjectId(), "Chest opening");
					chestEvent.openChest(chestEvent.getChests().indexOf(nearest), phantom);
					return;
				}
				
				// Fight anyone blocking the path, then continue to the chest.
				final Player blocker = findEventEnemy(phantom, event);
				if (blocker != null && phantom.distance3D(blocker) < bestDistance)
				{
					attackPlayer(phantom, blocker, "Chest defend ");
					return;
				}
				
				if (!phantom.isMoving() && !phantom.getAttack().isAttackingNow() && !phantom.getCast().isCastingNow())
				{
					LAST_ACTIONS.put(phantom.getObjectId(), "Chest to " + nearest.getX() + "," + nearest.getY());
					moveTo(phantom, new Location(nearest.getX(), nearest.getY(), nearest.getZ()), "Chest hunt");
				}
				return;
			}
		}
		
		// No chests available - generic behavior.
		handleGenericEventMode(phantom, event);
	}
	
	/**
	 * RaidInTheMiddle role: the phantom rushes the raid boss and focuses it,
	 * even when enemy players are nearby (they can't steal the kill that way).
	 */
	private static void handleRaidRole(Player phantom, AbstractEvent event)
	{
		if (event instanceof RaidInTheMiddleEvent raidEvent)
		{
			// If the boss is alive, attack it with priority over enemy players.
			if (raidEvent.isBossAlive())
			{
				final Monster boss = findEventNpcById(phantom, raidEvent.getBossNpcId());
				if (boss != null && !boss.isDead())
				{
					attackNpc(phantom, boss, "Raid boss ");
					return;
				}
				
				// Boss spawned but not yet visible in range - move to its location.
				final Location bossLoc = raidEvent.getBossLocation();
				if (bossLoc != null && !phantom.isMoving() && !phantom.getAttack().isAttackingNow() && !phantom.getCast().isCastingNow())
				{
					LAST_ACTIONS.put(phantom.getObjectId(), "Raid to boss");
					moveTo(phantom, bossLoc, "Raid to boss");
					return;
				}
			}
			else if (!phantom.isMoving() && !phantom.getAttack().isAttackingNow() && !phantom.getCast().isCastingNow())
			{
				// Boss not spawned yet - pre-position at the middle between team spawns.
				final Location mid = raidEvent.getBossLocation();
				if (mid != null)
				{
					LAST_ACTIONS.put(phantom.getObjectId(), "Raid pre-position");
					moveTo(phantom, mid, "Raid pre-position");
					return;
				}
			}
		}
		
		handleGenericEventMode(phantom, event);
	}
	
	/**
	 * Finds the nearest enemy event participant.
	 * Uses event.canAttack() so teammates are never targeted and FFA events target anyone.
	 */
	private static Player findEventEnemy(Player phantom, AbstractEvent event)
	{
		final Player[] nearest = new Player[1];
		final double[] nearestDistance = { Double.MAX_VALUE };
		final int range = Math.max(1200, PhantomConfig.aggroRange() * 2);
		
		phantom.forEachKnownTypeInRadius(Player.class, range, player ->
		{
			if (player == null || player == phantom || player.isDead() || !player.isVisible())
				return;
			
			if (!event.isParticipating(player.getObjectId()))
				return;
			
			if (!event.canAttack(phantom.getObjectId(), player.getObjectId()))
				return;
			
			final double distance = phantom.distance3D(player);
			if (distance < nearestDistance[0])
			{
				nearest[0] = player;
				nearestDistance[0] = distance;
			}
		});
		return nearest[0];
	}
	
	/**
	 * Finds the nearest attackable NPC with the given NPC id within event range.
	 * Used to focus the raid boss even when enemy players are nearby.
	 */
	private static Monster findEventNpcById(Player phantom, int npcId)
	{
		final Monster[] nearest = new Monster[1];
		final double[] nearestDistance = { Double.MAX_VALUE };
		final int range = Math.max(1500, PhantomConfig.aggroRange() * 3);
		
		phantom.forEachKnownTypeInRadius(Monster.class, range, monster ->
		{
			if (monster == null || monster.isDead() || !monster.isVisible() || !monster.isAttackableBy(phantom))
				return;
			
			if (monster.getNpcId() != npcId)
				return;
			
			final double distance = phantom.distance3D(monster);
			if (distance < nearestDistance[0])
			{
				nearest[0] = monster;
				nearestDistance[0] = distance;
			}
		});
		return nearest[0];
	}
	
	/**
	 * Finds the nearest attackable event objective NPC (chests, boss, guards) within the
	 * event arena. Only NPCs inside the arena radius (around the event center) are valid
	 * targets, so normal farm mobs near the arena are never attacked.
	 */
	private static Monster findEventNpcTarget(Player phantom, AbstractEvent event)
	{
		final Location arenaCenter = event.getData().getPositionAll();
		if (arenaCenter == null)
			return null;
		
		// Only attack NPCs inside the event arena (center + radius), never normal mobs.
		final int arenaRadius = Math.max(1500, event.getData().getPositionRadius() * 2);
		
		final Monster[] nearest = new Monster[1];
		final double[] nearestDistance = { Double.MAX_VALUE };
		final int range = Math.max(1200, PhantomConfig.aggroRange() * 2);
		
		phantom.forEachKnownTypeInRadius(Monster.class, range, monster ->
		{
			if (monster == null || monster.isDead() || !monster.isVisible() || !monster.isAttackableBy(phantom))
				return;
			
			// Skip NPCs outside the event arena (e.g. farm mobs nearby).
			if (monster.distance3D(arenaCenter) > arenaRadius)
				return;
			
			final double distance = phantom.distance3D(monster);
			if (distance < nearestDistance[0])
			{
				nearest[0] = monster;
				nearestDistance[0] = distance;
			}
		});
		return nearest[0];
	}
	
	/**
	 * Moves the phantom toward the event arena: its own team spawn (or the event center).
	 */
	private static void moveToEventCenter(Player phantom, AbstractEvent event)
	{
		Location center = event.getData().getPositionAll();
		
		// Prefer the phantom's team spawn to regroup with its side.
		final EventPlayer ep = event.getEventPlayer(phantom.getObjectId());
		if (ep != null && ep.getTeamId() >= 0)
		{
			for (EventTeam team : event.getTeams())
			{
				if (team.getId() == ep.getTeamId() && team.getSpawnLocation() != null)
				{
					center = team.getSpawnLocation();
					break;
				}
			}
		}
		
		if (center == null)
		{
			LAST_ACTIONS.put(phantom.getObjectId(), "Event scan");
			return;
		}
		
		final int offsetX = Rnd.get(-250, 250);
		final int offsetY = Rnd.get(-250, 250);
		LAST_ACTIONS.put(phantom.getObjectId(), "Event advance");
		moveTo(phantom, new Location(center.getX() + offsetX, center.getY() + offsetY, center.getZ()), "Event advance");
	}
	
	/**
	 * Handles phantom death - schedules respawn in town/faction base.
	 * After respawn, if faction war is running, the phantom returns to the war map.
	 * If an event is running, the phantom returns to its event team spawn instead of town.
	 */
	private static void handleDeath(Player phantom)
	{
		if (!PhantomConfig.respawnInTown())
		{
			LAST_ACTIONS.put(phantom.getObjectId(), "Dead");
			return;
		}
		
		// During a RUNNING event the event owns the respawn:
		// - Reviving events (TvT/DM/CTF...) call onEventDie -> revive at team spawn.
		// - Elimination events (LMS/BombFight/SimonSays...) keep the phantom dead
		//   (eliminated) until the match ends, which is its role.
		// Scheduling our own respawn here would cause a redundant double revive
		// + teleport to the same spawn.
		final AbstractEvent event = EventEngine.getInstance().getEventForPlayer(phantom.getObjectId());
		if (event != null && event.getState() == AbstractEvent.State.RUNNING)
		{
			LAST_ACTIONS.put(phantom.getObjectId(), "Dead - event controls respawn");
			return;
		}
		
		if (DEATH_HANDLING.putIfAbsent(phantom.getObjectId(), Boolean.TRUE) != null)
		{
			LAST_ACTIONS.put(phantom.getObjectId(), "Dead - waiting town");
			return;
		}
		
		LAST_ACTIONS.put(phantom.getObjectId(), "Dead - to nearest town");
		// War participants respawn with a longer, randomized delay so the corpse stays a while
		// and phantoms trickle back to the fight instead of vanishing instantly and re-dying.
		final long respawnDelay = PhantomEngine.canJoinWar(phantom) ? warRespawnDelay() : PhantomConfig.respawnDelayMs();
		ThreadPool.schedule(() -> respawnInTown(phantom), respawnDelay);
	}
	
	private static long warRespawnDelay()
	{
		final int random = PhantomConfig.warRespawnRandomMs();
		return PhantomConfig.warRespawnDelayMs() + (random > 0 ? Rnd.get(0, random) : 0);
	}
	
	private static void respawnInTown(Player phantom)
	{
		try
		{
			if (phantom == null || !phantom.isOnline())
				return;
			
			// War participants respawn straight at their faction war spawn (single teleport,
			// no town detour that blinks the phantom twice). Non-participants revive at the
			// faction base or nearest town as usual.
			final boolean returnToWar = PhantomEngine.canJoinWar(phantom);
			
			Location destination = null;
			if (returnToWar)
				destination = FactionWarManager.getInstance().getFactionSpawn(phantom.getFactionId());
			else if (Config.ENABLE_FACTION_SYSTEM && phantom.getFactionId() > 0)
			{
				final Faction faction = FactionData.getInstance().getFaction(phantom.getFactionId());
				if (faction != null && faction.getHomeLocation() != null)
					destination = faction.getHomeLocation();
			}
			
			if (destination == null)
			{
				destination = RestartPointData.getInstance().getLocationToTeleport(phantom, RestartType.TOWN);
				if (destination == null)
					destination = RestartPointData.getInstance().getNearestRestartLocation(phantom);
			}
			
			if (phantom.isDead())
				phantom.doRevive();
			
			if (destination != null)
			{
				final int rx = destination.getX() + Rnd.get(-250, 250);
				final int ry = destination.getY() + Rnd.get(-250, 250);
				phantom.teleportTo(rx, ry, destination.getZ(), 20);
				if (phantom.isTeleporting())
					phantom.onTeleported();
				
				if (returnToWar)
				{
					phantom.revalidateZone(true);
					phantom.broadcastUserInfo();
					clearStuckState(phantom.getObjectId());
					PhantomLog.info("Phantom " + phantom.getName() + " returned to war map after death.");
				}
			}
			
			final Location home = new Location(phantom.getX(), phantom.getY(), phantom.getZ());
			HOMES.put(phantom.getObjectId(), home);
			PATROL_POINTS.put(phantom.getObjectId(), nextPatrolPoint(phantom));
			LAST_ACTIONS.put(phantom.getObjectId(), "Respawn " + (returnToWar ? "war map" : (Config.ENABLE_FACTION_SYSTEM && phantom.getFactionId() > 0 ? "faction base" : "town")));
			phantom.store();
			
			if (PhantomConfig.returnToLevelZoneAfterDeath() && PhantomConfig.useLevelZones() && !returnToWar)
				ThreadPool.schedule(() -> returnToLevelZone(phantom), PhantomConfig.returnToLevelZoneDelayMs());
			else
				DEATH_HANDLING.remove(phantom.getObjectId());
		}
		catch (Exception e)
		{
			DEATH_HANDLING.remove(phantom == null ? 0 : phantom.getObjectId());
			LOGGER.warn("Phantom town respawn failed for {}.", e, phantom == null ? "null" : phantom.getName());
		}
	}
	
	private static void returnToLevelZone(Player phantom)
	{
		try
		{
			if (phantom == null || !phantom.isOnline() || phantom.isDead())
				return;
			
			final Location zone = PhantomConfig.getLevelZone(phantom.getStatus().getLevel());
			if (zone != null)
			{
				final Location destination = getFarmDestination(phantom, zone);
				if (destination == null)
					return;
				
				phantom.teleToLocation(destination);
				HOMES.put(phantom.getObjectId(), destination);
				PATROL_POINTS.put(phantom.getObjectId(), nextPatrolPoint(phantom));
				LAST_ACTIONS.put(phantom.getObjectId(), "Gatekeeper to level zone");
				phantom.store();
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Phantom level zone return failed for {}.", e, phantom == null ? "null" : phantom.getName());
		}
		finally
		{
			if (phantom != null)
				DEATH_HANDLING.remove(phantom.getObjectId());
		}
	}
	
	private static boolean tryUseOffensiveSkill(Player phantom, Creature target, boolean ctrlPressed)
	{
		final boolean mage = ClassId.isInGroup(phantom, "@mage_group");
		if (mage && !PhantomConfig.mageUseSkills())
			return false;
		
		if (!mage && !PhantomConfig.advancedSkillUsage())
			return false;
		
		final int chance = mage ? PhantomConfig.mageSkillChance() : PhantomConfig.skillChance();
		if (Rnd.get(100) >= chance)
			return false;
		
		final L2Skill skill = selectOffensiveSkill(phantom, mage);
		if (skill == null)
			return false;
		
		if (phantom.isSitting())
			phantom.standUp();
		
		LAST_ACTIONS.put(phantom.getObjectId(), "Skill " + skill.getName() + " -> " + target.getName());
		phantom.getAI().tryToCast(target, skill, ctrlPressed, false, 0);
		return true;
	}
	
	private static L2Skill selectOffensiveSkill(Player phantom, boolean mage)
	{
		final List<L2Skill> skills = new ArrayList<>();
		for (L2Skill skill : phantom.getSkills().values())
		{
			if (skill == null || !skill.isActive() || !skill.isOffensive() || !hasMpForSkill(phantom, skill))
				continue;
			
			if (mage)
			{
				if (skill.isMagic() && isMagicDamageSkill(skill.getSkillType()))
					skills.add(skill);
			}
			else
			{
				if (isDamageSkill(skill.getSkillType()))
					skills.add(skill);
			}
		}
		
		if (skills.isEmpty())
			return null;
		
		skills.sort(Comparator.comparingInt(L2Skill::getLevel).reversed());
		return skills.get(Rnd.get(Math.min(skills.size(), 4)));
	}
	
	private static boolean shouldRestMageMp(Player phantom)
	{
		return PhantomConfig.mageNeverMelee() && ClassId.isInGroup(phantom, "@mage_group") && ((phantom.getStatus().getMpRatio() * 100) < PhantomConfig.mageRestMpPercent());
	}
	
	private static void restMp(Player phantom)
	{
		LAST_ACTIONS.put(phantom.getObjectId(), "Rest MP");
		if (!phantom.isSitting())
			phantom.sitDown();
	}
	
	private static boolean hasMpForSkill(Player phantom, L2Skill skill)
	{
		return phantom.getStatus().getMp() >= (skill.getMpConsume() + skill.getMpInitialConsume());
	}
	
	private static boolean isMagicDamageSkill(SkillType type)
	{
		return switch (type)
		{
			case MDAM, DOT, MDOT, DRAIN, DRAIN_SOUL, DEATHLINK, MANADAM -> true;
			default -> false;
		};
	}
	
	private static boolean isDamageSkill(SkillType type)
	{
		return switch (type)
		{
			case PDAM, FATAL, BLOW, CHARGEDAM, REAL_DAMAGE, MDAM, DOT, MDOT, DRAIN, DRAIN_SOUL, DEATHLINK, MANADAM -> true;
			default -> false;
		};
	}
	
	private static boolean tryLootAny(Player phantom)
	{
		return tryLootHerb(phantom) || tryLootDrop(phantom);
	}
	
	private static boolean tryLootHerb(Player phantom)
	{
		if (!PhantomConfig.autoLootHerbs())
			return false;
		
		final ItemInstance item = findNearestHerb(phantom);
		if (item == null || !claimItem(phantom, item))
			return false;
		
		if (phantom.isSitting())
			phantom.standUp();
		
		LAST_ACTIONS.put(phantom.getObjectId(), "Herb " + item.getName());
		phantom.getAI().tryToPickUp(item.getObjectId(), false);
		return true;
	}
	
	private static ItemInstance findNearestHerb(Player phantom)
	{
		final ItemInstance[] nearest = new ItemInstance[1];
		final double[] nearestDistance =
		{
			Double.MAX_VALUE
		};
		
		phantom.forEachKnownTypeInRadius(ItemInstance.class, PhantomConfig.autoLootHerbRange(), item ->
		{
			if (item == null || !item.isVisible() || !isHerb(item) || !canLootItem(phantom, item))
				return;
			
			final double distance = phantom.distance3D(item);
			if (distance < nearestDistance[0])
			{
				nearest[0] = item;
				nearestDistance[0] = distance;
			}
		});
		return nearest[0];
	}
	
	private static boolean isHerb(ItemInstance item)
	{
		if (PhantomConfig.contains(PhantomConfig.herbItemIds(), item.getItemId()))
			return true;
		
		final String name = item.getName();
		return name != null && name.toLowerCase().contains("herb");
	}
	
	private static boolean tryLootDrop(Player phantom)
	{
		if (!PhantomConfig.autoLootDrops())
			return false;
		
		final ItemInstance item = findNearestDrop(phantom);
		if (item == null || !claimItem(phantom, item))
			return false;
		
		LAST_ACTIONS.put(phantom.getObjectId(), "Loot " + item.getName());
		phantom.getAI().tryToPickUp(item.getObjectId(), false);
		return true;
	}
	
	private static ItemInstance findNearestDrop(Player phantom)
	{
		final ItemInstance[] nearest = new ItemInstance[1];
		final double[] nearestDistance =
		{
			Double.MAX_VALUE
		};
		
		phantom.forEachKnownTypeInRadius(ItemInstance.class, PhantomConfig.autoLootRange(), item ->
		{
			if (item == null || !item.isVisible() || isHerb(item) || !canLootItem(phantom, item))
				return;
			
			final double distance = phantom.distance3D(item);
			if (distance < nearestDistance[0])
			{
				nearest[0] = item;
				nearestDistance[0] = distance;
			}
		});
		return nearest[0];
	}
	
	private static void rememberLootArea(Player phantom, Creature target)
	{
		if (phantom == null || target == null)
			return;
		
		LAST_LOOT_AREAS.put(phantom.getObjectId(), new LootMemory(new Location(target.getX(), target.getY(), target.getZ()), System.currentTimeMillis()));
	}
	
	private static boolean canLootItem(Player phantom, ItemInstance item)
	{
		if (!PhantomConfig.lootOwnDropsOnly())
			return !isItemClaimedByOther(item, phantom.getObjectId());
		
		final int ownerId = item.getOwnerId();
		if (ownerId > 0)
			return ownerId == phantom.getObjectId() && !isItemClaimedByOther(item, phantom.getObjectId());
		
		final LootMemory memory = LAST_LOOT_AREAS.get(phantom.getObjectId());
		if (memory == null)
			return false;
		
		final long now = System.currentTimeMillis();
		if (now - memory.time > PhantomConfig.lootOwnDropMemoryMs())
		{
			LAST_LOOT_AREAS.remove(phantom.getObjectId());
			return false;
		}
		
		return distance3D(memory.location, item) <= PhantomConfig.lootOwnDropRadius() && !isItemClaimedByOther(item, phantom.getObjectId());
	}
	
	private static double distance3D(Location loc, WorldObject object)
	{
		final long dx = loc.getX() - object.getX();
		final long dy = loc.getY() - object.getY();
		final long dz = loc.getZ() - object.getZ();
		return Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
	}
	
	private static boolean claimItem(Player phantom, ItemInstance item)
	{
		cleanupItemClaims();
		
		final int phantomId = phantom.getObjectId();
		final int itemId = item.getObjectId();
		final ItemClaim claim = ITEM_CLAIMS.get(itemId);
		if (claim != null && claim.phantomId != phantomId && System.currentTimeMillis() - claim.time <= PhantomConfig.lootClaimMs())
			return false;
		
		ITEM_CLAIMS.put(itemId, new ItemClaim(phantomId, System.currentTimeMillis()));
		return true;
	}
	
	private static boolean isItemClaimedByOther(ItemInstance item, int phantomId)
	{
		final ItemClaim claim = ITEM_CLAIMS.get(item.getObjectId());
		if (claim == null || claim.phantomId == phantomId)
			return false;
		
		if (System.currentTimeMillis() - claim.time > PhantomConfig.lootClaimMs())
		{
			ITEM_CLAIMS.remove(item.getObjectId());
			return false;
		}
		return true;
	}
	
	private static void cleanupItemClaims()
	{
		final long now = System.currentTimeMillis();
		ITEM_CLAIMS.entrySet().removeIf(entry -> now - entry.getValue().time > PhantomConfig.lootClaimMs());
	}
	
	private static boolean maybeRelocateToLevelZone(Player phantom, boolean initial)
	{
		// Never pull a phantom out of an active faction war.
		if (PhantomEngine.canJoinWar(phantom))
			return false;
		
		if (!PhantomConfig.useLevelZones())
			return false;
		
		if (!initial && Rnd.get(100) >= PhantomConfig.levelZoneTeleportChance())
			return false;
		
		final Location zone = PhantomConfig.getLevelZone(phantom.getStatus().getLevel());
		if (zone == null)
			return false;
		
		if (!initial && phantom.distance3D(zone) < PhantomConfig.levelZoneRadius())
			return false;
		
		final Location destination = getFarmDestination(phantom, zone);
		if (destination == null)
			return false;
		
		phantom.teleToLocation(destination);
		HOMES.put(phantom.getObjectId(), destination);
		PATROL_POINTS.put(phantom.getObjectId(), nextPatrolPoint(phantom));
		LAST_ACTIONS.put(phantom.getObjectId(), "Move to level zone");
		return true;
	}
	
	private static boolean maybeMoveToFarmZoneStep(Player phantom)
	{
		// Never pull a phantom out of an active faction war.
		if (PhantomEngine.canJoinWar(phantom))
			return false;
		
		if (!PhantomConfig.autoMoveToFarmZones() || !PhantomConfig.useLevelZones())
			return false;
		
		final long readyTime = FARM_ZONE_READY_TIMES.getOrDefault(phantom.getObjectId(), 0L);
		if (System.currentTimeMillis() < readyTime)
		{
			LAST_ACTIONS.put(phantom.getObjectId(), "Farm wait");
			return false;
		}
		
		final int step = Math.max(1, PhantomConfig.farmZoneMoveStep());
		final int level = Math.max(1, phantom.getStatus().getLevel());
		final int bucket = (level - 1) / step;
		final Integer previous = FARM_ZONE_BUCKETS.get(phantom.getObjectId());
		final Location zone = PhantomConfig.getLevelZone(level);
		if (zone == null)
			return false;
		
		if (previous != null && previous == bucket && phantom.distance3D(zone) < (PhantomConfig.levelZoneRadius() * 2))
			return false;
		
		FARM_ZONE_BUCKETS.put(phantom.getObjectId(), bucket);
		return forceMoveToFarmZone(phantom, "Farm zone L" + ((bucket * step) + 1) + "+");
	}
	
	private static boolean forceMoveToFarmZone(Player phantom, String action)
	{
		// Never pull a phantom out of an active faction war.
		if (PhantomEngine.canJoinWar(phantom))
			return false;
		
		if (!PhantomConfig.useLevelZones())
			return false;
		
		final Location zone = PhantomConfig.getLevelZone(phantom.getStatus().getLevel());
		if (zone == null)
			return false;
		
		final Location destination = getFarmDestination(phantom, zone);
		if (destination == null)
			return false;
		
		phantom.teleToLocation(destination);
		HOMES.put(phantom.getObjectId(), destination);
		PATROL_POINTS.put(phantom.getObjectId(), nextPatrolPoint(phantom));
		LAST_ACTIONS.put(phantom.getObjectId(), action);
		PhantomLog.info("Phantom " + phantom.getName() + " teleported to farm zone at " + destination.getX() + "," + destination.getY() + "," + destination.getZ() + " reason=" + action + ".");
		phantom.store();
		return true;
	}
	
	private static Location getFarmDestination(Player phantom, Location zone)
	{
		final Location npcAnchor = findNpcAnchoredFarmLocation(phantom, zone);
		if (npcAnchor != null)
			return npcAnchor;
		
		PhantomLog.warn("No NPC anchor found near farm zone " + zone.getX() + "," + zone.getY() + "," + zone.getZ() + " for level " + phantom.getStatus().getLevel() + "; staying in current area.");
		return null;
	}
	
	private static Location findNpcAnchoredFarmLocation(Player phantom, Location zone)
	{
		if (!PhantomConfig.farmZoneUseNpcSpawns())
			return null;
		
		Npc bestNpc = null;
		double bestScore = Double.MAX_VALUE;
		final int level = phantom.getStatus().getLevel();
		final int radius = PhantomConfig.farmZoneNpcSearchRadius();
		final int tolerance = PhantomConfig.farmZoneNpcLevelTolerance();
		
		for (NpcMaker maker : SpawnManager.getInstance().getNpcMakers(zone))
		{
			if (maker == null || maker.getSpawns() == null)
				continue;
			
			for (MultiSpawn spawn : maker.getSpawns())
			{
				if (spawn == null || spawn.getTemplate() == null || !spawn.getTemplate().canBeAttacked())
					continue;
				
				final int levelDiff = Math.abs(spawn.getTemplate().getLevel() - level);
				if (tolerance > 0 && levelDiff > tolerance)
					continue;
				
				final Npc npc = spawn.getNpc();
				if (npc == null || npc.isDead() || !npc.isVisible())
					continue;
				
				final double distance = npc.distance3D(zone);
				if (distance > radius)
					continue;
				
				final double score = distance + (levelDiff * 300.0);
				if (score < bestScore)
				{
					bestScore = score;
					bestNpc = npc;
				}
			}
		}
		
		if (bestNpc == null)
			return null;
		
		PhantomLog.info("Farm zone NPC anchor for " + phantom.getName() + ": " + bestNpc.getName() + " (" + bestNpc.getNpcId() + ") at " + bestNpc.getX() + "," + bestNpc.getY() + "," + bestNpc.getZ() + ".");
		return new Location(bestNpc.getX(), bestNpc.getY(), bestNpc.getZ());
	}
	
	private static Monster findTarget(Player phantom)
	{
		cleanupClaims();
		
		final Monster[] nearest = new Monster[1];
		final double[] nearestDistance =
		{
			Double.MAX_VALUE
		};
		
		phantom.forEachKnownTypeInRadius(Monster.class, PhantomConfig.aggroRange(), monster ->
		{
			if (monster == null || monster.isDead() || !monster.isVisible() || !monster.isAttackableBy(phantom))
				return;
			
			if (isClaimedByOther(monster, phantom.getObjectId()))
				return;
			
			final Location home = HOMES.get(phantom.getObjectId());
			if (home != null && monster.distance3D(home) > Math.max(PhantomConfig.patrolRadius(), PhantomConfig.aggroRange() + PhantomConfig.wanderRange()))
				return;
			
			final double distance = phantom.distance3D(monster);
			if (distance < nearestDistance[0])
			{
				nearest[0] = monster;
				nearestDistance[0] = distance;
			}
		});
		return nearest[0];
	}
	
	/**
	 * Finds war-related NPC targets (FactionWarFlag, FactionWarCpFlag, FactionWarGuard)
	 * within the war zone. These are all Monster subclasses, so they can be targeted via Monster.class.
	 * Enemy guards are attacked; flags and checkpoints are always capturable.
	 */
	private static Monster findWarNpcTarget(Player phantom)
	{
		final int myFaction = phantom.getFactionId();
		if (myFaction <= 0)
			return null;
		
		final Monster[] nearest = new Monster[1];
		final double[] nearestDistance = { Double.MAX_VALUE };
		
		// Use war range (same as player search)
		final int warRange = Math.max(1500, PhantomConfig.aggroRange() * 2);
		
		phantom.forEachKnownTypeInRadius(Monster.class, warRange, monster ->
		{
			if (monster == null || monster.isDead() || !monster.isVisible() || !monster.isAttackableBy(phantom))
				return;
			
			// Only war-related NPCs: main flag, checkpoints and guards. Never random farm mobs.
			if (!(monster instanceof FactionWarFlag) && !(monster instanceof FactionWarCpFlag) && !(monster instanceof FactionWarGuard))
				return;
			
			// Flags and checkpoints are neutral objectives capturable by either faction, so they
			// are always valid. Enemy guards are only attacked when the phantom is not restricted
			// to faction players only (attackOnlyEnemyFaction).
			if (monster instanceof FactionWarGuard && PhantomConfig.attackOnlyEnemyFaction())
				return;
			
			final double distance = phantom.distance3D(monster);
			if (distance < nearestDistance[0])
			{
				nearest[0] = monster;
				nearestDistance[0] = distance;
			}
		});
		return nearest[0];
	}
	
	private static void patrol(Player phantom)
	{
		if (phantom.isMoving())
		{
			LAST_ACTIONS.put(phantom.getObjectId(), "Patrol");
			return;
		}
		
		Location waypoint = PATROL_POINTS.get(phantom.getObjectId());
		if (waypoint == null || phantom.distance3D(waypoint) < PhantomConfig.patrolWaypointReach())
		{
			waypoint = nextPatrolPoint(phantom);
			PATROL_POINTS.put(phantom.getObjectId(), waypoint);
		}
		
		LAST_ACTIONS.put(phantom.getObjectId(), "Patrol");
		moveTo(phantom, waypoint, "Patrol");
	}
	
	private static Location nextPatrolPoint(Player phantom)
	{
		Location base = PhantomConfig.useLevelZones() ? PhantomConfig.getLevelZone(phantom.getStatus().getLevel()) : null;
		if (base == null)
			base = HOMES.getOrDefault(phantom.getObjectId(), new Location(phantom.getX(), phantom.getY(), phantom.getZ()));
		
		return withSpread(phantom, base, PhantomConfig.patrolRadius());
	}
	
	private static void wander(Player phantom)
	{
		final Location home = HOMES.getOrDefault(phantom.getObjectId(), new Location(phantom.getX(), phantom.getY(), phantom.getZ()));
		final Location destination = withSpread(phantom, home, PhantomConfig.wanderRange());
		
		final WorldObject target = phantom.getTarget();
		if (target instanceof Monster monster && monster.isDead())
			phantom.setTarget(null);
		
		LAST_ACTIONS.put(phantom.getObjectId(), "Wander");
		moveTo(phantom, destination, "Wander");
	}
	
	private static Player findEnemyFactionPlayer(Player phantom)
	{
		final int myFaction = phantom.getFactionId();
		if (myFaction <= 0)
			return null;
		
		// No attacking in neutral zone
		if (FactionWarConfig.isEnabled() && FactionWarConfig.isInNeutralZone(phantom.getPosition()))
			return null;
		
		final Player[] nearest = new Player[1];
		final double[] nearestDistance = { Double.MAX_VALUE };
		
		phantom.forEachKnownTypeInRadius(Player.class, PhantomConfig.aggroRange(), player ->
		{
			if (player == null || player == phantom || player.isDead() || !player.isVisible())
				return;
			
			if (player.getFactionId() <= 0 || player.getFactionId() == myFaction)
				return;
			
			// Skip if target is in neutral zone
			if (FactionWarConfig.isEnabled() && FactionWarConfig.isInNeutralZone(player.getPosition()))
				return;
			
			final double distance = phantom.distance3D(player);
			if (distance < nearestDistance[0])
			{
				nearest[0] = player;
				nearestDistance[0] = distance;
			}
		});
		return nearest[0];
	}
	
	/**
	 * Aggressive faction enemy search used during active Faction War.
	 * Uses a larger search range (2x normal aggro range) and also checks
	 * the war map flag/checkpoint areas for enemies to converge on the fight.
	 * Always attacks with CTRL-pressed simulation for PvP without restrictions.
	 */
	private static Player findEnemyFactionPlayerInWar(Player phantom)
	{
		final int myFaction = phantom.getFactionId();
		if (myFaction <= 0)
			return null;
		
		// Don't attack in neutral zone
		if (FactionWarConfig.isEnabled() && FactionWarConfig.isInNeutralZone(phantom.getPosition()))
			return null;
		
		final FactionWarManager fwm = FactionWarManager.getInstance();
		if (!fwm.isRunning())
			return null;
		
		// Use double the normal aggro range for war, min 1500
		final int warRange = Math.max(1500, PhantomConfig.aggroRange() * 2);
		
		final Player[] nearest = new Player[1];
		final double[] nearestDistance = { Double.MAX_VALUE };
		
		phantom.forEachKnownTypeInRadius(Player.class, warRange, player ->
		{
			if (player == null || player == phantom || player.isDead() || !player.isVisible())
				return;
			
			if (player.getFactionId() <= 0 || player.getFactionId() == myFaction)
				return;
			
			// Skip targets in neutral zone
			if (FactionWarConfig.isEnabled() && FactionWarConfig.isInNeutralZone(player.getPosition()))
				return;
			
			final double distance = phantom.distance3D(player);
			if (distance < nearestDistance[0])
			{
				nearest[0] = player;
				nearestDistance[0] = distance;
			}
		});
		return nearest[0];
	}
	
	/**
	 * Opens a private sell store for the phantom in neutral zone.
	 * The phantom receives real items, lists them in a sell store with a title,
	 * sits down and stays in store mode until the store is closed (timer or sold out).
	 */
	private static void openPrivateStore(Player phantom)
	{
		if (phantom == null || phantom.isDead() || phantom.getOperateType() != OperateType.NONE)
			return;
		
		if (!PhantomConfig.storeEnabled())
			return;
		
		final int[] itemIds = PhantomConfig.storeItemIds();
		if (itemIds.length == 0)
			return;
		
		try
		{
			// Fresh sell list.
			phantom.getSellList().clear();
			
			// Give the phantom real items and list a random subset of the configured ids.
			final int listSize = Rnd.get(PhantomConfig.storeItemListMin(), PhantomConfig.storeItemListMax());
			for (int i = 0; i < listSize; i++)
			{
				final int itemId = itemIds[Rnd.get(itemIds.length)];
				
				final Item template = ItemData.getInstance().getTemplate(itemId);
				if (template == null || !template.isTradable() || template.isQuestItem())
					continue;
				
				final int count = Rnd.get(PhantomConfig.storeItemCountMin(), PhantomConfig.storeItemCountMax());
				final ItemInstance item = phantom.getInventory().addItem(itemId, count);
				if (item == null)
					continue;
				
				final int price = Math.max(1, (int) (item.getReferencePrice() * PhantomConfig.storePriceMultiplier()));
				phantom.getSellList().addItem(item.getObjectId(), item.getCount(), price);
			}
			
			if (phantom.getSellList().isEmpty())
				return;
			
			phantom.getSellList().setTitle(PhantomConfig.storeTitle());
			phantom.sitDown();
			phantom.setOperateType(OperateType.SELL);
			phantom.broadcastUserInfo();
			LAST_ACTIONS.put(phantom.getObjectId(), "Store");
			PhantomLog.info("Phantom " + phantom.getName() + " abrio tienda en zona neutral con " + phantom.getSellList().size() + " items.");
			
			// Schedule closing the store after a random time.
			final int objectId = phantom.getObjectId();
			ThreadPool.schedule(() ->
			{
				final Player p = PhantomEngine.getActivePhantom(objectId);
				if (p != null && p.getOperateType() == OperateType.SELL)
				{
					p.getSellList().clear();
					p.setOperateType(OperateType.NONE);
					p.standUp();
					p.broadcastUserInfo();
					PhantomLog.info("Phantom " + p.getName() + " cerro la tienda.");
				}
			}, Rnd.get(PhantomConfig.storeDurationMinMs(), PhantomConfig.storeDurationMaxMs()));
		}
		catch (Exception e)
		{
			PhantomLog.warn("openPrivateStore fallo para " + phantom.getName() + ": " + e.getMessage());
		}
	}
	
	/**
	 * Ensures phantoms of the same faction are in a party together.
	 * Creates parties of up to 9 phantoms grouped by faction.
	 */
	public static void ensureFactionParties()
	{
		try
		{
			final java.util.Map<Integer, java.util.List<Player>> byFaction = new java.util.HashMap<>();
			for (Player phantom : PhantomEngine.getActivePhantoms())
			{
				if (phantom == null || phantom.isDead() || !phantom.isOnline())
					continue;
				final int fId = phantom.getFactionId();
				if (fId > 0)
					byFaction.computeIfAbsent(fId, k -> new java.util.ArrayList<>()).add(phantom);
			}
			
			for (java.util.List<Player> factionPhantoms : byFaction.values())
			{
				if (factionPhantoms.size() < 2)
					continue;
				
				// Remove phantoms already in a party
				final java.util.List<Player> unpartied = new java.util.ArrayList<>();
				for (Player p : factionPhantoms)
				{
					if (p.getParty() == null)
						unpartied.add(p);
				}
				
				// Group into parties of up to 9
				for (int i = 0; i < unpartied.size(); i += 9)
				{
					final int end = Math.min(i + 9, unpartied.size());
					final Player leader = unpartied.get(i);
					final java.util.List<Player> members = unpartied.subList(i + 1, end);
					
					for (Player member : members)
					{
						if (member.getParty() == null && leader.getParty() == null)
						{
							// Create new party with leader + member
							new Party(leader, member, net.sf.l2j.gameserver.enums.LootRule.ITEM_RANDOM);
						}
						else if (leader.getParty() != null && member.getParty() == null)
						{
							leader.getParty().addPartyMember(member);
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			PhantomLog.warn("ensureFactionParties failed: " + e.getMessage());
		}
	}
	
	/**
	 * Moves the phantom toward the war zone's flag center or a checkpoint.
	 * This makes phantoms converge on the battle area when no enemies are nearby,
	 * keeping the war dynamic and populated.
	 */
	private static void moveToWarCenter(Player phantom)
	{
		try
		{
			final FactionWarManager fwm = FactionWarManager.getInstance();
			if (!fwm.isRunning())
				return;
			
			final int myFaction = phantom.getFactionId();
			if (myFaction <= 0)
				return;
			
			// Get current map center (flag position)
			final net.sf.l2j.gameserver.factionwar.FactionWarConfig.WarMap currentMap = FactionWarConfig.getMaps().isEmpty() ? null : FactionWarConfig.getMaps().get(fwm.getCurrentMapIndex());
			if (currentMap == null)
				return;
			
			// If phantom is already near the center, just patrol around
			final Location center = currentMap.getCenter();
			if (phantom.distance3D(center) <= 800)
			{
				// Already near center - patrol around the flag area with a per-phantom spread
				// so phantoms fan out around the flag instead of stacking on it.
				final Location patrolPoint = withSpread(phantom, center, 500 + Rnd.get(0, 250));
				LAST_ACTIONS.put(phantom.getObjectId(), "War patrol");
				moveTo(phantom, patrolPoint, "War patrol");
				return;
			}
			
			// Advance toward a random point of a ring around the flag center so the two
			// factions converge on the flag from all sides instead of marching in a straight
			// line and clumping on a single point.
			final int offset = Rnd.get(400, 900);
			final double angle = Math.toRadians(Rnd.get(360));
			final int offsetX = (int) Math.round(Math.cos(angle) * offset);
			final int offsetY = (int) Math.round(Math.sin(angle) * offset);
			final Location destination = new Location(center.getX() + offsetX, center.getY() + offsetY, center.getZ());
			
			LAST_ACTIONS.put(phantom.getObjectId(), "War advance");
			moveTo(phantom, destination, "War advance");
		}
		catch (Exception e)
		{
			PhantomLog.warn("moveToWarCenter failed for " + phantom.getName() + ": " + e.getMessage());
		}
	}
	
	/**
	 * Logs what a phantom is currently doing during the faction war, throttled so the
	 * log does not flood on every AI tick. Each entry carries the phantom name, faction,
	 * action and current coordinates, so the behavior can be reviewed afterwards.
	 * @param phantom : The phantom to log.
	 * @param action : Human-readable description of the action (e.g. "attacking player X").
	 */
	private static void logWarMove(Player phantom, String action)
	{
		if (phantom == null)
			return;
		
		final int objectId = phantom.getObjectId();
		final long now = System.currentTimeMillis();
		final long last = WAR_LOG_TIMES.getOrDefault(objectId, 0L);
		if (now - last < 10000)
			return;
		
		WAR_LOG_TIMES.put(objectId, now);
		final String mapName = FactionWarManager.getInstance().isRunning() ? "map " + FactionWarManager.getInstance().getCurrentMapIndex() : "map ?";
		PhantomLog.info("WarMove " + phantom.getName() + " fac=" + phantom.getFactionId() + " " + mapName + " " + action + " at " + phantom.getX() + "," + phantom.getY() + "," + phantom.getZ() + ".");
	}
	
	private static void claimTarget(Player phantom, Monster monster)
	{
		if (PhantomConfig.avoidClaimedTargets() && phantom != null && monster != null)
			TARGET_CLAIMS.put(monster.getObjectId(), new TargetClaim(phantom.getObjectId(), System.currentTimeMillis()));
	}
	
	private static boolean isClaimedByOther(Monster monster, int phantomId)
	{
		if (!PhantomConfig.avoidClaimedTargets())
			return false;
		
		final TargetClaim claim = TARGET_CLAIMS.get(monster.getObjectId());
		if (claim == null || claim.phantomId == phantomId)
			return false;
		
		if (System.currentTimeMillis() - claim.time > PhantomConfig.targetClaimMs())
		{
			TARGET_CLAIMS.remove(monster.getObjectId());
			return false;
		}
		return true;
	}
	
	private static void cleanupClaims()
	{
		final long now = System.currentTimeMillis();
		TARGET_CLAIMS.entrySet().removeIf(entry -> now - entry.getValue().time > PhantomConfig.targetClaimMs());
	}
	
	public static void clearDeathFlag(int objectId)
	{
		DEATH_HANDLING.remove(objectId);
	}
	
	/**
	 * Resets all transient "stuck / idle" tracking for a phantom.
	 * Used after a manual teleport (admin bridge) so the stuck detector
	 * doesn't immediately relocate the phantom to a safe spawn.
	 */
	public static void clearStuckState(int objectId)
	{
		LAST_POSITIONS.remove(objectId);
		STUCK_TICKS.remove(objectId);
		IDLE_STUCK_TICKS.remove(objectId);
		STUCK_ESCAPES.remove(objectId);
	}
	
	private static void clearClaims(int phantomId)
	{
		TARGET_CLAIMS.entrySet().removeIf(entry -> entry.getValue().phantomId == phantomId);
	}
	
	private static void clearItemClaims(int phantomId)
	{
		ITEM_CLAIMS.entrySet().removeIf(entry -> entry.getValue().phantomId == phantomId);
	}
	
	private static boolean detectAndEscapeStuck(Player phantom)
	{
		if (!PhantomConfig.stuckCheckEnabled())
			return false;
		
		final int objectId = phantom.getObjectId();
		final Location current = new Location(phantom.getX(), phantom.getY(), phantom.getZ());
		final Location last = LAST_POSITIONS.put(objectId, current);
		if (last == null)
			return false;
		
		if (!phantom.isMoving())
		{
			STUCK_TICKS.remove(objectId);
			if (current.distance3D(last) <= PhantomConfig.stuckCheckDistance())
			{
				final int idleTicks = IDLE_STUCK_TICKS.merge(objectId, 1, Integer::sum);
				if (PhantomConfig.stuckTeleportToSafeSpawn() && idleTicks >= PhantomConfig.idleStuckTicks())
					return teleportToSafeSpawn(phantom, "Idle stuck");
			}
			else
			{
				IDLE_STUCK_TICKS.remove(objectId);
				STUCK_ESCAPES.remove(objectId);
			}
			return false;
		}
		
		if (current.distance3D(last) > PhantomConfig.stuckCheckDistance())
		{
			STUCK_TICKS.remove(objectId);
			STUCK_ESCAPES.remove(objectId);
			IDLE_STUCK_TICKS.remove(objectId);
			return false;
		}
		
		final int ticks = STUCK_TICKS.merge(objectId, 1, Integer::sum);
		if (ticks < PhantomConfig.stuckCheckTicks())
			return false;
		
		STUCK_TICKS.remove(objectId);
		final int escapes = STUCK_ESCAPES.merge(objectId, 1, Integer::sum);
		if (escapes >= PhantomConfig.stuckTeleportAttempts())
		{
			STUCK_ESCAPES.remove(objectId);
			if (PhantomConfig.stuckTeleportToSafeSpawn())
			{
				PhantomLog.warn("Phantom " + phantom.getName() + " was stuck repeatedly at " + current.getX() + "," + current.getY() + "," + current.getZ() + "; teleporting to racial safe spawn.");
				return teleportToSafeSpawn(phantom, "Geo stuck");
			}
			if (PhantomConfig.stuckTeleportToFarmZone())
			{
				PhantomLog.warn("Phantom " + phantom.getName() + " was stuck repeatedly at " + current.getX() + "," + current.getY() + "," + current.getZ() + "; teleporting to farm zone.");
				return forceMoveToFarmZone(phantom, "Geo unstuck farm");
			}
		}
		final Location home = HOMES.getOrDefault(objectId, current);
		final Location escape = withSpread(phantom, home, PhantomConfig.stuckEscapeRadius());
		LAST_ACTIONS.put(objectId, "Unstuck");
		moveTo(phantom, escape, "Unstuck");
		PhantomLog.warn("Phantom " + phantom.getName() + " looked stuck at " + current.getX() + "," + current.getY() + "," + current.getZ() + "; moving to " + escape.getX() + "," + escape.getY() + "," + escape.getZ());
		return true;
	}
	
	private static boolean teleportToSafeSpawn(Player phantom, String reason)
	{
		// During an active faction war the racial safe spawn is off the war map;
		// do NOT yank the phantom out of the battle.
		if (PhantomEngine.canJoinWar(phantom))
			return false;
		
		final Location safe = phantom.getBaseTemplate().getRandomSpawn();
		if (safe == null)
			return false;
		
		final Location destination = new Location(safe.getX(), safe.getY(), safe.getZ());
		phantom.teleToLocation(destination);
		HOMES.put(phantom.getObjectId(), destination);
		PATROL_POINTS.put(phantom.getObjectId(), nextPatrolPoint(phantom));
		LAST_POSITIONS.put(phantom.getObjectId(), destination);
		STUCK_TICKS.remove(phantom.getObjectId());
		STUCK_ESCAPES.remove(phantom.getObjectId());
		IDLE_STUCK_TICKS.remove(phantom.getObjectId());
		LAST_ACTIONS.put(phantom.getObjectId(), reason + " safe");
		PhantomLog.warn("Phantom " + phantom.getName() + " moved to racial safe spawn " + destination.getX() + "," + destination.getY() + "," + destination.getZ() + " reason=" + reason + ".");
		phantom.store();
		return true;
	}
	
	private static void moveTo(Player phantom, Location destination, String action)
	{
		final Location valid = validateDestination(phantom, destination);
		LAST_ACTIONS.put(phantom.getObjectId(), action);
		phantom.getAI().tryToMoveTo(valid, null);
	}
	
	private static Location withSpread(Player phantom, Location loc, int range)
	{
		final Location destination = new Location(loc.getX() + Rnd.get(-range, range), loc.getY() + Rnd.get(-range, range), loc.getZ());
		return validateDestination(phantom, destination);
	}
	
	private static Location validateDestination(Player phantom, Location destination)
	{
		if (!PhantomConfig.useGeoPathing() || phantom == null || destination == null)
			return destination;
		
		try
		{
			return GeoEngine.getInstance().getValidLocation(phantom, destination);
		}
		catch (Exception e)
		{
			PhantomLog.warn("Geo path validation failed for " + phantom.getName() + ": " + e.getMessage());
			return destination;
		}
	}
	
	private record TargetClaim(int phantomId, long time)
	{
	}
	
	private record LootMemory(Location location, long time)
	{
	}
	
	private record ItemClaim(int phantomId, long time)
	{
	}
}
