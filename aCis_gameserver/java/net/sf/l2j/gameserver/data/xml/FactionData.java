package net.sf.l2j.gameserver.data.xml;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.commons.pool.ConnectionPool;
import net.sf.l2j.commons.data.StatSet;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;

import org.w3c.dom.Document;

public class FactionData implements IXmlReader
{
	private static final String INSERT_CHARACTER_FACTION = "INSERT INTO mods_faction (char_id,factionId,factionPoints) VALUES (?,?,?)";
	private static final String DELETE_CHARACTER_FACTION = "DELETE FROM mods_faction WHERE char_id=?";
	private static final String RESTORE_CHARACTER_FACTION = "SELECT factionId, factionPoints FROM mods_faction WHERE char_id=?";
	private static final String UPDATE_CHARACTER_FACTION_POINTS = "UPDATE mods_faction SET factionPoints=? WHERE char_id=?";
	
	private final Map<Integer, Faction> _factions = new HashMap<>();
	
	protected FactionData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		_factions.clear();
		parseFile("./data/xml/faction.xml");
		LOGGER.info("Loaded {} factions.", _factions.size());
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "list", listNode -> forEach(listNode, "faction", factionNode ->
		{
			final StatSet set = parseAttributes(factionNode);
			_factions.put(set.getInteger("id"), new Faction(set));
		}));
	}
	
	public Faction getFaction(int id)
	{
		return _factions.get(id);
	}
	
	public int getFactionCount()
	{
		return _factions.size();
	}
	
	public int[] getFactionIds()
	{
		return _factions.keySet().stream().mapToInt(Integer::intValue).toArray();
	}
	
	public void onPlayerEnter(Player player)
	{
		if (!Config.ENABLE_FACTION_SYSTEM)
			return;
		
		restoreData(player);
		
		final Faction faction = _factions.get(player.getFactionId());
		if (faction != null)
		{
			player.getAppearance().setNameColor(faction.getNameColor());
			player.getAppearance().setTitleColor(faction.getTitleColor());
			player.setTitle(faction.getName());
			
			// Town restriction: if player is near enemy base, teleport to own base
			if (FactionWarConfig.isEnabled() && FactionWarConfig.isTownRestriction() && faction.getHomeLocation() != null)
			{
				final int playerFactionId = player.getFactionId();
				final int enemyFactionId = (playerFactionId == FactionWarConfig.getGoodFactionId()) ? FactionWarConfig.getEvilFactionId() : FactionWarConfig.getGoodFactionId();
				final Location enemyBase = net.sf.l2j.gameserver.factionwar.FactionWarManager.getInstance().getFactionSpawn(enemyFactionId);
				final int radius = FactionWarConfig.getTownRestrictionRadius();
				
				if (enemyBase != null && player.getPosition().distance3D(enemyBase) < radius)
				{
					final Location ownBase = net.sf.l2j.gameserver.factionwar.FactionWarManager.getInstance().getFactionSpawn(playerFactionId);
					if (ownBase != null)
						player.teleportTo(ownBase, 50);
					else
						player.teleportTo(faction.getHomeLocation(), 0);
					player.sendMessage("Zona enemiga restringida. Regresaste a tu base.");
				}
			}
		}
	}
	
	public void storeData(Player player)
	{
		if (!Config.ENABLE_FACTION_SYSTEM)
			return;
		
		removeData(player);
		
		if (player.getFactionId() == 0)
			return;
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(INSERT_CHARACTER_FACTION))
		{
			ps.setInt(1, player.getObjectId());
			ps.setInt(2, player.getFactionId());
			ps.setInt(3, player.getFactionPoints());
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't store faction data.", e);
		}
	}
	
	public void removeData(Player player)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(DELETE_CHARACTER_FACTION))
		{
			ps.setInt(1, player.getObjectId());
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't delete player faction.", e);
		}
	}
	
	public void restoreData(Player player)
	{
		if (!Config.ENABLE_FACTION_SYSTEM)
			return;
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(RESTORE_CHARACTER_FACTION))
		{
			ps.setInt(1, player.getObjectId());
			
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					player.setFactionId(rs.getInt("factionId"));
					player.setFactionPoints(rs.getInt("factionPoints"));
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't restore faction data.", e);
		}
	}
	
	public static FactionData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final FactionData INSTANCE = new FactionData();
	}
}
