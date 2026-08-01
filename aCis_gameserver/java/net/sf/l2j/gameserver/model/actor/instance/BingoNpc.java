package net.sf.l2j.gameserver.model.actor.instance;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import net.sf.l2j.gameserver.bingo.BingoCard;
import net.sf.l2j.gameserver.bingo.BingoGame;
import net.sf.l2j.gameserver.bingo.BingoManager;
import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class BingoNpc extends Folk
{
	private static final String HTML_PATH = "data/html/mods/bingo/";
	
	public BingoNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		final StringTokenizer st = new StringTokenizer(command, " ");
		if (!st.hasMoreTokens())
		{
			super.onBypassFeedback(player, command);
			return;
		}
		
		final String currentCommand = st.nextToken();
		
		if (currentCommand.equals("bingo"))
			handleBingoBypass(player, command.substring(5).trim());
		else
			super.onBypassFeedback(player, command);
	}
	
	@Override
	public void showChatWindow(Player player, int val)
	{
		showBingoWindow(player, BingoManager.getInstance().getGame());
	}
	
	private void handleBingoBypass(Player player, String bypass)
	{
		try
		{
			final StringTokenizer st = new StringTokenizer(bypass, " ");
			if (!st.hasMoreTokens())
				return;
			
			final String currentCommand = st.nextToken();
			final BingoGame bingo = BingoManager.getInstance().getGame();
			
			if (currentCommand.equals("index"))
				showBingoWindow(player, bingo);
			else if (currentCommand.equals("newgame"))
			{
				if (!player.isGM() || bingo != null)
					return;
				
				handleNewBingoBypass(player, bypass.substring(7).trim());
			}
			else
			{
				if (bingo == null)
					return;
				
				// No impedimos que los players compren cartelas si el bingo ya inicio.
				if (currentCommand.equals("selectcard"))
					BingoManager.getInstance().showCardHtm(player, 0, getObjectId());
				else if (currentCommand.equals("buycard"))
				{
					if (player.getInventory().getItemCount(BingoManager.ID_ITEM_CARTELA) >= BingoManager.MAX_CARTELAS)
						player.sendMessage("No es posible comprar mas cartelas.");
					else if (bingo.getManagerId() == player.getObjectId())
						player.sendMessage("El operador del bingo no puede comprar cartelas.");
					else if (player.destroyItemByItemId(bingo.getCoinId(), bingo.getCoinCount(), true))
					{
						BingoManager.getInstance().createCard(player);
						showBingoWindow(player, bingo);
					}
					else
						showNoCoinWindow(player, bingo.getCoinId(), bingo.getCoinCount());
				}
				else
				{
					if (!player.isGM() && bingo.getManager() != null && bingo.getManager().getObjectId() != player.getObjectId())
						return;
					
					if (currentCommand.equals("cancelgame") && player.isGM())
					{
						BingoManager.getInstance().cancelGame(true);
						return;
					}
					
					if (currentCommand.equals("setmanager"))
						handleSetManager(player, st, bingo);
					else if (currentCommand.equals("schedule"))
						handleSchedule(player, st, bingo);
					else if (currentCommand.equals("startgame"))
						BingoManager.getInstance().startGame();
					else if (currentCommand.equals("announce"))
						BingoManager.getInstance().announce();
					else if (currentCommand.equals("callnumber"))
						BingoManager.getInstance().callNumber();
					
					showBingoManagerWindow(player, bingo);
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Fallo al manejar el bypass del BingoNpc para el bingo.", e);
		}
	}
	
	private void handleSetManager(Player player, StringTokenizer st, BingoGame bingo)
	{
		final String managerName = st.hasMoreTokens() ? st.nextToken() : null;
		if (managerName == null)
		{
			if (bingo.isStarted())
				BingoManager.getInstance().enableAutomaticMode();
			
			if (bingo.getManager() != null)
				bingo.getManager().sendMessage("El bingo fue cambiado al modo automatico. Ya no eres el operador.");
			
			bingo.setManager(null);
			return;
		}
		
		Player manager = null;
		
		// No es permitido definir un nuevo manager de un bingo activo.
		if (player.isGM() && !bingo.isStarted())
		{
			if (managerName.equalsIgnoreCase(player.getName()))
				manager = player;
			else
				manager = World.getInstance().getPlayer(managerName);
		}
		
		if (manager != null)
		{
			bingo.setManager(manager);
			manager.sendMessage("Has sido definido como el operador del bingo.");
		}
	}
	
	private void handleSchedule(Player player, StringTokenizer st, BingoGame bingo)
	{
		if (!player.isGM() || bingo.isStarted())
			return;
		
		final String time = st.hasMoreTokens() ? st.nextToken() : null;
		if (time == null || time.equals("0"))
		{
			bingo.setScheduledTime(0);
			BingoManager.getInstance().cancelGameTask();
		}
		else
		{
			final long start = getScheduledBingoTime(time);
			if (start == 0)
				return;
			
			bingo.setScheduledTime(start);
			BingoManager.getInstance().scheduleGame(start);
		}
	}
	
	private void showBingoManagerWindow(Player player, BingoGame bingo)
	{
		if (!player.isGM() && bingo.getManager() != player)
			return;
		
		final Map<Integer, Integer> markedNumbers = new HashMap<>();
		for (BingoCard card : bingo.getCards())
			markedNumbers.merge(card.getMarkedNumbersList().size(), 1, Integer::sum);
		
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(HTML_PATH + "manager.htm");
		
		for (int i = 0; i <= 5; i++)
			html.replace("%marked_" + i + "%", markedNumbers.getOrDefault(i, 0));
		
		html.replace("%players%", bingo.getTotalParticipants());
		html.replace("%cards%", bingo.getCards().size());
		html.replace("%start%", bingo.getScheduledTime() == 0 ? "manual" : "automatico");
		html.replace("%coin_name%", ItemData.getInstance().getTemplate(bingo.getCoinId()).getName());
		html.replace("%coin_qnt%", bingo.getCoinCount());
		html.replace("%reward_name%", ItemData.getInstance().getTemplate(bingo.getRewardId()).getName());
		html.replace("%reward_qnt%", bingo.getRewardCount());
		html.replace("%scheduled%", bingo.getScheduledTime() == 0 ? "--:--" : new SimpleDateFormat("HH:mm").format(bingo.getScheduledTime()));
		html.replace("%status%", bingo.isStarted() ? "<font color=00ff00>Activado</font>" : "<font color=ff0000>Desactivado</font>");
		html.replace("%manager%", bingo.getManager() == null ? "automatico" : bingo.getManager().getName());
		html.replace("%lastnumber%", bingo.getLastNumber() == 0 ? "" : BingoManager.getInstance().getLetter(bingo.getLastNumber()) + ":" + bingo.getLastNumber());
		html.replace("%lastnumbertime%", bingo.getLastNumberTime() == 0 ? "" : "(" + new SimpleDateFormat("HH:mm:ss").format(bingo.getLastNumberTime()) + ")");
		html.replace("%callednumbers%", bingo.getCalledNumbersString());
		html.replace("%totalnumbers%", bingo.getCalledNumbers().size());
		html.replace("%id%", bingo.getId());
		
		sendHtmlMessage(player, html);
	}
	
	private void showBingoWindow(Player player, BingoGame bingo)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(HTML_PATH + "bingo.htm");
		
		if (bingo == null)
		{
			html.replace("%game%", "No hay ningun juego disponible en este momento.");
			html.replace("%manager%", player.isGM() ? "<a action=\"bypass -h npc_%objectId%_bingo newgame\">Crear nuevo</a>" : "");
		}
		else
		{
			html.replace("%game%", HtmCache.getInstance().getHtmForce(HTML_PATH + "game.htm"));
			html.replace("%callednumbers%", bingo.isStarted() ? "<br><a action=\"bypass bingo callednumbers\">Numeros llamados</a>" : "");
			html.replace("%coin_name%", ItemData.getInstance().getTemplate(bingo.getCoinId()).getName());
			html.replace("%coin_qnt%", bingo.getCoinCount());
			html.replace("%reward_name%", ItemData.getInstance().getTemplate(bingo.getRewardId()).getName());
			html.replace("%reward_qnt%", bingo.getRewardCount());
			html.replace("%start%", bingo.getScheduledTime() == 0 ? "En cualquier momento" : new SimpleDateFormat("HH:mm").format(bingo.getScheduledTime()));
			html.replace("%manager%", player.isGM() || bingo.getManager() == player ? "<a action=\"bypass -h npc_%objectId%_bingo manager\">Gestionar</a>" : "");
		}
		
		sendHtmlMessage(player, html);
	}
	
	private void showNewBingoWindow(Player player, String msg)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(HTML_PATH + "new.htm");
		html.replace("%msg%", msg != null ? msg + "<br1>" : "");
		sendHtmlMessage(player, html);
	}
	
	private void handleNewBingoBypass(Player player, String bypass)
	{
		try
		{
			final StringTokenizer st = new StringTokenizer(bypass, ";");
			
			if (!st.hasMoreTokens())
			{
				showNewBingoWindow(player, null);
				return;
			}
			
			if (st.countTokens() < 4)
			{
				showNewBingoWindow(player, "Completa los campos obligatorios.");
				return;
			}
			
			final int coinId = Integer.valueOf(st.nextToken().trim());
			if (coinId < 0 || ItemData.getInstance().getTemplate(coinId) == null)
			{
				showNewBingoWindow(player, "El item id " + coinId + " de la moneda no existe.");
				return;
			}
			
			final int coinCount = Integer.valueOf(st.nextToken().trim());
			if (coinCount <= 0)
			{
				showNewBingoWindow(player, "Cantidad invalida de la moneda.");
				return;
			}
			
			final int rewardId = Integer.valueOf(st.nextToken().trim());
			if (rewardId < 0 || ItemData.getInstance().getTemplate(rewardId) == null)
			{
				showNewBingoWindow(player, "El item id " + rewardId + " del premio no existe.");
				return;
			}
			
			final int rewardCount = Integer.valueOf(st.nextToken().trim());
			if (rewardCount <= 0)
			{
				showNewBingoWindow(player, "Cantidad invalida del premio.");
				return;
			}
			
			final String start = st.hasMoreTokens() ? st.nextToken().trim() : "";
			long startTime = 0;
			
			if (!start.isEmpty())
			{
				startTime = getScheduledBingoTime(start);
				if (startTime == 0)
				{
					showNewBingoWindow(player, "Horario invalido.");
					return;
				}
			}
			
			final String managerName = st.hasMoreTokens() ? st.nextToken().trim() : null;
			Player manager = null;
			
			if (managerName != null)
			{
				if (managerName.equalsIgnoreCase(player.getName()))
					manager = player;
				else
					manager = World.getInstance().getPlayer(managerName);
				
				if (manager == null)
				{
					showNewBingoWindow(player, "Player no encontrado.");
					return;
				}
			}
			
			final BingoGame bingo = new BingoGame(coinId, coinCount, rewardId, rewardCount, startTime, manager, this);
			BingoManager.getInstance().registerGame(bingo);
			showBingoWindow(player, bingo);
		}
		catch (Exception e)
		{
			showNewBingoWindow(player, "Completa los campos correctamente.");
		}
	}
	
	private static long getScheduledBingoTime(String time)
	{
		try
		{
			final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
			final LocalTime specifiedTime = LocalTime.parse(time, formatter);
			final LocalDateTime now = LocalDateTime.now();
			LocalDateTime specifiedDateTime = now.with(specifiedTime);
			
			// El horario es del dia siguiente.
			if (specifiedDateTime.isBefore(now))
				specifiedDateTime = specifiedDateTime.plusDays(1);
			
			return specifiedDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		}
		catch (Exception e)
		{
			return 0;
		}
	}
	
	private void showNoCoinWindow(Player player, int coin, int price)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile(HTML_PATH + "nocoin.htm");
		html.replace("%coin_name%", ItemData.getInstance().getTemplate(coin).getName());
		html.replace("%coin_qnt%", price);
		sendHtmlMessage(player, html);
	}
	
	private void sendHtmlMessage(Player player, NpcHtmlMessage html)
	{
		html.replace("%objectId%", getObjectId());
		player.sendPacket(html);
	}
}
