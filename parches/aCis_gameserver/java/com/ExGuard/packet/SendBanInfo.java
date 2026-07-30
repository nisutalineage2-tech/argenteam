package com.ExGuard.packet;

import net.sf.l2j.gameserver.network.serverpackets.L2GameServerPacket;

public class SendBanInfo extends L2GameServerPacket
{
	private String _info;
	
	public SendBanInfo(String info)
	{
		_info = info;
	}
	
	@Override
	protected void writeImpl()
	{
		writeC(0xF6);
		writeC(0xA3);
		writeS(_info);
	}
}