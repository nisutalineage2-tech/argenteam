package net.sf.l2j.gameserver.event;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ConnectionPool;

import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.skills.L2Skill;

/**
 * Allows players to select buffs before an event starts.
 * Buffs are persisted per-player and applied when the event begins.
 */
public final class EventBuffer
{
	private static final CLogger LOGGER = new CLogger(EventBuffer.class.getName());
	
	private static final String SELECT_BUFFS = "SELECT buffs FROM event_buffs WHERE player=?";
	private static final String INSERT_BUFFS = "INSERT INTO event_buffs (player,buffs) VALUES (?,?) ON DUPLICATE KEY UPDATE buffs=?";
	
	/** Default allowed buff IDs if config property is missing. */
	private static final int[] DEFAULT_ALLOWED_BUFFS =
	{
		1068, 1085, 1086, 1087
	};
	
	private final ConcurrentHashMap<Integer, List<Integer>> _playerBuffs = new ConcurrentHashMap<>();
	
	private EventBuffer()
	{
	}
	
	private static class SingletonHolder
	{
		protected static final EventBuffer INSTANCE = new EventBuffer();
	}
	
	public static EventBuffer getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	/**
	 * Load saved buffs from database for a player.
	 */
	public void loadBuffs(int playerObjId)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_BUFFS))
		{
			ps.setInt(1, playerObjId);
			
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					final String buffStr = rs.getString("buffs");
					if (buffStr != null && !buffStr.isEmpty())
					{
						final List<Integer> buffs = new ArrayList<>();
						for (String s : buffStr.split(","))
						{
							try
							{
								buffs.add(Integer.parseInt(s.trim()));
							}
							catch (NumberFormatException e)
							{
								// Skip invalid entries
							}
						}
						_playerBuffs.put(playerObjId, buffs);
					}
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to load event buffs for player {}: {}", e, playerObjId);
		}
	}
	
	/**
	 * Save selected buffs to database.
	 */
	public void saveBuffs(int playerObjId, List<Integer> buffIds)
	{
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < buffIds.size(); i++)
		{
			if (i > 0)
				sb.append(",");
			sb.append(buffIds.get(i));
		}
		final String buffStr = sb.toString();
		
		// Update cache
		_playerBuffs.put(playerObjId, new ArrayList<>(buffIds));
		
		// Update database
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(INSERT_BUFFS))
		{
			ps.setInt(1, playerObjId);
			ps.setString(2, buffStr);
			ps.setString(3, buffStr);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to save event buffs for player {}: {}", e, playerObjId);
		}
	}
	
	/**
	 * @return The saved buff IDs for a player (may be empty).
	 */
	public List<Integer> getSavedBuffs(int playerObjId)
	{
		return _playerBuffs.getOrDefault(playerObjId, List.of());
	}
	
	/**
	 * Apply all saved buffs to a player (called when event match starts).
	 */
	public void applyBuffs(Player player)
	{
		final List<Integer> buffIds = getSavedBuffs(player.getObjectId());
		if (buffIds.isEmpty())
			return;
		
		final int maxBuffs = EventConfig.getMaxBuffNum();
		int count = 0;
		
		for (int skillId : buffIds)
		{
			if (count >= maxBuffs)
				break;
			
			final L2Skill skill = player.getSkill(skillId);
			if (skill != null)
			{
				skill.getEffects(player, player);
				count++;
			}
		}
	}
	
	/**
	 * Shows the buff selection HTML page to a player.
	 */
	public void showBufferPage(Player player, int npcObjId)
	{
		final List<Integer> savedBuffs = getSavedBuffs(player.getObjectId());
		final List<Integer> allowedBuffs = getAllowedBuffs();
		final int maxBuffs = EventConfig.getMaxBuffNum();
		
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><body><center>");
		sb.append("<font color=\"LEVEL\">Selección de Buffs del Evento</font><br>");
		sb.append("Selecciona hasta ").append(maxBuffs).append(" buffs:<br><br>");
		sb.append("<table width=\"280\">");
		
		int col = 0;
		for (int skillId : allowedBuffs)
		{
			final L2Skill skill = player.getSkill(skillId);
			if (skill == null)
				continue;
			
			final boolean isSelected = savedBuffs.contains(skillId);
			
			if (col == 0)
				sb.append("<tr>");
			
			sb.append("<td width=\"140\">");
			sb.append("<a action=\"bypass -h npc_" + npcObjId + "_event_buff ").append(skillId).append("\">");
			sb.append(isSelected ? "<font color=\"00FF00\">[X]</font> " : "[ ] ");
			sb.append(skill.getName());
			sb.append("</a></td>");
			
			col++;
			if (col >= 2)
			{
				sb.append("</tr>");
				col = 0;
			}
		}
		
		if (col > 0)
			sb.append("</tr>");
		
		sb.append("</table><br>");
		sb.append("<button value=\"Limpiar Todo\" action=\"bypass -h npc_").append(npcObjId).append("_event_buff_clear\" width=\"100\" height=\"20\" back=\"sek.cbui94\" fore=\"sek.cbui92\">");
		sb.append("</center></body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(npcObjId);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
	
	/**
	 * Toggles a buff for a player. If already selected, removes it; otherwise adds it.
	 */
	public void toggleBuff(Player player, int skillId)
	{
		final List<Integer> buffs = new ArrayList<>(_playerBuffs.getOrDefault(player.getObjectId(), List.of()));
		final int maxBuffs = EventConfig.getMaxBuffNum();
		
		if (buffs.contains(skillId))
			buffs.remove(Integer.valueOf(skillId));
		else if (buffs.size() < maxBuffs)
			buffs.add(skillId);
		else
		{
			player.sendMessage("Solo puedes seleccionar hasta " + maxBuffs + " buffs.");
			return;
		}
		
		saveBuffs(player.getObjectId(), buffs);
	}
	
	/**
	 * Clears all saved buffs for a player.
	 */
	public void clearBuffs(Player player)
	{
		_playerBuffs.remove(player.getObjectId());
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(INSERT_BUFFS))
		{
			ps.setInt(1, player.getObjectId());
			ps.setString(2, "");
			ps.setString(3, "");
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.warn("Failed to clear event buffs for player {}: {}", e, player.getObjectId());
		}
		
		player.sendMessage("Buffs del evento eliminados.");
	}
	
	/**
	 * @return List of allowed buff skill IDs from config.
	 */
	private List<Integer> getAllowedBuffs()
	{
		final List<Integer> buffs = new ArrayList<>();
		final String allowedStr = EventConfig.getAllowedBuffsList();
		
		if (allowedStr == null || allowedStr.isEmpty())
		{
			for (int id : DEFAULT_ALLOWED_BUFFS)
				buffs.add(id);
			return buffs;
		}
		
		for (String s : allowedStr.split(","))
		{
			try
			{
				buffs.add(Integer.parseInt(s.trim()));
			}
			catch (NumberFormatException e)
			{
				// Skip invalid entries
			}
		}
		return buffs;
	}
	
	/**
	 * Remove player from cache on logout.
	 */
	public void removePlayer(int playerObjId)
	{
		_playerBuffs.remove(playerObjId);
	}
}
