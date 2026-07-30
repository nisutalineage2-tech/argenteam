/* This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package net.sf.l2j.gameserver.custom.achievementEngine;

import java.util.Iterator;
import java.util.Map;

import net.sf.l2j.gameserver.instancemanager.RaidBossPointsManager;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;

public class RaidKill extends Condition
{
	public RaidKill(Object value)
	{
		super(value);
		setName("Raid Kill");
	}
	
	@Override
	public boolean meetConditionRequirements(L2PcInstance player)
	{
		if (getValue() == null)
		{
			return false;
		}
		int val = Integer.parseInt(getValue().toString());
		Map<Integer, Integer> list = RaidBossPointsManager.getInstance().getList(player);
		Iterator<Integer> i$;
		if (list != null)
		{
			for (i$ = list.keySet().iterator(); i$.hasNext();)
			{
				int bid = i$.next().intValue();
				
				if (bid == val)
				{
					if (RaidBossPointsManager.getInstance().getList(player).get(Integer.valueOf(bid)).intValue() > 0)
						return true;
				}
			}
		}
		return false;
	}
}