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
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.network.serverpackets.ValidateLocation;
import net.sf.l2j.gameserver.taskmanager.AttackStanceTaskManager;

/**
 * @author DarthVader
 * @version 1.3
 */

public class L2ClawTpInstance extends L2NpcInstance
{
	public L2ClawTpInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2PcInstance player, String command)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken();
		String where = "";
		if (st.countTokens() == 1)
		{
			where = st.nextToken();
		}
		if (actualCommand.equalsIgnoreCase("tp"))
		{
			
			int realLoc[] = new int[5];
			if (where.equals("faction1"))
			{
				realLoc = Config.FACTION_TEAM1_BASE;
				player.teleToLocation(realLoc[0] + Rnd.get(realLoc[3], realLoc[4]), realLoc[1] + Rnd.get(realLoc[3], realLoc[4]), realLoc[2], 0);
			}
			else if (where.equals("faction2"))
			{
				realLoc = Config.FACTION_TEAM2_BASE;
				player.teleToLocation(realLoc[0] + Rnd.get(realLoc[3], realLoc[4]), realLoc[1] + Rnd.get(realLoc[3], realLoc[4]), realLoc[2], 0);
			}
			else
			{
				player.sendMessage("Location not found! Please inform administrator about this issue.");
			}
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
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
		StringUtil.append(strBuffer, "<html><title>Home GateKeeper</title><body><center>");
		if (AttackStanceTaskManager.getInstance().isInAttackStance(player) && !player.isGM())
		{
			player.sendMessage("You can't use gatekeeper while you're in combat.");
			strBuffer.append("Sadly, <font color=\"LEVEL\">%charname%</font>, I can't serve you.<br>");
			strBuffer.append("Came back when you will not be in a combat.<br>");
		}
		else
		{
			strBuffer.append("Welcome, <font color=\"LEVEL\">%charname%</font>!<br>");
			strBuffer.append("I can teleport you to other faction home bases.<br>");
			strBuffer.append("Don't worry, you won't die in peace zone. ;)<br>");
			strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
			strBuffer.append("<button value=\"" + Config.FACTION_TEAM1_NAME + " Homeland\" action=\"bypass -h npc_%objectId%_tp faction1\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
			strBuffer.append("<button value=\"" + Config.FACTION_TEAM2_NAME + " Homeland\" action=\"bypass -h npc_%objectId%_tp faction2\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
			strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
		}
		strBuffer.append("</center></body></html>");
		html.setHtml(strBuffer.toString());
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%charname%", player.getName());
		player.sendPacket(html);
	}
}
