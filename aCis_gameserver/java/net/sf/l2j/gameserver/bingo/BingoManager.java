package net.sf.l2j.gameserver.bingo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ConnectionPool;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.data.cache.HtmCache;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.Dice;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class BingoManager
{
	// Configuracion

	// ID del item utilizado como cartela.
	public static int ID_ITEM_CARTELA = 9300;

	// Numero maximo de cartelas que un player puede comprar/marcar.
	public static int MAX_CARTELAS = 12;

	// Utilizar el item "Rolling Dices" para simular los numeros llamados del bingo.
	public static boolean LANZAR_DADOS = true;

	// Impide que los players usen el chat de trade durante el bingo.
	public static boolean BLOQUEAR_CHAT_TRADE = true;

	// Anunciar el numero llamado utilizando frases de bingos.
	private static boolean FRASES_DE_NUMEROS = true;

	// Intervalo entre los numeros llamados de forma automatica. Valor en segundos. Min: 10.
	private static int INTERVALO_NUMEROS_AUTOMATICOS = 20;

	// Si el bingo esta agendado y el manager (player) no esta al lado del NPC al momento del inicio, el bingo pasa a automatico.
	private static boolean REMOVER_MANAGER_AUTOMATICAMENTE = true;

	// Frases genericas usadas al divulgar los numeros sorteados.
	private static String[] FRASES_GENERICAS = {
		"Marquen en sus cartelas",
		"Nuevo numero llamado",
		"No se olviden de marcar",
		"Marquen con atencion",
		"Vamos",
		"No lo dejen pasar"
	};

	private static String HTML_PATH = "data/html/mods/bingo/";

	// Fin de la configuracion

	private static final CLogger LOGGER = new CLogger(BingoManager.class.getName());

	private static final String DELETE_ITEMS = "DELETE FROM items WHERE item_id=?";

	private int _tick;
	private long _lastHotCount;
	private Map<Integer, int[][]> _temporaryCards = new ConcurrentHashMap<>();
	private ScheduledFuture<?> _gameTask;
	private BingoGame _game;

	public BingoManager()
	{
		deleteOfflineCards();
	}

	public boolean blockChatTrade()
	{
		return BLOQUEAR_CHAT_TRADE && _game != null && _game.isStarted();
	}

	public void registerGame(BingoGame game)
	{
		_game = game;

		if (game.getScheduledTime() > 0)
			scheduleGame(game.getScheduledTime());

		broadcastMessage("Un nuevo bingo iniciara en breve. Compra tu cartela y participa!");
	}

	public void startGame()
	{
		if (_game == null || _game.isStarted())
			return;

		if (_game.getCards().isEmpty())
		{
			cancelGame(true);
			return;
		}

		broadcastMessage("El Bingo ha iniciado!");

		if (REMOVER_MANAGER_AUTOMATICAMENTE && !checkManager())
			_game.setManager(null);

		if (_game.getManager() == null)
			enableAutomaticMode();
		else
		{
			// El inicio puede haber sido forzado.
			cancelGameTask();

			_game.getManager().sendMessage("Tu movimiento ha sido limitado durante el bingo.");
			callNumber();
		}

		_game.start();
	}

	public void handleCardBypass(Player player, String bypass)
	{
		try
		{
			final StringTokenizer st = new StringTokenizer(bypass, " ");
			final String command = st.nextToken();

			if (command.equals("close"))
				return;

			if (_game == null || !_game.isParticipant(player.getObjectId()) || _game.getManager() == player)
				return;

			final int cardId = st.hasMoreTokens() ? Integer.valueOf(st.nextToken()) : 0;
			BingoCard card = null;

			if (cardId > 0)
			{
				card = getCard(player.getObjectId(), cardId);
				if (card == null)
					return;

				if (player.getInventory().getItemByObjectId(cardId) == null)
					return;
			}

			if (command.equals("showcard"))
			{
				showCardHtm(player, cardId, 0);
				return;
			}

			if (!_game.isStarted())
			{
				player.sendMessage("El bingo aun no ha comenzado.");
				return;
			}

			if (command.equals("callednumbers"))
			{
				player.sendMessage("[" + new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()) + "] Numeros llamados hasta el momento");
				for (String message : _game.getCalledNumbersString().split("<br1>"))
					player.sendMessage(message);
			}
			else if (command.equals("mark"))
			{
				final int number = Integer.valueOf(st.nextToken());
				if (!_game.getCalledNumbers().contains(number))
					return;

				if (card != null)
					card.markNumber(number);

				showCardHtm(player, cardId, 0);
			}
			else if (command.equals("win"))
			{
				if (card == null || !card.isWinner())
					return;

				rewardWinner(player, card);
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("BingoManager: Player {}: fallo al manejar el bypass {}", e, player.getName(), bypass);
		}
	}

	private synchronized void rewardWinner(Player player, BingoCard card)
	{
		if (_game == null)
			return;

		// Divulgacion del resultado.
		broadcastMessage("BINGO!!! " + player.getName() + " es el gran ganador!");
		broadcastMessage("Marco los numeros " + card.getWinningNumbers().toString());

		// Premio.
		player.addItem(_game.getRewardId(), _game.getRewardCount(), true);

		// Cartela con los numeros ganadores.
		_game.setWinningCard(card.getObjectId());
		showCardHtm(player, card.getObjectId(), 0);

		// Fin del juego.
		cancelGame(false);
	}

	public void announce()
	{
		final int calledNumbers = _game.getCalledNumbers().size();
		if (calledNumbers == 0)
		{
			if (_game.getManager() == null || _game.isStarted())
				broadcastMessage("El primer numero del bingo sera llamado en breve, preparate!");
			else
				broadcastMessage("Compra tu cartela y participa del bingo!");
		}
		else
		{
			if (calledNumbers % 5 == 0)
				broadcastMessage(calledNumbers + " bolas ya fueron llamadas!");
			else
				broadcastMessage("La proxima bola sera llamada en breve!");
		}
	}

	private boolean checkManager()
	{
		if (_game.getManager() == null)
			return false;

		if (!_game.getManager().isOnline() || !_game.getManager().isIn3DRadius(_game.getNpc(), Npc.INTERACTION_DISTANCE))
			return false;

		return true;
	}

	private void throwDices(int number)
	{
		if (!LANZAR_DADOS)
			return;

		if (!checkManager())
			return;

		final List<Integer> dices = new ArrayList<>();
		int sum = 0;

		while (sum < number)
		{
			for (int value = 6; value >= 1; value--)
			{
				if (sum + value <= number)
				{
					dices.add(value);
					sum += value;
					break;
				}
			}
		}

		final Player manager = _game.getManager();
		for (int i : dices)
		{
			manager.getPosition().setHeading(manager.getPosition().getHeading() + 5000);
			manager.broadcastPacket(new Dice(manager, 4627, i));
		}
	}

	public void callNumber()
	{
		if (_game == null || !_game.isStarted())
			return;

		final long hot = _game.getCards().stream().filter(c -> c.getMissingNumbersForWin() <= 1).count();
		if (hot > 0 && hot != _lastHotCount)
		{
			if (hot == 1)
				broadcastMessage("Muy cerca del premio! 1 cartela esta armada!");
			else if (hot > 1)
				broadcastMessage("Muy cerca del premio! " + hot + " cartelas estan armadas!");

			_lastHotCount = hot;
		}
		else if (_tick % 2 == 0 && _game.getManager() == null && INTERVALO_NUMEROS_AUTOMATICOS >= 20)
		{
			announce();
			_tick++;
			return;
		}

		final int number = _game.callRandomNumber();
		if (number < 0)
		{
			cancelGame(true);
			return;
		}

		// Mostrar dados.
		throwDices(number);

		if (!FRASES_DE_NUMEROS)
			broadcastMessage("Marquen en sus cartelas, bola numero " + number + "!");
		else
		{
			String phrase = null;
			switch (number)
			{
				case 20, 30, 40, 50, 60, 70:
					phrase = "De rombo!";
					break;
				case 1:
					phrase = "Comienza el juego!";
					break;
				case 6:
					phrase = "Media docena!";
					break;
				case 10:
					phrase = "El crack del equipo!";
					break;
				case 13:
					phrase = "Mala suerte!";
					break;
				case 22:
					phrase = "Dos patitos en la laguna!";
					break;
				case 33:
					phrase = "La edad de Cristo!";
					break;
				case 51:
					phrase = "Una buena idea!";
					break;
				case 75:
					phrase = "Termino el juego!";
					break;
			}

			if (phrase == null)
				broadcastMessage(FRASES_GENERICAS[Rnd.get(FRASES_GENERICAS.length)] + ", letra " + getLetter(number) + ", numero " + number);
			else
				broadcastMessage(phrase + " Letra " + getLetter(number) + ", numero " + number);
		}

		_tick++;
	}

	public String getLetter(int number)
	{
		if (number >= 1 && number <= 15)
			return "B";
		else if (number >= 16 && number <= 30)
			return "I";
		else if (number >= 31 && number <= 45)
			return "N";
		else if (number >= 46 && number <= 60)
			return "G";
		else if (number >= 61 && number <= 75)
			return "O";
		else
			return null;
	}

	public void onPlayerLogin(Player player)
	{
		if (_game == null)
			return;

		if (_game.getManagerId() == player.getObjectId())
			_game.setManager(player);

		_game.getCardsByPlayer(player.getObjectId()).forEach(c -> c.setPlayer(player));
	}

	public boolean canMove(int playerId, Location target)
	{
		if (_game == null || _game.getManagerId() != playerId)
			return true;

		return target.isIn3DRadius(_game.getNpc().getPosition(), Npc.INTERACTION_DISTANCE);
	}

	private BingoCard getCard(int playerId, int id)
	{
		return _game == null ? null : _game.getCardsByPlayer(playerId).stream().filter(c -> c.getObjectId() == id).findFirst().orElse(null);
	}

	public BingoCard getCardById(int id)
	{
		return _game == null ? null : _game.getCards().stream().filter(c -> c.getObjectId() == id).findFirst().orElse(null);
	}

	private void updateCardOwner(Player player, int cardId)
	{
		final BingoCard card = getCardById(cardId);
		updateCardOwner(player, card);
	}

	public void updateCardOwner(Player player, BingoCard card)
	{
		_game.getCardsByPlayer(card.getOwnerId()).remove(card);
		_game.getCardsByPlayer(player.getObjectId()).add(card);
		card.setPlayer(player);
	}

	private void broadcastMessage(String message)
	{
		World.announceToOnlinePlayers(String.format("%s: [%s] %s", _game.getNpc().getName(), new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()), message), true);
	}

	public void cancelGame(boolean announce)
	{
		if (_game == null)
			return;

		// Prioridad.
		cancelGameTask();

		if (announce)
			broadcastMessage(_tick == 0 ? "El bingo agendado fue cancelado" : "El bingo en curso fue cancelado");

		IdFactory.getInstance().releaseId(_game.getId());
		deleteCards();
		_game = null;
		_tick = 0;
		_lastHotCount = 0;
	}

	public void enableAutomaticMode()
	{
		_gameTask = ThreadPool.scheduleAtFixedRate(this::callNumber, 0, (INTERVALO_NUMEROS_AUTOMATICOS / 2) * 1000);
	}

	public void scheduleGame(long startTime)
	{
		cancelGameTask();
		_gameTask = ThreadPool.schedule(this::startGame, startTime - System.currentTimeMillis());
	}

	public synchronized void cancelGameTask()
	{
		if (_gameTask == null)
			return;

		_gameTask.cancel(true);
		_gameTask = null;
	}

	public void createCard(Player player)
	{
		final ItemInstance item = player.addItem(ID_ITEM_CARTELA, 1, true);
		final BingoCard card = new BingoCard(item, player, _temporaryCards.remove(player.getObjectId()));
		_game.getCardsByPlayer(player.getObjectId()).add(card);
	}

	public void showCardHtm(Player player, int cardId, int npcId)
	{
		final int[][] cardNumbers;
		List<Integer> winningNumbers = null;
		BingoCard currentCard = null;

		if (cardId > 0)
		{
			currentCard = getCard(player.getObjectId(), cardId);
			cardNumbers = currentCard.getCardNumbers();

			if (_game.getWinningCardId() == cardId)
				winningNumbers = currentCard.getWinningNumbers();
		}
		else
		{
			cardNumbers = generateRandomNumbers();
			_temporaryCards.put(player.getObjectId(), cardNumbers);
		}

		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile(HTML_PATH + "card.htm");
		html.replace("%actions%", HtmCache.getInstance().getHtmForce(HTML_PATH + (npcId > 0 ? "card_buy.htm" : "card_mark.htm")));

		for (int col = 0; col < 5; col++)
		{
			for (int row = 0; row < 5; row++)
			{
				final int number = cardNumbers[row][col];
				final String marker = "%" + col + "_" + row + "%";

				// Resaltar numeros de la victoria.
				if (winningNumbers != null)
					html.replace(marker, "<font color=" + (winningNumbers.contains(number) ? "00ff00" : "ffffff") + ">[x] %number%</font>");
				else if (cardId == 0)
					html.replace(marker, "[  ] %number%");
				else if (currentCard != null && currentCard.getMarkedNumbersList().contains(number))
					html.replace(marker, "<font color=" + (_game.getWinningCardId() == cardId ? "00ff00" : "dc7633") + ">[x] %number%</font>");
				else
					html.replace(marker, "<a action=\"bypass bingo mark %card% %number%\">[  ] %number%</a>");

				html.replace("%number%", number);
			}
		}

		final List<ItemInstance> cards = player.getInventory().getItemsByItemId(ID_ITEM_CARTELA);
		// Identificar si existe un intento de evadir el limite de cartelas.
		if (cards.size() > MAX_CARTELAS)
		{
			// No es posible comprar mas del limite, pero es posible obtener mas de otros players.
			cards.removeIf(i -> i.getOwnerId() != player.getObjectId());
		}
		else
		{
			// Cartelas que eran de otros characters pero que el player actual aun no registro.
			cards.stream().filter(c -> c.getOwnerId() != player.getObjectId()).forEach(i -> updateCardOwner(player, i.getObjectId()));
		}

		if (cards.size() > 1)
		{
			final StringBuilder sb = new StringBuilder();
			int count = 0;
			sb.append("<table><tr>");

			for (ItemInstance card : cards)
			{
				if (count % 4 == 0)
					sb.append("</tr><tr>");

				count++;
				final String texture = card.getObjectId() == cardId ? "back=\"sek.cbui67\" fore=\"sek.cbui67\"" : "back=\"sek.cbui69\" fore=\"sek.cbui69\"";
				sb.append("<td><button value=\"Cartela " + count + "\" action=\"bypass bingo showcard " + card.getObjectId() + "\" width=60 height=13 " + texture + "></td>");
			}

			sb.append("</tr></table><br>");
			html.replace("%cardlist%", sb.toString());
		}
		else
			html.replace("%cardlist%", "");

		if (cardId > 0)
			html.setItemId(ID_ITEM_CARTELA);

		html.replace("%objectId%", npcId);
		html.replace("%card%", cardId);
		player.sendPacket(html);
	}

	public BingoGame getGame()
	{
		return _game;
	}

	public void removeCard(int playerId, int itemId)
	{
		if (_game == null)
			return;

		_game.getCardsByPlayer(playerId).removeIf(c -> c.getObjectId() == itemId);
	}

	private void deleteCards()
	{
		_game.getCards().forEach(BingoCard::deleteMe);
		deleteOfflineCards();
	}

	private int[][] generateRandomNumbers()
	{
		final int[][] card = new int[5][5];

		// Columnas: B (1-15), I (16-30), N (31-45), G (46-60), O (61-75).
		for (int col = 0; col < 5; col++)
		{
			final int start = col * 15 + 1;
			final List<Integer> columnNumbers = new ArrayList<>();

			// Genera los numeros posibles para la columna.
			for (int i = start; i < start + 15; i++)
				columnNumbers.add(i);

			// Mezclar.
			Collections.shuffle(columnNumbers);

			for (int row = 0; row < 5; row++)
			{
				if (!(col == 2 && row == 2))
					card[row][col] = columnNumbers.get(row);
			}
		}

		// Cartelas iguales (cual es la probabilidad de que ocurra?).
		if (!_game.getCards().stream().anyMatch(c -> Arrays.deepEquals(c.getCardNumbers(), card)))
			return card;

		// Intenta generar otra recursivamente.
		return generateRandomNumbers();
	}

	private static void deleteOfflineCards()
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(DELETE_ITEMS))
		{
			ps.setInt(1, ID_ITEM_CARTELA);
			ps.execute();
		}
		catch (Exception e)
		{
			LOGGER.error("BingoManager: fallo al eliminar cartelas del bingo de la base de datos.", e);
		}
	}

	public static final BingoManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}

	private static class SingletonHolder
	{
		protected static final BingoManager INSTANCE = new BingoManager();
	}
}
