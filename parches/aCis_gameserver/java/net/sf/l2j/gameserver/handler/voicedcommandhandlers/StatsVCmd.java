/*
 * L2jProject x - www.l2jprojectx.com 
 * 
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
package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.serverpackets.GMViewCharacterInfo;

public class StatsVCmd implements IVoicedCommandHandler
{
	private static final String[] VOICED_COMMANDS =
	{
		"stats"
	};
	
	private enum CommandEnum
	{
		stats
	}
	
	@Override
	public boolean useVoicedCommand(final String command, final L2PcInstance activeChar, final String target)
	{
		final CommandEnum comm = CommandEnum.valueOf(command);
		
		if (comm == null)
			return false;
		
		switch (comm)
		{
			case stats:
			{
				if (activeChar.getTarget() == null)
				{
					activeChar.sendMessage("No tienes un target.");
					return false;
				}
				if (activeChar.getTarget() == activeChar)
				{
					activeChar.sendMessage("No puedes ver tus stats, usa ALT+T.");
					return false;
				}
				
				if (!(activeChar.getTarget() instanceof L2PcInstance))
				{
					activeChar.sendMessage("Solo puedes ver la informacion de un jugador.");
					return false;
				}
				
				final L2PcInstance targetp = (L2PcInstance) activeChar.getTarget();
				activeChar.sendPacket(new GMViewCharacterInfo(targetp));
			}
			default:
			{
				return false;
			}
		}
	}

	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}