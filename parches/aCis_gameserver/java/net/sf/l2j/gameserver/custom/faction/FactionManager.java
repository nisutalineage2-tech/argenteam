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
package net.sf.l2j.gameserver.custom.faction;

import java.sql.Connection;
import java.sql.PreparedStatement;

import net.sf.l2j.Config;
import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.base.Experience;
import net.sf.l2j.gameserver.model.group.Party.MessageType;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.network.serverpackets.TutorialCloseHtml;
import net.sf.l2j.gameserver.network.serverpackets.TutorialShowHtml;

/**
 * @author Erlando
 */
public class FactionManager
{
	public void showTutorialChooseWindow(L2PcInstance player)
	{
		/*
		 * String html; final StringBuilder strBuffer = StringUtil.startAppend(3500, "<html><title>Faction Manager</title><body><center>"); int _team1_on = World.getInstance().getAllteam1Players().size(); int _team2_on = World.getInstance().getAllteam2Players().size(); int _team3_on =
		 * World.getInstance().getAllteam3Players().size(); if (player.getFactionId() < 1) { strBuffer.append("Welcome to Triple Faction World, <font color=\"LEVEL\">%charname%</font>.<br>"); strBuffer.append("This is the place, to chose your destiny!<br>");
		 * strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>"); } else { strBuffer.append("You belong to: <font color=\"LEVEL\">%faction%</font> faction.<br>"); if (Config.FACTION_CHANGE_PRICE > 0)
		 * strBuffer.append("Price for changing faction: <font color=\"LEVEL\">" + Config.FACTION_CHANGE_PRICE + " Adena</font><br>"); if (player.isVip()) strBuffer.append("But as you're VIP, I won't take any Adenas from you."); strBuffer.append("Currently available factions to chose:<br>");
		 * strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>"); } switch (player.getFactionId()) { case 1: strBuffer.append("<button value=\"" + Config.FACTION_TEAM2_NAME +
		 * " [On: "+_team2_on+"]\" action=\"link cf 2\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">"); strBuffer.append("<button value=\"" + Config.FACTION_TEAM3_NAME +
		 * " [On: "+_team3_on+"]\" action=\"link cf 3\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">"); break; case 2: strBuffer.append("<button value=\"" + Config.FACTION_TEAM1_NAME +
		 * " [On: "+_team1_on+"]\" action=\"link cf 1\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">"); strBuffer.append("<button value=\"" + Config.FACTION_TEAM3_NAME +
		 * " [On: "+_team3_on+"]\" action=\"link cf 3\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">"); break; case 3: strBuffer.append("<button value=\"" + Config.FACTION_TEAM1_NAME +
		 * " [On: "+_team1_on+"]\" action=\"link cf 1\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">"); strBuffer.append("<button value=\"" + Config.FACTION_TEAM2_NAME +
		 * " [On: "+_team2_on+"]\" action=\"link cf 2\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">"); break; default: strBuffer.append("<button value=\"" + Config.FACTION_TEAM1_NAME +
		 * " [On: "+_team1_on+"]\" action=\"link cf 1\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">"); strBuffer.append("<button value=\"" + Config.FACTION_TEAM2_NAME +
		 * " [On: "+_team2_on+"]\" action=\"link cf 2\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">"); strBuffer.append("<button value=\"" + Config.FACTION_TEAM3_NAME +
		 * " [On: "+_team3_on+"]\" action=\"link cf 3\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">"); break; } strBuffer.append("<img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left>"); strBuffer.append("</center></body></html>"); html =
		 * strBuffer.toString(); if (player.getFactionId() == 1) html.replace("%faction%", Config.FACTION_TEAM1_NAME); else if (player.getFactionId() == 2) html.replace("%faction%", Config.FACTION_TEAM2_NAME); else if (player.getFactionId() == 3) html.replace("%faction%", Config.FACTION_TEAM3_NAME);
		 * else html.replace("%faction%", "No Faction"); html.replace("%charname%", player.getName());
		 */
		String html = "<html><body><title>Labas</title>Ate suski</body></html>";
		player.sendPacket(new TutorialShowHtml(html));
	}
	
	public void useBypass(L2PcInstance player, String bypass)
	{
		if (bypass.startsWith("cf"))
		{
			int val = Integer.parseInt(bypass.substring(3));
			int team1Supports = World.getInstance().getTeam1Supports();
			int team2Supports = World.getInstance().getTeam2Supports();
			
			int team1Count = World.getInstance().getAllteam1Players().size();
			int team2Count = World.getInstance().getAllteam2Players().size();
			
			if (player.getAdena() >= Config.FACTION_CHANGE_PRICE || player.getFactionId() == 0)
			{
				switch (val)
				{
					case 1:
					{
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
						if (Config.FACTION_ENABLE_SPEAKS)
						{
							player.broadcastPacket(new CreatureSay(player.getObjectId(), 0, player.getName(), Config.FACTION_TEAM1_PLAYER_VC.replace("%n", player.getName().toString())));
						}
						break;
					}
					case 2:
					{
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
						if (Config.FACTION_ENABLE_SPEAKS)
						{
							player.broadcastPacket(new CreatureSay(player.getObjectId(), 0, player.getName(), Config.FACTION_TEAM2_PLAYER_VC.replace("%n", player.getName().toString())));
						}
						break;
					}
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
			MagicSkillUse MSU = new MagicSkillUse(player, player, 2024, 1, 1, 0);
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
			player.sendPacket(TutorialCloseHtml.STATIC_PACKET);
		}
	}
	
	public static final FactionManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final FactionManager _instance = new FactionManager();
	}
}