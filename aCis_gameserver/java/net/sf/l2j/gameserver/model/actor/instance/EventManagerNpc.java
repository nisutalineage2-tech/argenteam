package net.sf.l2j.gameserver.model.actor.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.commons.util.SysUtil;

import net.sf.l2j.gameserver.event.AbstractEvent;
import net.sf.l2j.gameserver.event.EventBuffer;
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
		// Guard: a malformed/empty bypass (e.g. "npc_<id>_") must never crash the handler thread.
		if (command == null || command.isEmpty())
			return;
		
		StringTokenizer st = new StringTokenizer(command, " ");
		if (!st.hasMoreTokens())
			return;
		String currentCommand = st.nextToken();
		
		if (currentCommand.startsWith("event_buff_clear"))
		{
			EventBuffer.getInstance().clearBuffs(player);
			EventBuffer.getInstance().showBufferPage(player, getObjectId());
		}
		else if (currentCommand.startsWith("event_buff"))
		{
			if (st.hasMoreTokens())
			{
				try
				{
					final int skillId = Integer.parseInt(st.nextToken());
					EventBuffer.getInstance().toggleBuff(player, skillId);
				}
				catch (NumberFormatException e)
				{
					player.sendMessage("Invalid buff ID.");
				}
			}
			EventBuffer.getInstance().showBufferPage(player, getObjectId());
		}
		else if (currentCommand.startsWith("event_"))
		{
			final String action = currentCommand.substring("event_".length());
			if (action.isEmpty())
				return;
			
			if (action.equals("join"))
			{
				if (!st.hasMoreTokens())
					return;				final int eventId;
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
					player.sendMessage("[Event] You joined " + event.getData().getEventName() + ".");
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
				showEventList(player, 0);
			}
			else if (action.equals("page"))
			{
				int page = 0;
				if (st.hasMoreTokens())
				{
					try
					{
						page = Integer.parseInt(st.nextToken());
					}
					catch (NumberFormatException e)
					{
						// keep page 0
					}
				}
				showEventList(player, page);
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
					sb.append("<center><font color=LEVEL>").append(SysUtil.escapeHtml(event.getData().getEventName())).append("</font></center><br>");
					sb.append("Estado: ").append(event.getState().name()).append("<br>");
					sb.append("Jugadores: ").append(event.getAllPlayers().size()).append(" / ").append(event.getData().getMinPlayers()).append("+<br>");
					sb.append("Nivel: ").append(event.getData().getMinLvl()).append("-").append(event.getData().getMaxLvl()).append("<br>");
					sb.append("Duracion: ").append(event.getData().getMatchTime()).append(" min<br>");
					sb.append("Pociones: ").append(event.getData().isAllowPotions() ? "Permitidas" : "Desactivadas").append("<br>");
					sb.append("Magia: ").append(event.getData().isAllowMagic() ? "Permitida" : "Desactivada").append("<br>");
					sb.append("<br><center><button value=\"Unirse\" action=\"bypass -h npc_").append(getObjectId()).append("_event_join ").append(eventId).append("\" width=70 height=25 back=\"L2UI_CH3.btn\" fore=\"L2UI_CH3.btn\"></center>");
					sb.append("</body></html>");
					
					final NpcHtmlMessage msg = new NpcHtmlMessage(getObjectId());
					msg.setHtml(sb.toString());
					player.sendPacket(msg);
			}
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	private void showEventList(Player player, int page)
	{
		final int npcObjId = getObjectId();
		
		// Collect enabled events (only those with an active EventEngine instance).
		final List<EventConfig.EventData> events = new ArrayList<>();
		for (EventConfig.EventData data : EventConfig.getEvents())
		{
			if (!data.isEnabled())
				continue;
			
			if (net.sf.l2j.gameserver.event.EventEngine.getInstance().getEvent(data.getId()) == null)
				continue;
			
			events.add(data);
		}
		
		// Pagination: 8 events per page to stay under the client HTML limit (8192 chars).
		final int pageSize = 8;
		final int totalPages = Math.max(1, (events.size() + pageSize - 1) / pageSize);
		final int currentPage = Math.max(0, Math.min(page, totalPages - 1));
		final int start = currentPage * pageSize;
		final int end = Math.min(start + pageSize, events.size());
		
		final StringBuilder sb = new StringBuilder(4096);
		sb.append("<html><body>");
		
		// Header
		sb.append("<center><font color=\"FFD700\">Administrador de Eventos</font></center>");
		sb.append("<img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\"><br>");
		
		// Check if player is already in an event
		final boolean alreadyInEvent = net.sf.l2j.gameserver.event.EventEngine.getInstance().isPlayerInAnyEvent(player.getObjectId());
		
		if (alreadyInEvent)
		{
			final AbstractEvent currentEvent = net.sf.l2j.gameserver.event.EventEngine.getInstance().getEventForPlayer(player.getObjectId());
			if (currentEvent != null)
			{
				sb.append("<table width=\"270\" bgcolor=\"1A1A2E\"><tr><td align=\"center\">");
				sb.append("<font color=\"00FF00\">Estas inscrito en: <b>").append(SysUtil.escapeHtml(currentEvent.getData().getEventName())).append("</b></font><br1>");
				sb.append("<font color=\"808080\" size=\"10\">Estado: ").append(getStateColor(currentEvent.getState())).append("</font><br>");
				if (currentEvent.getState() == AbstractEvent.State.REGISTER || currentEvent.getState() == AbstractEvent.State.IDLE)
				{
					sb.append("<button value=\"Abandonar\" action=\"bypass -h npc_" + npcObjId + "_event_leave\" width=\"100\" height=\"22\" back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
				}
				sb.append("</td></tr></table><br>");
			}
		}
		
		// Events list (current page only)
		for (int i = start; i < end; i++)
		{
			final EventConfig.EventData data = events.get(i);
			final AbstractEvent event = net.sf.l2j.gameserver.event.EventEngine.getInstance().getEvent(data.getId());
			if (event == null)
				continue;
			
			final AbstractEvent.State state = event.getState();
			final int registeredCount = event.getAllPlayers().size();
			final int minPlayers = data.getMinPlayers();
			
			sb.append("<table width=\"270\" cellpadding=\"2\" cellspacing=\"1\" bgcolor=\"" + (state == AbstractEvent.State.RUNNING ? "112211" : state == AbstractEvent.State.REGISTER ? "222211" : "111111") + "\">");
			
			// Event name row (use fallback if name is empty)
			final String displayName = data.getEventName().isEmpty() ? ("Evento #" + data.getId()) : data.getEventName();
			sb.append("<tr><td width=\"270\" colspan=\"2\"><font color=\"" + getStateHtmlColor(state) + "\">").append(SysUtil.escapeHtml(displayName)).append("</font></td></tr>");
			
			// Info row
			sb.append("<tr><td width=\"140\"><font color=\"808080\" size=\"10\">");
			sb.append(registeredCount).append("/").append(minPlayers).append(" jugadores<br1>");
			sb.append("Nvl ").append(data.getMinLvl()).append("-").append(data.getMaxLvl());
			sb.append("</font></td>");
			
			// Action buttons
			sb.append("<td width=\"130\" align=\"right\">");
			if (state == AbstractEvent.State.REGISTER && !alreadyInEvent)
			{
				sb.append("<button value=\"Unirse\" action=\"bypass -h npc_" + npcObjId + "_event_join " + data.getId() + "\" width=\"60\" height=\"20\" back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
			}
			else if (state == AbstractEvent.State.REGISTER && alreadyInEvent)
			{
				sb.append("<font color=\"808080\">Ya inscrito</font>");
			}
			else if (state == AbstractEvent.State.RUNNING)
			{
				sb.append("<font color=\"00FF00\">En curso</font>");
			}
			else if (state == AbstractEvent.State.STARTING)
			{
				sb.append("<font color=\"FFCC00\">Iniciando</font>");
			}
			else
			{
				sb.append("<font color=\"808080\">Esperando</font>");
			}
			sb.append("</td></tr>");
			
			sb.append("</table>");
			sb.append("<img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\">");
		}
		
		// Pagination controls
		if (totalPages > 1)
		{
			sb.append("<br>");
			if (currentPage > 0)
			{
				sb.append("<button value=\"&lt; Anterior\" action=\"bypass -h npc_" + npcObjId + "_event_page " + (currentPage - 1) + "\" width=\"80\" height=\"20\" back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
			}
			sb.append("<font color=\"808080\" size=\"10\"> Pagina " + (currentPage + 1) + "/" + totalPages + " </font>");
			if (currentPage < totalPages - 1)
			{
				sb.append("<button value=\"Siguiente &gt;\" action=\"bypass -h npc_" + npcObjId + "_event_page " + (currentPage + 1) + "\" width=\"80\" height=\"20\" back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
			}
		}
		
		// Footer with buffer button
		if (EventConfig.isEventBufferEnabled())
		{
			sb.append("<br><center><button value=\"Buffs para Eventos\" action=\"bypass -h npc_" + npcObjId + "_event_buff\" width=\"220\" height=\"24\" back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></center>");
		}
		
		sb.append("</body></html>");
		
		final NpcHtmlMessage msg = new NpcHtmlMessage(getObjectId());
		msg.setHtml(sb.toString());
		player.sendPacket(msg);
	}
	
	/**
	 * Returns color-coded state name for display.
	 */
	private String getStateColor(AbstractEvent.State state)
	{
		switch (state)
		{
			case IDLE: return "<font color=\"808080\">IDLE</font>";
			case REGISTER: return "<font color=\"FFCC00\">INSCRIPCION</font>";
			case STARTING: return "<font color=\"FFA500\">INICIANDO</font>";
			case RUNNING: return "<font color=\"00FF00\">EN CURSO</font>";
			default: return "<font color=\"808080\">" + state.name() + "</font>";
		}
	}
	
	private String getStateHtmlColor(AbstractEvent.State state)
	{
		switch (state)
		{
			case RUNNING: return "00FF00";
			case REGISTER: return "FFCC00";
			case STARTING: return "FFA500";
			default: return "808080";
		}
	}
	
	@Override
	public void showChatWindow(Player player, int val)
	{
		showEventList(player, 0);
	}
}
