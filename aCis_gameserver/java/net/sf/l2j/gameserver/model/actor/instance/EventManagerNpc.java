package net.sf.l2j.gameserver.model.actor.instance;

import java.util.StringTokenizer;

import net.sf.l2j.gameserver.event.AbstractEvent;
import net.sf.l2j.gameserver.event.EventConfig;
import net.sf.l2j.gameserver.event.EventEngine;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class EventManagerNpc extends Folk
{
	public EventManagerNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String currentCommand = st.nextToken();
		
		if (currentCommand.startsWith("event_"))
		{
			if (!st.hasMoreTokens())
				return;
			
			final String action = st.nextToken();
			
			if (action.equals("join"))
			{
				if (!st.hasMoreTokens())
					return;
				
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
				if (event == null || event.getState() != AbstractEvent.State.REGISTER)
				{
					player.sendMessage("[Event] This event is not available for registration.");
					return;
				}
				
				if (EventEngine.getInstance().isPlayerInAnyEvent(player.getObjectId()))
				{
					player.sendMessage("[Event] You are already in an event.");
					return;
				}
				
				if (event.registerPlayer(player))
					player.sendMessage("[Event] You joined " + event.getData().getEventName() + "!");
				else
					player.sendMessage("[Event] Cannot join. Check your level or try again later.");
			}
			else if (action.equals("leave"))
			{
				final AbstractEvent event = EventEngine.getInstance().getEventForPlayer(player.getObjectId());
				if (event == null)
				{
					player.sendMessage("[Event] You are not in any event.");
					return;
				}
				
				if (event.getState() == AbstractEvent.State.REGISTER)
				{
					event.unregisterPlayer(player.getObjectId());
					player.sendMessage("[Event] You left the event.");
				}
				else
				{
					player.sendMessage("[Event] You cannot leave during an active event.");
				}
			}
			else if (action.equals("list"))
			{
				showEventList(player);
			}
			else if (action.equals("info"))
			{
				if (!st.hasMoreTokens())
					return;
				
				final int eventId;
				try
				{
					eventId = Integer.parseInt(st.nextToken());
				}
				catch (NumberFormatException e)
				{
					return;
				}
				
				final AbstractEvent event = EventEngine.getInstance().getEvent(eventId);
				if (event == null)
				{
					player.sendMessage("[Event] Unknown event.");
					return;
				}
				
				final StringBuilder sb = new StringBuilder();
				sb.append("<html><title>Event Info</title><body>");
				sb.append("<center><font color=LEVEL>").append(event.getData().getEventName()).append("</font></center><br>");
				sb.append("Status: ").append(event.getState().name()).append("<br>");
				sb.append("Players: ").append(event.getAllPlayers().size()).append(" / ").append(event.getData().getMinPlayers()).append("+<br>");
				sb.append("Level: ").append(event.getData().getMinLvl()).append("-").append(event.getData().getMaxLvl()).append("<br>");
				sb.append("Duration: ").append(event.getData().getMatchTime()).append(" min<br>");
				sb.append("Potions: ").append(event.getData().isAllowPotions() ? "Allowed" : "Disabled").append("<br>");
				sb.append("Magic: ").append(event.getData().isAllowMagic() ? "Allowed" : "Disabled").append("<br>");
				sb.append("<br><center><button value=\"Join\" action=\"bypass -h npc_%objectId%_event_join ").append(eventId).append("\" width=70 height=25 back=\"L2UI_CH3.btn\" fore=\"L2UI_CH3.btn\"></center>");
				sb.append("</body></html>");
				
				final NpcHtmlMessage msg = new NpcHtmlMessage(getObjectId());
				msg.setHtml(sb.toString());
				player.sendPacket(msg);
			}
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	private void showEventList(Player player)
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><title>Event Manager</title><body>");
		sb.append("<center><font color=LEVEL>Event Manager</font></center><br>");
		sb.append("<img src=\"L2UI.SquareGray\" width=\"290\" height=\"1\">");
		
		for (EventConfig.EventData data : EventConfig.getEvents())
		{
			if (!data.isEnabled())
				continue;
			
			final AbstractEvent event = EventEngine.getInstance().getEvent(data.getId());
			final String status = event != null ? event.getState().name() : "N/A";
			
			sb.append("<table width=\"290\" height=\"40\">");
			sb.append("<tr>");
			sb.append("<td width=\"200\">").append(data.getEventName()).append("<br1><font color=\"808080\">").append(status).append("</font></td>");
			sb.append("<td width=\"90\"><button value=\"Info\" action=\"bypass -h npc_%objectId%_event_info ").append(data.getId()).append("\" width=\"50\" height=\"22\" back=\"L2UI_CH3.Minimap.mapbutton_zoomin1_over\" fore=\"L2UI_CH3.Minimap.mapbutton_zoomin1\"></td>");
			sb.append("</tr>");
			sb.append("</table>");
			sb.append("<img src=\"L2UI.SquareGray\" width=\"290\" height=\"1\">");
		}
		
		sb.append("</body></html>");
		
		final NpcHtmlMessage msg = new NpcHtmlMessage(getObjectId());
		msg.setHtml(sb.toString());
		player.sendPacket(msg);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String filename;
		if (val == 0)
			filename = "" + npcId;
		else
			filename = npcId + "-" + val;
		
		return "data/html/mods/event/" + filename + ".htm";
	}
}
