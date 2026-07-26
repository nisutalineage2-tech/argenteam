package net.sf.l2j.gameserver.factionwar;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ConnectionPool;

import net.sf.l2j.gameserver.model.actor.Player;

public class FactionWarRegistry
{
	private static final CLogger LOGGER = new CLogger(FactionWarRegistry.class.getName());
	
	private static final String INSERT = "INSERT INTO mods_factionwar (char_id, faction_id) VALUES (?,?) ON DUPLICATE KEY UPDATE faction_id=VALUES(faction_id)";
	private static final String DELETE = "DELETE FROM mods_factionwar WHERE char_id=?";
	private static final String SELECT = "SELECT faction_id FROM mods_factionwar WHERE char_id=?";
	
	private static class SingletonHolder
	{
		protected static final FactionWarRegistry INSTANCE = new FactionWarRegistry();
	}
	
	public static FactionWarRegistry getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private FactionWarRegistry()
	{
	}
	
	public void register(Player player)
	{
		if (player == null || player.getFactionId() <= 0)
			return;
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(INSERT))
		{
			ps.setInt(1, player.getObjectId());
			ps.setInt(2, player.getFactionId());
			ps.executeUpdate();
			LOGGER.info("Player {} registered for Faction War (faction {}).", player.getName(), player.getFactionId());
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to register player {} for Faction War.", e, player.getName());
		}
	}
	
	public void unregister(Player player)
	{
		if (player == null)
			return;
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(DELETE))
		{
			ps.setInt(1, player.getObjectId());
			ps.executeUpdate();
			LOGGER.info("Player {} unregistered from Faction War.", player.getName());
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to unregister player {} from Faction War.", e, player.getName());
		}
	}
	
	public boolean isRegistered(Player player)
	{
		if (player == null)
			return false;
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT))
		{
			ps.setInt(1, player.getObjectId());
			try (ResultSet rs = ps.executeQuery())
			{
				return rs.next();
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to check registration for player {}.", e, player.getName());
			return false;
		}
	}
	
	public int getRegisteredFaction(Player player)
	{
		if (player == null)
			return 0;
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT))
		{
			ps.setInt(1, player.getObjectId());
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
					return rs.getInt("faction_id");
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to get registered faction for player {}.", e, player.getName());
		}
		return 0;
	}
}
