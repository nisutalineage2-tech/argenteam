package net.sf.l2j.gameserver.model.actor.instance;

import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;

public class FactionWarGuard extends Monster
{
	private ScheduledFuture<?> _factionScanTask;
	
	public FactionWarGuard(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		setNoRndWalk(true);
		
		if (FactionWarConfig.isEnabled())
		{
			final int guardFaction = getGuardFactionId();
			if (guardFaction > 0)
			{
				_factionScanTask = ThreadPool.scheduleAtFixedRate(() ->
				{
					if (isDead() || !FactionWarManager.getInstance().isRunning())
						return;
					
					final int enemyFactionId = (guardFaction == FactionWarConfig.getGoodFactionId())
						? FactionWarConfig.getEvilFactionId()
						: FactionWarConfig.getGoodFactionId();
					
					forEachKnownType(Player.class, player ->
					{
						if (player.isDead() || player.getFactionId() != enemyFactionId)
							return;
						
						if (!isIn3DRadius(player, getTemplate().getAggroRange()))
							return;
						
						if (!getAI().getAggroList().containsKey(player))
							addAttacker(player);
					});
				}, 3000, 3000);
			}
		}
	}
	
	@Override
	public void onDecay()
	{
		if (_factionScanTask != null)
		{
			_factionScanTask.cancel(false);
			_factionScanTask = null;
		}
		
		super.onDecay();
	}
	
	@Override
	public int getDriftRange()
	{
		return 20;
	}
	
	@Override
	public boolean returnHome()
	{
		if (isDead())
			return false;
		
		if (!isInMyTerritory())
		{
			abortAll(true);
			teleportTo(getSpawnLocation(), 0);
			return true;
		}
		
		return super.returnHome();
	}
	
	private int getGuardFactionId()
	{
		final int npcId = getNpcId();
		if (npcId != FactionWarConfig.getGuardNpcId())
			return 0;
		
		// Prefer the explicit faction stored in the spawn memo at spawn time.
		if (getSpawn() != null)
		{
			final int memoFaction = getSpawn().getMemo().getInteger("factionId", 0);
			if (memoFaction > 0)
				return memoFaction;
		}
		
		final var goodLoc = FactionWarManager.getInstance().getFactionSpawn(FactionWarConfig.getGoodFactionId());
		final var evilLoc = FactionWarManager.getInstance().getFactionSpawn(FactionWarConfig.getEvilFactionId());
		
		if (goodLoc != null && getPosition().distance3D(goodLoc) < 500)
			return FactionWarConfig.getGoodFactionId();
		if (evilLoc != null && getPosition().distance3D(evilLoc) < 500)
			return FactionWarConfig.getEvilFactionId();
		
		return 0;
	}
	
	@Override
	public boolean isAggressive()
	{
		return FactionWarManager.getInstance().isRunning();
	}
	
	@Override
	public boolean isAttackableBy(Creature attacker)
	{
		if (isDead())
			return false;
		
		if (!FactionWarConfig.isEnabled() || !FactionWarManager.getInstance().isRunning())
			return false;
		
		final Player player = attacker.getActingPlayer();
		if (player == null)
			return true;
		
		final int guardFaction = getGuardFactionId();
		if (guardFaction <= 0)
			return true;
		
		return player.getFactionId() != guardFaction;
	}
	
	@Override
	public void calculateRewards(Creature killer)
	{
	}
	
	@Override
	public boolean doDie(Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		FactionWarManager.getInstance().onGuardDied(this);
		
		return true;
	}
}
