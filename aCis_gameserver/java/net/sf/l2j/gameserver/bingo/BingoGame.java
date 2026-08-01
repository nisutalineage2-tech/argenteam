package net.sf.l2j.gameserver.bingo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.BingoNpc;

public class BingoGame
{
	private final int _id;
	private final int _coinId;
	private final int _coinCount;
	private final int _rewardId;
	private final int _rewardCount;
	private final Map<Integer, List<BingoCard>> _cards = new ConcurrentHashMap<>();
	private final Set<Integer> _calledNumbers = new ConcurrentSkipListSet<>();
	private int _winningCardId;
	private int _lastNumber;
	private long _lastNumberTime;
	private long _scheduledTime;
	private long _startedTime;
	private boolean _started;
	private Player _manager;
	private BingoNpc _npc;

	public BingoGame(int coinId, int coinCount, int rewardId, int rewardCount, long scheduledTime, Player manager, BingoNpc npc)
	{
		_id = IdFactory.getInstance().getNextId();
		_coinId = coinId;
		_coinCount = coinCount;
		_rewardId = rewardId;
		_rewardCount = rewardCount;
		_scheduledTime = scheduledTime;
		_manager = manager;
		_npc = npc;
	}

	public final int getId()
	{
		return _id;
	}

	public final int getCoinId()
	{
		return _coinId;
	}

	public final int getCoinCount()
	{
		return _coinCount;
	}

	public final int getRewardId()
	{
		return _rewardId;
	}

	public final int getRewardCount()
	{
		return _rewardCount;
	}

	public final List<BingoCard> getCards()
	{
		return _cards.values().stream().flatMap(List::stream).toList();
	}

	public final List<BingoCard> getCardsByPlayer(int playerId)
	{
		return _cards.computeIfAbsent(playerId, k -> new ArrayList<>());
	}

	public final Set<Integer> getCalledNumbers()
	{
		return _calledNumbers;
	}

	public String getCalledNumbersString()
	{
		final Map<String, String> groupedNumbers = _calledNumbers.stream().collect(Collectors.groupingBy(number ->
			BingoManager.getInstance().getLetter(number),
			Collectors.mapping(String::valueOf, Collectors.joining(", "))
		));

		final StringBuilder result = new StringBuilder();
		for (String letter : List.of("B", "I", "N", "G", "O"))
			result.append(letter).append(" : ").append(groupedNumbers.getOrDefault(letter, "")).append("<br1>");

		return result.toString();
	}

	public boolean isParticipant(int playerId)
	{
		return _cards.containsKey(playerId) || getManagerId() == playerId;
	}

	public Player getManager()
	{
		return _manager;
	}

	public void setManager(Player player)
	{
		_manager = player;
	}

	public int getManagerId()
	{
		return _manager == null ? -1 : _manager.getObjectId();
	}

	public BingoNpc getNpc()
	{
		return _npc;
	}

	public long getScheduledTime()
	{
		return _scheduledTime;
	}

	public void setScheduledTime(long value)
	{
		_scheduledTime = value;
	}

	public void start()
	{
		_started = true;
		_startedTime = System.currentTimeMillis();
	}

	public void end()
	{
		_started = false;
	}

	public boolean isStarted()
	{
		return _started;
	}

	public long getStartedTime()
	{
		return _startedTime;
	}

	public int getLastNumber()
	{
		return _lastNumber;
	}

	public long getLastNumberTime()
	{
		return _lastNumberTime;
	}

	public int getTotalParticipants()
	{
		return _cards.size();
	}

	public int getWinningCardId()
	{
		return _winningCardId;
	}

	public void setWinningCard(int id)
	{
		_winningCardId = id;
	}

	public int callRandomNumber()
	{
		final Integer number = Rnd.get(IntStream.rangeClosed(1, 75).filter(i -> !_calledNumbers.contains(i)).boxed().toList());
		// No quedan mas numeros, se finaliza el juego.
		if (number == null)
			return -1;

		_calledNumbers.add(number);
		_lastNumber = number;
		_lastNumberTime = System.currentTimeMillis();
		return number;
	}
}
