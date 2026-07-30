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

public class SetupGauge extends L2GameServerPacket
{
	public enum GaugeColor
	{
		BLUE,
		RED,
		CYAN,
		GREEN
	}
	
	private final GaugeColor _color;
	private final int _time;
	private final int _maxTime;
	
	public SetupGauge(GaugeColor color, int time)
	{
		_color = color;
		_time = time;
		_maxTime = time;
	}
	
	public SetupGauge(GaugeColor color, int currentTime, int maxTime)
	{
		_color = color;
		_time = currentTime;
		_maxTime = maxTime;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x6d);
		writeD(_color.ordinal());
		writeD(_time);
		writeD(_maxTime);
	}
}