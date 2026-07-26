package net.sf.l2j.gameserver.phantom;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;

public final class PhantomCombat
{
	private static final Map<Integer, HitMemory> HITS = new ConcurrentHashMap<>();
	private static final Map<Integer, Integer> RETALIATION_TARGETS = new ConcurrentHashMap<>();
	private static final Set<Integer> RAGING_PKS = ConcurrentHashMap.newKeySet();
	
	private PhantomCombat()
	{
	}
	
	public static void onPlayerDamaged(Player victim, Creature attacker)
	{
		if (victim == null || attacker == null || !PhantomEngine.isPhantom(victim.getObjectId()))
			return;
		
		final Player attackerPlayer = attacker.getActingPlayer();
		if (attackerPlayer == null || attackerPlayer == victim || attackerPlayer.isDead())
			return;
		
		if (!PhantomConfig.pvpEnabled())
			return;
		
		if (victim.distance3D(attackerPlayer) > PhantomConfig.pvpResponseRange())
			return;
		
		final long now = System.currentTimeMillis();
		final HitMemory memory = HITS.compute(victim.getObjectId(), (id, old) ->
		{
			if (old == null || old.attackerId != attackerPlayer.getObjectId() || now - old.lastHit > PhantomConfig.pvpHitMemoryMs())
				return new HitMemory(attackerPlayer.getObjectId(), 1, now);
			return new HitMemory(attackerPlayer.getObjectId(), old.hits + 1, now);
		});
		
		if (memory.hits >= PhantomConfig.pvpRetaliationHits())
		{
			RETALIATION_TARGETS.put(victim.getObjectId(), attackerPlayer.getObjectId());
			PhantomState.setAggressive(victim.getObjectId(), true);
			PhantomLog.warn("Phantom " + victim.getName() + " retaliates against " + attackerPlayer.getName() + " after " + memory.hits + " hits.");
		}
	}
	
	public static void onPlayerDied(Player victim, Creature killer)
	{
		if (victim == null)
			return;
		
		HITS.remove(victim.getObjectId());
		RETALIATION_TARGETS.remove(victim.getObjectId());
		PhantomState.setAggressive(victim.getObjectId(), false);
		
		if (RAGING_PKS.remove(victim.getObjectId()))
		{
			PhantomState.setPk(victim.getObjectId(), false);
			victim.setKarma(0);
			if (PhantomConfig.pkRageTransfersOnDeath())
				assignRagingPk(victim);
		}
	}
	
	public static Player getRetaliationTarget(Player phantom)
	{
		if (phantom == null || !PhantomConfig.pvpEnabled())
			return null;
		
		final Integer targetId = RETALIATION_TARGETS.get(phantom.getObjectId());
		if (targetId == null)
			return null;
		
		final Player target = World.getInstance().getPlayer(targetId);
		if (target == null || target.isDead() || !target.isVisible() || phantom.distance3D(target) > PhantomConfig.pvpResponseRange())
		{
			RETALIATION_TARGETS.remove(phantom.getObjectId());
			PhantomState.setAggressive(phantom.getObjectId(), false);
			return null;
		}
		return target;
	}
	
	public static boolean isRagingPk(Player phantom)
	{
		return phantom != null && PhantomConfig.pkEnabled() && RAGING_PKS.contains(phantom.getObjectId());
	}
	
	public static void ensureRagingPk()
	{
		if (!PhantomConfig.pkEnabled() || PhantomConfig.pkRagePercent() <= 0)
		{
			clearRage();
			return;
		}
		
		final int desired = desiredRagingCount();
		while (RAGING_PKS.size() < desired)
		{
			final int before = RAGING_PKS.size();
			assignRagingPk(null);
			if (RAGING_PKS.size() == before)
				break;
		}
	}
	
	public static void assignRagingPk(Player exclude)
	{
		final Collection<Player> active = PhantomEngine.getActivePhantoms();
		if (active.isEmpty())
			return;
		
		final ArrayList<Player> candidates = new ArrayList<>();
		for (Player phantom : active)
		{
			if (phantom != null && phantom.isOnline() && !phantom.isDead() && !RAGING_PKS.contains(phantom.getObjectId()) && (exclude == null || phantom.getObjectId() != exclude.getObjectId()))
				candidates.add(phantom);
		}
		
		if (candidates.isEmpty())
			return;
		
		final Player chosen = candidates.get(Rnd.get(candidates.size()));
		RAGING_PKS.add(chosen.getObjectId());
		PhantomState.setPk(chosen.getObjectId(), true);
		if (PhantomConfig.pkKarma() > 0)
			chosen.setKarma(PhantomConfig.pkKarma());
	}
	
	public static Player findPkTarget(Player phantom)
	{
		if (!isRagingPk(phantom))
			return null;
		
		return findAttackablePlayer(phantom, PhantomConfig.pkSearchRange(), PhantomConfig.pkAttackRealPlayers());
	}
	
	public static Player findVisiblePk(Player phantom)
	{
		if (phantom == null || !PhantomConfig.attackVisiblePk())
			return null;
		
		final Player[] target = new Player[1];
		final double[] distance =
		{
			Double.MAX_VALUE
		};
		
		phantom.forEachKnownTypeInRadius(Player.class, PhantomConfig.visiblePkRange(), player ->
		{
			if (player == null || player == phantom || player.isDead() || !player.isVisible() || player.getKarma() <= 0)
				return;
			
			final double d = phantom.distance3D(player);
			if (d < distance[0])
			{
				target[0] = player;
				distance[0] = d;
			}
		});
		return target[0];
	}
	
	public static void clearRetaliation(Player phantom)
	{
		if (phantom == null)
			return;
		
		RETALIATION_TARGETS.remove(phantom.getObjectId());
		PhantomState.setAggressive(phantom.getObjectId(), false);
	}
	
	private static Player findAttackablePlayer(Player phantom, int range, boolean includeRealPlayers)
	{
		final Player[] target = new Player[1];
		final double[] distance =
		{
			Double.MAX_VALUE
		};
		
		phantom.forEachKnownTypeInRadius(Player.class, range, player ->
		{
			if (player == null || player == phantom || player.isDead() || !player.isVisible())
				return;
			
			if (!includeRealPlayers && !PhantomEngine.isPhantom(player.getObjectId()))
				return;
			
			final double d = phantom.distance3D(player);
			if (d < distance[0])
			{
				target[0] = player;
				distance[0] = d;
			}
		});
		return target[0];
	}
	
	private static int desiredRagingCount()
	{
		final int active = PhantomEngine.size();
		if (active <= 0)
			return 0;
		
		return Math.max(1, (int) Math.ceil(active * (PhantomConfig.pkRagePercent() / 100.0)));
	}
	
	private static void clearRage()
	{
		for (int objectId : RAGING_PKS)
		{
			final Player phantom = PhantomEngine.getActivePhantom(objectId);
			if (phantom != null)
				phantom.setKarma(0);
			PhantomState.setPk(objectId, false);
		}
		RAGING_PKS.clear();
	}
	
	private record HitMemory(int attackerId, int hits, long lastHit)
	{
	}
}
