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

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.custom.entity.FactionMaps;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;

public class Points implements IVoicedCommandHandler
{
	private static final String[] _voicedCommands =
	{
		"points"
	};
	
	@Override
	public boolean useVoicedCommand(String command, L2PcInstance activeChar, String target)
	{
		if (command.equalsIgnoreCase("points"))
		{
			if (activeChar.getFactionId() > 0)
			{
				activeChar.sendMessage("--------------------------------------------------------------");
				activeChar.sendMessage("Your points in this round: [" + activeChar.getCurrentPts() + "]");
				activeChar.sendMessage("Your overtime points: [" + activeChar.getTotalPts() + "]");
				switch (activeChar.getFactionId())
				{
					case 1:
						activeChar.sendMessage("Your Faction points: [" + FactionMaps.getTeam1Pts() + "]");
						activeChar.sendMessage(Config.FACTION_TEAM2_NAME + " Faction points: [" + FactionMaps.getTeam2Pts() + "]");
						break;
					case 2:
						activeChar.sendMessage("Your Faction points: [" + FactionMaps.getTeam2Pts() + "]");
						activeChar.sendMessage(Config.FACTION_TEAM1_NAME + " Faction points: [" + FactionMaps.getTeam1Pts() + "]");
						break;
				}
				activeChar.sendMessage("--------------------------------------------------------------");
				activeChar.sendMessage("The Winners of this round will get a special reward! Good luck ;)");
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