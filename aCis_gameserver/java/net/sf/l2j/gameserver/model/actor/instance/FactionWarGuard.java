package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;

public class FactionWarGuard extends Monster
{
	public FactionWarGuard(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public boolean isAggressive()
	{
		return FactionWarManager.getInstance().isRunning();
	}
	
	@Override
	public boolean isAttackableBy(Creature attacker)
	{
		return !isDead();
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
		
		FactionWarManager.getInstance().onGuardDied();
		
		return true;
	}
}
