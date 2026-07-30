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

import java.util.List;

import net.sf.l2j.gameserver.instancemanager.CastleManorManager;
import net.sf.l2j.gameserver.model.manor.Seed;

public class ExShowManorDefaultInfo extends L2GameServerPacket
{
	private final List<Seed> _crops;
	private final boolean _hideButtons;
	
	public ExShowManorDefaultInfo(boolean hideButtons)
	{
		_crops = CastleManorManager.getInstance().getCrops();
		_hideButtons = hideButtons;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0x1E);
		writeC(_hideButtons ? 0x01 : 0x00);
		writeD(_crops.size());
		for (Seed crop : _crops)
		{
			writeD(crop.getCropId()); // crop Id
			writeD(crop.getLevel()); // level
			writeD(crop.getSeedReferencePrice()); // seed price
			writeD(crop.getCropReferencePrice()); // crop price
			writeC(1); // Reward 1 type
			writeD(crop.getReward(1)); // Reward 1 itemId
			writeC(1); // Reward 2 type
			writeD(crop.getReward(2)); // Reward 2 itemId
		}
	}
}