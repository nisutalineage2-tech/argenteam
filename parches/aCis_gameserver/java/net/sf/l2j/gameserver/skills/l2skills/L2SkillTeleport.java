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
package net.sf.l2j.gameserver.skills.l2skills;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.datatables.MapRegionTable;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.templates.StatsSet;
import net.sf.l2j.gameserver.templates.skills.L2SkillType;

public class L2SkillTeleport extends L2Skill
{
	private final String _recallType;
	private final Location _loc;
	
	public L2SkillTeleport(StatsSet set)
	{
		super(set);
		
		_recallType = set.getString("recallType", "");
		String coords = set.getString("teleCoords", null);
		if (coords != null)
		{
			String[] valuesSplit = coords.split(",");
			_loc = new Location(Integer.parseInt(valuesSplit[0]), Integer.parseInt(valuesSplit[1]), Integer.parseInt(valuesSplit[2]));
		}
		else
		{
			_loc = null;
		}
	}
	
	@Override
	public void useSkill(L2Character activeChar, L2Object[] targets)
	{
		if (activeChar instanceof L2PcInstance)
		{
			// Check invalid states.
			if (activeChar.isAfraid() || ((L2PcInstance) activeChar).isInOlympiadMode())
			{
				return;
			}
		}
		
		for (L2Character target : (L2Character[]) targets)
		{
			if (target == null)
			{
				return;
			}
			
			if (target instanceof L2PcInstance)
			{
				L2PcInstance targetChar = (L2PcInstance) target;
				
				// Check invalid states.
				if (targetChar.isFestivalParticipant() || targetChar.isInJail() || targetChar.isInDuel())
				{
					continue;
				}
				
				if (targetChar != activeChar)
				{
					if (targetChar.isInOlympiadMode())
					{
						continue;
					}
				}
			}
			
			Location loc = null;
			if (getSkillType() == L2SkillType.TELEPORT)
			{
				if (_loc != null)
				{
					if (!(target instanceof L2PcInstance) || !target.isFlying())
					{
						loc = _loc;
					}
				}
			}
			else
			{
				if (_recallType.equalsIgnoreCase("Castle"))
				{
					loc = MapRegionTable.getInstance().getTeleToLocation(target, MapRegionTable.TeleportWhereType.CASTLE);
				}
				else if (_recallType.equalsIgnoreCase("ClanHall"))
				{
					loc = MapRegionTable.getInstance().getTeleToLocation(target, MapRegionTable.TeleportWhereType.CLAN_HALL);
				}
			}
			
			if (loc != null)
			{
				if (target instanceof L2PcInstance)
				{
					((L2PcInstance) target).setIsIn7sDungeon(false);
				}
				
				target.teleToLocation(loc, 0);
			}
			else
			{
				int realLoc[] = new int[5];
				switch (target.getActingPlayer().getFactionId())
				{
					case 1:
						realLoc = Config.FACTION_TEAM1_BASE;
						target.teleToLocation(realLoc[0] + Rnd.get(realLoc[3], realLoc[4]), realLoc[1] + Rnd.get(realLoc[3], realLoc[4]), realLoc[2], 0);
						break;
					case 2:
						realLoc = Config.FACTION_TEAM2_BASE;
						target.teleToLocation(realLoc[0] + Rnd.get(realLoc[3], realLoc[4]), realLoc[1] + Rnd.get(realLoc[3], realLoc[4]), realLoc[2], 0);
						break;
					default:
						realLoc = Config.FACTION_NEWBIE_BASE;
						target.teleToLocation(realLoc[0] + Rnd.get(realLoc[3], realLoc[4]), realLoc[1] + Rnd.get(realLoc[3], realLoc[4]), realLoc[2], 0);
						break;
				}
			}
		}
	}
}