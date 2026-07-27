package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.StringTokenizer;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.event.AbstractEvent;
import net.sf.l2j.gameserver.event.EventConfig;
import net.sf.l2j.gameserver.event.EventEngine;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class AdminEvent implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_event"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		final StringTokenizer st = new StringTokenizer(command);
		st.nextToken();
		
		if (!st.hasMoreTokens())
		{
			showPanel(player, null);
			return;
		}
		
		final String action = st.nextToken().toLowerCase();
		
		switch (action)
		{
			case "start":
			{
				if (!st.hasMoreTokens())
				{
					player.sendMessage("[Event] Usage: //event start <event_id>");
					return;
				}
				final int eventId;
				try
				{
					eventId = Integer.parseInt(st.nextToken());
				}
				catch (NumberFormatException e)
				{
					player.sendMessage("[Event] Invalid event id.");
					return;
				}
				final AbstractEvent event = EventEngine.getInstance().getEvent(eventId);
				if (event == null)
				{
					player.sendMessage("[Event] Event not found.");
					return;
				}
				if (event.getState() != AbstractEvent.State.IDLE)
				{
					player.sendMessage("[Event] Event is not idle.");
					return;
				}
				if (EventEngine.getInstance().isAnyEventActive())
				{
					final AbstractEvent active = EventEngine.getInstance().getActiveEvent();
					player.sendMessage("[Event] Cannot start - " + active.getData().getEventName() + " is already in progress.");
					return;
				}
				event.startRegistering();
				showPanel(player, "Event " + event.getData().getEventName() + " registration opened.");
				break;
			}
			case "stop":
			{
				if (!st.hasMoreTokens())
				{
					player.sendMessage("[Event] Usage: //event stop <event_id>");
					return;
				}
				final int eventId;
				try
				{
					eventId = Integer.parseInt(st.nextToken());
				}
				catch (NumberFormatException e)
				{
					player.sendMessage("[Event] Invalid event id.");
					return;
				}
				final AbstractEvent event = EventEngine.getInstance().getEvent(eventId);
				if (event == null)
				{
					player.sendMessage("[Event] Event not found.");
					return;
				}
				event.stop();
				showPanel(player, "Event " + event.getData().getEventName() + " stopped.");
				break;
			}
			case "reload":
			{
				EventEngine.getInstance().reload();
				showPanel(player, "Event system reloaded.");
				break;
			}
			case "list":
			{
				final StringBuilder sb = new StringBuilder();
				sb.append("Events:<br>");
				for (EventConfig.EventData data : EventConfig.getEvents())
				{
					final AbstractEvent event = EventEngine.getInstance().getEvent(data.getId());
					final String status = event != null ? event.getState().name() : "N/A";
					sb.append(data.getId()).append(". ").append(data.getEventName()).append(" [").append(status).append("]<br>");
				}
				showPanel(player, sb.toString());
				break;
			}
			default:
				showPanel(player, "Usage: //event start|stop|reload|list");
				break;
		}
	}
	
	private void showPanel(Player player, String message)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><title>Event Admin</title><body>");
		sb.append("<center><font color=LEVEL>Event Manager Panel</font></center><br>");
		sb.append("<img src=\"L2UI.SquareGray\" width=\"290\" height=\"1\">");
		
		if (message != null)
		{
			sb.append("<br><font color=FF8800>").append(message).append("</font><br>");
			sb.append("<img src=\"L2UI.SquareGray\" width=\"290\" height=\"1\">");
		}
		
		for (EventConfig.EventData data : EventConfig.getEvents())
		{
			if (!data.isEnabled())
				continue;
			
			final AbstractEvent event = EventEngine.getInstance().getEvent(data.getId());
			final String status = event != null ? event.getState().name() : "N/A";
			final int players = event != null ? event.getAllPlayers().size() : 0;
			
			sb.append("<table width=\"290\" height=\"35\">");
			sb.append("<tr>");
			sb.append("<td width=\"180\">").append(data.getEventName()).append("<br1><font color=\"808080\">").append(status).append(" (").append(players).append(")</font></td>");
			sb.append("<td width=\"55\"><button value=\"Start\" action=\"bypass -h admin_event start ").append(data.getId()).append("\" width=\"50\" height=\"22\" back=\"L2UI_CH3.btn\" fore=\"L2UI_CH3.btn\"></td>");
			sb.append("<td width=\"55\"><button value=\"Stop\" action=\"bypass -h admin_event stop ").append(data.getId()).append("\" width=\"50\" height=\"22\" back=\"L2UI_CH3.btn\" fore=\"L2UI_CH3.btn\"></td>");
			sb.append("</tr>");
			sb.append("</table>");
			sb.append("<img src=\"L2UI.SquareGray\" width=\"290\" height=\"1\">");
		}
		
		sb.append("<br><center><button value=\"Reload\" action=\"bypass -h admin_event reload\" width=\"80\" height=\"25\" back=\"L2UI_CH3.btn\" fore=\"L2UI_CH3.btn\"></center>");
		sb.append("</body></html>");
		
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
