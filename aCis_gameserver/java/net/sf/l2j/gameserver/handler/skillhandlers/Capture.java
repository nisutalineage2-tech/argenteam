package net.sf.l2j.gameserver.handler.skillhandlers;

import net.sf.l2j.gameserver.enums.skills.SkillType;
import net.sf.l2j.gameserver.event.AbstractEvent;
import net.sf.l2j.gameserver.event.BattlefieldEvent;
import net.sf.l2j.gameserver.event.EventEngine;
import net.sf.l2j.gameserver.handler.ISkillHandler;
import net.sf.l2j.gameserver.model.WorldObject;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * Capture skill handler for Battlefield event.
 * When used on a flag position, it captures the nearest flag for the caster's team.
 */
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
		
		// Get active Battlefield event
		final AbstractEvent event = EventEngine.getInstance().getActiveEvent();
		if (!(event instanceof BattlefieldEvent bfEvent))
		{
			player.sendMessage("Capture skill can only be used during a Battlefield event.");
			return;
		}
		
		// Find the nearest unowned flag to the player
		double bestDist = Double.MAX_VALUE;
		int bestIndex = -1;
		
		for (int i = 0; i < bfEvent.getFlags().size(); i++)
		{
			final BattlefieldEvent.Flag flag = bfEvent.getFlags().get(i);
			if (flag.getOwnerTeam() >= 0)
				continue; // Already captured
			
			final double dist = player.distance3D(new Location(flag.getX(), flag.getY(), flag.getZ()));
			if (dist < bestDist)
			{
				bestDist = dist;
				bestIndex = i;
			}
		}
		
		if (bestIndex >= 0)
		{
			bfEvent.captureFlag(bestIndex, player);
			player.sendMessage("Captured a flag for your team!");
		}
		else
		{
			player.sendMessage("No uncaptured flags nearby.");
		}
	}
	
	@Override
	public SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}
