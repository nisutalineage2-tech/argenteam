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

import net.sf.l2j.gameserver.custom.entity.FactionMaps;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;

public class MapInfo implements IVoicedCommandHandler
{
	private static final String[] _voicedCommands =
	{
		"mapinfo"
	};
	
	@Override
	public boolean useVoicedCommand(String command, L2PcInstance activeChar, String target)
	{
		if (command.equalsIgnoreCase("mapinfo"))
		{
			activeChar.sendMessage("--------------------------------------------------------------");
			activeChar.sendMessage("Current Map: " + FactionMaps.getMapName() + ". Round [" + FactionMaps.getMapId() + "].");
			activeChar.sendMessage("Next Map: " + FactionMaps._all_maps.get(FactionMaps.getNextMap(FactionMaps.getMapId())) + ".");
			activeChar.sendMessage("Time left: " + FactionMaps.getDelayUntilVoting());
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