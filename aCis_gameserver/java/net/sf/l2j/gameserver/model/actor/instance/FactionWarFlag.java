package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;

public class FactionWarFlag extends Monster
{
	public FactionWarFlag(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public boolean isAggressive()
	{
		return false;
	}
	
	@Override
	public boolean isAttackableBy(Creature attacker)
	{
		return FactionWarManager.getInstance().isRunning() && !isDead();
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
		
		if (killer instanceof Player player && player.getFactionId() > 0)
		{
			FactionWarManager.getInstance().onFlagKilled(player.getFactionId(), player.getObjectId());
		}
		
		return true;
	}
}
