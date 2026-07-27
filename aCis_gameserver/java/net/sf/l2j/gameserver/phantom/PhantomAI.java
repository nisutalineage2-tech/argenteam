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
import net.sf.l2j.gameserver.data.xml.RestartPointData;
import net.sf.l2j.gameserver.enums.RestartType;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.WorldObject;
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
	
	private PhantomAI()
	{
	}
	
	public static void start(Player phantom)
	{
		if (phantom == null || !PhantomConfig.aiEnabled())
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
			
			if (detectAndEscapeStuck(phantom))
				return;
			
			if (phantom.getAttack().isAttackingNow() || phantom.getCast().isCastingNow())
			{
				LAST_ACTIONS.put(phantom.getObjectId(), "Busy");
				return;
			}
			
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
	
	private static void handleDeath(Player phantom)
	{
		if (!PhantomConfig.respawnInTown())
		{
			LAST_ACTIONS.put(phantom.getObjectId(), "Dead");
			return;
		}
		
		if (DEATH_HANDLING.putIfAbsent(phantom.getObjectId(), Boolean.TRUE) != null)
		{
			LAST_ACTIONS.put(phantom.getObjectId(), "Dead - waiting town");
			return;
		}
		
		LAST_ACTIONS.put(phantom.getObjectId(), "Dead - to nearest town");
		ThreadPool.schedule(() -> respawnInTown(phantom), PhantomConfig.respawnDelayMs());
	}
	
	private static void respawnInTown(Player phantom)
	{
		try
		{
			if (phantom == null || !phantom.isOnline())
				return;
			
			Location town = null;
			
			if (Config.ENABLE_FACTION_SYSTEM && phantom.getFactionId() > 0)
			{
				final Faction faction = FactionData.getInstance().getFaction(phantom.getFactionId());
				if (faction != null && faction.getHomeLocation() != null)
					town = faction.getHomeLocation();
			}
			
			if (town == null)
			{
				town = RestartPointData.getInstance().getLocationToTeleport(phantom, RestartType.TOWN);
				if (town == null)
					town = RestartPointData.getInstance().getNearestRestartLocation(phantom);
			}
			
			if (phantom.isDead())
				phantom.doRevive();
			
			if (town != null)
				phantom.teleportTo(town, 20);
			
			final Location home = new Location(phantom.getX(), phantom.getY(), phantom.getZ());
			HOMES.put(phantom.getObjectId(), home);
			PATROL_POINTS.put(phantom.getObjectId(), nextPatrolPoint(phantom));
			LAST_ACTIONS.put(phantom.getObjectId(), "Respawn " + (Config.ENABLE_FACTION_SYSTEM && phantom.getFactionId() > 0 ? "faction base" : "town"));
			phantom.store();
			
			if (PhantomConfig.returnToLevelZoneAfterDeath() && PhantomConfig.useLevelZones())
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
		
		final Player[] nearest = new Player[1];
		final double[] nearestDistance = { Double.MAX_VALUE };
		
		phantom.forEachKnownTypeInRadius(Player.class, PhantomConfig.aggroRange(), player ->
		{
			if (player == null || player == phantom || player.isDead() || !player.isVisible())
				return;
			
			if (player.getFactionId() <= 0 || player.getFactionId() == myFaction)
				return;
			
			if (PhantomEngine.isPhantom(player.getObjectId()))
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
