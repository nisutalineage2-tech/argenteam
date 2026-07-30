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

import net.sf.l2j.gameserver.model.BlockList;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.group.Party.LootRule;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.AskJoinParty;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.network.serverpackets.MyTargetSelected;
public final class RequestJoinParty extends L2GameClientPacket
{
	private static final L2PcInstance targetPlayer = null;
	private String _name;
	private int _itemDistribution;
	
	@Override
	protected void readImpl()
	{
		_name = readS();
		_itemDistribution = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final L2PcInstance requestor = getClient().getActiveChar();
		if (requestor == null)
			return;
		
		final L2PcInstance target = World.getInstance().getPlayer(_name);
		if (target == null)
		{
			requestor.sendPacket(SystemMessageId.FIRST_SELECT_USER_TO_INVITE_TO_PARTY);
			return;
		}
		
		if (BlockList.isBlocked(target, requestor))
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_HAS_ADDED_YOU_TO_IGNORE_LIST).addCharName(target));
			return;
		}
		
		if (target.equals(requestor) || target.isCursedWeaponEquipped() || requestor.isCursedWeaponEquipped() || target.getAppearance().getInvisible())
		{
			requestor.sendPacket(SystemMessageId.YOU_HAVE_INVITED_THE_WRONG_TARGET);
			return;
		}
		
		if (target.isInParty())
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_IS_ALREADY_IN_PARTY).addCharName(target));
			return;
		}
		

		if (target.getFactionId() == 1 && requestor.getFactionId() == 2)
					{
						requestor.sendPacket(SystemMessageId.INCORRECT_TARGET);
						return;
					}
		if (target.getFactionId() == 2 && requestor.getFactionId() == 1)
				{
						requestor.sendPacket(SystemMessageId.INCORRECT_TARGET);
						return;
				}
							
	
		if (target.getClient().isDetached())
		{
			requestor.sendMessage("The player you tried to invite is in offline mode.");
			return;
		}
		
		if (target.isInJail() || requestor.isInJail())
		{
			requestor.sendMessage("The player you tried to invite is currently jailed.");
			return;
		}
		
		if (target.isInOlympiadMode() || requestor.isInOlympiadMode())
			return;
		
		if (requestor.isProcessingRequest())
		{
			requestor.sendPacket(SystemMessageId.WAITING_FOR_ANOTHER_REPLY);
			return;
		}
		
		if (target.isProcessingRequest())
		{
			requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_IS_BUSY_TRY_LATER).addCharName(target));
			return;
		}
		
		final Party party = requestor.getParty();
		if (party != null)
		{
			if (!party.isLeader(requestor))
			{
				requestor.sendPacket(SystemMessageId.ONLY_LEADER_CAN_INVITE);
				return;
			}
			
			if (party.getMembersCount() >= 9)
			{
				requestor.sendPacket(SystemMessageId.PARTY_FULL);
				return;
			}
			
			if (party.getPendingInvitation() && !party.isInvitationRequestExpired())
			{
				requestor.sendPacket(SystemMessageId.WAITING_FOR_ANOTHER_REPLY);
				return;
			}
			
			party.setPendingInvitation(true);
		}
		else
			requestor.setLootRule(LootRule.VALUES[_itemDistribution]);
		
		requestor.onTransactionRequest(target);
		requestor.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_INVITED_S1_TO_PARTY).addCharName(target));
		
		target.sendPacket(new AskJoinParty(requestor.getName(), (party != null) ? party.getLootRule().ordinal() : _itemDistribution));
	}
}