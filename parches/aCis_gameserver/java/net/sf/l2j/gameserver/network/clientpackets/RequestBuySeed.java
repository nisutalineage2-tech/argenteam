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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.datatables.ItemTable;
import net.sf.l2j.gameserver.instancemanager.CastleManager;
import net.sf.l2j.gameserver.instancemanager.CastleManorManager;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2ManorManagerInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.entity.Castle;
import net.sf.l2j.gameserver.model.holder.IntIntHolder;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.manor.SeedProduction;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.util.FloodProtectors;
import net.sf.l2j.gameserver.util.FloodProtectors.Action;

public class RequestBuySeed extends L2GameClientPacket
{
	private static final int BATCH_LENGTH = 8;
	
	private int _manorId;
	private List<IntIntHolder> _items;
	
	@Override
	protected void readImpl()
	{
		_manorId = readD();
		
		final int count = readD();
		if (count <= 0 || count > Config.MAX_ITEM_IN_PACKET || count * BATCH_LENGTH != _buf.remaining())
			return;
		
		_items = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			final int itemId = readD();
			final int cnt = readD();
			
			if (cnt < 1 || itemId < 1)
			{
				_items = null;
				return;
			}
			
			_items.add(new IntIntHolder(itemId, cnt));
		}
	}
	
	@Override
	protected void runImpl()
	{
		if (!FloodProtectors.performAction(getClient(), Action.MANOR))
			return;
		
		final L2PcInstance player = getClient().getActiveChar();
		if (player == null)
			return;
		
		if (_items == null)
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		final CastleManorManager manor = CastleManorManager.getInstance();
		if (manor.isUnderMaintenance())
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		final Castle castle = CastleManager.getInstance().getCastleById(_manorId);
		if (castle == null)
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		final L2Npc manager = player.getCurrentFolkNPC();
		if (!(manager instanceof L2ManorManagerInstance) || !manager.canInteract(player) || manager.getCastle() != castle)
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		int totalPrice = 0;
		int slots = 0;
		int totalWeight = 0;
		
		final Map<Integer, SeedProduction> _productInfo = new HashMap<>();
		
		for (IntIntHolder ih : _items)
		{
			final SeedProduction sp = manor.getSeedProduct(_manorId, ih.getId(), false);
			if (sp == null || sp.getPrice() <= 0 || sp.getAmount() < ih.getValue() || ((Integer.MAX_VALUE / ih.getValue()) < sp.getPrice()))
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			// Calculate price
			totalPrice += (sp.getPrice() * ih.getValue());
			if (totalPrice > Integer.MAX_VALUE)
			{
				sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			final Item template = ItemTable.getInstance().getTemplate(ih.getId());
			totalWeight += ih.getValue() * template.getWeight();
			
			// Calculate slots
			if (!template.isStackable())
				slots += ih.getValue();
			else if (player.getInventory().getItemByItemId(ih.getId()) == null)
				slots++;
			
			_productInfo.put(ih.getId(), sp);
		}
		
		if (!player.getInventory().validateWeight(totalWeight))
		{
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.WEIGHT_LIMIT_EXCEEDED));
			return;
		}
		
		if (!player.getInventory().validateCapacity(slots))
		{
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.SLOTS_FULL));
			return;
		}
		
		if (totalPrice < 0 || player.getAdena() < totalPrice)
		{
			sendPacket(SystemMessage.getSystemMessage(SystemMessageId.YOU_NOT_ENOUGH_ADENA));
			return;
		}
		
		// Proceed the purchase
		for (IntIntHolder i : _items)
		{
			final SeedProduction sp = _productInfo.get(i.getId());
			final int price = sp.getPrice() * i.getValue();
			
			// Take Adena and decrease seed amount
			if (!sp.decreaseAmount(i.getValue()) || !player.reduceAdena("Buy", price, player, false))
			{
				// failed buy, reduce total price
				totalPrice -= price;
				continue;
			}
			
			// Add item to player's inventory
			player.addItem("Buy", i.getId(), i.getValue(), manager, true);
		}
		
		// Adding to treasury for Manor Castle
		if (totalPrice > 0)
		{
			castle.addToTreasuryNoTax(totalPrice);
			player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_DISAPPEARED_ADENA).addItemNumber(totalPrice));
		}
	}
}