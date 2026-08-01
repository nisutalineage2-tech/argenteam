package net.sf.l2j.gameserver.data.sql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ConnectionPool;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.LoginServerThread;
import net.sf.l2j.gameserver.data.xml.RecipeData;
import net.sf.l2j.gameserver.enums.actors.OperateType;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.records.ManufactureItem;
import net.sf.l2j.gameserver.model.records.Recipe;
import net.sf.l2j.gameserver.model.trade.TradeItem;
import net.sf.l2j.gameserver.network.GameClient;
import net.sf.l2j.gameserver.network.GameClient.GameClientState;

/**
 * Handles persistence of offline private stores (trade & craft).<br>
 * <br>
 * Offline traders are regular {@link Player}s which entered offline mode upon disconnection while in store mode. Their shop
 * content is saved into dedicated tables, so it can be restored after a server restart (or an owner disconnection).
 */
public class OfflineTradersTable
{
	private static final CLogger LOGGER = new CLogger(OfflineTradersTable.class.getName());
	
	// SQL definitions
	private static final String SAVE_OFFLINE_STATUS = "INSERT INTO character_offline_trade (`charId`,`time`,`type`,`title`) VALUES (?,?,?,?)";
	private static final String SAVE_ITEMS = "INSERT INTO character_offline_trade_items (`charId`,`item`,`count`,`price`) VALUES (?,?,?,?)";
	private static final String CLEAR_OFFLINE_TABLE = "DELETE FROM character_offline_trade";
	private static final String CLEAR_OFFLINE_TABLE_ITEMS = "DELETE FROM character_offline_trade_items";
	private static final String LOAD_OFFLINE_STATUS = "SELECT * FROM character_offline_trade";
	private static final String LOAD_OFFLINE_ITEMS = "SELECT * FROM character_offline_trade_items WHERE charId = ?";
	
	/**
	 * Save every offline trader currently in the world into the database.
	 */
	public static void storeOffliners()
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			try (PreparedStatement stm = con.prepareStatement(CLEAR_OFFLINE_TABLE))
			{
				stm.execute();
			}
			try (PreparedStatement stm = con.prepareStatement(CLEAR_OFFLINE_TABLE_ITEMS))
			{
				stm.execute();
			}
			
			con.setAutoCommit(false); // avoid halfway done
			try (PreparedStatement stm = con.prepareStatement(SAVE_OFFLINE_STATUS);
				PreparedStatement stmItems = con.prepareStatement(SAVE_ITEMS))
			{
				for (Player pc : World.getInstance().getPlayers())
				{
					try
					{
						if (pc.getOperateType() != OperateType.NONE && (pc.getClient() == null || pc.getClient().isDetached()))
						{
							stm.setInt(1, pc.getObjectId());
							stm.setLong(2, pc.getOfflineStartTime());
							stm.setInt(3, pc.getOperateType().getId());
							String title = null;
							
							switch (pc.getOperateType())
							{
								case BUY:
									if (!Config.OFFLINE_TRADE_ENABLE)
										continue;
									title = pc.getBuyList().getTitle();
									for (TradeItem i : pc.getBuyList())
									{
										stmItems.setInt(1, pc.getObjectId());
										stmItems.setInt(2, i.getItemId());
										stmItems.setInt(3, i.getCount());
										stmItems.setInt(4, i.getPrice());
										stmItems.executeUpdate();
										stmItems.clearParameters();
									}
									break;
								
								case SELL:
								case PACKAGE_SELL:
									if (!Config.OFFLINE_TRADE_ENABLE)
										continue;
									title = pc.getSellList().getTitle();
									for (TradeItem i : pc.getSellList())
									{
										stmItems.setInt(1, pc.getObjectId());
										stmItems.setInt(2, i.getObjectId());
										stmItems.setInt(3, i.getCount());
										stmItems.setInt(4, i.getPrice());
										stmItems.executeUpdate();
										stmItems.clearParameters();
									}
									break;
								
								case MANUFACTURE:
									if (!Config.OFFLINE_CRAFT_ENABLE)
										continue;
									title = pc.getManufactureList().getStoreName();
									for (ManufactureItem i : pc.getManufactureList())
									{
										stmItems.setInt(1, pc.getObjectId());
										stmItems.setInt(2, i.recipeId());
										stmItems.setLong(3, 0);
										stmItems.setLong(4, i.cost());
										stmItems.executeUpdate();
										stmItems.clearParameters();
									}
									break;
								
								default:
									continue;
							}
							
							stm.setString(4, title);
							stm.executeUpdate();
							stm.clearParameters();
							con.commit(); // flush
						}
					}
					catch (Exception e)
					{
						LOGGER.warn("OfflineTradersTable[storeTradeItems()]: Error while saving offline trader: {} - {}", pc.getObjectId(), e.getMessage());
					}
				}
			}
			finally
			{
				con.setAutoCommit(true);
			}
			LOGGER.info("Offline traders stored.");
		}
		catch (Exception e)
		{
			LOGGER.warn("OfflineTradersTable[storeTradeItems()]: Error while saving offline traders: {}", e.getMessage());
		}
	}
	
	/**
	 * Restore offline traders from the database into the world.
	 */
	public static void restoreOfflineTraders()
	{
		LOGGER.info("Loading offline traders...");
		int nTraders = 0;
		try (Connection con = ConnectionPool.getConnection())
		{
			try (PreparedStatement stm = con.prepareStatement(LOAD_OFFLINE_STATUS);
				ResultSet rs = stm.executeQuery())
			{
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
					
					final int type = rs.getInt("type");
					if (type == OperateType.NONE.getId())
						continue;
					
					Player player = null;
					try
					{
						final GameClient client = new GameClient(null);
						client.setDetached(true);
						player = Player.restore(rs.getInt("charId"));
						if (player == null)
							continue;
						
						client.setPlayer(player);
						player.setOnlineStatus(true, false);
						client.setAccountName(player.getAccountName());
						client.setState(GameClientState.IN_GAME);
						player.setClient(client);
						player.setOfflineStartTime(time);
						player.spawnMe(player.getX(), player.getY(), player.getZ());
						LoginServerThread.getInstance().addGameServerLogin(player.getAccountName(), client);
						
						try (PreparedStatement stmItems = con.prepareStatement(LOAD_OFFLINE_ITEMS))
						{
							stmItems.setInt(1, player.getObjectId());
							try (ResultSet items = stmItems.executeQuery())
							{
								switch (getOperateType(type))
								{
									case BUY:
										while (items.next())
											if (player.getBuyList().addItemByItemId(items.getInt(2), items.getInt(3), items.getInt(4), 0) == null)
												throw new NullPointerException();
										player.getBuyList().setTitle(rs.getString("title"));
										break;
									
									case SELL:
									case PACKAGE_SELL:
										while (items.next())
											if (player.getSellList().addItem(items.getInt(2), items.getInt(3), items.getInt(4)) == null)
												throw new NullPointerException();
										player.getSellList().setTitle(rs.getString("title"));
										player.getSellList().setPackaged(type == OperateType.PACKAGE_SELL.getId());
										break;
									
									case MANUFACTURE:
										while (items.next())
										{
											final int recipeId = items.getInt(2);
											final Recipe recipe = RecipeData.getInstance().getRecipeList(recipeId);
											player.getManufactureList().add(new ManufactureItem(recipeId, items.getInt(4), recipe != null && recipe.isDwarven()));
										}
										player.getManufactureList().setStoreName(rs.getString("title"));
										if (!player.getManufactureList().isEmpty())
											player.getManufactureList().setState(player.getManufactureList().get(0).isDwarven());
										break;
									
									default:
										break;
								}
							}
						}
						
						player.sitDown();
						if (Config.OFFLINE_SET_NAME_COLOR)
							player.getAppearance().setNameColor(Config.OFFLINE_NAME_COLOR);
						player.setOperateType(getOperateType(type));
						player.setOnlineStatus(true, true);
						player.restoreEffects();
						player.broadcastUserInfo();
						nTraders++;
					}
					catch (Exception e)
					{
						LOGGER.warn("OfflineTradersTable[loadOffliners()]: Error loading trader: {} - {}", player, e.getMessage());
						if (player != null)
							player.deleteMe();
					}
				}
			}
			
			// Cleanup offline tables after a successful restore.
			try (PreparedStatement stm = con.prepareStatement(CLEAR_OFFLINE_TABLE))
			{
				stm.execute();
			}
			try (PreparedStatement stm = con.prepareStatement(CLEAR_OFFLINE_TABLE_ITEMS))
			{
				stm.execute();
			}
			LOGGER.info("Loaded: {} offline trader(s)", nTraders);
		}
		catch (Exception e)
		{
			LOGGER.warn("OfflineTradersTable[loadOffliners()]: Error while loading offline traders: {}", e.getMessage());
		}
	}
	
	/**
	 * Map the legacy stored type id to the modern {@link OperateType} enum.
	 * @param type : The stored type id (SELL=1, BUY=3, MANUFACTURE=5, PACKAGE_SELL=8).
	 * @return The matching {@link OperateType}, or NONE if unknown.
	 */
	private static OperateType getOperateType(int type)
	{
		switch (type)
		{
			case 1:
				return OperateType.SELL;
			case 3:
				return OperateType.BUY;
			case 5:
				return OperateType.MANUFACTURE;
			case 8:
				return OperateType.PACKAGE_SELL;
			default:
				return OperateType.NONE;
		}
	}
}
