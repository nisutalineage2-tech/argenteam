package net.sf.l2j.gameserver.custom.achievementEngine;

import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;

public class Mageclass extends Condition
{
	public Mageclass(Object value)
	{
		super(value);
		setName("Mage Class");
	}

	@Override
	public boolean meetConditionRequirements(L2PcInstance player)
	{
		if (getValue() == null)
		{
			return false;
		}

		return player.isMageClass();
	}
}