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
package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.datatables.MapRegionTable;
import net.sf.l2j.gameserver.datatables.MapRegionTable.TeleportWhereType;
import net.sf.l2j.gameserver.instancemanager.CastleManager;
import net.sf.l2j.gameserver.instancemanager.ClanHallManager;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2SiegeClan;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.instance.L2FactTeleporterInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2TpFlagInstance;
import net.sf.l2j.gameserver.model.entity.Castle;
import net.sf.l2j.gameserver.model.entity.ClanHall;

public final class RequestRestartPoint extends L2GameClientPacket
{
	protected int _requestedPointType;
	protected boolean _continuation;
	
	@Override
	protected void readImpl()
	{
		_requestedPointType = readD();
	}
	
	class DeathTask implements Runnable
	{
		final L2PcInstance activeChar;
		
		DeathTask(L2PcInstance _activeChar)
		{
			activeChar = _activeChar;
		}
		
		@SuppressWarnings("null")
		@Override
		public void run()
		{
			Location loc = null;
			Castle castle = null;
			
			// force
			if (activeChar.isInJail())
			{
				_requestedPointType = 27;
			}
			else if (activeChar.isFestivalParticipant())
			{
				_requestedPointType = 4;
			}
			
			switch (_requestedPointType)
			{
				case 1: // to clanhall
					if (activeChar.getClan() == null || !activeChar.getClan().hasHideout())
					{
						_log.warning(activeChar.getName() + " called RestartPointPacket - To Clanhall while he doesn't have clan / Clanhall.");
						return;
					}
					
					loc = MapRegionTable.getInstance().getTeleToLocation(activeChar, TeleportWhereType.CLAN_HALL);
					
					if (ClanHallManager.getInstance().getClanHallByOwner(activeChar.getClan()) != null && ClanHallManager.getInstance().getClanHallByOwner(activeChar.getClan()).getFunction(ClanHall.FUNC_RESTORE_EXP) != null)
					{
						activeChar.restoreExp(ClanHallManager.getInstance().getClanHallByOwner(activeChar.getClan()).getFunction(ClanHall.FUNC_RESTORE_EXP).getLvl());
					}
					break;
				
				case 2: // to castle
					castle = CastleManager.getInstance().getCastle(activeChar);
					
					if (castle != null && castle.getSiege().isInProgress())
					{
						// Siege in progress
						if (castle.getSiege().checkIsDefender(activeChar.getClan()))
						{
							loc = MapRegionTable.getInstance().getTeleToLocation(activeChar, TeleportWhereType.CASTLE);
						}
						else if (castle.getSiege().checkIsAttacker(activeChar.getClan()))
						{
							loc = MapRegionTable.getInstance().getTeleToLocation(activeChar, TeleportWhereType.TOWN);
						}
						else
						{
							_log.warning(activeChar.getName() + " called RestartPointPacket - To Castle while he doesn't have Castle.");
							return;
						}
					}
					else
					{
						if (activeChar.getClan() == null || !activeChar.getClan().hasCastle())
						{
							return;
						}
						
						loc = MapRegionTable.getInstance().getTeleToLocation(activeChar, TeleportWhereType.CASTLE);
					}
					break;
				
				case 3: // to siege HQ
					L2SiegeClan siegeClan = null;
					castle = CastleManager.getInstance().getCastle(activeChar);
					
					if (castle != null && castle.getSiege().isInProgress())
					{
						siegeClan = castle.getSiege().getAttackerClan(activeChar.getClan());
					}
					
					// Not a normal scenario.
					if (siegeClan == null)
					{
						_log.warning(activeChar.getName() + " called RestartPointPacket - To Siege HQ while he doesn't have Siege HQ.");
						return;
					}
					
					// If a player was waiting with flag option and then the flag dies before the


				     // player pushes the button, he is send back to closest/second closest town.
					if (siegeClan == null || siegeClan.getFlags() == null || siegeClan.getFlags().isEmpty())
				     {
				      loc = MapRegionTable.getInstance().getTeleToLocation(activeChar, TeleportWhereType.TOWN);
				     }
				     else
				     {
				      loc = teletoflag(activeChar);
				     }
				     break;

				     
				     
				     
				
				case 4: // Fixed or player is a festival participant
					if (!activeChar.isDead() && !!activeChar.isDead())
					{
						_log.warning(activeChar.getName() + " called RestartPointPacket - Fixed while he isn't festival participant!");
						return;
					}
					loc = new Location(45288, 50016, -3036);
					break;
				
				case 27: // to jail
					if (!activeChar.isInJail())
					{
						return;
					}
					loc = new Location(-114356, -249645, -2984);
					break;

					default:
						

					 
						if (Config.RESPAWN_FACTION_BASE)
					if (activeChar.getFactionId() == 1)
					{
						loc = new Location(Config.FACTION_TEAM1_BASE[0] + Rnd.get(Config.FACTION_TEAM1_BASE[3], Config.FACTION_TEAM1_BASE[4]), Config.FACTION_TEAM1_BASE[1] + Rnd.get(Config.FACTION_TEAM1_BASE[3], Config.FACTION_TEAM1_BASE[4]), Config.FACTION_TEAM1_BASE[2]);
						{
						loc = MapRegionTable.getInstance().getTeleToLocation(activeChar, TeleportWhereType.TOWN);
						break;	
						}
						
					}
					else if (activeChar.getFactionId() == 2)
					{
						loc = new Location(Config.FACTION_TEAM2_BASE[0] + Rnd.get(Config.FACTION_TEAM2_BASE[3], Config.FACTION_TEAM2_BASE[4]), Config.FACTION_TEAM2_BASE[1] + Rnd.get(Config.FACTION_TEAM2_BASE[3], Config.FACTION_TEAM2_BASE[4]), Config.FACTION_TEAM2_BASE[2]);
						break;
					}
					else
						                       loc = MapRegionTable.getInstance().getTeleToLocation(activeChar, TeleportWhereType.TOWN);
					                     break;

			}
			
			// Teleport and revive
			activeChar.setIsIn7sDungeon(false);
			
			if (activeChar.isDead())
			{
				activeChar.doRevive();
			}
			
			activeChar.teleToLocation(loc, 20);
		}
	}
	

	protected void runImpl()
	{
		final L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null)
		{
			return;
		}
		
		if (activeChar.isFakeDeath())
		{
			activeChar.stopFakeDeath(true);
			return;
		}
		
		if (!activeChar.isDead())
		{
			_log.warning("Living player [" + activeChar.getName() + "] called RequestRestartPoint packet.");
			return;
		}
		
		Castle castle = CastleManager.getInstance().getCastle(activeChar.getX(), activeChar.getY(), activeChar.getZ());
		if (castle != null && castle.getSiege().isInProgress())
		{
			if (activeChar.getClan() != null && castle.getSiege().checkIsAttacker(activeChar.getClan()))
			{
				// Schedule respawn delay for attacker
				ThreadPool.schedule(new DeathTask(activeChar), Config.ATTACKERS_RESPAWN_DELAY);
				
				if (Config.ATTACKERS_RESPAWN_DELAY > 0)
				{
					activeChar.sendMessage("You will be teleported in " + Config.ATTACKERS_RESPAWN_DELAY / 1000 + " seconds.");
				}
				
				return;
			}
		}
		
		// run immediately (no need to schedule)
		new DeathTask(activeChar).run();
	}
	public Location teletoflag(L2PcInstance player)
	 {
	       switch (player.getFactionId())
	      {
	        case 1:
	        	for (L2TpFlagInstance unFlag : L2FactTeleporterInstance._tpTeam1Flags)
	          {
	            if (unFlag.isUnoccupayable() == 1)
	            {
	              return new Location(unFlag.getX() + 50, unFlag.getY(), unFlag.getZ());
	            }
	          }
	          break;
	        case 2:
	        	for (L2TpFlagInstance unFlag : L2FactTeleporterInstance._tpTeam2Flags)
	          {
	            if (unFlag.isUnoccupayable() == 1)
	            {
	              return new Location(unFlag.getX() + 50, unFlag.getY(), unFlag.getZ());
	            }
	          }
	      }
		return null;
	 }
	


	/* (non-Javadoc)
	 * @see net.sf.l2j.gameserver.network.clientpackets.L2GameClientPacket#runImpl()
	 */

	protected void runImpl1()
	{
		// TODO Auto-generated method stub
		
	}
}