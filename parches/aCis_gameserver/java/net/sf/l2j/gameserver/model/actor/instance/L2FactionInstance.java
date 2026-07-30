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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.StringTokenizer;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.base.Experience;
import net.sf.l2j.gameserver.model.group.Party.MessageType;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.MyTargetSelected;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.network.serverpackets.ValidateLocation;

public final class L2FactionInstance extends L2NpcInstance
{
	public L2FactionInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
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
		
		if (actualCommand.equalsIgnoreCase("setfaction"))
		{
			int team1Supports = World.getInstance().getTeam1Supports();
			int team2Supports = World.getInstance().getTeam2Supports();
			
			int team1Count = World.getInstance().getAllteam1Players().size();
			int team2Count = World.getInstance().getAllteam2Players().size();
			
			if (player.getAdena() >= Config.FACTION_CHANGE_PRICE || player.getFactionId() == 0)
			{
				switch (val)
				{
					case 1:
						switch (player.getFactionId())
						{
							case 1:
								player.sendMessage("You already belong to " + Config.FACTION_TEAM1_NAME + " faction.");
								player.sendPacket(ActionFailed.STATIC_PACKET);
								return;
							case 2:
								if (team1Count >= team2Count)
								{
									player.sendMessage("Too many players in " + Config.FACTION_TEAM1_NAME + " faction. Come back later.");
									player.sendPacket(ActionFailed.STATIC_PACKET);
									return;
								}
								if (Config.FACTION_ENABLE_CLASS_BLNC && (player.getClassId().getId() == 15 || player.getClassId().getId() == 16 || player.getClassId().getId() == 17 || player.getClassId().getId() == 29 || player.getClassId().getId() == 30 || player.getClassId().getId() == 42 || player.getClassId().getId() == 43 || player.getClassId().getId() == 112 || player.getClassId().getId() == 105 || player.getClassId().getId() == 98 || player.getClassId().getId() == 97) && team1Supports > team2Supports)
								{
									player.sendMessage("Too many support classes in " + Config.FACTION_TEAM1_NAME + " faction. Come back later.");
									player.sendPacket(ActionFailed.STATIC_PACKET);
									return;
								}
								if (!player.isVip())
								{
									player.reduceAdena("pay", Config.FACTION_CHANGE_PRICE, player, true);
								}
								World.getInstance().getAllTeam2().remove(player.getName().toLowerCase());
								World.getInstance().getAllTeam1().put(player.getName().toLowerCase(), player);
								player.sendMessage("[" + Config.FACTION_TEAM2_NAME + "] Bye bye, traitor!");
								player.sendMessage("[" + Config.FACTION_TEAM1_NAME + "] Welcome to our new hero!");
								break;
							default:
								if (team1Count > team2Count)
								{
									player.sendMessage("Too many players in " + Config.FACTION_TEAM1_NAME + " faction. Please select another faction.");
									player.sendPacket(ActionFailed.STATIC_PACKET);
									return;
								}
								World.getInstance().getAllTeam1().put(player.getName().toLowerCase(), player);
								player.sendMessage("[" + Config.FACTION_TEAM1_NAME + "] Welcome to our new hero!");
								break;
						}
						if (player.getParty() != null)
						{
							player.getParty().removePartyMember(player, MessageType.EXPELLED);
						}
						player.getAppearance().setNameColor(Config.FACTION_TEAM1_COLOR);
						if (!player.isVip())
						{
							player.getAppearance().setTitleColor(Config.FACTION_TEAM1_COLOR);
						}
						else
						{
							player.getAppearance().setTitleColor(0x00CCFF);
						}
						player.broadcastUserInfo();
													if(Config.AURA_TEAM_ENABLE){
															player.setTeam(2);
														}						
						if (Config.FACTION_ENABLE_SPEAKS)
						{
							broadcastPacket(new CreatureSay(getObjectId(), 0, String.valueOf(getName()), Config.FACTION_TEAM1_NPC_VC.replace("%n", player.getName().toString())));
							player.broadcastPacket(new CreatureSay(player.getObjectId(), 0, player.getName(), Config.FACTION_TEAM1_PLAYER_VC.replace("%n", player.getName().toString())));
						}
						break;
					case 2:
						switch (player.getFactionId())
						{
							case 1:
								if (team2Count >= team1Count)
								{
									player.sendMessage("Too many players in " + Config.FACTION_TEAM2_NAME + " faction. Come back later.");
									player.sendPacket(ActionFailed.STATIC_PACKET);
									return;
								}
								if (Config.FACTION_ENABLE_CLASS_BLNC && (player.getClassId().getId() == 15 || player.getClassId().getId() == 16 || player.getClassId().getId() == 17 || player.getClassId().getId() == 29 || player.getClassId().getId() == 30 || player.getClassId().getId() == 42 || player.getClassId().getId() == 43 || player.getClassId().getId() == 112 || player.getClassId().getId() == 105 || player.getClassId().getId() == 98 || player.getClassId().getId() == 97) && team2Supports > team1Supports)
								{
									player.sendMessage("Too many support classes in " + Config.FACTION_TEAM2_NAME + " faction. Come back later.");
									player.sendPacket(ActionFailed.STATIC_PACKET);
									return;
								}
								if (!player.isVip())
								{
									player.reduceAdena("pay", Config.FACTION_CHANGE_PRICE, player, true);
								}
								World.getInstance().getAllTeam1().remove(player.getName().toLowerCase());
								World.getInstance().getAllTeam2().put(player.getName().toLowerCase(), player);
								player.sendMessage("[" + Config.FACTION_TEAM1_NAME + "] Goodbye!");
								player.sendMessage("[" + Config.FACTION_TEAM2_NAME + "] For Faction Glory!");
								break;
							case 2:
								player.sendMessage("You already belong to " + Config.FACTION_TEAM2_NAME + " faction.");
								player.sendPacket(ActionFailed.STATIC_PACKET);
								return;
							default:
								if (team2Count > team1Count)
								{
									player.sendMessage("Too many players in " + Config.FACTION_TEAM2_NAME + " faction. Please select another faction.");
									player.sendPacket(ActionFailed.STATIC_PACKET);
									return;
								}
								World.getInstance().getAllTeam2().put(player.getName().toLowerCase(), player);
								player.sendMessage("[" + Config.FACTION_TEAM2_NAME + "] For Faction Glory!");
								break;
						}
						if (player.getParty() != null)
						{
							player.getParty().removePartyMember(player, MessageType.EXPELLED);
						}
						player.getAppearance().setNameColor(Config.FACTION_TEAM2_COLOR);
						if (!player.isVip())
						{
							player.getAppearance().setTitleColor(Config.FACTION_TEAM2_COLOR);
						}
						else
						{
							player.getAppearance().setTitleColor(0x00CCFF);
						}
						player.broadcastUserInfo();
													if(Config.AURA_TEAM_ENABLE){
														player.setTeam(1);
														}						
						if (Config.FACTION_ENABLE_SPEAKS)
						{
							broadcastPacket(new CreatureSay(getObjectId(), 0, String.valueOf(getName()), Config.FACTION_TEAM2_NPC_VC.replace("%n", player.getName().toString())));
							player.broadcastPacket(new CreatureSay(player.getObjectId(), 0, player.getName(), Config.FACTION_TEAM2_PLAYER_VC.replace("%n", player.getName().toString())));
						}
						break;
					default:
						return;
				}
			}
			else
			{
				player.sendMessage("Not enought adena.");
				player.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			player.broadcastPacket(new SocialAction(player, 3));
			MagicSkillUse MSU = new MagicSkillUse(player, player, 86, 3, 2, 0);
			player.broadcastPacket(MSU);
			
			Connection connection = null;
			try
			{
				connection = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE characters SET factionId=? WHERE char_name=?");
				statement.setInt(1, val);
				statement.setString(2, player.getName());
				statement.execute();
				statement.close();
				connection.close();
			}
			catch (Exception e)
			{
				System.out.println("Couldn't set player faction:" + val + " " + e);
			}
			finally
			{
				try
				{
					connection.close();
				}
				catch (Exception e)
				{
				}
			}
			
			player.setFactionId(val);
			player.broadcastUserInfo();
			if (player.getLevel() < 5)
			{
				player.addExpAndSp(Experience.LEVEL[Config.FACTION_START_LVL], 0);
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
		StringUtil.append(strBuffer, "<html><title>Faction Manager</title><body><center>");
		
		int _team1_on = World.getInstance().getAllteam1Players().size();
		int _team2_on = World.getInstance().getAllteam2Players().size();
		
		if (player.getFactionId() < 1)
		{
			strBuffer.append("Welcome to Fast vs FuriousGvE, <font color=\"LEVEL\">%charname%</font>.<br>");
			strBuffer.append("This is the place, to chose your destiny!<br>");
			strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
		}
		else
		{
			strBuffer.append("You belong to: <font color=\"LEVEL\">%faction%</font> faction.<br>");
			if (Config.FACTION_CHANGE_PRICE > 0)
			{
				strBuffer.append("Price for changing faction: <font color=\"LEVEL\">" + Config.FACTION_CHANGE_PRICE + " Adena</font><br>");
			}
			if (player.isVip())
			{
				strBuffer.append("But as you're VIP, I won't take any Adenas from you.");
			}
			strBuffer.append("Currently available factions to chose:<br>");
			strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
		}
		switch (player.getFactionId())
		{
			case 1:
				strBuffer.append("<button value=\"" + Config.FACTION_TEAM2_NAME + " [On: " + _team2_on + "]\" action=\"bypass -h npc_%objectId%_setfaction 2\" width=170 height=20 back=\"L2UI_CH3.refinegrade3_21\" fore=\"L2UI_CH3.refinegrade3_21\">");
				break;
			case 2:
				strBuffer.append("<button value=\"" + Config.FACTION_TEAM1_NAME + " [On: " + _team1_on + "]\" action=\"bypass -h npc_%objectId%_setfaction 1\" width=170 height=20 back=\"L2UI_CH3.refinegrade3_21\" fore=\"L2UI_CH3.refinegrade3_21\">");
				break;
			case 3:
				strBuffer.append("<button value=\"" + Config.FACTION_TEAM1_NAME + " [On: " + _team1_on + "]\" action=\"bypass -h npc_%objectId%_setfaction 1\" width=170 height=20 back=\"L2UI_CH3.refinegrade3_21\" fore=\"L2UI_CH3.refinegrade3_21\">");
				strBuffer.append("<button value=\"" + Config.FACTION_TEAM2_NAME + " [On: " + _team2_on + "]\" action=\"bypass -h npc_%objectId%_setfaction 2\" width=170 height=20 back=\"L2UI_CH3.refinegrade3_21\" fore=\"L2UI_CH3.refinegrade3_21\">");
				break;
			default:
				strBuffer.append("<button value=\"" + Config.FACTION_TEAM1_NAME + " [On: " + _team1_on + "]\" action=\"bypass -h npc_%objectId%_setfaction 1\" width=170 height=20 back=\"L2UI_CH3.refinegrade3_21\" fore=\"L2UI_CH3.refinegrade3_21\">");
				strBuffer.append("<button value=\"" + Config.FACTION_TEAM2_NAME + " [On: " + _team2_on + "]\" action=\"bypass -h npc_%objectId%_setfaction 2\" width=170 height=20 back=\"L2UI_CH3.refinegrade3_21\" fore=\"L2UI_CH3.refinegrade3_21\">");
				break;
		}
		strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>");
		strBuffer.append("<img src=\"town_map.town_map_darkelf_t00\" width=300 height=132 align=left>");
		strBuffer.append("<img src=\"SSQ_dungeon_T.SSQ_fire1_e013\" width=256 height=64 align=left>");		
		strBuffer.append("</center></body></html>");
		html.setHtml(strBuffer.toString());
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
		html.replace("%objectId%", String.valueOf(getObjectId()));
		html.replace("%npcname%", getName());
		html.replace("%charname%", player.getName());
		player.sendPacket(html);
	}
}