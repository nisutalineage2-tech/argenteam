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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import net.sf.l2j.commons.concurrent.ThreadPool;

import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.scripting.scripts.ai.L2AttackableAIScript;

public class Donate extends L2AttackableAIScript
{
	public Donate()
	{
		super("faction/ai");
		
		ThreadPool.scheduleAtFixedRate(new Runnable()
		{
			@Override
			public void run()
			{
				loadFromDB();
			}
		}, 60000, 60000);
	}
	
	public void loadFromDB()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement("SELECT * FROM donate_table");
			
			ResultSet result = statement.executeQuery();
			
			String name = "";
			int item_donate = 0;
			int itemId = 0;
			int item_amount = 0;
			int nobless = 0;
			int vip = 0;
			while (result.next())
			{
				name = result.getString("char_name");
				item_donate = result.getInt("item_donate");
				itemId = result.getInt("itemId");
				item_amount = result.getInt("item_amount");
				nobless = result.getInt("nobless_donate");
				vip = result.getInt("vip_donate");
				
				for (L2PcInstance player : World.getInstance().getPlayers())
				{
					if (name.equals(player.getName()) && player.isOnline())
					{
						if (item_donate != 0 & itemId != 0 && item_amount != 0)
						{
							player.addItem("Donate", itemId, item_amount, player, true);
							player.sendMessage("You just received your order.");
							statement.executeUpdate("DELETE FROM donate_table WHERE char_name = '" + player.getName() + "' AND item_donate != 0");
						}
						if (nobless != 0)
						{
							player.setNoble(true, true);
							player.sendMessage("You just received your nobless status.");
							statement.executeUpdate("DELETE FROM donate_table WHERE char_name = '" + player.getName() + "' AND nobless_donate != 0");
						}
						if (vip != 0)
						{
							player.setIsVip(true);
							player.sendMessage("You just received your vip status.");
							statement.executeUpdate("DELETE FROM donate_table WHERE char_name = '" + player.getName() + "' AND vip_donate != 0");
						}
					}
				}
			}
			
			result.close();
			statement.close();
		}
		catch (Exception e)
		{
			// e.printStackTrace();
		}
		finally
		{
			try
			{
				con.close();
			}
			catch (Exception e)
			{
			}
		}
	}
}