/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.l2j.gameserver.scripting.scripts.faction.ai;

import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.scripting.EventType;
import net.sf.l2j.gameserver.scripting.scripts.ai.L2AttackableAIScript;

public class FactionGuards extends L2AttackableAIScript
{
	// Faction guards
	private static int[] _guards =
	{
		95,
		94,
		93
	};
	
	public FactionGuards()
	{
		super("faction/ai");
	}
	
	@Override
	protected void registerNpcs()
	{
		addEventIds(_guards, EventType.ON_SPAWN);
	}
	
	@Override
	public String onSpawn(L2Npc npc)
	{
		switch (npc.getNpcId())
		{
			case 95:
			case 94:
			case 93:
				startQuestTimer("activate", 3000, npc, null, true);
				break;
		}
		return super.onSpawn(npc);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		if (event.equalsIgnoreCase("activate"))
		{
			for (L2PcInstance obj : npc.getKnownTypeInRadius(L2PcInstance.class, 200))
			{
				switch (npc.getNpcId())
				{
					case 95:
						if (obj.getActingPlayer().getFactionId() != 1)
						{
							npc.setTarget(obj.getActingPlayer());
							L2Skill skill = SkillTable.getInstance().getInfo(1069, 170);
							npc.doCast(skill);
							npc.setIsRunning(true);
							obj.getActingPlayer().doDie(npc);
						}
						break;
					case 94:
						if (obj.getActingPlayer().getFactionId() != 2)
						{
							npc.setTarget(obj.getActingPlayer());
							L2Skill skill = SkillTable.getInstance().getInfo(1069, 170);
							npc.doCast(skill);
							npc.setIsRunning(true);
							obj.getActingPlayer().doDie(npc);
						}
						break;
					case 93:
						if (obj.getActingPlayer().getFactionId() != 3)
						{
							npc.setTarget(obj.getActingPlayer());
							L2Skill skill = SkillTable.getInstance().getInfo(1069, 170);
							npc.doCast(skill);
							npc.setIsRunning(true);
							obj.getActingPlayer().doDie(npc);
						}
						break;
				}
				
			}
		}
		return super.onAdvEvent(event, npc, player);
	}
}
