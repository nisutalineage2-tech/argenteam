package com.ExGuard.packet;

import net.sf.l2j.gameserver.network.serverpackets.L2GameServerPacket;

public final class OpenSite extends L2GameServerPacket
{
	private String _site;
	
	public OpenSite(String site)
	{
		_site = site;
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0xF5);
		writeS(_site);
	}
	
	@Override
	public String getType()
	{
		return "[S] F5 OpenSite";
	}
}