package net.sf.l2j.gameserver.handler.admincommandhandlers;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * AdminEvent stub — events are disabled. FactionWar is the only game mode.
 */
public class AdminEvent implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_event"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		player.sendMessage("[Event] Events are disabled. Use //factionwar to manage Faction War.");
		
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><title>Event Admin</title><body>");
		sb.append("<center><font color=LEVEL>Event Manager</font></center><br>");
		sb.append("<img src=\"L2UI.SquareGray\" width=\"290\" height=\"1\">");
		sb.append("<br><center>");
		sb.append("<font color=FF8800>Events are DISABLED.</font><br>");
		sb.append("FactionWar is the only game mode.<br><br>");
		sb.append("Use <font color=LEVEL>//factionwar</font> to manage the war.");
		sb.append("</center></body></html>");
		
		final NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setHtml(sb.toString());
		player.sendPacket(msg);
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
