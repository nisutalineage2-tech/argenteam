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

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.datatables.NpcTable;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2FactTeleporterInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2GrandBossInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2TpFlagInstance;
import net.sf.l2j.gameserver.network.serverpackets.NpcSay;
import net.sf.l2j.gameserver.scripting.EventType;
import net.sf.l2j.gameserver.scripting.scripts.ai.L2AttackableAIScript;
import net.sf.l2j.gameserver.util.Broadcast;

public class Benom extends L2AttackableAIScript
{
	public Benom()
	{
		super("faction/ai");
	}
	
	@Override
	protected void registerNpcs()
	{
		addEventIds(29054, EventType.ON_KILL);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		if (event.equalsIgnoreCase("respawn_benom"))
		{
			L2GrandBossInstance benom = new L2GrandBossInstance(IdFactory.getInstance().getNextId(), NpcTable.getInstance().getTemplate(29054));
			benom.setCurrentHpMp(benom.getMaxHp(), benom.getMaxMp());
			benom.setHeading(32791);
			benom.spawnMe(L2TpFlagInstance._benom_spawn_x, L2TpFlagInstance._benom_spawn_y, L2TpFlagInstance._benom_spawn_z);
			L2FactTeleporterInstance._bosses.add(benom);
		}
		
		return super.onAdvEvent(event, npc, player);
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isPet)
	{
		if (killer.getClan() != null)
		{
			killer.getClan().setReputationScore(killer.getClan().getReputationScore() + 10000);
			killer.sendMessage("Your clan received 10000 reputation points.");
		}
		npc.broadcastPacket(new NpcSay(npc.getObjectId(), 0, npc.getNpcId(), "You will pay! You will definitely pay!"));
		int _factionId = killer.getFactionId();
		if (killer.getFactionId() != L2TpFlagInstance._goddard_owners)
		{
			switch (_factionId)
			{
				case 1:
					L2TpFlagInstance._goddard_owners = 1;
					for (L2TpFlagInstance flag : L2FactTeleporterInstance._tpTeam2Flags)
					{
						if (!flag.isInvul())
						{
							flag.doDie(killer);
						}
					}
					Broadcast.sendMessToAllTeam1Players("[" + Config.FACTION_TEAM1_NAME + "] Our faction just captured the Goddard Town!");
					break;
				case 2:
					L2TpFlagInstance._goddard_owners = 2;
					for (L2TpFlagInstance flag : L2FactTeleporterInstance._tpTeam1Flags)
					{
						if (!flag.isInvul())
						{
							flag.doDie(killer);
						}
					}
					Broadcast.sendMessToAllTeam2Players("[" + Config.FACTION_TEAM2_NAME + "] Our faction just captured the Goddard Town!");
					break;
			}
		}
		
		startQuestTimer("respawn_benom", 60000, npc, null, false);
		L2FactTeleporterInstance._bosses.remove(npc);
		
		return super.onKill(npc, killer, isPet);
	}
}