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

public final class ExRegenMax extends L2GameServerPacket
{
	private final int _count;
	private final int _time;
	private final double _hpRegen;
	
	public ExRegenMax(int count, int time, double hpRegen)
	{
		_count = count;
		_time = time;
		_hpRegen = hpRegen * 0.66;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xFE);
		writeH(0x01);
		writeD(1);
		writeD(_count);
		writeD(_time);
		writeF(_hpRegen);
	}
}