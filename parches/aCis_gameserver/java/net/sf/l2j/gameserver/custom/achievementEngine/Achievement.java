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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;

/**
 * @author Matim,Wallister
 * @version v1
 */
public class Achievement
{
	private final int _id;
	private final String _name;
	private final String _reward;
	private String _description = "No Description!";
	private final boolean _repeatable;
	
	private final HashMap<Integer, Long> _rewardList;
	private final ArrayList<Condition> _conditions;
	
	private static Logger _log = Logger.getLogger(Achievement.class.getName());
	
	public Achievement(int id, String name, String description, String reward, boolean repeatable, ArrayList<Condition> conditions)
	{
		_rewardList = new HashMap<>();
		_id = id;
		_name = name;
		_description = description;
		_reward = reward;
		_conditions = conditions;
		_repeatable = repeatable;
		
		createRewardList();
	}
	
	private void createRewardList()
	{
		for (String s : _reward.split(";"))
		{
			if (s == null || s.isEmpty())
				continue;
			
			String[] split = s.split(",");
			Integer item = 0;
			Long count = new Long(0);
			try
			{
				item = Integer.valueOf(split[0]);
				count = Long.valueOf(split[1]);
			}
			catch (NumberFormatException nfe)
			{
				_log.warning("[AchievementsEngine] Error: Wrong reward " + nfe);
			}
			_rewardList.put(item, count);
		}
	}
	
	public boolean meetAchievementRequirements(L2PcInstance player)
	{
		for (Condition c : getConditions())
		{
			if (!c.meetConditionRequirements(player))
			{
				return false;
			}
		}
		return true;
	}
	
	public int getID()
	{
		return _id;
	}
	
	public String getName()
	{
		return _name;
	}
	
	public String getDescription()
	{
		return _description;
	}
	
	public String getReward()
	{
		return _reward;
	}
	
	public boolean isRepeatable()
	{
		return _repeatable;
	}
	
	public HashMap<Integer, Long> getRewardList()
	{
		return _rewardList;
	}
	
	public ArrayList<Condition> getConditions()
	{
		return _conditions;
	}
}