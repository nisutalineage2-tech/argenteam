package net.sf.l2j.gameserver.data.xml;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.commons.pool.ConnectionPool;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.data.StatSet;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;

import org.w3c.dom.Document;

public class FactionData implements IXmlReader
{
	private static final String INSERT_CHARACTER_FACTION = "REPLACE INTO mods_faction (char_id,factionId,factionPoints) VALUES (?,?,?)";
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
		
		try
		{
			// restoreData() puebla el factionId desde la DB; debe correr ANTES del lookup para
			// que el gate 'faction != null' funcione para jugadores reales (colores + teleport).
			applyFactionVisuals(player);
			
			// Real players with a faction are teleported to their OWN faction zone after a short
			// delay, so each faction has its own base (faction.xml homeX/homeY/homeZ) instead of
			// everyone sharing the neutral zone:
			//   - If the war is running -> faction base on the current war map.
			//   - Otherwise            -> faction home city defined in faction.xml.
			// (Phantoms use applyFactionVisuals() directly to skip this behavior.)
			final Faction faction = _factions.get(player.getFactionId());
			if (faction != null && FactionWarConfig.isEnabled())
			{
				Location target = null;
				if (FactionWarManager.getInstance().isRunning())
					target = FactionWarManager.getInstance().getFactionSpawn(player.getFactionId());
				if (target == null)
					target = faction.getHomeLocation();
				
				if (target != null)
				{
					final int px = player.getX();
					final int py = player.getY();
					final int pz = player.getZ();
					
					if (Math.abs(px - target.getX()) > 100 || Math.abs(py - target.getY()) > 100 || Math.abs(pz - target.getZ()) > 50)
					{
						final Location dest = target;
						ThreadPool.schedule(() ->
						{
							if (player.isOnline() && !player.isInJail())
							{
								player.teleportTo(dest, 50);
								player.sendMessage("Bienvenido a la base de " + faction.getName() + ".");
							}
						}, 3000);
					}
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Error during onPlayerEnter for {}.", e, player.getName());
		}
	}
	
	/**
	 * Applies faction visuals (name color, title color, title) to the given player
	 * WITHOUT scheduling the delayed neutral-zone teleport used for real players.
	 * Used by the phantom system so a freshly spawned/bridged phantom doesn't "disappear" 3s later.
	 */
	public void applyFactionVisuals(Player player)
	{
		if (!Config.ENABLE_FACTION_SYSTEM)
			return;

		try
		{
			restoreData(player);

			final Faction faction = _factions.get(player.getFactionId());
			if (faction != null)
			{
				String name = faction.getName();
				int nameColor = faction.getNameColor();
				int titleColor = faction.getTitleColor();

				// Override with properties if faction war is enabled and this is a good/evil faction
				if (FactionWarConfig.isEnabled())
				{
					final int playerFactionId = player.getFactionId();
					if (playerFactionId == FactionWarConfig.getGoodFactionId())
					{
						name = FactionWarConfig.getGoodFactionName();
						nameColor = FactionWarConfig.getGoodFactionColor();
						titleColor = FactionWarConfig.getGoodFactionColor();
					}
					else if (playerFactionId == FactionWarConfig.getEvilFactionId())
					{
						name = FactionWarConfig.getEvilFactionName();
						nameColor = FactionWarConfig.getEvilFactionColor();
						titleColor = FactionWarConfig.getEvilFactionColor();
					}
				}

				player.getAppearance().setNameColor(nameColor);
				player.getAppearance().setTitleColor(titleColor);
				player.setTitle(name);
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Error during applyFactionVisuals for {}.", e, player.getName());
		}
	}
	
	public void storeData(Player player)
	{
		if (!Config.ENABLE_FACTION_SYSTEM)
			return;
		
		if (player.getFactionId() == 0)
		{
			removeData(player);
			return;
		}
		
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
