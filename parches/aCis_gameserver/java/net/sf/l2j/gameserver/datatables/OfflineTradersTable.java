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
package net.sf.l2j.gameserver.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.LoginServerThread;
import net.sf.l2j.gameserver.model.L2ManufactureItem;
import net.sf.l2j.gameserver.model.L2ManufactureList;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance.StoreType;
import net.sf.l2j.gameserver.model.tradelist.TradeItem;
import net.sf.l2j.gameserver.network.L2GameClient;
import net.sf.l2j.gameserver.network.L2GameClient.GameClientState;

public class OfflineTradersTable
{
	private static Logger _log = Logger.getLogger(OfflineTradersTable.class.getName());
	
	// SQL DEFINITIONS
	private static final String SAVE_OFFLINE_STATUS = "INSERT INTO character_offline_trade (`charId`,`time`,`type`,`title`) VALUES (?,?,?,?)";
	
	private static final String SAVE_ITEMS = "INSERT INTO character_offline_trade_items (`charId`,`item`,`count`,`price`) VALUES (?,?,?,?)";
	
	private static final String CLEAR_OFFLINE_TABLE = "DELETE FROM character_offline_trade";
	
	private static final String CLEAR_OFFLINE_TABLE_ITEMS = "DELETE FROM character_offline_trade_items";
	
	private static final String LOAD_OFFLINE_STATUS = "SELECT * FROM character_offline_trade";
	
	private static final String LOAD_OFFLINE_ITEMS = "SELECT * FROM character_offline_trade_items WHERE charId = ?";
	
	public static void storeOffliners()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement stm = con.prepareStatement(CLEAR_OFFLINE_TABLE);
			stm.execute();
			stm.close();
			stm = con.prepareStatement(CLEAR_OFFLINE_TABLE_ITEMS);
			stm.execute();
			stm.close();
			
			con.setAutoCommit(false); // avoid halfway done
			stm = con.prepareStatement(SAVE_OFFLINE_STATUS);
			PreparedStatement stm_items = con.prepareStatement(SAVE_ITEMS);
			
			// TextBuilder items = TextBuilder.newInstance();
			for (L2PcInstance pc : World.getInstance().getPlayers())
			{
				try
				{
					if ((pc.getStoreType() != StoreType.NONE) && (pc.getClient() == null || pc.getClient().isDetached()))
					{
						stm.setInt(1, pc.getObjectId()); // Char Id
						stm.setLong(2, pc.getOfflineStartTime());
						stm.setInt(3, pc.getStoreType().ordinal()); // store type
						String title = null;
						
						switch (pc.getStoreType())
						{
							case BUY:
								if (!Config.OFFLINE_TRADE_ENABLE)
									continue;
								title = pc.getBuyList().getTitle();
								for (TradeItem i : pc.getBuyList().getItems())
								{
									stm_items.setInt(1, pc.getObjectId());
									stm_items.setInt(2, i.getItem().getItemId());
									stm_items.setInt(3, i.getCount());
									stm_items.setInt(4, i.getPrice());
									stm_items.executeUpdate();
									stm_items.clearParameters();
								}
								break;
							case SELL:
							case PACKAGE_SELL:
								if (!Config.OFFLINE_TRADE_ENABLE)
									continue;
								title = pc.getSellList().getTitle();
								for (TradeItem i : pc.getSellList().getItems())
								{
									stm_items.setInt(1, pc.getObjectId());
									stm_items.setInt(2, i.getObjectId());
									stm_items.setInt(3, i.getCount());
									stm_items.setInt(4, i.getPrice());
									stm_items.executeUpdate();
									stm_items.clearParameters();
								}
								break;
							case MANUFACTURE:
								if (!Config.OFFLINE_CRAFT_ENABLE)
									continue;
								title = pc.getCreateList().getStoreName();
								for (L2ManufactureItem i : pc.getCreateList().getList())
								{
									stm_items.setInt(1, pc.getObjectId());
									stm_items.setInt(2, i.getRecipeId());
									stm_items.setLong(3, 0);
									stm_items.setLong(4, i.getCost());
									stm_items.executeUpdate();
									stm_items.clearParameters();
								}
						}
						stm.setString(4, title);
						stm.executeUpdate();
						stm.clearParameters();
						con.commit(); // flush
					}
				}
				catch (Exception e)
				{
					_log.log(Level.WARNING, "OfflineTradersTable[storeTradeItems()]: Error while saving offline trader: " + pc.getObjectId() + " " + e, e);
				}
			}
			stm.close();
			stm_items.close();
			_log.info("Offline traders stored.");
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "OfflineTradersTable[storeTradeItems()]: Error while saving offline traders: " + e, e);
		}
		finally
		{
			try
			{
				con.close();
			}
			catch (Exception e)
			{
			}
			con = null;
		}
	}
	
	public static void restoreOfflineTraders()
	{
		_log.info("Loading offline traders...");
		Connection con = null;
		int nTraders = 0;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement stm = con.prepareStatement(LOAD_OFFLINE_STATUS);
			ResultSet rs = stm.executeQuery();
			while (rs.next())
			{
				long time = rs.getLong("time");
				if (Config.OFFLINE_MAX_DAYS > 0)
				{
					Calendar cal = Calendar.getInstance();
					cal.setTimeInMillis(time);
					cal.add(Calendar.DAY_OF_YEAR, Config.OFFLINE_MAX_DAYS);
					if (cal.getTimeInMillis() <= System.currentTimeMillis())
						continue;
				}
				
				int type = rs.getInt("type");
				if (type == StoreType.NONE.ordinal())
					continue;
				
				L2PcInstance player = null;
				
				try
				{
					L2GameClient client = new L2GameClient(null);
					client.setDetached(true);
					player = L2PcInstance.restore(rs.getInt("charId"));
					client.setActiveChar(player);
					player.setOnlineStatus(true, false);
					client.setAccountName(player.getAccountName());
					client.setState(GameClientState.IN_GAME);
					player.setClient(client);
					player.setOfflineStartTime(time);
					player.spawnMe(player.getX(), player.getY(), player.getZ());
					LoginServerThread.getInstance().addGameServerLogin(player.getAccountName(), client);
					PreparedStatement stm_items = con.prepareStatement(LOAD_OFFLINE_ITEMS);
					stm_items.setInt(1, player.getObjectId());
					ResultSet items = stm_items.executeQuery();
					
					StoreType store = StoreType.values()[type];
					switch (store)
					{
						case BUY:
							while (items.next())
							{
								if (player.getBuyList().addItemByItemId(items.getInt(2), items.getInt(3), items.getInt(4)) == null)
									throw new NullPointerException();
							}
							player.getBuyList().setTitle(rs.getString("title"));
							break;
						case SELL:
						case PACKAGE_SELL:
							while (items.next())
							{
								if (player.getSellList().addItem(items.getInt(2), items.getInt(3), items.getInt(4)) == null)
									throw new NullPointerException();
							}
							player.getSellList().setTitle(rs.getString("title"));
							player.getSellList().setPackaged(store == StoreType.PACKAGE_SELL);
							break;
						case MANUFACTURE:
							L2ManufactureList createList = new L2ManufactureList();
							while (items.next())
							{
								createList.add(new L2ManufactureItem(items.getInt(2), items.getInt(4)));
							}
							player.setCreateList(createList);
							player.getCreateList().setStoreName(rs.getString("title"));
							break;
					}
					items.close();
					stm_items.close();
					
					player.sitDown();
					if (Config.OFFLINE_SET_NAME_COLOR)
						player.getAppearance().setNameColor(Config.OFFLINE_NAME_COLOR);
					player.setStoreType(store);
					player.setOnlineStatus(true, true);
					player.restoreEffects();
					player.broadcastUserInfo();
					nTraders++;
				}
				catch (Exception e)
				{
					_log.log(Level.WARNING, "OfflineTradersTable[loadOffliners()]: Error loading trader: " + player, e);
					if (player != null)
					{
						player.deleteMe();
					}
				}
			}
			rs.close();
			stm.close();
			_log.info("Loaded: " + nTraders + " offline trader(s)");
			stm = con.prepareStatement(CLEAR_OFFLINE_TABLE);
			stm.execute();
			stm.close();
			stm = con.prepareStatement(CLEAR_OFFLINE_TABLE_ITEMS);
			stm.execute();
			stm.close();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "OfflineTradersTable[loadOffliners()]: Error while loading offline traders: ", e);
		}
		finally
		{
			try
			{
				con.close();
			}
			catch (Exception e)
			{
			}
			con = null;
		}
	}
}