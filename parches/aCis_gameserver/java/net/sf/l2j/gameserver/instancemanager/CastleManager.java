/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.l2j.gameserver.instancemanager;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.datatables.ClanTable;
import net.sf.l2j.gameserver.instancemanager.SevenSigns.CabalType;
import net.sf.l2j.gameserver.model.L2Clan;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.SpawnLocation;
import net.sf.l2j.gameserver.model.TowerSpawn;
import net.sf.l2j.gameserver.model.entity.Castle;
import net.sf.l2j.gameserver.model.entity.Siege;
import net.sf.l2j.gameserver.model.item.MercenaryTicket;
import net.sf.l2j.gameserver.templates.StatsSet;
import net.sf.l2j.gameserver.xmlfactory.XMLDocumentFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public final class CastleManager
{
	private static final Logger LOG = Logger.getLogger(CastleManager.class.getName());
	
	private static final String LOAD_CASTLES = "SELECT * FROM castle ORDER BY id";
	private static final String LOAD_OWNERS = "SELECT clan_id FROM clan_data WHERE hasCastle=?";
	
	private final Map<Integer, Castle> _castles = new HashMap<>();
	
	protected CastleManager()
	{
		// Generate Castle objects with dynamic data.
		try (Connection con = L2DatabaseFactory.getInstance().getConnection(); PreparedStatement ps = con.prepareStatement(LOAD_CASTLES); ResultSet rs = ps.executeQuery())
		{
			while (rs.next())
			{
				final int id = rs.getInt("id");
				final Castle castle = new Castle(id, rs.getString("name"));
				
				castle.setSiegeDate(Calendar.getInstance());
				castle.getSiegeDate().setTimeInMillis(rs.getLong("siegeDate"));
				castle.setTimeRegistrationOver(rs.getBoolean("regTimeOver"));
				castle.setTaxPercent(rs.getInt("taxPercent"), false);
				castle.setTreasury(rs.getLong("treasury"));
				
				try (PreparedStatement ps1 = con.prepareStatement(LOAD_OWNERS))
				{
					ps1.setInt(1, id);
					try (ResultSet rs1 = ps1.executeQuery())
					{
						while (rs1.next())
						{
							final int ownerId = rs1.getInt("clan_id");
							if (ownerId > 0)
							{
								final L2Clan clan = ClanTable.getInstance().getClan(ownerId);
								if (clan != null)
									castle.setOwnerId(ownerId);
							}
						}
					}
				}
				
				_castles.put(id, castle);
			}
		}
		catch (Exception e)
		{
			LOG.log(Level.WARNING, "CastleManager: SQL loading failed : " + e.getMessage(), e);
		}
		
		// Feed Castle objects with static data.
		try
		{
			final File f = new File("./data/xml/castles.xml");
			final StatsSet set = new StatsSet();
			
			final Document doc = XMLDocumentFactory.getInstance().loadDocument(f);
			
			final Node list = doc.getFirstChild();
			for (Node cas = list.getFirstChild(); cas != null; cas = cas.getNextSibling())
			{
				if ("castle".equalsIgnoreCase(cas.getNodeName()))
				{
					NamedNodeMap attrs = cas.getAttributes();
					
					final Castle castle = _castles.get(Integer.parseInt(attrs.getNamedItem("id").getNodeValue()));
					if (castle == null)
						continue;
					
					castle.setCircletId(Integer.parseInt(attrs.getNamedItem("circletId").getNodeValue()));
					
					for (Node cat = cas.getFirstChild(); cat != null; cat = cat.getNextSibling())
					{
						if ("artifact".equalsIgnoreCase(cat.getNodeName()))
							castle.setArtifacts(cat.getAttributes().getNamedItem("val").getNodeValue());
						else if ("controlTowers".equalsIgnoreCase(cat.getNodeName()))
						{
							attrs = cat.getAttributes();
							for (Node tower = cat.getFirstChild(); tower != null; tower = tower.getNextSibling())
							{
								if ("tower".equalsIgnoreCase(tower.getNodeName()))
								{
									attrs = tower.getAttributes();
									
									final String[] location = attrs.getNamedItem("loc").getNodeValue().split(",");
									
									castle.getControlTowers().add(new TowerSpawn(13002, new SpawnLocation(Integer.parseInt(location[0]), Integer.parseInt(location[1]), Integer.parseInt(location[2]), -1)));
								}
							}
						}
						else if ("flameTowers".equalsIgnoreCase(cat.getNodeName()))
						{
							attrs = cat.getAttributes();
							for (Node tower = cat.getFirstChild(); tower != null; tower = tower.getNextSibling())
							{
								if ("tower".equalsIgnoreCase(tower.getNodeName()))
								{
									attrs = tower.getAttributes();
									
									final String[] location = attrs.getNamedItem("loc").getNodeValue().split(",");
									final String[] zoneIds = attrs.getNamedItem("zones").getNodeValue().split(",");
									
									castle.getFlameTowers().add(new TowerSpawn(13004, new SpawnLocation(Integer.parseInt(location[0]), Integer.parseInt(location[1]), Integer.parseInt(location[2]), -1), zoneIds));
								}
							}
						}
						else if ("relatedNpcIds".equalsIgnoreCase(cat.getNodeName()))
							castle.setRelatedNpcIds(cat.getAttributes().getNamedItem("val").getNodeValue());
						else if ("tickets".equalsIgnoreCase(cat.getNodeName()))
						{
							attrs = cat.getAttributes();
							for (Node ticket = cat.getFirstChild(); ticket != null; ticket = ticket.getNextSibling())
							{
								if ("ticket".equalsIgnoreCase(ticket.getNodeName()))
								{
									attrs = ticket.getAttributes();
									
									set.set("itemId", Integer.valueOf(attrs.getNamedItem("itemId").getNodeValue()));
									set.set("type", attrs.getNamedItem("type").getNodeValue());
									set.set("stationary", Boolean.valueOf(attrs.getNamedItem("stationary").getNodeValue()));
									set.set("npcId", Integer.valueOf(attrs.getNamedItem("npcId").getNodeValue()));
									set.set("maxAmount", Integer.valueOf(attrs.getNamedItem("maxAmount").getNodeValue()));
									set.set("ssq", attrs.getNamedItem("ssq").getNodeValue());
									
									castle.getTickets().add(new MercenaryTicket(set));
									set.clear();
								}
							}
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			LOG.log(Level.WARNING, "CastleManager: XML loading failed : ", e);
		}
		LOG.info("CastleManager: Loaded " + _castles.size() + " castles.");
		
		// Load traps informations. Generate siege entities for every castle (if not handled, it's only processed during player login).
		for (Castle castle : _castles.values())
		{
			castle.loadTrapUpgrade();
			castle.setSiege(new Siege(castle));
		}
	}
	
	public Castle getCastleById(int castleId)
	{
		return _castles.get(castleId);
	}
	
	public Castle getCastleByOwner(L2Clan clan)
	{
		final Optional<Castle> opt = _castles.values().stream().filter(c -> c.getOwnerId() == clan.getClanId()).findFirst();
		return opt.isPresent() ? opt.get() : null;
	}
	
	public Castle getCastleByName(String name)
	{
		final Optional<Castle> opt = _castles.values().stream().filter(c -> c.getName().equalsIgnoreCase(name)).findFirst();
		return opt.isPresent() ? opt.get() : null;
	}
	
	public Castle getCastle(int x, int y, int z)
	{
		final Optional<Castle> opt = _castles.values().stream().filter(c -> c.checkIfInZone(x, y, z)).findFirst();
		return opt.isPresent() ? opt.get() : null;
	}
	
	public Castle getCastle(L2Object object)
	{
		return getCastle(object.getX(), object.getY(), object.getZ());
	}
	
	public Collection<Castle> getCastles()
	{
		return _castles.values();
	}
	
	public void validateTaxes(CabalType sealStrifeOwner)
	{
		int maxTax;
		switch (sealStrifeOwner)
		{
			case DAWN:
				maxTax = 25;
				break;
			
			case DUSK:
				maxTax = 5;
				break;
			
			default:
				maxTax = 15;
				break;
		}
		
		_castles.values().stream().filter(c -> c.getTaxPercent() > maxTax).forEach(c -> c.setTaxPercent(maxTax, true));
	}
	
	public Siege getSiege(L2Object activeObject)
	{
		return getSiege(activeObject.getX(), activeObject.getY(), activeObject.getZ());
	}
	
	public Siege getSiege(int x, int y, int z)
	{
		for (Castle castle : _castles.values())
			if (castle.getSiege().checkIfInZone(x, y, z))
				return castle.getSiege();
			
		return null;
	}
	
	public static final CastleManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static final class SingletonHolder
	{
		protected static final CastleManager INSTANCE = new CastleManager();
	}
}