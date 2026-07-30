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
package net.sf.l2j.gameserver.scripting.tasks;

import java.util.Collection;

import net.sf.l2j.commons.concurrent.ThreadPool;

import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.scripting.Quest;

public final class Guard extends Quest implements Runnable
{
	public static final boolean BANNED = true;
	
	public static final boolean MESSAGE = true;
	
	private static final int ITEM_ID_COUNT[][] =
	{
		{
			57,20000

		},
		{		
			5575,3500
		},
		{		
			7485,3500
		}		
		
	};
	
	public Guard()
	{
		super(-1, "tasks");
		
		// Run task 1 min after player login and each 1 minute later.
		ThreadPool.scheduleAtFixedRate(this, 60000, 60000);
	}
	
	@Override
	public final void run()
	{
		Collection<L2PcInstance> players = World.getInstance().getPlayers();
		for (L2PcInstance player : players)
		{
			for (int[] Item : ITEM_ID_COUNT)
			{
				ItemInstance Item0 = player.getInventory().getItemByItemId(Item[0]); ItemInstance Item1 = player.getWarehouse().getItemByItemId(Item[0]);	
				
				if ((Item0 != null) && (Item0.getCount() >= Item[1]))
				{
					if (BANNED)
					{
						if (MESSAGE)
						{
							System.out.println("Guard; Banned account: " + player.getAccountName() + " name: " + player.getName() + " reason: incorrect quantity of items.");
						}
						
						player.setAccountAccesslevel(-100);
						player.logout();
					}
				}
				else if ((Item1 != null) && (Item1.getCount() >= Item[1]))
				{
					if (BANNED)
					{
						if (MESSAGE)
						{
							System.out.println("Guard; Banned account: " + player.getAccountName() + " name: " + player.getName() + " reason: incorrect quantity of items.");
						}
						
						player.setAccountAccesslevel(-100);
						player.logout();
					}					
				}
			}
		}
	}
}
