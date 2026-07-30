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
package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.commons.lang.StringUtil;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.item.type.CrystalType;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;

public class EnchantInfo implements IVoicedCommandHandler
{
	private static final String[] _voicedCommands =
	{
		"info"
	};
	
	@Override
	public boolean useVoicedCommand(String command, L2PcInstance activeChar, String target)
	{
		if (command.equalsIgnoreCase("info"))
		{
			if (activeChar.getFactionId() > 0)
			{
				if (activeChar.getCurrentEnItem() != null)
				{
					
					int killsleft = 0;
					if (activeChar.getCurrentEnItem().getItem().getCrystalType() == CrystalType.B)
						killsleft = Config.FACTION_ENCHANT_B - activeChar.getEnchantCnt();
					else if (activeChar.getCurrentEnItem().getItem().getCrystalType() == CrystalType.A)
						killsleft = Config.FACTION_ENCHANT_A - activeChar.getEnchantCnt();
					else
						killsleft = Config.FACTION_ENCHANT_S - activeChar.getEnchantCnt();
					
					NpcHtmlMessage html = new NpcHtmlMessage(1);
					final StringBuilder strBuffer = new StringBuilder();
					StringUtil.append(strBuffer, "<html><title>Enchanting</title><body>");
					strBuffer.append("<center>Selected item even unequiped will be enchanted.<br></center>");
					strBuffer.append("<center><img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left></center>");
					strBuffer.append("Your selected item: <font color=\"LEVEL\">" + activeChar.getCurrentEnItem().getItemName() + "</font><br>");
					if (activeChar.getCurrentEnItem().getItem().getCrystalType() == CrystalType.B)
					{
						strBuffer.append("Grade: <font color=\"LEVEL\">B</font>");
						if (killsleft >= 0)
							strBuffer.append("<br>Kills information: <font color=\"LEVEL\">Left: " + killsleft + " / Needed: " + Config.FACTION_ENCHANT_B + "</font>");
						else
							strBuffer.append("<br>Item will be enchanted after next kill.");
					}
					else if (activeChar.getCurrentEnItem().getItem().getCrystalType() == CrystalType.A)
					{
						strBuffer.append("Grade: <font color=\"LEVEL\">A</font>");
						if (killsleft >= 0)
							strBuffer.append("<br>Kills information: <font color=\"LEVEL\">Left: " + killsleft + " / Needed: " + Config.FACTION_ENCHANT_A + "</font>");
						else
							strBuffer.append("<br>Item will be enchanted after next kill.");
					}
					else
					{
						strBuffer.append("Grade: <font color=\"LEVEL\">S</font>");
						if (killsleft >= 0)
							strBuffer.append("<br>Kills information: <font color=\"LEVEL\">Left: " + killsleft + " / Needed: " + Config.FACTION_ENCHANT_S + "</font>");
						else
							strBuffer.append("<br>Item will be enchanted after next kill.");
					}
					strBuffer.append("<center><img src=\"L2UI_CH3.onscrmsg_pattern01_2\" width=300 height=32 align=left></center>");
					strBuffer.append("</body></html>");
					html.setHtml(strBuffer.toString());
					activeChar.sendPacket(html);
				}
				else
				{
					activeChar.sendPacket(new PlaySound("monsound5.frintessa_shout2"));
					activeChar.sendMessage("Item for auto enchantment is not selected!");
				}
			}
			else
			{
				activeChar.sendPacket(new PlaySound("monsound5.frintessa_shout2"));
				activeChar.sendMessage("Please chose your faction at Faction Manager!");
			}
		}
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return _voicedCommands;
	}
}