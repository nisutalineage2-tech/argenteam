/* This program is free software: you can redistribute it and/or modify it under
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
package net.sf.l2j.gameserver.custom.entity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.instance.L2FactTeleporterInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2GrandBossInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2NpcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2ProtectorInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2TpFlagInstance;
import net.sf.l2j.gameserver.model.zone.ZoneId;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.util.Broadcast;

/**
 * @author DarthVader
 */
public class FactionMaps
{
	private final static Logger _log = Logger.getLogger(FactionMaps.class.getName());
	
	public static Map<Integer, String> _all_maps = new HashMap<>();
	
	public static int _mapId = 0;
	public static String _mapName = "";
	public static int _maps_count = 0;
	public static boolean _voting = false;
	public static int[] _mapVotes = new int[20];
	public static String[] _mapNames = new String[20];
	private static long _nextMapIn = 0;
	private static int _team1pts = 0;
	private static int _team2pts = 0;
	
	public static int getTeam1Pts()
	{
		return _team1pts;
	}
	
	public static int getTeam2Pts()
	{
		return _team2pts;
	}
	
	public static void setTeam1Pts(int a)
	{
		_team1pts = a;
	}
	
	public static void setTeam2Pts(int a)
	{
		_team2pts = a;
	}
	
	public static boolean isVoting()
	{
		return _voting;
	}
	
	public static int getMapId()
	{
		return _mapId;
	}
	
	public static String getMapName()
	{
		return _mapName;
	}
	
	public static String getDelayUntilVoting()
	{
		long trim = _nextMapIn - Calendar.getInstance().getTimeInMillis();
		int h = 0;
		int m = 0;
		int s = 0;
		int k = 1 / 2;
		
		if (trim / 3600000 >= 1)
		{
			h = Math.round(trim / 3600000 - k);
			trim -= h * 3600000;
		}
		if (trim / 60 >= 1)
		{
			m = Math.round(trim / 60000 - k);
			trim -= m * 60000;
		}
		if (trim / 1000 >= 1)
		{
			s = Math.round(trim / 1000 - k);
		}
		
		return h + "h. " + m + "m. " + s + "s.";
	}
	
	public static void loadCurrentMap()
	{
		Connection con = null;
		try
		{
			con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement stmt = con.prepareStatement("SELECT mapId,map_name FROM faction_maps WHERE current=1");
			ResultSet rset = stmt.executeQuery();
			while (rset.next())
			{
				_mapId = rset.getInt("mapId");
				_mapName = rset.getString("map_name");
			}
			rset.close();
			stmt.close();
			
			// Put all maps in FastMap
			PreparedStatement stmt_all = con.prepareStatement("SELECT mapId,map_name FROM faction_maps");
			ResultSet rset_all = stmt_all.executeQuery();
			while (rset_all.next())
			{
				_all_maps.put(rset_all.getInt("mapId"), rset_all.getString("map_name"));
			}
			rset_all.close();
			stmt_all.close();
			
			_maps_count = _all_maps.size();
			
		}
		catch (Exception e)
		{
			_log.warning("Load current map: " + e);
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
		}
		ThreadPool.schedule(() -> endVoting(true), 1000);
	}
	
	public static void beginVoting()
	{
		for (L2TpFlagInstance flag : L2FactTeleporterInstance._tpTeam1Flags)
		{
			flag.deleteMe();
		}
		for (L2TpFlagInstance flag : L2FactTeleporterInstance._tpTeam2Flags)
		{
			flag.deleteMe();
		}
		for (L2ProtectorInstance guard : L2FactTeleporterInstance._guards)
		{
			guard.deleteMe();
		}
		for (L2NpcInstance blazer : L2FactTeleporterInstance._blazers)
		{
			blazer.deleteMe();
		}
		for (L2TpFlagInstance not_capt : L2FactTeleporterInstance._not_captured)
		{
			not_capt.deleteMe();
		}
		for (L2GrandBossInstance boss : L2FactTeleporterInstance._bosses)
		{
			boss.deleteMe();
		}
		L2FactTeleporterInstance._not_captured.clear();
		L2FactTeleporterInstance._tpTeam1Flags.clear();
		L2FactTeleporterInstance._tpTeam2Flags.clear();
		L2FactTeleporterInstance._guards.clear();
		L2FactTeleporterInstance._blazers.clear();
		L2FactTeleporterInstance._bosses.clear();
		L2TpFlagInstance._goddard_owners = 0;
		if (Config.FACTION_TELEPORT_ON_VOTE)
		{
			for (L2PcInstance player : World.getInstance().getPlayers())
			{
				player.setVotedForMap(false);
				// if (!player.isInSiege() && !player.isInsideZone(ZoneId.TOWN) && !player.isInsideZone(ZoneId.JAIL) && !player.isInOlympiadMode() && !player.isInsideZone(ZoneId.CASTLE) && !player.isInsideZone(ZoneId.CLAN_HALL))
				if (!player.isInsideZone(ZoneId.JAIL) && !player.isInOlympiadMode())
				{
					int realLoc[] = new int[5];
					L2Character activeChar = null;
					switch (player.getFactionId())
					{
						case 1:
							realLoc = Config.FACTION_TEAM1_BASE;
							if (player.isInsideRadius(realLoc[0], realLoc[1], 6000, false))
								
							{
								break;
							}
							player.teleToLocation(realLoc[0] + Rnd.get(realLoc[3], realLoc[4]), realLoc[1] + Rnd.get(realLoc[3], realLoc[4]), realLoc[2], 0);
							break;
						case 2:
								realLoc = Config.FACTION_TEAM2_BASE;
							if (player.isInsideRadius(realLoc[0], realLoc[1], 6000, false))
							{
								break;
							}
							player.teleToLocation(realLoc[0] + Rnd.get(realLoc[3], realLoc[4]), realLoc[1] + Rnd.get(realLoc[3], realLoc[4]), realLoc[2], 0);
							break;
						default:
							realLoc = Config.FACTION_NEWBIE_BASE;
							if (player.isInsideRadius(realLoc[0], realLoc[1], 6000, false))
							{
								break;
							}
							player.teleToLocation(realLoc[0] + Rnd.get(realLoc[3], realLoc[4]), realLoc[1] + Rnd.get(realLoc[3], realLoc[4]), realLoc[2], 0);
							break;
					}
				}
			}
		}
		Broadcast.announceToOnlinePlayers("Round [" + _mapId + "] just ended.");
		if (Config.FACTION_ENABLE_VOTE_MAP)
		{
			Broadcast.announceToOnlinePlayers("Voting for the next map has begun. It will end in 60 seconds.");
			_voting = true;
			NpcHtmlMessage html = new NpcHtmlMessage(1);
			final StringBuilder strBuffer = new StringBuilder();
			StringUtil.append(strBuffer, "<html><body><center>");
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement = con.prepareStatement("SELECT * FROM faction_maps WHERE current=0");
				ResultSet rset = statement.executeQuery();
				for (int r = 0; r < _mapVotes.length; r++)
				{
					_mapVotes[r] = 0;
				}
				while (rset.next())
				{
					strBuffer.append("<button value=\"" + rset.getString("map_name") + "\" action=\"bypass -h voteformap_" + rset.getInt("mapId") + "\" width=170 height=20 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
					_mapNames[rset.getInt("mapId")] = rset.getString("map_name");
				}
				
				rset.close();
				statement.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
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
			}
			strBuffer.append("</center></body></html>");
			html.setHtml(strBuffer.toString());
			for (L2PcInstance player : World.getInstance().getPlayers())
			{
				player.sendPacket(html);
			}
			try
			{
				Thread.sleep(60000);
			}
			catch (Exception e)
			{
			}
			endVoting(false);
		}
		else
		{
			try
			{
				Thread.sleep(4000);
			}
			catch (Exception e)
			{
			}
			endVoting(false);
		}
	}
	
	public static void endVoting(boolean onLoad)
	{
		_nextMapIn = Calendar.getInstance().getTimeInMillis() + (Config.FACTION_MAP_DURATION * 60 * 1000);
		if (!onLoad)
		{
			_voting = false;
			
			int mostVotes = 0;
			int mostMapId = 0;
			
			if (Config.FACTION_ENABLE_VOTE_MAP)
			{
				for (int i = 0; i < _mapVotes.length; i++)
				{
					if (_mapVotes[i] > mostVotes)
					{
						mostVotes = _mapVotes[i];
						mostMapId = i;
					}
				}
				if (mostVotes == 0)
				{
					mostMapId = 5;
				}
				_mapName = _mapNames[mostMapId];
				_mapId = mostMapId;
				Broadcast.announceToOnlinePlayers("Voting for faction maps has ended.");
				Broadcast.announceToOnlinePlayers("The next map will be: " + _mapName + ". Votes: " + mostVotes);
			}
			else
			{
				_mapId = getNextMap(_mapId);
				_mapName = _all_maps.get(_mapId);
				Broadcast.announceToOnlinePlayers(_mapName + " map loaded. Round [" + _mapId + "].");
				Broadcast.announceToOnlinePlayers("Next Map: " + _all_maps.get(getNextMap(getMapId())) + ".");
				Broadcast.announceToOnlinePlayers("Time left: " + getDelayUntilVoting());
			}
			
			if (World.getInstance().getPlayers().size() > 3)
			{
				HashMap<L2PcInstance, Integer> map = new HashMap<>();
				ValueComparator bvc = new ValueComparator(map);
				TreeMap<L2PcInstance, Integer> sorted_map = new TreeMap<>(bvc);
				
				for (L2PcInstance bestplayer : World.getInstance().getPlayers())
				{
					map.put(bestplayer, bestplayer.getCurrentPts());
				}
				
				sorted_map.putAll(map);
				
				int until = 1;
				L2PcInstance first = null;
				L2PcInstance second = null;
				L2PcInstance third = null;
				while (until <= 3)
				{
					for (L2PcInstance key : sorted_map.keySet())
					{
						switch (until)
						{
							case 1:
								first = key;
								break;
							case 2:
								second = key;
								break;
							case 3:
								third = key;
								break;
						}
						until++;
					}
				}
				Broadcast.sendMessToAllFactionPlayers("Top Players of this round:");
				if (first != null && sorted_map.get(first) > 0)
				{
					Broadcast.sendMessToAllFactionPlayers("[1] " + first.getName() + ": " + sorted_map.get(first) + " points. Overall: " + first.getTotalPts());
				}
				if (second != null && sorted_map.get(second) > 0)
				{
					Broadcast.sendMessToAllFactionPlayers("[2] " + second.getName() + ": " + sorted_map.get(second) + " points. Overall: " + second.getTotalPts());
				}
				if (third != null && sorted_map.get(third) > 0)
				{
					Broadcast.sendMessToAllFactionPlayers("[3] " + third.getName() + ": " + sorted_map.get(third) + " points. Overall: " + third.getTotalPts());
				}
				
				first.addItem("Admin", 57, Config.FACTION_ADENA_REWARD_FIRST_RANK, first, true);
				if (Config.FACTION_ENABLE_FIRST_PLC_AA)
				{
					first.addItem("Admin", 5575, Config.FACTION_AA_REWARD_FIRST_RANK, first, true);
				}
				first.addExpAndSp(0, Config.FACTION_SP_REWARD_FIRST_RANK);
				if (Config.ENABLE_ITEM_REWARD_TOP)
				{
					first.addItem("Admin", Config.FACTION_ITEM_REWARD_TOP_ID, Config.FACTION_ITEM_REWARD_TOP_AMOUNT, first, true);
				}
				if (first != second)
				{
					second.addItem("Admin", 57, Config.FACTION_ADENA_REWARD_SECOND_RANK, second, true);
				}
				if (second != third)
				{
					third.addItem("Admin", 57, Config.FACTION_ADENA_REWARD_THIRD_RANK, third, true);
				}
				
				Broadcast.sendMessToAllFactionPlayers("Rewards for Top Players:");
				if (Config.ENABLE_ITEM_REWARD_TOP)
				{
					if (Config.FACTION_ENABLE_FIRST_PLC_AA)
					{
						Broadcast.sendMessToAllFactionPlayers("[1] " + first.getName() + " received " + Config.FACTION_ADENA_REWARD_FIRST_RANK + " Adena, " + Config.FACTION_AA_REWARD_FIRST_RANK + " Ancient Adena, " + Config.FACTION_SP_REWARD_FIRST_RANK + " SP and an special item.");
					}
					else
					{
						Broadcast.sendMessToAllFactionPlayers("[1] " + first.getName() + " received " + Config.FACTION_ADENA_REWARD_FIRST_RANK + " Adena, " + Config.FACTION_SP_REWARD_FIRST_RANK + " SP and an special item.");
					}
				}
				else
				{
					if (Config.FACTION_ENABLE_FIRST_PLC_AA)
					{
						Broadcast.sendMessToAllFactionPlayers("[1] " + first.getName() + " received " + Config.FACTION_ADENA_REWARD_FIRST_RANK + " Adena, " + Config.FACTION_AA_REWARD_FIRST_RANK + " Ancient Adena and " + Config.FACTION_SP_REWARD_FIRST_RANK + " SP.");
					}
					else
					{
						Broadcast.sendMessToAllFactionPlayers("[1] " + first.getName() + " received " + Config.FACTION_ADENA_REWARD_FIRST_RANK + " Adena and " + Config.FACTION_SP_REWARD_FIRST_RANK + " SP.");
					}
				}
				if (first != second)
				{
					Broadcast.sendMessToAllFactionPlayers("[2] " + second.getName() + " received " + Config.FACTION_ADENA_REWARD_SECOND_RANK + " Adena.");
				}
				if (second != third)
				{
					Broadcast.sendMessToAllFactionPlayers("[3] " + third.getName() + " received " + Config.FACTION_ADENA_REWARD_THIRD_RANK + " Adena.");
				}
				
				for (L2PcInstance zaid : World.getInstance().getPlayers())
				{
					zaid.setCurrentPts(0);
				}
				
				int team1 = FactionMaps.getTeam1Pts();
				int team2 = FactionMaps.getTeam2Pts();
				
				if (team1 > team2)
				{
					Broadcast.sendMessToAllFactionPlayers("Best Faction of the round: " + Config.FACTION_TEAM1_NAME);
					Broadcast.sendMessToAllFactionPlayers("Points during this round: [" + team1 + "]");
					if (Config.FACTIONS_WIN_REWARD_ENABLED)
					{
						for (L2PcInstance winner : World.getInstance().getAllteam1Players())
						{
							winner.addItem("Admin", Config.FACTIONS_WIN_REWARD_ID, Config.FACTIONS_WIN_REWARD_AMOUNT, winner, true);
						}
						Broadcast.sendMessToAllFactionPlayers("Winners were rewarded!");
					}
				}
				else if (team2 < team1)
				{
					Broadcast.sendMessToAllFactionPlayers("Best Faction of the round: " + Config.FACTION_TEAM2_NAME);
					Broadcast.sendMessToAllFactionPlayers("Points during this round: [" + team2 + "]");
					if (Config.FACTIONS_WIN_REWARD_ENABLED)
					{
						for (L2PcInstance winner : World.getInstance().getAllteam2Players())
						{
							winner.addItem("Admin", Config.FACTIONS_WIN_REWARD_ID, Config.FACTIONS_WIN_REWARD_AMOUNT, winner, true);
						}
						Broadcast.sendMessToAllFactionPlayers("Winners were rewarded!");
					}
				}
				else if (team1 == team2)
				{
					Broadcast.sendMessToAllFactionPlayers("All three factions were best in this round!");
					Broadcast.sendMessToAllFactionPlayers("Points during this round: [" + team1 + "]");
					if (Config.FACTIONS_WIN_REWARD_ENABLED)
					{
						for (L2PcInstance winner : World.getInstance().getAllteam1Players())
						{
							winner.addItem("Admin", Config.FACTIONS_WIN_REWARD_ID, Config.FACTIONS_WIN_REWARD_AMOUNT, winner, true);
						}
						for (L2PcInstance winner : World.getInstance().getAllteam2Players())
						{
							winner.addItem("Admin", Config.FACTIONS_WIN_REWARD_ID, Config.FACTIONS_WIN_REWARD_AMOUNT, winner, true);
						}
						Broadcast.sendMessToAllFactionPlayers("Winners were rewarded!");
					}
				}
				
				map.clear();
			}
			else
			{
				Broadcast.sendMessToAllFactionPlayers("No round rewards at this time.");
			}
			FactionMaps.setTeam1Pts(0);
			FactionMaps.setTeam2Pts(0);
			
			L2TpFlagInstance.spawnFlags();
			
			Connection con = null;
			try
			{
				con = L2DatabaseFactory.getInstance().getConnection();
				PreparedStatement statement = null;
				statement = con.prepareStatement("UPDATE faction_maps SET current=0");
				statement.execute();
				statement = con.prepareStatement("UPDATE faction_maps SET current=1 WHERE mapId=?");
				statement.setInt(1, _mapId);
				statement.execute();
				statement.close();
			}
			catch (Exception e)
			{
				_log.warning("End voting: " + e);
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
			}
		}
		int durat = Config.FACTION_MAP_DURATION * 60 * 1000;
		try
		{
			Thread.sleep(durat);
		}
		catch (Exception e)
		{
		}
		beginVoting();
	}
	
	public static int getNextMap(int mapid)
	{
		if (_mapId < _maps_count)
		{
			return _mapId + 1;
		}
		
		return 1;
	}
	
	public static class ValueComparator implements Comparator<Object>
	{
		Map<L2PcInstance, Integer> base;
		
		public ValueComparator(Map<L2PcInstance, Integer> base1)
		{
			this.base = base1;
		}
		
		@Override
		public int compare(Object a, Object b)
		{
			if (base.get(a) < base.get(b))
			{
				return 1;
			}
			else if (base.get(a) == base.get(b))
			{
				return 0;
			}
			else
			{
				return -1;
			}
		}
	}
	
	public static void voteForMap(int mapId)
	{
		_mapVotes[mapId] = _mapVotes[mapId] + 1;
	}
}