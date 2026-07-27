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
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.actor.Player;

import org.w3c.dom.Document;

public class FactionData implements IXmlReader
{
	private static final String INSERT_CHARACTER_FACTION = "INSERT INTO mods_faction (char_id,factionId) VALUES (?,?)";
	private static final String DELETE_CHARACTER_FACTION = "DELETE FROM mods_faction WHERE char_id=?";
	private static final String RESTORE_CHARACTER_FACTION = "SELECT factionId FROM mods_faction WHERE char_id=?";
	
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
					player.setFactionId(rs.getInt("factionId"));
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
