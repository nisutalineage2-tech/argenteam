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
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;

public class OnlinePlayers implements IVoicedCommandHandler
{
	private static final String[] _voicedCommands =
	{
		"online"
	};
	
	@Override
	public boolean useVoicedCommand(String command, L2PcInstance activeChar, String target)
	{
		if (command.equalsIgnoreCase("online"))
		{
			activeChar.sendMessage("--------------------------------------------------------------");
			activeChar.sendMessage(Config.FACTION_TEAM1_NAME + ": " + World.getInstance().getAllteam1Players().size());
			activeChar.sendMessage(Config.FACTION_TEAM2_NAME + ": " + World.getInstance().getAllteam2Players().size());
			activeChar.sendMessage("Total: " + World.getInstance().getPlayers().size() + " online.");
			activeChar.sendMessage("--------------------------------------------------------------");
		}
		return false;
	}
	
	@Override
	public String[] getVoicedCommandList()
	{
		return _voicedCommands;
	}
}