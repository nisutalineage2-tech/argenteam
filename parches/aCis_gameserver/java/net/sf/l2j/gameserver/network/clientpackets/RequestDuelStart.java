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

import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.group.CommandChannel;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ExDuelAskStart;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public final class RequestDuelStart extends L2GameClientPacket
{
	private String _player;
	private int _partyDuel;
	
	@Override
	protected void readImpl()
	{
		_player = readS();
		_partyDuel = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
			return;
		
		final L2PcInstance targetChar = World.getInstance().getPlayer(_player);
		if (targetChar == null || activeChar == targetChar)
		{
			activeChar.sendPacket(SystemMessageId.THERE_IS_NO_OPPONENT_TO_RECEIVE_YOUR_CHALLENGE_FOR_A_DUEL);
			return;
		}
		
		// Check if duel is possible.
		if (!activeChar.canDuel())
		{
			activeChar.sendPacket(SystemMessageId.YOU_ARE_UNABLE_TO_REQUEST_A_DUEL_AT_THIS_TIME);
			return;
		}
		
		if (!targetChar.canDuel())
		{
			activeChar.sendPacket(targetChar.getNoDuelReason());
			return;
		}
		
		// Players musn't be too far.
		if (!activeChar.isInsideRadius(targetChar, 2000, false, false))
		{
			activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_CANNOT_RECEIVE_A_DUEL_CHALLENGE_BECAUSE_S1_IS_TOO_FAR_AWAY).addCharName(targetChar));
			return;
		}
		
		// Duel is a party duel.
		if (_partyDuel == 1)
		{
			// Player must be a party leader, the target can't be of the same party.
			final Party activeCharParty = activeChar.getParty();
			if (activeCharParty == null || !activeCharParty.isLeader(activeChar) || activeCharParty.containsPlayer(targetChar))
			{
				activeChar.sendPacket(SystemMessageId.YOU_ARE_UNABLE_TO_REQUEST_A_DUEL_AT_THIS_TIME);
				return;
			}
			
			// Target must be in a party.
			final Party targetCharParty = targetChar.getParty();
			if (targetCharParty == null)
			{
				activeChar.sendPacket(SystemMessageId.SINCE_THE_PERSON_YOU_CHALLENGED_IS_NOT_CURRENTLY_IN_A_PARTY_THEY_CANNOT_DUEL_AGAINST_YOUR_PARTY);
				return;
			}
			
			// Check if every player is ready for a duel.
			for (L2PcInstance member : activeCharParty.getMembers())
			{
				if (member != activeChar && !member.canDuel())
				{
					activeChar.sendPacket(SystemMessageId.YOU_ARE_UNABLE_TO_REQUEST_A_DUEL_AT_THIS_TIME);
					return;
				}
			}
			
			for (L2PcInstance member : targetCharParty.getMembers())
			{
				if (member != targetChar && !member.canDuel())
				{
					activeChar.sendPacket(SystemMessageId.THE_OPPOSING_PARTY_IS_CURRENTLY_UNABLE_TO_ACCEPT_A_CHALLENGE_TO_A_DUEL);
					return;
				}
			}
			
			final L2PcInstance partyLeader = targetCharParty.getLeader();
			
			// Send request to targetChar's party leader.
			if (!partyLeader.isProcessingRequest())
			{
				// Drop command channels, for both requestor && player parties.
				final CommandChannel activeCharChannel = activeCharParty.getCommandChannel();
				if (activeCharChannel != null)
					activeCharChannel.removeParty(activeCharParty);
				
				final CommandChannel targetCharChannel = targetCharParty.getCommandChannel();
				if (targetCharChannel != null)
					targetCharChannel.removeParty(targetCharParty);
				
				// Partymatching
				for (L2PcInstance member : activeCharParty.getMembers())
					member.removeMeFromPartyMatch();
				
				for (L2PcInstance member : targetCharParty.getMembers())
					member.removeMeFromPartyMatch();
				
				activeChar.onTransactionRequest(partyLeader);
				partyLeader.sendPacket(new ExDuelAskStart(activeChar.getName(), _partyDuel));
				
				activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_PARTY_HAS_BEEN_CHALLENGED_TO_A_DUEL).addCharName(partyLeader));
				targetChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_PARTY_HAS_CHALLENGED_YOUR_PARTY_TO_A_DUEL).addCharName(activeChar));
			}
			else
				activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_IS_BUSY_TRY_LATER).addCharName(partyLeader));
		}
		// 1vs1 duel.
		else
		{
			if (!targetChar.isProcessingRequest())
			{
				// Partymatching
				activeChar.removeMeFromPartyMatch();
				targetChar.removeMeFromPartyMatch();
				
				activeChar.onTransactionRequest(targetChar);
				targetChar.sendPacket(new ExDuelAskStart(activeChar.getName(), _partyDuel));
				
				activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_BEEN_CHALLENGED_TO_A_DUEL).addCharName(targetChar));
				targetChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_CHALLENGED_YOU_TO_A_DUEL).addCharName(activeChar));
			}
			else
				activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_IS_BUSY_TRY_LATER).addCharName(targetChar));
		}
	}
}