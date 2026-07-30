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
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.partymatching.PartyMatchRoom;
import net.sf.l2j.gameserver.model.partymatching.PartyMatchRoomList;
import net.sf.l2j.gameserver.network.serverpackets.ExManagePartyRoomMember;
import net.sf.l2j.gameserver.network.serverpackets.JoinParty;

public final class RequestAnswerJoinParty extends L2GameClientPacket
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
		
		requestor.sendPacket(new JoinParty(_response));
		
		Party party = requestor.getParty();
		if (_response == 1)
		{
			if (party == null)
				party = new Party(requestor, player, requestor.getLootRule());
			else
				party.addPartyMember(player);
			
			if (requestor.isInPartyMatchRoom())
			{
				final PartyMatchRoomList list = PartyMatchRoomList.getInstance();
				if (list != null)
				{
					final PartyMatchRoom room = list.getPlayerRoom(requestor);
					if (room != null)
					{
						if (player.isInPartyMatchRoom())
						{
							if (list.getPlayerRoomId(requestor) == list.getPlayerRoomId(player))
							{
								final ExManagePartyRoomMember packet = new ExManagePartyRoomMember(player, room, 1);
								for (L2PcInstance member : room.getPartyMembers())
									member.sendPacket(packet);
							}
						}
						else
						{
							room.addMember(player);
							
							final ExManagePartyRoomMember packet = new ExManagePartyRoomMember(player, room, 1);
							for (L2PcInstance member : room.getPartyMembers())
								member.sendPacket(packet);
							
							player.setPartyRoom(room.getId());
							player.broadcastUserInfo();
						}
					}
				}
			}
		}
		
		// Must be kept out of "ok" answer, can't be merged with higher content.
		if (party != null)
			party.setPendingInvitation(false);
		
		player.setActiveRequester(null);
		requestor.onTransactionResponse();
	}
}