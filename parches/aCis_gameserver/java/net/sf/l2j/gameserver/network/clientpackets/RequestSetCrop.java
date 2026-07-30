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
import java.util.List;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.instancemanager.CastleManorManager;
import net.sf.l2j.gameserver.model.L2Clan;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.manor.CropProcure;
import net.sf.l2j.gameserver.model.manor.Seed;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;

public class RequestSetCrop extends L2GameClientPacket
{
	private static final int BATCH_LENGTH = 13;
	
	private int _manorId;
	private List<CropProcure> _items;
	
	@Override
	protected void readImpl()
	{
		_manorId = readD();
		final int count = readD();
		if (count <= 0 || count > Config.MAX_ITEM_IN_PACKET || (count * BATCH_LENGTH) != _buf.remaining())
			return;
		
		_items = new ArrayList<>(count);
		for (int i = 0; i < count; i++)
		{
			final int itemId = readD();
			final int sales = readD();
			final int price = readD();
			final int type = readC();
			
			if (itemId < 1 || sales < 0 || price < 0)
			{
				_items.clear();
				return;
			}
			
			if (sales > 0)
				_items.add(new CropProcure(itemId, sales, type, sales, price));
		}
	}
	
	@Override
	protected void runImpl()
	{
		if (_items.isEmpty())
			return;
		
		final CastleManorManager manor = CastleManorManager.getInstance();
		if (!manor.isModifiablePeriod())
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// Check player privileges
		final L2PcInstance player = getClient().getActiveChar();
		if (player == null || player.getClan() == null || player.getClan().getCastleId() != _manorId || ((player.getClanPrivileges() & L2Clan.CP_CS_MANOR_ADMIN) != L2Clan.CP_CS_MANOR_ADMIN) || !player.getCurrentFolkNPC().canInteract(player))
		{
			sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		// Filter crops with start amount lower than 0 and incorrect price
		final List<CropProcure> list = new ArrayList<>(_items.size());
		for (CropProcure cp : _items)
		{
			final Seed s = manor.getSeedByCrop(cp.getId(), _manorId);
			if (s != null && cp.getStartAmount() <= s.getCropLimit() && cp.getPrice() >= s.getCropMinPrice() && cp.getPrice() <= s.getCropMaxPrice())
				list.add(cp);
		}
		
		// Save crop list
		manor.setNextCropProcure(list, _manorId);
	}
}