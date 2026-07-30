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
package net.sf.l2j.gameserver.model.actor.instance;

import java.util.StringTokenizer;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.instancemanager.CastleManorManager;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.BuyListSeed;
import net.sf.l2j.gameserver.network.serverpackets.ExShowCropInfo;
import net.sf.l2j.gameserver.network.serverpackets.ExShowManorDefaultInfo;
import net.sf.l2j.gameserver.network.serverpackets.ExShowProcureCropDetail;
import net.sf.l2j.gameserver.network.serverpackets.ExShowSeedInfo;
import net.sf.l2j.gameserver.network.serverpackets.ExShowSellCropList;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public class L2ManorManagerInstance extends L2MerchantInstance
{
	public L2ManorManagerInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2PcInstance player, String command)
	{
		if (command.startsWith("manor_menu_select"))
		{
			if (CastleManorManager.getInstance().isUnderMaintenance())
			{
				player.sendPacket(ActionFailed.STATIC_PACKET);
				player.sendPacket(SystemMessageId.THE_MANOR_SYSTEM_IS_CURRENTLY_UNDER_MAINTENANCE);
				return;
			}
			
			final StringTokenizer st = new StringTokenizer(command, "&");
			
			final int ask = Integer.parseInt(st.nextToken().split("=")[1]);
			final int state = Integer.parseInt(st.nextToken().split("=")[1]);
			final boolean time = st.nextToken().split("=")[1].equals("1");
			
			final int castleId = (state < 0) ? getCastle().getCastleId() : state;
			
			switch (ask)
			{
				case 1: // Seed purchase
					if (castleId != getCastle().getCastleId())
						player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.HERE_YOU_CAN_BUY_ONLY_SEEDS_OF_S1_MANOR).addString(getCastle().getName()));
					else
						player.sendPacket(new BuyListSeed(player.getAdena(), castleId));
					break;
				
				case 2: // Crop sales
					player.sendPacket(new ExShowSellCropList(player.getInventory(), castleId));
					break;
				
				case 3: // Current seeds (Manor info)
					player.sendPacket(new ExShowSeedInfo(castleId, time, false));
					break;
				
				case 4: // Current crops (Manor info)
					player.sendPacket(new ExShowCropInfo(castleId, time, false));
					break;
				
				case 5: // Basic info (Manor info)
					player.sendPacket(new ExShowManorDefaultInfo(false));
					break;
				
				case 6: // Buy harvester
					showBuyWindow(player, 300000 + getNpcId());
					break;
				
				case 9: // Edit sales (Crop sales)
					player.sendPacket(new ExShowProcureCropDetail(state));
					break;
			}
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		return "data/html/manormanager/manager.htm";
	}
	
	@Override
	public void showChatWindow(L2PcInstance player)
	{
		if (!Config.ALLOW_MANOR)
		{
			showChatWindow(player, "data/html/npcdefault.htm");
			return;
		}
		
		if (getCastle() != null && player.getClan() != null && getCastle().getOwnerId() == player.getClanId() && player.isClanLeader())
			showChatWindow(player, "data/html/manormanager/manager-lord.htm");
		else
			showChatWindow(player, "data/html/manormanager/manager.htm");
	}
}
