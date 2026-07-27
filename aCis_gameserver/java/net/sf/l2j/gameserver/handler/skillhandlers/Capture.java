package net.sf.l2j.gameserver.handler.skillhandlers;

import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.handler.ISkillHandler;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.skills.L2Skill;

public class Capture implements ISkillHandler
{
	private static final SkillType[] SKILL_IDS =
	{
		SkillType.CAPTURE
	};
	
	@Override
	public void useSkill(Creature creature, L2Skill skill, WorldObject[] targets, ItemInstance item)
	{
		if (!(creature instanceof Player player))
			return;
		
		if (targets.length == 0)
			return;
		
		final WorldObject target = targets[0];
		if (target == null)
			return;
		
		// The capture skill captures a flag/base NPC. 
		// The flag NPC handles scoring logic via its onBypassFeedback or other interaction.
		// For now, just notify the player they used capture.
		player.sendMessage("Captured target!");
	}
	
	@Override
	public SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
