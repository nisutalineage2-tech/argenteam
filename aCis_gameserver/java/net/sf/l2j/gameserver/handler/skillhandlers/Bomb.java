package net.sf.l2j.gameserver.handler.skillhandlers;

import net.sf.l2j.commons.logging.CLogger;

import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.event.AbstractEvent;
import net.sf.l2j.gameserver.event.EventEngine;
import net.sf.l2j.gameserver.handler.ISkillHandler;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * Bomb Fight skill handler. When used, kills all enemy team players within the skill's effect radius,
 * then kills the caster (suicide attack). Only works during Bomb Fight events.
 */
public class Bomb implements ISkillHandler
{
	private static final CLogger LOGGER = new CLogger(Bomb.class.getName());
	
	private static final SkillType[] SKILL_IDS =
	{
		SkillType.BOMB
	};
	
	@Override
	public void useSkill(Creature creature, L2Skill skill, WorldObject[] targets, ItemInstance item)
	{
		if (!(creature instanceof Player player))
			return;
		
		final int radius = skill.getEffectRange() > 0 ? skill.getEffectRange() : 400;
		
		// Find active Bomb Fight event
		final AbstractEvent event = EventEngine.getInstance().getActiveEvent();
		if (event == null || !(event instanceof net.sf.l2j.gameserver.event.BombFightEvent))
			return;
		
		// Find all valid targets in skill radius
		for (Creature known : player.getKnownTypeInRadius(Creature.class, radius))
		{
			if (!(known instanceof Player target))
				continue;
			
			if (target == player || target.isDead() || target.isAlikeDead())
				continue;
			
			// Only kill enemies of different team
			if (event.areTeammates(player.getObjectId(), target.getObjectId()))
				continue;
			
			// Kill the target
			target.doDie(player);
		}
		
		// Suicide - the bomber dies too
		player.doDie(player);
	}
	
	@Override
	public SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
