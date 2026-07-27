package net.sf.l2j.gameserver.network.clientpackets;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.enums.SayType;
import net.sf.l2j.gameserver.event.AbstractEvent;
import net.sf.l2j.gameserver.event.EventConfig;
import net.sf.l2j.gameserver.event.EventEngine;
import net.sf.l2j.gameserver.event.SimonSaysEvent;
import net.sf.l2j.gameserver.handler.ChatHandler;
import net.sf.l2j.gameserver.handler.IChatHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.SystemMessageId;

public final class Say2 extends L2GameClientPacket
{
	private static final Logger CHAT_LOG = Logger.getLogger("chat");
	
	private static final String[] WALKER_COMMAND_LIST =
	{
		"USESKILL",
		"USEITEM",
		"BUYITEM",
		"SELLITEM",
		"SAVEITEM",
		"LOADITEM",
		"MSG",
		"DELAY",
		"LABEL",
		"JMP",
		"CALL",
		"RETURN",
		"MOVETO",
		"NPCSEL",
		"NPCDLG",
		"DLGSEL",
		"CHARSTATUS",
		"POSOUTRANGE",
		"POSINRANGE",
		"GOHOME",
		"SAY",
		"EXIT",
		"PAUSE",
		"STRINDLG",
		"STRNOTINDLG",
		"CHANGEWAITTYPE",
		"FORCEATTACK",
		"ISMEMBER",
		"REQUESTJOINPARTY",
		"REQUESTOUTPARTY",
		"QUITPARTY",
		"MEMBERSTATUS",
		"CHARBUFFS",
		"ITEMCOUNT",
		"FOLLOWTELEPORT"
	};
	
	private String _text;
	private int _id;
	private String _target;
	
	@Override
	protected void readImpl()
	{
		_text = readS();
		_id = readD();
		_target = (_id == SayType.TELL.ordinal()) ? readS() : null;
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		if (_id < 0 || _id >= SayType.VALUES.length)
			return;
		
		if (_text.isEmpty() || _text.length() > 100)
			return;
		
		SayType type = SayType.VALUES[_id];
		if (Config.L2WALKER_PROTECTION && type == SayType.TELL && checkBot(_text))
			return;
		
		if (!player.isGM() && (type == SayType.ANNOUNCEMENT || type == SayType.CRITICAL_ANNOUNCE))
			return;
		
		if (player.isChatBanned() || (player.isInJail() && !player.isGM()))
		{
			player.sendPacket(SystemMessageId.CHATTING_PROHIBITED);
			return;
		}
		
		if (type == SayType.PETITION_PLAYER && player.isGM())
			type = SayType.PETITION_GM;
		
		if (Config.LOG_CHAT)
		{
			final LogRecord logRecord = new LogRecord(Level.INFO, _text);
			logRecord.setLoggerName("chat");
			
			if (type == SayType.TELL)
				logRecord.setParameters(new Object[]
				{
					type,
					"[" + player.getName() + " to " + _target + "]"
				});
			else
				logRecord.setParameters(new Object[]
				{
					type,
					"[" + player.getName() + "]"
				});
			
			CHAT_LOG.log(logRecord);
		}
		
		_text = _text.replaceAll("\\\\n", "");
		
		if (_text.startsWith(".") && type == SayType.ALL)
		{
			handlePlayerCommand(player, _text);
			return;
		}
		
		final IChatHandler handler = ChatHandler.getInstance().getHandler(type);
		if (handler == null)
		{
			LOGGER.warn("{} tried to use unregistred chathandler type: {}.", player.getName(), type);
			return;
		}
		
		handler.handleChat(type, player, _target, _text);
	}
	
	private static void handlePlayerCommand(Player player, String text)
	{
		final String[] parts = text.split(" ", 3);
		final String cmd = parts[0].toLowerCase();
		
		if (cmd.equals(".event") || cmd.equals(".eventjoin"))
		{
			if (!EventConfig.isEnabled())
			{
				player.sendMessage("[Event] Events are disabled.");
				return;
			}
			
			if (cmd.equals(".eventjoin"))
			{
				// .eventjoin alone -> auto-join active event
				if (parts.length < 2)
				{
					final AbstractEvent active = EventEngine.getInstance().getActiveEvent();
					if (active == null)
					{
						player.sendMessage("[Event] No events are open. Use .eventjoin list to see available events.");
						return;
					}
					handleEventJoin(player, active.getData().getId());
					return;
				}
				
				final String arg = parts[1].toLowerCase();
				if (arg.equals("leave"))
					handleEventLeave(player);
				else if (arg.equals("list"))
					handleEventList(player);
				else
				{
					try
					{
						final int eventId = Integer.parseInt(arg);
						handleEventJoin(player, eventId);
					}
					catch (NumberFormatException e)
					{
						player.sendMessage("[Event] Invalid event id. Usage: .eventjoin <id>");
					}
				}
				return;
			}
			
			// .event join|leave|list
			if (parts.length < 2)
			{
				player.sendMessage("[Event] Usage: .event join <id> | .event leave | .event list");
				return;
			}
			
			final String action = parts[1].toLowerCase();
			
			switch (action)
			{
				case "join":
				{
					if (parts.length < 3)
					{
						player.sendMessage("[Event] Usage: .event join <event_id>");
						return;
					}
					try
					{
						final int eventId = Integer.parseInt(parts[2]);
						handleEventJoin(player, eventId);
					}
					catch (NumberFormatException e)
					{
						player.sendMessage("[Event] Invalid event id.");
					}
					break;
				}
				case "leave":
				{
					handleEventLeave(player);
					break;
				}
				case "list":
				{
					handleEventList(player);
					break;
				}
				default:
				{
					player.sendMessage("[Event] Usage: .event join <id> | .event leave | .event list");
					break;
				}
			}
			return;
		}
		
		// Simon Says command
		if (cmd.equals(".simon") && parts.length >= 2)
		{
			final String word = parts[1].trim();
			final AbstractEvent active = EventEngine.getInstance().getActiveEvent();
			if (active instanceof SimonSaysEvent simon)
				simon.onPlayerSay(word, player);
			return;
		}
		
		// Faction commands
		if (cmd.equals(".charge") || cmd.equals(".finfo") || cmd.equals(".fhelp"))
		{
			handleFactionCommands(player, cmd, parts);
			return;
		}
	}
	
	private static void handleEventJoin(Player player, int eventId)
	{
		final AbstractEvent event = EventEngine.getInstance().getEvent(eventId);
		if (event == null || event.getState() != AbstractEvent.State.REGISTER)
		{
			player.sendMessage("[Event] This event is not available for registration.");
			return;
		}
		
		if (EventEngine.getInstance().isPlayerInAnyEvent(player.getObjectId()))
		{
			player.sendMessage("[Event] You are already registered in an event.");
			return;
		}
		
		if (event.registerPlayer(player))
			player.sendMessage("[Event] You joined " + event.getData().getEventName() + "!");
		else
			player.sendMessage("[Event] Cannot join. Check your level requirements.");
	}
	
	private static void handleEventLeave(Player player)
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
	
	private static void handleEventList(Player player)
	{
		player.sendMessage("[Event] Available events:");
		for (EventConfig.EventData data : EventConfig.getEvents())
		{
			if (!data.isEnabled())
				continue;
			
			final AbstractEvent event = EventEngine.getInstance().getEvent(data.getId());
			final String status = event != null ? event.getState().name() : "N/A";
			player.sendMessage("[Event] " + data.getId() + ". " + data.getEventName() + " [" + status + "]");
		}
	}
	
	private static void handleFactionCommands(Player player, String cmd, String[] parts)
	{
		if (!Config.ENABLE_FACTION_SYSTEM)
		{
			player.sendMessage("[Faction] Faction system is disabled.");
			return;
		}
		
		switch (cmd)
		{
			case ".charge":
			{
				final int points = player.getFactionPoints();
				if (points > 0)
				{
					player.getInventory().addItem(57, points);
					player.sendMessage("[Faction] " + points + " faction points converted to adena!");
					player.setFactionPoints(0);
				}
				else
					player.sendMessage("[Faction] You have no faction points to charge.");
				break;
			}
			case ".finfo":
			{
				final int factionId = player.getFactionId();
				final String factionName = switch (factionId)
				{
					case 1 -> "Good";
					case 2 -> "Evil";
					default -> "None";
				};
				int onlineGood = 0, onlineEvil = 0;
				for (Player p : net.sf.l2j.gameserver.model.World.getInstance().getPlayers())
				{
					if (p != null && p.isOnline())
					{
						if (p.getFactionId() == 1) onlineGood++;
						else if (p.getFactionId() == 2) onlineEvil++;
					}
				}
				player.sendMessage("[Faction] ---- " + factionName + " ----");
				player.sendMessage("[Faction] Points: " + player.getFactionPoints());
				player.sendMessage("[Faction] Good online: " + onlineGood + " | Evil online: " + onlineEvil);
				break;
			}
			case ".fhelp":
			{
				player.sendMessage("[Faction] Available commands:");
				player.sendMessage("[Faction] .charge - Convert faction points to adena");
				player.sendMessage("[Faction] .finfo - Show faction info");
				player.sendMessage("[Faction] .fhelp - Show this help");
				break;
			}
		}
	}
	
	private static boolean checkBot(String text)
	{
		for (String botCommand : WALKER_COMMAND_LIST)
		{
			if (text.startsWith(botCommand))
				return true;
		}
		return false;
	}
	
	@Override
	protected boolean triggersOnActionRequest()
	{
		return false;
	}
}