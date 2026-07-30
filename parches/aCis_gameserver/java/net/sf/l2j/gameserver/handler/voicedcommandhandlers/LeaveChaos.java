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

import net.sf.l2j.gameserver.custom.entity.ChaosEvent;
import net.sf.l2j.gameserver.custom.entity.ChaosEvent.State;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;

public class LeaveChaos implements IVoicedCommandHandler
{
	private static final String[] _voicedCommands =
	{
		"chaosleave"
	};
	
	@Override
	public boolean useVoicedCommand(String command, L2PcInstance activeChar, String target)
	{
		if (command.equalsIgnoreCase("chaosleave"))
		{
			if (ChaosEvent.getState() == State.REGISTRACIJA)
			{
				if (activeChar.isRegInChaosEvent())
				{
					activeChar.setIsRegInChaosEvent(false);
					activeChar.sendMessage("Chaos Event: You have successfuly unregistered from Chaos event.");
				}
				else
					activeChar.sendMessage("Chaos Event: You haven't registered in Chaos event yet!");
			}
			else
			{
				activeChar.sendPacket(new PlaySound("monsound5.frintessa_shout2"));
				activeChar.sendMessage("Chaos Event: Event registration isn't active!");
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