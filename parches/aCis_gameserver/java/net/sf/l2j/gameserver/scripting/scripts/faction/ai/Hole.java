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

public class Hole extends L2AttackableAIScript
{
	private static int[] _npcId =
	{
		29053
	};
	private L2Npc _dummy, _dummy1;
	
	public Hole()
	{
		super("faction/ai");
		
		_dummy = addSpawn(29053, 131485, 114446, -3715, 16384, false, 0, false);
		_dummy1 = addSpawn(29052, 131485, 114446, -3715, 16384, false, 0, false);
		_dummy.setIsImmobilized(true);
		_dummy1.setIsImmobilized(true);
		_dummy.setIsInvul(true);
		_dummy1.setIsInvul(true);
		
	}
	
	@Override
	protected void registerNpcs()
	{
		addEventIds(_npcId, EventType.ON_AGGRO);
	}
	
	@Override
	public String onAggro(L2Npc npc, L2PcInstance player, boolean isPet)
	{
		if (npc.getNpcId() == _dummy.getNpcId())
		{
			npc.setTarget(_dummy1);
			L2Skill skill = SkillTable.getInstance().getInfo(5004, 1);
			npc.doCast(skill);
		}
		return super.onAggro(npc, player, isPet);
	}
}