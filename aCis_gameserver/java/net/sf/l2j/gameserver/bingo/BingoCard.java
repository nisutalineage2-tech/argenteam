package net.sf.l2j.gameserver.bingo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;

public class BingoCard
{
	private static final int SIZE = 5;
	private final int[][] _cardNumbers;
	private final boolean[][] _markedNumbers;
	private final ItemInstance _item;
	private Player _player;

	public BingoCard(ItemInstance item, Player player, int[][] numbers)
	{
		_cardNumbers = numbers;
		_markedNumbers = new boolean[5][5];
		_markedNumbers[2][2] = true; // espacio libre en el centro
		_item = item;
		_player = player;
	}

	public final int getObjectId()
	{
		return _item.getObjectId();
	}

	public final int getOwnerId()
	{
		return _player.getObjectId();
	}

	public final int[][] getCardNumbers()
	{
		return _cardNumbers;
	}

	public final boolean[][] getMarkedNumbers()
	{
		return _markedNumbers;
	}

	public List<Integer> getMarkedNumbersList()
	{
		final List<Integer> markedNumbersList = new ArrayList<>();
		for (int row = 0; row < SIZE; row++)
		{
			for (int col = 0; col < SIZE; col++)
			{
				if (_markedNumbers[row][col] && _cardNumbers[row][col] != 0)
					markedNumbersList.add(_cardNumbers[row][col]);
			}
		}
		return markedNumbersList;
	}

	public void markNumber(int number)
	{
		for (int row = 0; row < SIZE; row++)
		{
			for (int col = 0; col < SIZE; col++)
			{
				if (_cardNumbers[row][col] == number)
				{
					_markedNumbers[row][col] = true;
					return;
				}
			}
		}
	}

	public boolean isWinner()
	{
		return !getWinningNumbers().isEmpty();
	}

	public List<Integer> getWinningNumbers()
	{
		for (int i = 0; i < SIZE; i++)
		{
			if (isLineComplete(i, true))
				return getLine(i, true);

			if (isLineComplete(i, false))
				return getLine(i, false);
		}

		if (isDiagonalComplete(true))
			return getDiagonal(true);

		if (isDiagonalComplete(false))
			return getDiagonal(false);

		return Collections.emptyList();
	}

	private boolean isLineComplete(int index, boolean isRow)
	{
		return IntStream.range(0, SIZE).allMatch(i -> _markedNumbers[isRow ? index : i][isRow ? i : index]);
	}

	private boolean isDiagonalComplete(boolean isMain)
	{
		return IntStream.range(0, SIZE).allMatch(i -> _markedNumbers[i][isMain ? i : SIZE - 1 - i]);
	}

	private List<Integer> getLine(int index, boolean isRow)
	{
		final List<Integer> line = new ArrayList<>();
		for (int i = 0; i < SIZE; i++)
			line.add(_cardNumbers[isRow ? index : i][isRow ? i : index]);

		return line;
	}

	private List<Integer> getDiagonal(boolean isMain)
	{
		final List<Integer> diagonal = new ArrayList<>();
		for (int i = 0; i < SIZE; i++)
			diagonal.add(_cardNumbers[i][isMain ? i : SIZE - 1 - i]);

		return diagonal;
	}

	public int getMissingNumbersForWin()
	{
		int minMissing = SIZE;
		for (int i = 0; i < SIZE; i++)
		{
			minMissing = Math.min(minMissing, getMissingInLine(i, true));
			minMissing = Math.min(minMissing, getMissingInLine(i, false));
		}

		minMissing = Math.min(minMissing, getMissingInDiagonal(true));
		minMissing = Math.min(minMissing, getMissingInDiagonal(false));
		return minMissing;
	}

	private int getMissingInLine(int index, boolean isRow)
	{
		return (int) IntStream.range(0, SIZE).filter(i -> !_markedNumbers[isRow ? index : i][isRow ? i : index]).count();
	}

	private int getMissingInDiagonal(boolean isMain)
	{
		return (int) IntStream.range(0, SIZE).filter(i -> !_markedNumbers[i][isMain ? i : SIZE - 1 - i]).count();
	}

	public void setPlayer(Player player)
	{
		_player = player;
	}

	public void deleteMe()
	{
		// Cambio de dueno.
		if (_item.getOwnerId() != _player.getObjectId())
			_player = World.getInstance().getPlayer(_item.getOwnerId());

		// Sera eliminado directamente por la base de datos.
		if (_player == null || !_player.isOnline())
			return;

		_player.destroyItem(_item, false);
	}
}
