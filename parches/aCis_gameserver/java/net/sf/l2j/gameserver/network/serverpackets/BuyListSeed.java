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
package net.sf.l2j.gameserver.network.serverpackets;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.instancemanager.CastleManorManager;
import net.sf.l2j.gameserver.model.manor.SeedProduction;

public final class BuyListSeed extends L2GameServerPacket
{
	private final int _manorId;
	private final List<SeedProduction> _list;
	private final int _money;
	
	public BuyListSeed(int currentMoney, int castleId)
	{
		_money = currentMoney;
		_manorId = castleId;
		
		_list = new ArrayList<>();
		for (SeedProduction s : CastleManorManager.getInstance().getSeedProduction(castleId, false))
		{
			if (s.getAmount() > 0 && s.getPrice() > 0)
				_list.add(s);
		}
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xE8);
		
		writeD(_money);
		writeD(_manorId);
		
		if (!_list.isEmpty())
		{
			writeH(_list.size());
			for (SeedProduction s : _list)
			{
				writeH(0x04);
				writeD(s.getId());
				writeD(s.getId());
				writeD(s.getAmount());
				writeH(0x04);
				writeH(0x00);
				writeD(s.getPrice());
			}
		}
	}
}