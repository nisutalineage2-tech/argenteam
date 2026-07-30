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
package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.gameserver.datatables.ArmorSetsTable;
import net.sf.l2j.gameserver.datatables.ItemTable;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.item.ArmorSet;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.network.serverpackets.ItemList;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * This class handles following admin commands:<br>
 * <br>
 * - itemcreate = show "item creation" menu<br>
 * - create_item = creates num items with respective id, if num is not specified, assumes 1.<br>
 * - create_set = creates armorset with respective chest id.<br>
 * - create_coin = creates currency, using the choice box or typing good IDs.<br>
 * - reward_all = reward all online players with items.
 */
public class AdminCreateItem implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_itemcreate",
		"admin_create_item",
		"admin_create_set",
		"admin_create_coin",
		"admin_reward_all"
	};
	
	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar)
	{
		StringTokenizer st = new StringTokenizer(command);
		command = st.nextToken();
		
		if (command.equals("admin_itemcreate"))
		{
			AdminHelpPage.showHelpPage(activeChar, "itemcreation.htm");
		}
		else if (command.equals("admin_reward_all"))
		{
			try
			{
				final int id = Integer.parseInt(st.nextToken());
				final int count = (st.hasMoreTokens()) ? Integer.parseInt(st.nextToken()) : 1;
				
				final Collection<L2PcInstance> players = World.getInstance().getPlayers();
				for (L2PcInstance player : players)
					createItem(activeChar, player, id, count, 0, false);
				
				activeChar.sendMessage(players.size() + " players rewarded with " + ItemTable.getInstance().getTemplate(id).getName());
			}
			catch (Exception e)
			{
				activeChar.sendMessage("Usage: //reward_all <itemId> [amount]");
			}
			AdminHelpPage.showHelpPage(activeChar, "itemcreation.htm");
		}
		else
		{
			L2PcInstance target = activeChar;
			if (activeChar.getTarget() != null && activeChar.getTarget() instanceof L2PcInstance)
				target = (L2PcInstance) activeChar.getTarget();
			
			if (command.equals("admin_create_item"))
			{
				try
				{
					final int id = Integer.parseInt(st.nextToken());
					
					int count = 1;
					int radius = 0;
					
					if (st.hasMoreTokens())
					{
						count = Integer.parseInt(st.nextToken());
						if (st.hasMoreTokens())
							radius = Integer.parseInt(st.nextToken());
					}
					
					createItem(activeChar, target, id, count, radius, true);
				}
				catch (Exception e)
				{
					activeChar.sendMessage("Usage: //create_item <itemId> [amount] [radius]");
				}
				AdminHelpPage.showHelpPage(activeChar, "itemcreation.htm");
			}
			else if (command.equals("admin_create_coin"))
			{
				try
				{
					final int id = getCoinId(st.nextToken());
					if (id <= 0)
					{
						activeChar.sendMessage("Usage: //create_coin <name> [amount]");
						return false;
					}
					
					createItem(activeChar, target, id, (st.hasMoreTokens()) ? Integer.parseInt(st.nextToken()) : 1, 0, true);
				}
				catch (Exception e)
				{
					activeChar.sendMessage("Usage: //create_coin <name> [amount]");
				}
				AdminHelpPage.showHelpPage(activeChar, "itemcreation.htm");
			}
			else if (command.equals("admin_create_set"))
			{
				// More tokens means you try to use the command directly with a chestId.
				if (st.hasMoreTokens())
				{
					try
					{
						final ArmorSet set = ArmorSetsTable.getInstance().getSet(Integer.parseInt(st.nextToken()));
						if (set == null)
						{
							activeChar.sendMessage("This chest has no set.");
							return false;
						}
						
						for (int itemId : set.getSetItemsId())
						{
							if (itemId > 0)
								target.getInventory().addItem("Admin", itemId, 1, target, activeChar);
						}
						
						if (set.getShield() > 0)
							target.getInventory().addItem("Admin", set.getShield(), 1, target, activeChar);
						
						activeChar.sendMessage("You have spawned " + set.toString() + " in " + target.getName() + "'s inventory.");
						
						// Send the whole item list and open inventory window.
						target.sendPacket(new ItemList(target, true));
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Usage: //create_set <chestId>");
					}
				}
				
				// Regular case (first HTM with all possible sets).
				int i = 0;
				
				final StringBuilder sb = new StringBuilder();
				for (ArmorSet set : ArmorSetsTable.getInstance().getSets())
				{
					final boolean isNextLine = i % 2 == 0;
					if (isNextLine)
						sb.append("<tr>");
					
					sb.append("<td><a action=\"bypass -h admin_create_set " + set.getSetItemsId()[0] + "\">" + set.toString() + "</a></td>");
					
					if (isNextLine)
						sb.append("</tr>");
					
					i++;
				}
				
				final NpcHtmlMessage html = new NpcHtmlMessage(0);
				html.setFile("data/html/admin/itemsets.htm");
				html.replace("%sets%", sb.toString());
				activeChar.sendPacket(html);
			}
		}
		return true;
	}
	
	private static void createItem(L2PcInstance activeChar, L2PcInstance target, int id, int num, int radius, boolean sendGmMessage)
	{
		final Item template = ItemTable.getInstance().getTemplate(id);
		if (template == null)
		{
			activeChar.sendMessage("This item doesn't exist.");
			return;
		}
		
		if (num > 1 && !template.isStackable())
		{
			activeChar.sendMessage("This item doesn't stack - Creation aborted.");
			return;
		}
		
		if (radius > 0)
		{
			final List<L2PcInstance> players = activeChar.getKnownTypeInRadius(L2PcInstance.class, radius);
			for (L2PcInstance obj : players)
			{
				obj.addItem("Admin", id, num, activeChar, false);
				obj.sendMessage("A GM spawned " + num + " " + template.getName() + " in your inventory.");
			}
			
			if (sendGmMessage)
				activeChar.sendMessage(players.size() + " players rewarded with " + num + " " + template.getName() + " in a " + radius + " radius.");
		}
		else
		{
			target.getInventory().addItem("Admin", id, num, target, activeChar);
			if (activeChar != target)
				target.sendMessage("A GM spawned " + num + " " + template.getName() + " in your inventory.");
			
			if (sendGmMessage)
				activeChar.sendMessage("You have spawned " + num + " " + template.getName() + " (" + id + ") in " + target.getName() + "'s inventory.");
			
			// Send the whole item list and open inventory window.
			target.sendPacket(new ItemList(target, true));
		}
	}
	
	private static int getCoinId(String name)
	{
		if (name.equalsIgnoreCase("adena"))
			return 57;
		
		if (name.equalsIgnoreCase("ancientadena"))
			return 5575;
		
		if (name.equalsIgnoreCase("festivaladena"))
			return 6673;
		
		return 0;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}