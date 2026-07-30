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
package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.group.CommandChannel;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public final class RequestExAcceptJoinMPCC extends L2GameClientPacket
{
	private int _response;
	
	@Override
	protected void readImpl()
	{
		_response = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance player = getClient().getActiveChar();
		if (player == null)
			return;
		
		final L2PcInstance requestor = player.getActiveRequester();
		if (requestor == null)
			return;
		
		player.setActiveRequester(null);
		requestor.onTransactionResponse();
		
		final Party requestorParty = requestor.getParty();
		if (requestorParty == null)
			return;
		
		final Party targetParty = player.getParty();
		if (targetParty == null)
			return;
		
		if (_response == 1)
		{
			CommandChannel channel = requestorParty.getCommandChannel();
			if (channel == null)
			{
				// Consume a Strategy Guide item from requestor. If not possible, cancel the CommandChannel creation.
				if (!requestor.destroyItemByItemId("CommandChannel Creation", 8871, 1, player, true))
					return;
				
				channel = new CommandChannel(requestorParty, targetParty);
			}
			else
				channel.addParty(targetParty);
		}
		else
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_DECLINED_CHANNEL_INVITATION).addCharName(player));
	}
}