package net.sf.l2j.gameserver.network.clientpackets;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;	import net.sf.l2j.Config;
	import net.sf.l2j.gameserver.enums.SayType;
	import net.sf.l2j.gameserver.event.AbstractEvent;
	import net.sf.l2j.gameserver.event.EventEngine;
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
		
		// Event commands
		if (cmd.equals(".eventjoin") || cmd.equals(".eventleave"))
		{
			handleEventCommands(player, cmd, parts);
			return;
		}
		
		// Faction commands
		if (cmd.equals(".charge") || cmd.equals(".finfo") || cmd.equals(".fhelp") || cmd.equals(".votemap"))
		{
			handleFactionCommands(player, cmd, parts);
			return;
		}
	}
	
	private static void handleEventCommands(Player player, String cmd, String[] parts)
	{
		final EventEngine eventEngine = EventEngine.getInstance();
		
		switch (cmd)
		{
			case ".eventjoin":
			{
				// Already in an event?
				if (eventEngine.isPlayerInAnyEvent(player.getObjectId()))
				{
					player.sendMessage("[Event] Ya estás registrado en un evento. Usa .eventleave para salir.");
					return;
				}
				
				// Resolve the target event: by id or the currently open one
				final AbstractEvent event;
				if (parts.length >= 2)
				{
					try
					{
						final int eventId = Integer.parseInt(parts[1]);
						event = eventEngine.getEvent(eventId);
						if (event == null)
						{
							player.sendMessage("[Event] No existe un evento con id " + eventId + ". Usa .eventjoin sin argumentos para unirte al evento abierto.");
							return;
						}
					}
					catch (NumberFormatException e)
					{
						player.sendMessage("[Event] Uso: .eventjoin o .eventjoin <id>");
						return;
					}
				}
				else
				{
					event = eventEngine.getActiveEvent();
					if (event == null)
					{
						player.sendMessage("[Event] No hay ningún evento abierto en este momento.");
						return;
					}
				}
				
				if (event.getState() != AbstractEvent.State.REGISTER)
				{
					player.sendMessage("[Event] El evento " + event.getData().getEventName() + " no está en fase de registro.");
					return;
				}
				
				// Level requirements
				final int level = player.getStatus().getLevel();
				if (level < event.getData().getMinLvl() || level > event.getData().getMaxLvl())
				{
					player.sendMessage("[Event] Tu nivel (" + level + ") no cumple los requisitos (" + event.getData().getMinLvl() + "-" + event.getData().getMaxLvl() + ").");
					return;
				}
				
				if (event.registerPlayer(player))
					player.sendMessage("[Event] ¡Registrado en " + event.getData().getEventName() + "!");
				break;
			}
			case ".eventleave":
			{
				final AbstractEvent event = eventEngine.getEventForPlayer(player.getObjectId());
				if (event == null)
				{
					player.sendMessage("[Event] No estás en ningún evento.");
					return;
				}
				if (event.unregisterPlayer(player.getObjectId()))
					player.sendMessage("[Event] Te has salido de " + event.getData().getEventName() + ".");
				break;
			}
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
				player.sendMessage("[Faction] .votemap <number> - Vote for next war map");
				break;
			}
			case ".votemap":
			{
				if (parts.length < 2)
				{
					player.sendMessage("[Faction] Usage: .votemap <number>");
					return;
				}
				try
				{
					final int mapIndex = Integer.parseInt(parts[1]) - 1;
					net.sf.l2j.gameserver.factionwar.FactionWarManager.getInstance().onPlayerVote(player, mapIndex);
				}
				catch (NumberFormatException e)
				{
					player.sendMessage("[Faction] Usage: .votemap <number>");
				}
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