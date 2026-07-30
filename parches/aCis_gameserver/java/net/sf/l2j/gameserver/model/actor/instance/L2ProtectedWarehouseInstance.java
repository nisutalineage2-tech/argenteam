/* This program is free software; you can redistribute it and/or modify
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
package net.sf.l2j.gameserver.model.actor.instance;

import java.util.Map;
import java.util.StringTokenizer;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.L2Clan;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.itemcontainer.PcFreight;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.PackageToList;
import net.sf.l2j.gameserver.network.serverpackets.WarehouseDepositList;
import net.sf.l2j.gameserver.network.serverpackets.WarehouseWithdrawList;

/**
 * @author Ventic - Anarchy
 */
public class L2ProtectedWarehouseInstance extends L2NpcInstance
{
	public L2ProtectedWarehouseInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String pom = "";
		if (val == 0)
		{
			pom = "" + npcId;
		}
		else
		{
			pom = npcId + "-" + val;
		}
		return "data/html/mods/protectedwarehouse/" + pom + ".htm";
	}
	
	private static void showRetrieveWindow(L2PcInstance player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		player.setActiveWarehouse(player.getWarehouse());
		
		if (player.getActiveWarehouse().getSize() == 0)
		{
			player.sendPacket(SystemMessageId.NO_ITEM_DEPOSITED_IN_WH);
			return;
		}
		
		if (Config.DEBUG)
			_log.fine("Showing stored items");
		player.sendPacket(new WarehouseWithdrawList(player, WarehouseWithdrawList.PRIVATE));
	}
	
	private static void showDepositWindow(L2PcInstance player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		player.setActiveWarehouse(player.getWarehouse());
		player.tempInventoryDisable();
		if (Config.DEBUG)
			_log.fine("Showing items to deposit");
		
		player.sendPacket(new WarehouseDepositList(player, WarehouseDepositList.PRIVATE));
	}
	
	private static void showDepositWindowClan(L2PcInstance player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		if (player.getClan() != null)
		{
			if (player.getClan().getLevel() == 0)
				player.sendPacket(SystemMessageId.ONLY_LEVEL_1_CLAN_OR_HIGHER_CAN_USE_WAREHOUSE);
			else
			{
				player.setActiveWarehouse(player.getClan().getWarehouse());
				player.tempInventoryDisable();
				if (Config.DEBUG)
					_log.fine("Showing items to deposit - clan");
				
				WarehouseDepositList dl = new WarehouseDepositList(player, WarehouseDepositList.CLAN);
				player.sendPacket(dl);
			}
		}
	}
	
	private static void showWithdrawWindowClan(L2PcInstance player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		if ((player.getClanPrivileges() & L2Clan.CP_CL_VIEW_WAREHOUSE) != L2Clan.CP_CL_VIEW_WAREHOUSE)
		{
			player.sendPacket(SystemMessageId.YOU_DO_NOT_HAVE_THE_RIGHT_TO_USE_CLAN_WAREHOUSE);
			return;
		}
		if (player.getClan().getLevel() == 0)
			player.sendPacket(SystemMessageId.ONLY_LEVEL_1_CLAN_OR_HIGHER_CAN_USE_WAREHOUSE);
		else
		{
			player.setActiveWarehouse(player.getClan().getWarehouse());
			if (Config.DEBUG)
				_log.fine("Showing items to deposit - clan");
			player.sendPacket(new WarehouseWithdrawList(player, WarehouseWithdrawList.CLAN));
		}
	}
	
	private void showWithdrawWindowFreight(L2PcInstance player)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		if (Config.DEBUG)
			_log.fine("Showing freightened items");
		
		PcFreight freight = player.getFreight();
		
		if (freight != null)
		{
			if (freight.getSize() > 0)
			{
				if (Config.ALT_GAME_FREIGHTS)
				{
					freight.setActiveLocation(0);
				}
				else
				{
					freight.setActiveLocation(getRegion().hashCode());
				}
				player.setActiveWarehouse(freight);
				player.sendPacket(new WarehouseWithdrawList(player, WarehouseWithdrawList.FREIGHT));
			}
			else
			{
				player.sendPacket(SystemMessageId.NO_ITEM_DEPOSITED_IN_WH);
			}
		}
		else
		{
			if (Config.DEBUG)
				_log.fine("no items freightened");
		}
	}
	
	private static void showDepositWindowFreight(L2PcInstance player)
	{
		// No other chars in the account of this player
		if (player.getAccountChars().size() == 0)
		{
			player.sendPacket(SystemMessageId.CHARACTER_DOES_NOT_EXIST);
		}
		// One or more chars other than this player for this account
		else
		{
			
			Map<Integer, String> chars = player.getAccountChars();
			
			if (chars.size() < 1)
			{
				player.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			player.sendPacket(new PackageToList(chars));
			
			if (Config.DEBUG)
				_log.fine("Showing destination chars to freight - char src: " + player.getName());
		}
	}
	
	private void showDepositWindowFreight(L2PcInstance player, int obj_Id)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		L2PcInstance destChar = L2PcInstance.restore(obj_Id);
		if (destChar == null)
		{
			// Something went wrong!
			if (Config.DEBUG)
				_log.warning("Error retrieving a target object for char " + player.getName() + " - using freight.");
			return;
		}
		
		PcFreight freight = destChar.getFreight();
		if (Config.ALT_GAME_FREIGHTS)
		{
			freight.setActiveLocation(0);
		}
		else
		{
			freight.setActiveLocation(getRegion().hashCode());
		}
		player.setActiveWarehouse(freight);
		player.tempInventoryDisable();
		destChar.deleteMe();
		
		if (Config.DEBUG)
			_log.fine("Showing items to freight");
		player.sendPacket(new WarehouseDepositList(player, WarehouseDepositList.FREIGHT));
	}
	
	@Override
	public void onBypassFeedback(L2PcInstance player, String command)
	{
		// lil check to prevent enchant exploit
		if (player.getActiveEnchantItem() != null)
		{
			_log.info("Player " + player.getName() + " trying to use enchant exploit, ban this player!");
			player.logout(true);
			return;
		}
		
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken();
		
		if (actualCommand.equalsIgnoreCase("login"))
		{
			if (!player.hasWarehouseAccount())
			{
				player.sendMessage("You don't have a warehouse account.");
				return;
			}
			
			if (st.countTokens() == 2)
			{
				String id = st.nextToken();
				String pwd = st.nextToken();
				
				if (id.equals(player.getWarehouseAccountId()) && pwd.equals(player.getWarehouseAccountPwd()))
				{
					showChatWindow(player, 4);
				}
				else
				{
					player.sendMessage("Wrong account inserted.");
				}
			}
			else
			{
				player.sendMessage("Wrong values inserted.");
			}
		}
		
		if (actualCommand.equalsIgnoreCase("register"))
		{
			if (player.hasWarehouseAccount())
			{
				player.sendMessage("You already have a warehouse account.");
				return;
			}
			
			if (st.countTokens() == 2)
			{
				String id, pwd;
				id = st.nextToken();
				pwd = st.nextToken();
				
				player.setWarehouseAccountId(id);
				player.setWarehouseAccountPwd(pwd);
				player.setHasWarehouseAccount(true);
				player.sendMessage("You have registered a new warehouse account. Id: " + player.getWarehouseAccountId() + " Pwd: " + player.getWarehouseAccountPwd());
				showChatWindow(player, 0);
			}
		}
		
		if (command.startsWith("WithdrawP"))
		{
			showRetrieveWindow(player);
		}
		else if (command.equals("DepositP"))
		{
			showDepositWindow(player);
		}
		else if (command.equals("WithdrawC"))
		{
			showWithdrawWindowClan(player);
		}
		else if (command.equals("DepositC"))
		{
			showDepositWindowClan(player);
		}
		else if (command.startsWith("WithdrawF"))
		{
			if (Config.ALLOW_FREIGHT)
			{
				showWithdrawWindowFreight(player);
			}
		}
		else if (command.startsWith("DepositF"))
		{
			if (Config.ALLOW_FREIGHT)
			{
				showDepositWindowFreight(player);
			}
		}
		else if (command.startsWith("FreightChar"))
		{
			if (Config.ALLOW_FREIGHT)
			{
				int startOfId = command.lastIndexOf("_") + 1;
				String id = command.substring(startOfId);
				showDepositWindowFreight(player, Integer.parseInt(id));
			}
		}
		else
		{
			// this class dont know any other commands, let forward
			// the command to the parent class
			
			super.onBypassFeedback(player, command);
		}
	}
}