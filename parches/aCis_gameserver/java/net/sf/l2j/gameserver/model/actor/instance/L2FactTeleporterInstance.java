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

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.custom.entity.FactionMaps;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MyTargetSelected;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.network.serverpackets.ValidateLocation;

/**
 * @author DarthVader
 */

public class L2FactTeleporterInstance extends L2NpcInstance
{
	public L2FactTeleporterInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	public static Set<L2TpFlagInstance> _tpTeam1Flags = new HashSet<>();
	public static Set<L2TpFlagInstance> _tpTeam2Flags = new HashSet<>();
	
	public static Set<L2TpFlagInstance> _not_captured = new HashSet<>();
	public static Set<L2ProtectorInstance> _guards = new HashSet<>();
	public static Set<L2NpcInstance> _blazers = new HashSet<>();
	public static Set<L2GrandBossInstance> _bosses = new HashSet<>();
	
	@Override
	public void onBypassFeedback(L2PcInstance player, String command)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken();
		int val = 0;
		if (st.countTokens() >= 1)
		{
			val = Integer.valueOf(st.nextToken());
		}
		
		if (actualCommand.equalsIgnoreCase("teletoflag"))
		{
			teleToFlag(val, player);
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
	
	public void teleToFlag(int objId, L2PcInstance player)
	{
		L2Object object = World.getInstance().getObject(objId);
		if (object != null && object instanceof L2TpFlagInstance)
		{
			L2TpFlagInstance flagbase = (L2TpFlagInstance) object;
			if (player.getFactionId() == flagbase.getFlagFactionId())
			{
				player.teleToLocation(flagbase.getX() + 50, flagbase.getY(), flagbase.getZ(), 0);
			}
		}
		else
		{
			player.sendMessage("Base location is not accessable due to occupation.");
			return;
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
		StringUtil.append(strBuffer, "<html><title>Faction Teleporter</title><body><center>");
		if (player.getFactionId() == 0)
		{
			strBuffer.append("SHIT HAPPENS");
			strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
			strBuffer.append("Please chose your faction and then come back!<br>");
			strBuffer.append("I am sure, that Faction manager will help you.<br>");
		}
		else if (FactionMaps.isVoting())
		{
			strBuffer.append("Map voting is in progress.<br>");
			strBuffer.append("Try again in <font color=\"LEVEL\">4 minute.</font><br>");
		}
		else if (player.isInOlympiadMode())
		{
			strBuffer.append("SHIT HAPPENS");
			strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
			strBuffer.append("I can't teleport you, because you're participating in olympiad!<br>");
		}
		else
		{
			strBuffer.append("Current map is: <font color=\"LEVEL\">" + FactionMaps.getMapName() + "</font><br>");
			strBuffer.append("Voting for the next map will begin in: " + FactionMaps.getDelayUntilVoting() + "<br>");
			strBuffer.append("Unoccupyable flags belonging to <font color=\"LEVEL\">%faction%</font> faction:<br>");
			strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
			switch (player.getFactionId())
			{
				case 1:
					for (L2TpFlagInstance unFlag : _tpTeam1Flags)
					{
						if (unFlag.isUnoccupayable() == 1)
						{
							strBuffer.append("<button value=\"" + unFlag.getFlagName() + "\" action=\"bypass -h npc_%objectId%_teletoflag " + unFlag.getObjectId() + "\" width=170 height=20 back=\"L2UI_CH3.refinegrade3_21\" fore=\"L2UI_CH3.refinegrade3_21\">");
						}
					}
					break;
				case 2:
					for (L2TpFlagInstance unFlag : _tpTeam2Flags)
					{
						if (unFlag.isUnoccupayable() == 1)
						{
							strBuffer.append("<button value=\"" + unFlag.getFlagName() + "\" action=\"bypass -h npc_%objectId%_teletoflag " + unFlag.getObjectId() + "\" width=170 height=20 back=\"L2UI_CH3.refinegrade3_21\" fore=\"L2UI_CH3.refinegrade3_21\">");
						}
					}
					break;
				default:
					strBuffer.append("No Flags found.");
					break;
			}
			strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
			strBuffer.append("Flags occupied by <font color=\"LEVEL\">%faction%</font> faction:<br>");
			switch (player.getFactionId())
			{
				case 1:
					for (L2TpFlagInstance unFlag : _tpTeam1Flags)
					{
						if (unFlag.isUnoccupayable() == 0)
						{
							strBuffer.append("<button value=\"" + unFlag.getFlagName() + "\" action=\"bypass -h npc_%objectId%_teletoflag " + unFlag.getObjectId() + "\" width=170 height=20 back=\"L2UI_CH3.refinegrade3_21\" fore=\"L2UI_CH3.refinegrade3_21\">");
						}
					}
					break;
				case 2:
					for (L2TpFlagInstance unFlag1 : _tpTeam2Flags)
					{
						if (unFlag1.isUnoccupayable() == 0)
						{
							strBuffer.append("<button value=\"" + unFlag1.getFlagName() + "\" action=\"bypass -h npc_%objectId%_teletoflag " + unFlag1.getObjectId() + "\" width=170 height=20 back=\"L2UI_CH3.refinegrade3_21\" fore=\"L2UI_CH3.refinegrade3_21\">");
						}
					}
					break;
				default:
					strBuffer.append("No Flags found.");
					break;
			}
		}
		strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
		strBuffer.append("<img src=\"botoes.gk\" width=256 height=256 align=left>");

		strBuffer.append("</center></body></html>");
		html.setHtml(strBuffer.toString());
		html.replace("%objectId%", String.valueOf(getObjectId()));
		if (player.getFactionId() == 1)
		{
			html.replace("%faction%", Config.FACTION_TEAM1_NAME);
		}
		else if (player.getFactionId() == 2)
		{
			html.replace("%faction%", Config.FACTION_TEAM2_NAME);
		}
		else
		{
			html.replace("%faction%", "No Faction");
		}
		html.replace("%npcname%", getName());
		player.sendPacket(html);
	}
}