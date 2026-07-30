package com.ExGuard.admin;

import com.ExGuard.ExGuard;
import com.ExGuard.packet.OpenSite;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class AdminExGuardInfo implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_hwidban",
		"admin_exguard",
		"admin_exeption",
		"admin_opensite"
	};
	
	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar)
	{
		final String[] currentCommand = command.split(" / ");
		
		if (currentCommand[0].startsWith("admin_opensite"))
		{
			if (currentCommand.length > 1)
				activeChar.sendPacket(new OpenSite(currentCommand[1])); 
		}
		else if (currentCommand[0].startsWith("admin_exguard"))
			ExGuardMain(activeChar);
		else if (currentCommand[0].startsWith("admin_exeption"))
		{
			if (currentCommand.length > 2)
			{
				if (currentCommand[1].startsWith("UrlAdd"))
					ExGuard.addUrl(activeChar, currentCommand[2]);
				else if (currentCommand[1].startsWith("UrlDel"))
					ExGuard.removeUrl(activeChar, currentCommand[2]);
			}
			NetPanel(activeChar);
		}
		else if (currentCommand[0].startsWith("admin_hwidban"))
		{
			if (currentCommand.length > 1)
			{
				final L2PcInstance banned = World.getInstance().getPlayer(currentCommand[1]);
				if (banned != null)
				{
					if (currentCommand.length > 2)
						ExGuard.getInstance().BanHwId(banned, currentCommand[2]);
					else
						activeChar.sendMessage("write reason !");
				}
				else
					activeChar.sendMessage("this player is not online");
			}
			banHwId(activeChar);
		}
		return true;
	}
	
	public void NetPanel(L2PcInstance player)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><title>AdminPanel ExceptionIp</title><body><center>");
		
		sb.append("<br><table align=center>");
		int count = 0;
		for (String ip : ExGuard.getUrls())
		{
			if (count++ == 0)
			{
				sb.append("<tr>");
				sb.append("<td width=100>" + ip + "</td>");
				sb.append("<td><button value=remove action=\"bypass admin_exeption / UrlDel / " + ip + " \" width=50 height=19  back=black fore=black\"></td>");
			}
			else
			{
				sb.append("<td><button value=remove action=\"bypass admin_exeption / UrlDel / " + ip + " \" width=50 height=19  back=black fore=black\"></td>");
				sb.append("<td width=100>" + ip + "</td>");
				sb.append("</tr>");
				count = 0;
			}
		}
		
		if (!sb.toString().endsWith("</tr>"))
			sb.append("</tr>");
		sb.append("</table>");
		sb.append("<br><width=120>Write Ip or Url <edit var=ip width=120>");
		sb.append("<br><button value=\"Add Ip/Url\" action=\"bypass  admin_exeption / UrlAdd / $ip \" width=\"134\" height=\"21\" back=\"L2UI_ch3.BigButton3_over\" fore=\"L2UI_ch3.BigButton3\"><br>");
		
		sb.append("<br><button value=\"Back\" action=\"bypass -h admin_exguard \" width=65 height=19 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
		sb.append("</center></body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(1);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
	
	private static void ExGuardMain(L2PcInstance activeChar)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><title>ExGuard</title><body><center>");
		sb.append("<br><img src=\"L2UI.SquareWhite\" width=280 height=1><br>");
		sb.append("<button value=\"Exeption\" action=\"bypass -h admin_exeption \" width=75 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"><br1>");
		sb.append("<img src=\"L2UI.SquareWhite\" width=280 height=1><br>");
		sb.append("<button value=\"Ban Hwid\" action=\"bypass -h admin_hwidban \" width=75 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"><br1>");
		sb.append("<img src=\"L2UI.SquareWhite\" width=280 height=1>");
		
		sb.append("<br><width=120>Write Url<edit var=ip width=120>");
		sb.append("<br><button value=\"open url\" action=\"bypass  admin_opensite / $ip \" width=\"134\" height=\"21\" back=\"L2UI_ch3.BigButton3_over\" fore=\"L2UI_ch3.BigButton3\"><br>");
		sb.append("<img src=\"L2UI.SquareWhite\" width=280 height=1>");
		sb.append("</center></body></html>");
		final NpcHtmlMessage html = new NpcHtmlMessage(1);
		html.setHtml(sb.toString());
		activeChar.sendPacket(html);
	}
	
	private static void banHwId(L2PcInstance activeChar)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><title>Admin HwIdBan</title><body><center>");
		
		sb.append("Player Name : <br1>");
		sb.append("<multiedit var=\"msg\" width=250 height=20><br>");
		
		sb.append("Reasons : <br1>");
		sb.append("<multiedit var=\"msg1\" width=250 height=20><br>");
		sb.append("<button value=\"Ban\" action=\"bypass -h admin_hwidban / $msg / $msg1 \" width=75 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"><br1>");
		
		sb.append("<button value=\"Back\" action=\"bypass -h admin_exguard \" width=75 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"><br1>");
		
		sb.append("</center></body></html>");
		final NpcHtmlMessage html = new NpcHtmlMessage(1);
		html.setHtml(sb.toString());
		activeChar.sendPacket(html);
	}

	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}