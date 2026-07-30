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

import com.ExGuard.ExGuard;
import com.ExGuard.packet.SendBanInfo;
import com.ExGuard.structure.HwId;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.network.serverpackets.ExRestartClient;
import net.sf.l2j.gameserver.network.serverpackets.KeyPacket;

public final class ProtocolVersion extends L2GameClientPacket
{
	private int _version;
	private String _hwidHdd;
	private String _hwidMac;
	private String _hwidGuId;
	private String _UserName;
	
	@Override
	protected void readImpl()
	{
		try
		{
			_version = readD();
			readB(new byte[(260)]);
			_hwidHdd = readS();
			_hwidMac = readS();
			_hwidGuId = readS();
			_UserName = readS();
		}
		catch (Exception e)
		{
			
		}
	}
	
	@Override
	protected void runImpl()
	{
		if (_version != Config.PROTOCOL_REVISION)
		{
			if (_version != -2)// this is just a ping attempt from the new C2 client // this packet is never encrypted
				_log.warning("Client: " + getClient().toString() + " -> Protocol Revision: " + _version + " is invalid. Protocol allowed are: " + Config.PROTOCOL_REVISION + " .");
			
			getClient().close(ExRestartClient.STATIC_PACKET);
		}
		else if (_hwidHdd == null || _hwidMac == null || _hwidGuId == null || _UserName == null)
		{
			_log.warning("Client: " + getClient().toString() + " login without Hwid.");
			getClient().close(ExRestartClient.STATIC_PACKET);
		}
		else
		{
			final HwId hwid = new HwId(_hwidHdd, _hwidMac, _hwidGuId, _UserName);
			final String info = ExGuard.getInstance().getInfo(hwid, getClient().getConnection().getInetAddress().getHostAddress());
			
			if (!info.equals("free"))
				getClient().close(new SendBanInfo(info));
			else
			{
				getClient().setHWID(hwid);
				getClient().sendPacket(new KeyPacket(getClient().enableCrypt()));
			}
		}
	}
}