package net.sf.l2j.gameserver.event;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ConnectionPool;

import net.sf.l2j.gameserver.model.actor.Player;

/**
 * Tracks per-player statistics across event sessions (kills, deaths, wins, losses).
 * Data is persisted to the database and cached in memory.
 */
public final class EventStats
{
	private static final CLogger LOGGER = new CLogger(EventStats.class.getName());
	
	private static final String SELECT_STATS = "SELECT * FROM event_stats WHERE player=? AND event=?";
	private static final String INSERT_STATS = "INSERT INTO event_stats (player,event,num,wins,losses,kills,deaths,scores) VALUES (?,?,1,?,?,?,?,?)";
	private static final String UPDATE_STATS = "UPDATE event_stats SET num=num+1,wins=wins+?,losses=losses+?,kills=kills+?,deaths=deaths+?,scores=scores+? WHERE player=? AND event=?";
	private static final String SELECT_ALL = "SELECT * FROM event_stats WHERE player=?";
	
	private final ConcurrentHashMap<Integer, Map<Integer, PlayerEventStats>> _cache = new ConcurrentHashMap<>();
	
	private EventStats()
	{
	}
	
	private static class SingletonHolder
	{
		protected static final EventStats INSTANCE = new EventStats();
	}
	
	public static EventStats getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	/**
	 * Called when an event ends. Updates the database with final stats for all participants.
	 * @param eventId the event type ID
	 * @param participants list of EventPlayers that finished the event
	 * @param winnerTeamId the winning team ID, or -1 for draw/FFA
	 */
	public void onEventEnd(int eventId, java.util.List<EventPlayer> participants, int winnerTeamId)
	{
		for (EventPlayer ep : participants)
		{
			if (!ep.isOnline())
				continue;
			
			final boolean isWinner = (winnerTeamId >= 0 && ep.getTeamId() == winnerTeamId);
			final int playerObjId = ep.getObjectId();
			
			saveStats(playerObjId, eventId, isWinner ? 1 : 0, isWinner ? 0 : 1, ep.getKills(), ep.getDeaths(), ep.getKills());
		}
	}
	
	/**
	 * Saves or updates stats for a player in a specific event type.
	 */
	private void saveStats(int playerObjId, int eventId, int wins, int losses, int kills, int deaths, int scores)
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			// Try to update existing record
			try (PreparedStatement ps = con.prepareStatement(UPDATE_STATS))
			{
				ps.setInt(1, wins);
				ps.setInt(2, losses);
				ps.setInt(3, kills);
				ps.setInt(4, deaths);
				ps.setInt(5, scores);
				ps.setInt(6, playerObjId);
				ps.setInt(7, eventId);
				
				if (ps.executeUpdate() == 0)
				{
					// No existing record, insert new one
					try (PreparedStatement ps2 = con.prepareStatement(INSERT_STATS))
					{
						ps2.setInt(1, playerObjId);
						ps2.setInt(2, eventId);
						ps2.setInt(3, wins);
						ps2.setInt(4, losses);
						ps2.setInt(5, kills);
						ps2.setInt(6, deaths);
						ps2.setInt(7, scores);
						ps2.executeUpdate();
					}
				}
			}
			
			// Update cache
			_cache.computeIfAbsent(playerObjId, k -> new ConcurrentHashMap<>())
				.put(eventId, new PlayerEventStats(wins, losses, kills, deaths, scores));
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to save event stats for player {}: {}", e, playerObjId, eventId);
		}
	}
	
	/**
	 * Loads all stats for a player from the database cache.
	 */
	public Map<Integer, PlayerEventStats> getStats(int playerObjId)
	{
		return _cache.getOrDefault(playerObjId, Map.of());
	}
	
	/**
	 * Gets total kills across all events for a player.
	 */
	public int getTotalKills(int playerObjId)
	{
		return _cache.getOrDefault(playerObjId, Map.of()).values().stream().mapToInt(s -> s.kills).sum();
	}
	
	/**
	 * Gets total wins across all events for a player.
	 */
	public int getTotalWins(int playerObjId)
	{
		return _cache.getOrDefault(playerObjId, Map.of()).values().stream().mapToInt(s -> s.wins).sum();
	}
	
	/**
	 * Loads stats from database into cache when a player logs in.
	 */
	public void loadPlayerStats(int playerObjId)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_ALL))
		{
			ps.setInt(1, playerObjId);
			
			final Map<Integer, PlayerEventStats> playerStats = new HashMap<>();
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final int eventId = rs.getInt("event");
					final int wins = rs.getInt("wins");
					final int losses = rs.getInt("losses");
					final int kills = rs.getInt("kills");
					final int deaths = rs.getInt("deaths");
					final int scores = rs.getInt("scores");
					playerStats.put(eventId, new PlayerEventStats(wins, losses, kills, deaths, scores));
				}
			}
			
			if (!playerStats.isEmpty())
				_cache.put(playerObjId, new ConcurrentHashMap<>(playerStats));
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to load event stats for player {}: {}", e, playerObjId);
		}
	}
	
	/**
	 * Removes a player from the cache on logout.
	 */
	public void removePlayer(int playerObjId)
	{
		_cache.remove(playerObjId);
	}
	
	/**
	 * Immutable stats snapshot for a player in one event type.
	 */
	public record PlayerEventStats(int wins, int losses, int kills, int deaths, int scores)
	{
		public int getTotalGames()
		{
			return wins + losses;
		}
		
		public double getKdRatio()
		{
			return deaths == 0 ? kills : (double) kills / deaths;
		}
		
		public double getWinRate()
		{
			final int total = wins + losses;
			return total == 0 ? 0 : (double) wins / total * 100;
		}
	}
}
