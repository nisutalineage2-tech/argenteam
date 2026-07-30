/*
 * This program is free software; you can redistribute it and/or modify
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
package net.sf.l2j.gameserver.model.actor.instance;

import java.util.StringTokenizer;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MyTargetSelected;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.network.serverpackets.ValidateLocation;

/**
 * @author DarthVader
 * @version 1.7
 */
public final class L2GmShopInstance extends L2NpcInstance
{
	public L2GmShopInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2PcInstance player, String command)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken();
		
		int itemid = 0;
		int amount = 0;
		int priceitemid = 0;
		int price = 0;
		if (st.countTokens() == 4)
		{
			itemid = Integer.valueOf(st.nextToken());
			amount = Integer.valueOf(st.nextToken());
			priceitemid = Integer.valueOf(st.nextToken());
			price = Integer.valueOf(st.nextToken());
		}
		
		if (actualCommand.equalsIgnoreCase("buy"))
		{
			if (itemid != 0 && amount != 0 && priceitemid != 0 && price != 0)
			{
				if (player.getInventory().getInventoryItemCount(priceitemid, -1) >= price)
				{
					player.addItem("Shop", itemid, amount, this, true);
					player.destroyItemByItemId("Pay", priceitemid, price, player, true);
					player.sendPacket(new PlaySound("ItemSound.quest_midle"));
					showMessageWindow(player);
				}
				else
					player.sendMessage("Incorrect item count.");
			}
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	@Override
	public void onAction(L2PcInstance player)
	{
		if (this != player.getTarget())
		{
			player.setTarget(this);
			player.sendPacket(new MyTargetSelected(getObjectId(), player.getLevel() - getLevel()));
			player.sendPacket(new ValidateLocation(this));
		}
		else if (isInsideRadius(player, INTERACTION_DISTANCE, false, false))
		{
			SocialAction sa = new SocialAction(this, Rnd.get(8));
			broadcastPacket(sa);
			player.setCurrentFolkNPC(this);
			showMessageWindow(player);
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
		else
		{
			player.getAI().setIntention(CtrlIntention.INTERACT, this);
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
	}
	
	private void showMessageWindow(L2PcInstance player)
	{
		NpcHtmlMessage html = new NpcHtmlMessage(1);
		final StringBuilder strBuffer = new StringBuilder();
		StringUtil.append(strBuffer, "<html><title>L2Exsade.com</title><body><center>");
		
		if (player.getPvpKills() < Config.FACTION_PVP_KILLS_SHOP && !player.isVip())
		{
			player.sendMessage("You need " + Config.FACTION_PVP_KILLS_SHOP + " pvp kills to access this shop.");
			strBuffer.append("Sadly, <font color=\"LEVEL\">%charname%</font>, you're too young.<br>");
			strBuffer.append("You need " + Config.FACTION_PVP_KILLS_SHOP + " pvp kills.<br>");
		}
		else
		{
			strBuffer.append("Hello, <font color=\"LEVEL\">%charname%</font>!<br>");
			strBuffer.append("Glad to see you in my little shop.<br>");
			strBuffer.append("I can offer you S Grade Items.<br>");
			strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
			strBuffer.append("<table width=300>");
			strBuffer.append("<tr><td><font color=\"LEVEL\">Armours</font></td><td><font color=\"LEVEL\">Weapons</font></td><td><font color=\"LEVEL\">Jewels</font></td></tr>");
			strBuffer.append("<tr><td><a action=\"bypass -h npc_%objectId%_multisell 900001\">S Grade</a></td> <td><a action=\"bypass -h npc_%objectId%_multisell 900013\">S Grade</a></td> <td><a action=\"bypass -h npc_%objectId%_multisell 90011\">S Grade</a></td></tr>");
			strBuffer.append("<tr><td><a action=\"bypass -h npc_%objectId%_multisell 30301\">Shields</a></td> <td></td> <td><a action=\"bypass -h npc_%objectId%_multisell 90012\">RB Jewels</a></td></tr>");
			strBuffer.append("</table>");
			strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
		}
		strBuffer.append("</center></body></html>");
		html.setHtml(strBuffer.toString());
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%charname%", player.getName());
		player.sendPacket(html);
	}
}