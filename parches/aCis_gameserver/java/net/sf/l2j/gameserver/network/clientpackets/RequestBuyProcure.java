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
import net.sf.l2j.gameserver.model.holder.IntIntHolder;

public class RequestBuyProcure extends L2GameClientPacket
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
			readD(); // service
			final int itemId = readD();
			final int cnt = readD();
			
			if (itemId < 1 || cnt < 1)
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
		_log.info("RequestBuyProcure: normally unused, but infos found for manorId :" + _manorId);
	}
}