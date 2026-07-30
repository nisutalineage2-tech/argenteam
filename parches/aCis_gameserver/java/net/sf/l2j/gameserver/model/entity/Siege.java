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
package net.sf.l2j.gameserver.model.entity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.l2j.commons.concurrent.ThreadPool;

import net.sf.l2j.Config;
import net.sf.l2j.L2DatabaseFactory;
import net.sf.l2j.gameserver.datatables.ClanTable;
import net.sf.l2j.gameserver.datatables.MapRegionTable;
import net.sf.l2j.gameserver.datatables.NpcTable;
import net.sf.l2j.gameserver.instancemanager.CastleManager;
import net.sf.l2j.gameserver.model.L2Clan;
import net.sf.l2j.gameserver.model.L2ClanMember;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2SiegeClan;
import net.sf.l2j.gameserver.model.L2SiegeClan.SiegeClanType;
import net.sf.l2j.gameserver.model.L2Spawn;
import net.sf.l2j.gameserver.model.TowerSpawn;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2ControlTowerInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2FlameTowerInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.network.serverpackets.SiegeInfo;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.network.serverpackets.UserInfo;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.util.Broadcast;

public class Siege implements Siegable
{
	public enum SiegeStatus
	{
		REGISTRATION_OPENED, // Equals canceled or end siege event.
		REGISTRATION_OVER,
		IN_PROGRESS // Equals siege start event.
	}
	
	protected static final Logger _log = Logger.getLogger(Siege.class.getName());
	
	public static final byte OWNER = -1;
	public static final byte DEFENDER = 0;
	public static final byte ATTACKER = 1;
	public static final byte DEFENDER_NOT_APPROVED = 2;
	
	private final List<L2SiegeClan> _attackerClans = new CopyOnWriteArrayList<>();
	private final List<L2SiegeClan> _defenderClans = new CopyOnWriteArrayList<>();
	private final List<L2SiegeClan> _defenderWaitingClans = new CopyOnWriteArrayList<>();
	private boolean _isNormalSide = true;
	
	private final Castle _castle;
	
	private final List<L2ControlTowerInstance> _controlTowers = new ArrayList<>();
	private final List<L2FlameTowerInstance> _flameTowers = new ArrayList<>();
	
	private final List<L2Npc> _destroyedTowers = new ArrayList<>();
	
	protected Calendar _siegeEndDate;
	protected ScheduledFuture<?> _scheduledStartSiegeTask;
	
	private SiegeStatus _siegeStatus = SiegeStatus.REGISTRATION_OPENED;
	
	private List<Quest> _questEvents = Collections.emptyList();
	
	public Siege(Castle castle)
	{
		_castle = castle;
		
		startAutoTask();
	}
	
	@Override
	public void endSiege()
	{
		if (isInProgress())
		{
			Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.SIEGE_OF_S1_HAS_ENDED).addString(getCastle().getName()));
			Broadcast.toAllOnlinePlayers(new PlaySound("systemmsg_e.18"));
			
			if (getCastle().getOwnerId() > 0)
			{
				L2Clan clan = ClanTable.getInstance().getClan(getCastle().getOwnerId());
				Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.CLAN_S1_VICTORIOUS_OVER_S2_S_SIEGE).addString(clan.getName()).addString(getCastle().getName()));
				
				// Delete circlets and crown's leader for initial castle's owner (if one was existing)
				if (getCastle().getInitialCastleOwner() != null && clan != getCastle().getInitialCastleOwner())
				{
					if (Config.REMOVE_CASTLE_CIRCLETS)
						getCastle().removeCirclet(getCastle().getInitialCastleOwner());
					
					for (L2ClanMember member : clan.getMembers())
					{
						if (member != null)
						{
							L2PcInstance player = member.getPlayerInstance();
							if (player != null && player.isNoble())
								Hero.getInstance().setCastleTaken(player.getObjectId(), getCastle().getCastleId());
						}
					}
				}
			}
			else
				Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.SIEGE_S1_DRAW).addString(getCastle().getName()));
			
			// Cleanup clans kills/deaths counters.
			for (L2SiegeClan attackerClan : getAttackerClans())
			{
				final L2Clan clan = ClanTable.getInstance().getClan(attackerClan.getClanId());
				if (clan != null)
				{
					clan.setSiegeKills(0);
					clan.setSiegeDeaths(0);
				}
			}
			
			for (L2SiegeClan defenderClan : getDefenderClans())
			{
				final L2Clan clan = ClanTable.getInstance().getClan(defenderClan.getClanId());
				if (clan != null)
				{
					clan.setSiegeKills(0);
					clan.setSiegeDeaths(0);
				}
			}
			
			getCastle().updateClansReputation();
			removeFlags(); // Removes all flags. Note: Remove flag before teleporting players
			
			teleportPlayers();
			
			updatePlayerSiegeStateFlags(true);
			saveCastleSiege(true); // Save castle specific data
			clearSiegeClan(); // Clear siege clan from db
			removeTowers(); // Remove all towers from this castle
			
			getCastle().despawnSiegeGuardsOrMercenaries();
			
			getCastle().spawnDoors(false); // Respawn door to castle
			
			getCastle().getZone().setIsActive(false);
			getCastle().getZone().updateZoneStatusForCharactersInside();
		}
	}
	
	/**
	 * When control of castle changed during siege.
	 */
	public void midVictory()
	{
		if (isInProgress()) // Siege still in progress
		{
			getCastle().despawnSiegeGuardsOrMercenaries();
			
			// If defender doesn't exist (Pc vs Npc) and only 1 attacker
			if (getDefenderClans().isEmpty() && getAttackerClans().size() == 1)
			{
				L2SiegeClan sc_newowner = getAttackerClan(getCastle().getOwnerId());
				removeAttacker(sc_newowner);
				addDefender(sc_newowner, SiegeClanType.OWNER);
				endSiege();
				return;
			}
			
			if (getCastle().getOwnerId() > 0)
			{
				int allyId = ClanTable.getInstance().getClan(getCastle().getOwnerId()).getAllyId();
				if (getDefenderClans().isEmpty())
				{
					// The player's clan is in an alliance
					if (allyId != 0)
					{
						boolean allinsamealliance = true;
						for (L2SiegeClan sc : getAttackerClans())
						{
							if (sc != null)
							{
								if (ClanTable.getInstance().getClan(sc.getClanId()).getAllyId() != allyId)
									allinsamealliance = false;
							}
						}
						if (allinsamealliance)
						{
							L2SiegeClan sc_newowner = getAttackerClan(getCastle().getOwnerId());
							removeAttacker(sc_newowner);
							addDefender(sc_newowner, SiegeClanType.OWNER);
							endSiege();
							return;
						}
					}
				}
				
				for (L2SiegeClan sc : getDefenderClans())
				{
					if (sc != null)
					{
						removeDefender(sc);
						addAttacker(sc);
					}
				}
				
				L2SiegeClan sc_newowner = getAttackerClan(getCastle().getOwnerId());
				removeAttacker(sc_newowner);
				addDefender(sc_newowner, SiegeClanType.OWNER);
				
				// The player's clan is in an alliance
				if (allyId != 0)
				{
					for (L2Clan clan : ClanTable.getInstance().getClans())
					{
						if (clan.getAllyId() == allyId)
						{
							L2SiegeClan sc = getAttackerClan(clan.getClanId());
							if (sc != null)
							{
								removeAttacker(sc);
								addDefender(sc, SiegeClanType.DEFENDER);
							}
						}
					}
				}
				teleportPlayers();
				
				removeDefenderFlags(); // Removes defenders' flags.
				getCastle().removeDoorUpgrade(); // Remove all castle doors upgrades.
				getCastle().removeTrapUpgrade(); // Remove all castle traps upgrades.
				getCastle().spawnDoors(true); // Respawn door to castle but make them weaker (50% hp).
				
				removeTowers(); // Remove all towers from this castle.
				
				spawnControlTowers(); // Each new siege midvictory CT are completely respawned.
				spawnFlameTowers();
				
				updatePlayerSiegeStateFlags(false);
			}
		}
	}
	
	/**
	 * When siege starts.
	 */
	@Override
	public void startSiege()
	{
		if (!isInProgress())
		{
			if (getAttackerClans().isEmpty())
			{
				SystemMessage sm;
				if (getCastle().getOwnerId() <= 0)
					sm = SystemMessage.getSystemMessage(SystemMessageId.SIEGE_OF_S1_HAS_BEEN_CANCELED_DUE_TO_LACK_OF_INTEREST);
				else
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_SIEGE_WAS_CANCELED_BECAUSE_NO_CLANS_PARTICIPATED);
				sm.addString(getCastle().getName());
				Broadcast.toAllOnlinePlayers(sm);
				saveCastleSiege(true);
				return;
			}
			
			_isNormalSide = true; // Atk is now atk
			changeStatus(SiegeStatus.IN_PROGRESS); // Flag so that same siege instance cannot be started again
			
			loadSiegeClan(); // Load siege clan from db
			updatePlayerSiegeStateFlags(false);
			teleportPlayers();
			
			spawnControlTowers(); // Spawn control towers
			spawnFlameTowers(); // Spawn flame towers
			getCastle().closeDoors(); // Close doors
			
			getCastle().spawnSiegeGuardsOrMercenaries();
			
			getCastle().getZone().setIsActive(true);
			getCastle().getZone().updateZoneStatusForCharactersInside();
			
			// Schedule a task to prepare auto siege end
			_siegeEndDate = Calendar.getInstance();
			_siegeEndDate.add(Calendar.MINUTE, Config.SIEGE_LENGTH);
			ThreadPool.schedule(new ScheduleEndSiegeTask(getCastle()), 1000);
			
			Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.SIEGE_OF_S1_HAS_STARTED).addString(getCastle().getName()));
			Broadcast.toAllOnlinePlayers(new PlaySound("systemmsg_e.17"));
		}
	}
	
	private void removeDefender(L2SiegeClan sc)
	{
		if (sc != null)
			getDefenderClans().remove(sc);
	}
	
	private void removeAttacker(L2SiegeClan sc)
	{
		if (sc != null)
			getAttackerClans().remove(sc);
	}
	
	private void addDefender(L2SiegeClan sc, SiegeClanType type)
	{
		if (sc == null)
			return;
		
		sc.setType(type);
		getDefenderClans().add(sc);
	}
	
	private void addAttacker(L2SiegeClan sc)
	{
		if (sc == null)
			return;
		
		sc.setType(SiegeClanType.ATTACKER);
		getAttackerClans().add(sc);
	}
	
	/**
	 * Broadcast a string to defenders.
	 * @param message The String of the message to send to player
	 * @param bothSides if true, broadcast too to attackers clans.
	 */
	public void announceToPlayer(SystemMessage message, boolean bothSides)
	{
		for (L2SiegeClan siegeClans : getDefenderClans())
			ClanTable.getInstance().getClan(siegeClans.getClanId()).broadcastToOnlineMembers(message);
		
		if (bothSides)
		{
			for (L2SiegeClan siegeClans : getAttackerClans())
				ClanTable.getInstance().getClan(siegeClans.getClanId()).broadcastToOnlineMembers(message);
		}
	}
	
	public void updatePlayerSiegeStateFlags(boolean clear)
	{
		for (L2SiegeClan siegeclan : getAttackerClans())
		{
			L2Clan clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2PcInstance member : clan.getOnlineMembers())
			{
				if (clear)
				{
					member.setSiegeState((byte) 0);
					member.setIsInSiege(false);
				}
				else
				{
					member.setSiegeState((byte) 1);
					if (checkIfInZone(member))
						member.setIsInSiege(true);
				}
				member.sendPacket(new UserInfo(member));
				member.broadcastRelationsChanges();
			}
		}
		
		for (L2SiegeClan siegeclan : getDefenderClans())
		{
			L2Clan clan = ClanTable.getInstance().getClan(siegeclan.getClanId());
			for (L2PcInstance member : clan.getOnlineMembers())
			{
				if (clear)
				{
					member.setSiegeState((byte) 0);
					member.setIsInSiege(false);
				}
				else
				{
					member.setSiegeState((byte) 2);
					if (checkIfInZone(member))
						member.setIsInSiege(true);
				}
				member.sendPacket(new UserInfo(member));
				member.broadcastRelationsChanges();
			}
		}
	}
	
	/**
	 * Approve clan as defender for siege.
	 * @param clanId The int of player's clan id
	 */
	public void approveSiegeDefenderClan(int clanId)
	{
		if (clanId <= 0)
			return;
		
		saveSiegeClan(ClanTable.getInstance().getClan(clanId), DEFENDER);
		loadSiegeClan();
	}
	
	/**
	 * Check if an object is inside an area using his location.
	 * @param object The Object to use positions.
	 * @return true if object is inside the zone
	 */
	public boolean checkIfInZone(L2Object object)
	{
		return checkIfInZone(object.getX(), object.getY(), object.getZ());
	}
	
	/**
	 * @param x
	 * @param y
	 * @param z
	 * @return true if object is inside the zone
	 */
	public boolean checkIfInZone(int x, int y, int z)
	{
		return isInProgress() && getCastle().checkIfInZone(x, y, z); // Castle zone during siege
	}
	
	/**
	 * Return true if clan is attacker
	 * @param clan The L2Clan of the player
	 */
	@Override
	public boolean checkIsAttacker(L2Clan clan)
	{
		return getAttackerClan(clan) != null;
	}
	
	/**
	 * Return true if clan is defender
	 * @param clan The L2Clan of the player
	 */
	@Override
	public boolean checkIsDefender(L2Clan clan)
	{
		return getDefenderClan(clan) != null;
	}
	
	/**
	 * @param clan The L2Clan of the player
	 * @return true if clan is defender waiting approval
	 */
	public boolean checkIsDefenderWaiting(L2Clan clan)
	{
		return getDefenderWaitingClan(clan) != null;
	}
	
	/** Clear all registered siege clans from database for castle */
	public void clearSiegeClan()
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement statement = con.prepareStatement("DELETE FROM siege_clans WHERE castle_id=?");
			statement.setInt(1, getCastle().getCastleId());
			statement.execute();
			statement.close();
			
			if (getCastle().getOwnerId() > 0)
			{
				statement = con.prepareStatement("DELETE FROM siege_clans WHERE clan_id=?");
				statement.setInt(1, getCastle().getOwnerId());
				statement.execute();
				statement.close();
			}
			
			getAttackerClans().clear();
			getDefenderClans().clear();
			getDefenderWaitingClans().clear();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: clearSiegeClan(): " + e.getMessage(), e);
		}
	}
	
	/** Clear all siege clans waiting for approval from database for castle */
	public void clearSiegeWaitingClan()
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement statement = con.prepareStatement("DELETE FROM siege_clans WHERE castle_id=? and type = 2");
			statement.setInt(1, getCastle().getCastleId());
			statement.execute();
			statement.close();
			
			getDefenderWaitingClans().clear();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: clearSiegeWaitingClan(): " + e.getMessage(), e);
		}
	}
	
	/**
	 * Remove the flag that was killed
	 * @param flag
	 */
	public void killedFlag(L2Npc flag)
	{
		if (flag == null)
			return;
		
		for (L2SiegeClan clan : getAttackerClans())
		{
			if (clan.removeFlag(flag))
				return;
		}
	}
	
	/**
	 * Display list of registered clans
	 * @param player The player who requested the list.
	 */
	public void listRegisterClan(L2PcInstance player)
	{
		player.sendPacket(new SiegeInfo(getCastle()));
	}
	
	/**
	 * Register clan as attacker
	 * @param player The L2PcInstance of the player trying to register
	 */
	public void registerAttacker(L2PcInstance player)
	{
		if (player.getClan() == null)
			return;
		
		int allyId = 0;
		if (getCastle().getOwnerId() != 0)
			allyId = ClanTable.getInstance().getClan(getCastle().getOwnerId()).getAllyId();
		
		// If the castle owning clan got an alliance
		if (allyId != 0)
		{
			// Same alliance can't be attacked
			if (player.getClan().getAllyId() == allyId)
			{
				player.sendPacket(SystemMessageId.CANNOT_ATTACK_ALLIANCE_CASTLE);
				return;
			}
		}
		
		// Can't register as attacker if at least one allied clan is registered as defender
		if (allyIsRegisteredOnOppositeSide(player.getClan(), true))
			player.sendPacket(SystemMessageId.CANT_ACCEPT_ALLY_ENEMY_FOR_SIEGE);
		// Save to database
		else if (checkIfCanRegister(player, ATTACKER))
			saveSiegeClan(player.getClan(), ATTACKER);
	}
	
	/**
	 * Register clan as defender.
	 * @param player The L2PcInstance of the player trying to register
	 */
	public void registerDefender(L2PcInstance player)
	{
		// Castle owned by NPC is considered as full side
		if (getCastle().getOwnerId() <= 0)
			player.sendPacket(SystemMessageId.DEFENDER_SIDE_FULL);
		// Can't register as defender if at least one allied clan is registered as attacker
		else if (allyIsRegisteredOnOppositeSide(player.getClan(), false))
			player.sendPacket(SystemMessageId.CANT_ACCEPT_ALLY_ENEMY_FOR_SIEGE);
		// Save to database
		else if (checkIfCanRegister(player, DEFENDER_NOT_APPROVED))
			saveSiegeClan(player.getClan(), DEFENDER_NOT_APPROVED);
	}
	
	/**
	 * Verify if allies are registered on different list than the actual player's choice. Let's say clan A and clan B are in same alliance. If clan A wants to attack a castle, clan B mustn't be on defenders' list. The contrary is right too : you can't defend if one ally is on attackers' list.
	 * @param clan The clan of L2PcInstance, used for alliance existence checks
	 * @param attacker A boolean used to know if this check is used for attackers or defenders.
	 * @return true if one clan of the alliance is registered in other side.
	 */
	private boolean allyIsRegisteredOnOppositeSide(L2Clan clan, boolean attacker)
	{
		int allyId = clan.getAllyId();
		
		// Check if player's clan got an alliance ; if not, skip the check
		if (allyId != 0)
		{
			// Verify through the clans list for existing clans
			for (L2Clan alliedClan : ClanTable.getInstance().getClans())
			{
				// If a clan with same allyId is found (so, same alliance)
				if (alliedClan.getAllyId() == allyId)
				{
					// Skip player's clan from the check
					if (alliedClan.getClanId() == clan.getClanId())
						continue;
					
					// If the check is made for attackers' list
					if (attacker)
					{
						// Check if the allied clan is on defender / defender waiting lists
						if (checkIsDefender(alliedClan) || checkIsDefenderWaiting(alliedClan))
							return true;
					}
					else
					{
						// Check if the allied clan is on attacker list
						if (checkIsAttacker(alliedClan))
							return true;
					}
				}
			}
		}
		return false;
	}
	
	/**
	 * Remove clan from siege.
	 * @param clanId The int of player's clan id
	 */
	public void removeSiegeClan(int clanId)
	{
		if (clanId <= 0)
			return;
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement statement = con.prepareStatement("DELETE FROM siege_clans WHERE castle_id=? and clan_id=?");
			statement.setInt(1, getCastle().getCastleId());
			statement.setInt(2, clanId);
			statement.execute();
			statement.close();
			
			loadSiegeClan();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: removeSiegeClan(): " + e.getMessage(), e);
		}
	}
	
	/**
	 * Remove clan from siege.
	 * @param clan
	 */
	public void removeSiegeClan(L2Clan clan)
	{
		if (clan == null || clan.getCastleId() == getCastle().getCastleId() || !clan.isRegisteredOnSiege())
			return;
		
		removeSiegeClan(clan.getClanId());
	}
	
	/**
	 * Remove clan from siege.
	 * @param player The L2PcInstance of player/clan being removed
	 */
	public void removeSiegeClan(L2PcInstance player)
	{
		removeSiegeClan(player.getClan());
	}
	
	/**
	 * This method allows to :
	 * <ul>
	 * <li>Load sides infos.</li>
	 * <li>Check if the siege time is deprecated, and recalculate otherwise.</li>
	 * <li>Schedule start siege (it's in an else because saveCastleSiege() already affect it).</li>
	 * </ul>
	 */
	private void startAutoTask()
	{
		loadSiegeClan();
		
		if (getCastle().getSiegeDate().getTimeInMillis() < Calendar.getInstance().getTimeInMillis())
			saveCastleSiege(false);
		else
		{
			if (_scheduledStartSiegeTask != null)
				_scheduledStartSiegeTask.cancel(false);
			
			_scheduledStartSiegeTask = ThreadPool.schedule(new ScheduleStartSiegeTask(getCastle()), 1000);
		}
	}
	
	/**
	 * Teleport non owner castle players to the second nearest town.
	 */
	private void teleportPlayers()
	{
		for (L2Character character : getCastle().getZone().getCharactersInside())
		{
			if (!(character instanceof L2PcInstance))
				continue;
			
			final L2PcInstance player = (L2PcInstance) character;
			final L2Clan clan = player.getClan();
			
			if (clan == null || getCastle().getOwnerId() != clan.getClanId())
				player.teleToLocation(MapRegionTable.getInstance().getSecondClosestTown(player.getX(), player.getY()).getSpawnLoc(), 20);
		}
	}
	
	/**
	 * Add clan as attacker
	 * @param clanId The int of clan's id
	 */
	private void addAttacker(int clanId)
	{
		getAttackerClans().add(new L2SiegeClan(clanId, SiegeClanType.ATTACKER)); // Add registered attacker to attacker list
	}
	
	/**
	 * Add clan as defender
	 * @param clanId The int of clan's id
	 */
	private void addDefender(int clanId)
	{
		getDefenderClans().add(new L2SiegeClan(clanId, SiegeClanType.DEFENDER)); // Add registered defender to defender list
	}
	
	/**
	 * Add clan as defender with the specified type
	 * @param clanId The int of clan's id
	 * @param type the type of the clan
	 */
	private void addDefender(int clanId, SiegeClanType type)
	{
		getDefenderClans().add(new L2SiegeClan(clanId, type));
	}
	
	/**
	 * Add clan as defender waiting approval
	 * @param clanId The int of clan's id
	 */
	private void addDefenderWaiting(int clanId)
	{
		getDefenderWaitingClans().add(new L2SiegeClan(clanId, SiegeClanType.DEFENDER_PENDING)); // Add registered defender to defender list
	}
	
	/**
	 * @param player The L2PcInstance of the player trying to register
	 * @param typeId
	 * @return true if the player can register.
	 */
	private boolean checkIfCanRegister(L2PcInstance player, byte typeId)
	{
		SystemMessage sm;
		
		if (isRegistrationOver())
			sm = SystemMessage.getSystemMessage(SystemMessageId.DEADLINE_FOR_SIEGE_S1_PASSED).addString(getCastle().getName());
		else if (isInProgress())
			sm = SystemMessage.getSystemMessage(SystemMessageId.NOT_SIEGE_REGISTRATION_TIME2);
		else if (player.getClan() == null || player.getClan().getLevel() < Config.MINIMUM_CLAN_LEVEL)
			sm = SystemMessage.getSystemMessage(SystemMessageId.ONLY_CLAN_LEVEL_4_ABOVE_MAY_SIEGE);
		else if (player.getClan().hasCastle())
			sm = SystemMessage.getSystemMessage(SystemMessageId.CLAN_THAT_OWNS_CASTLE_CANNOT_PARTICIPATE_OTHER_SIEGE);
		else if (player.getClan().getClanId() == getCastle().getOwnerId())
			sm = SystemMessage.getSystemMessage(SystemMessageId.CLAN_THAT_OWNS_CASTLE_IS_AUTOMATICALLY_REGISTERED_DEFENDING);
		else if (player.getClan().isRegisteredOnSiege())
			sm = SystemMessage.getSystemMessage(SystemMessageId.ALREADY_REQUESTED_SIEGE_BATTLE);
		else if (checkIfAlreadyRegisteredForSameDay(player.getClan()))
			sm = SystemMessage.getSystemMessage(SystemMessageId.APPLICATION_DENIED_BECAUSE_ALREADY_SUBMITTED_A_REQUEST_FOR_ANOTHER_SIEGE_BATTLE);
		else if (typeId == ATTACKER && getAttackerClans().size() >= Config.MAX_ATTACKERS_NUMBER)
			sm = SystemMessage.getSystemMessage(SystemMessageId.ATTACKER_SIDE_FULL);
		else if ((typeId == DEFENDER || typeId == DEFENDER_NOT_APPROVED || typeId == OWNER) && (getDefenderClans().size() + getDefenderWaitingClans().size() >= Config.MAX_DEFENDERS_NUMBER))
			sm = SystemMessage.getSystemMessage(SystemMessageId.DEFENDER_SIDE_FULL);
		else
			return true;
		
		player.sendPacket(sm);
		return false;
	}
	
	/**
	 * @param clan The L2Clan of the player trying to register
	 * @return true if the clan has already registered to a siege for the same day.
	 */
	public boolean checkIfAlreadyRegisteredForSameDay(L2Clan clan)
	{
		for (Castle castle : CastleManager.getInstance().getCastles())
		{
			final Siege siege = castle.getSiege();
			if (siege == this)
				continue;
			
			if (siege.getSiegeDate().get(Calendar.DAY_OF_WEEK) == getSiegeDate().get(Calendar.DAY_OF_WEEK))
			{
				if (siege.checkIsAttacker(clan))
					return true;
				
				if (siege.checkIsDefender(clan))
					return true;
				
				if (siege.checkIsDefenderWaiting(clan))
					return true;
			}
		}
		return false;
	}
	
	/** Load siege clans. */
	private void loadSiegeClan()
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			getAttackerClans().clear();
			getDefenderClans().clear();
			getDefenderWaitingClans().clear();
			
			// Add castle owner as defender (add owner first so that they are on the top of the defender list)
			if (getCastle().getOwnerId() > 0)
				addDefender(getCastle().getOwnerId(), SiegeClanType.OWNER);
			
			PreparedStatement statement = con.prepareStatement("SELECT clan_id,type FROM siege_clans where castle_id=?");
			statement.setInt(1, getCastle().getCastleId());
			ResultSet rs = statement.executeQuery();
			
			int typeId;
			while (rs.next())
			{
				typeId = rs.getInt("type");
				if (typeId == DEFENDER)
					addDefender(rs.getInt("clan_id"));
				else if (typeId == ATTACKER)
					addAttacker(rs.getInt("clan_id"));
				else if (typeId == DEFENDER_NOT_APPROVED)
					addDefenderWaiting(rs.getInt("clan_id"));
			}
			rs.close();
			statement.close();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: loadSiegeClan(): " + e.getMessage(), e);
		}
	}
	
	/** Remove all spawned towers. */
	private void removeTowers()
	{
		for (L2FlameTowerInstance ct : _flameTowers)
			ct.deleteMe();
		
		for (L2ControlTowerInstance ct : _controlTowers)
			ct.deleteMe();
		
		for (L2Npc ct : _destroyedTowers)
			ct.deleteMe();
		
		_flameTowers.clear();
		_controlTowers.clear();
		_destroyedTowers.clear();
	}
	
	/** Remove all flags. */
	private void removeFlags()
	{
		for (L2SiegeClan sc : getAttackerClans())
		{
			if (sc != null)
				sc.removeFlags();
		}
		for (L2SiegeClan sc : getDefenderClans())
		{
			if (sc != null)
				sc.removeFlags();
		}
	}
	
	/** Remove flags from defenders. */
	private void removeDefenderFlags()
	{
		for (L2SiegeClan sc : getDefenderClans())
		{
			if (sc != null)
				sc.removeFlags();
		}
	}
	
	/**
	 * Save castle siege related to database.
	 * @param launchTask : if true, launch the start siege task.
	 */
	private void saveCastleSiege(boolean launchTask)
	{
		// Set the next siege date in 2 weeks from now.
		setNextSiegeDate();
		
		// You can edit time anew.
		getCastle().setTimeRegistrationOver(false);
		
		// Save the new date.
		saveSiegeDate();
		
		// Prepare start siege task.
		if (launchTask)
			startAutoTask();
		
		_log.info("New date for " + getCastle().getName() + " siege: " + getCastle().getSiegeDate().getTime());
	}
	
	/**
	 * Save siege date to database.
	 */
	private void saveSiegeDate()
	{
		if (_scheduledStartSiegeTask != null)
		{
			_scheduledStartSiegeTask.cancel(true);
			_scheduledStartSiegeTask = ThreadPool.schedule(new ScheduleStartSiegeTask(getCastle()), 1000);
		}
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			PreparedStatement statement = con.prepareStatement("UPDATE castle SET siegeDate = ?, regTimeOver = ?  WHERE id = ?");
			statement.setLong(1, getSiegeDate().getTimeInMillis());
			statement.setString(2, String.valueOf(isTimeRegistrationOver()));
			statement.setInt(3, getCastle().getCastleId());
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: saveSiegeDate(): " + e.getMessage(), e);
		}
	}
	
	/**
	 * Save registration to database.
	 * @param clan : The L2Clan of player.
	 * @param typeId : -1 = owner 0 = defender, 1 = attacker, 2 = defender waiting
	 */
	private void saveSiegeClan(L2Clan clan, byte typeId)
	{
		if (clan.hasCastle())
			return;
		
		try (Connection con = L2DatabaseFactory.getInstance().getConnection())
		{
			if (typeId == DEFENDER || typeId == DEFENDER_NOT_APPROVED || typeId == OWNER)
			{
				if (getDefenderClans().size() + getDefenderWaitingClans().size() >= Config.MAX_DEFENDERS_NUMBER)
					return;
			}
			else
			{
				if (getAttackerClans().size() >= Config.MAX_ATTACKERS_NUMBER)
					return;
			}
			
			PreparedStatement statement = con.prepareStatement("INSERT INTO siege_clans (clan_id,castle_id,type,castle_owner) VALUES (?,?,?,0) ON DUPLICATE KEY UPDATE type=?");
			statement.setInt(1, clan.getClanId());
			statement.setInt(2, getCastle().getCastleId());
			statement.setInt(3, typeId);
			statement.setInt(4, typeId);
			statement.execute();
			statement.close();
			
			if (typeId == DEFENDER || typeId == OWNER)
				addDefender(clan.getClanId());
			else if (typeId == ATTACKER)
				addAttacker(clan.getClanId());
			else if (typeId == DEFENDER_NOT_APPROVED)
				addDefenderWaiting(clan.getClanId());
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: saveSiegeClan(L2Clan clan, int typeId): " + e.getMessage(), e);
		}
	}
	
	/**
	 * Set the date for the next siege.
	 */
	private void setNextSiegeDate()
	{
		final Calendar siegeDate = getCastle().getSiegeDate();
		if (siegeDate.getTimeInMillis() < System.currentTimeMillis())
			siegeDate.setTimeInMillis(System.currentTimeMillis());
		
		switch (getCastle().getCastleId())
		{
			case 3:
			case 4:
			case 6:
			case 7:
				siegeDate.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
				break;
			
			default:
				siegeDate.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
				break;
		}
		
		// Set next siege date if siege has passed ; add 14 days (2 weeks).
		siegeDate.add(Calendar.WEEK_OF_YEAR, 2);
		
		// Set default hour to 18:00. This can be changed - only once - by the castle leader via the chamberlain.
		siegeDate.set(Calendar.HOUR_OF_DAY, 18);
		siegeDate.set(Calendar.MINUTE, 0);
		siegeDate.set(Calendar.SECOND, 0);
		siegeDate.set(Calendar.MILLISECOND, 0);
		
		// Send message and allow registration for next siege.
		Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.S1_ANNOUNCED_SIEGE_TIME).addString(getCastle().getName()));
		changeStatus(SiegeStatus.REGISTRATION_OPENED);
	}
	
	/**
	 * Spawn control towers.
	 */
	private void spawnControlTowers()
	{
		for (TowerSpawn ts : getCastle().getControlTowers())
		{
			try
			{
				final L2Spawn spawn = new L2Spawn(NpcTable.getInstance().getTemplate(ts.getId()));
				spawn.setLoc(ts.getLocation());
				
				final L2ControlTowerInstance tower = (L2ControlTowerInstance) spawn.doSpawn(false);
				tower.setCastle(getCastle());
				
				_controlTowers.add(tower);
			}
			catch (Exception e)
			{
				_log.warning(getClass().getName() + ": Cannot spawn control tower! " + e);
			}
		}
	}
	
	/**
	 * Spawn flame towers.
	 */
	private void spawnFlameTowers()
	{
		for (TowerSpawn ts : getCastle().getFlameTowers())
		{
			try
			{
				final L2Spawn spawn = new L2Spawn(NpcTable.getInstance().getTemplate(ts.getId()));
				spawn.setLoc(ts.getLocation());
				
				final L2FlameTowerInstance tower = (L2FlameTowerInstance) spawn.doSpawn(false);
				tower.setCastle(getCastle());
				tower.setUpgradeLevel(ts.getUpgradeLevel());
				tower.setZoneList(ts.getZoneList());
				
				_flameTowers.add(tower);
			}
			catch (Exception e)
			{
				_log.warning(getClass().getName() + ": Cannot spawn flame tower! " + e);
			}
		}
	}
	
	@Override
	public final L2SiegeClan getAttackerClan(L2Clan clan)
	{
		if (clan == null)
			return null;
		
		return getAttackerClan(clan.getClanId());
	}
	
	@Override
	public final L2SiegeClan getAttackerClan(int clanId)
	{
		for (L2SiegeClan sc : getAttackerClans())
			if (sc != null && sc.getClanId() == clanId)
				return sc;
			
		return null;
	}
	
	@Override
	public final List<L2SiegeClan> getAttackerClans()
	{
		return (_isNormalSide) ? _attackerClans : _defenderClans;
	}
	
	public final Castle getCastle()
	{
		return _castle;
	}
	
	@Override
	public final L2SiegeClan getDefenderClan(L2Clan clan)
	{
		if (clan == null)
			return null;
		
		return getDefenderClan(clan.getClanId());
	}
	
	@Override
	public final L2SiegeClan getDefenderClan(int clanId)
	{
		for (L2SiegeClan sc : getDefenderClans())
			if (sc != null && sc.getClanId() == clanId)
				return sc;
			
		return null;
	}
	
	@Override
	public final List<L2SiegeClan> getDefenderClans()
	{
		return (_isNormalSide) ? _defenderClans : _attackerClans;
	}
	
	public final L2SiegeClan getDefenderWaitingClan(L2Clan clan)
	{
		if (clan == null)
			return null;
		
		return getDefenderWaitingClan(clan.getClanId());
	}
	
	public final L2SiegeClan getDefenderWaitingClan(int clanId)
	{
		for (L2SiegeClan sc : getDefenderWaitingClans())
			if (sc != null && sc.getClanId() == clanId)
				return sc;
			
		return null;
	}
	
	public final List<L2SiegeClan> getDefenderWaitingClans()
	{
		return _defenderWaitingClans;
	}
	
	public final boolean isInProgress()
	{
		return _siegeStatus == SiegeStatus.IN_PROGRESS;
	}
	
	public final boolean isRegistrationOver()
	{
		return _siegeStatus != SiegeStatus.REGISTRATION_OPENED;
	}
	
	public final boolean isTimeRegistrationOver()
	{
		return getCastle().isTimeRegistrationOver();
	}
	
	@Override
	public final Calendar getSiegeDate()
	{
		return getCastle().getSiegeDate();
	}
	
	/**
	 * @return siege registration end date, which always equals siege date minus one day.
	 */
	public final long getSiegeRegistrationEndDate()
	{
		return getCastle().getSiegeDate().getTimeInMillis() - 86400000;
	}
	
	public void endTimeRegistration(boolean automatic)
	{
		getCastle().setTimeRegistrationOver(true);
		if (!automatic)
			saveSiegeDate();
	}
	
	@Override
	public List<L2Npc> getFlag(L2Clan clan)
	{
		if (clan != null)
		{
			L2SiegeClan sc = getAttackerClan(clan);
			if (sc != null)
				return sc.getFlags();
		}
		return null;
	}
	
	public int getControlTowerCount()
	{
		return (int) _controlTowers.stream().filter(lc -> lc.isActive()).count();
	}
	
	public List<L2Npc> getDestroyedTowers()
	{
		return _destroyedTowers;
	}
	
	public class ScheduleEndSiegeTask implements Runnable
	{
		private final Castle _castleInst;
		
		public ScheduleEndSiegeTask(Castle pCastle)
		{
			_castleInst = pCastle;
		}
		
		@Override
		public void run()
		{
			if (!isInProgress())
				return;
			
			try
			{
				long timeRemaining = _siegeEndDate.getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
				if (timeRemaining > 3600000)
				{
					announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.S1_HOURS_UNTIL_SIEGE_CONCLUSION).addNumber(2), true);
					ThreadPool.schedule(new ScheduleEndSiegeTask(_castleInst), timeRemaining - 3600000);
				}
				else if (timeRemaining <= 3600000 && timeRemaining > 600000)
				{
					announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.S1_MINUTES_UNTIL_SIEGE_CONCLUSION).addNumber(Math.round(timeRemaining / 60000)), true);
					ThreadPool.schedule(new ScheduleEndSiegeTask(_castleInst), timeRemaining - 600000);
				}
				else if (timeRemaining <= 600000 && timeRemaining > 300000)
				{
					announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.S1_MINUTES_UNTIL_SIEGE_CONCLUSION).addNumber(Math.round(timeRemaining / 60000)), true);
					ThreadPool.schedule(new ScheduleEndSiegeTask(_castleInst), timeRemaining - 300000);
				}
				else if (timeRemaining <= 300000 && timeRemaining > 10000)
				{
					announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.S1_MINUTES_UNTIL_SIEGE_CONCLUSION).addNumber(Math.round(timeRemaining / 60000)), true);
					ThreadPool.schedule(new ScheduleEndSiegeTask(_castleInst), timeRemaining - 10000);
				}
				else if (timeRemaining <= 10000 && timeRemaining > 0)
				{
					announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.CASTLE_SIEGE_S1_SECONDS_LEFT).addNumber(Math.round(timeRemaining / 1000)), true);
					ThreadPool.schedule(new ScheduleEndSiegeTask(_castleInst), timeRemaining);
				}
				else
					_castleInst.getSiege().endSiege();
			}
			catch (Exception e)
			{
				_log.log(Level.SEVERE, "", e);
			}
		}
	}
	
	public class ScheduleStartSiegeTask implements Runnable
	{
		private final Castle _castleInst;
		
		public ScheduleStartSiegeTask(Castle pCastle)
		{
			_castleInst = pCastle;
		}
		
		@Override
		public void run()
		{
			_scheduledStartSiegeTask.cancel(false);
			if (isInProgress())
				return;
			
			try
			{
				if (!isTimeRegistrationOver())
				{
					long regTimeRemaining = getSiegeRegistrationEndDate() - Calendar.getInstance().getTimeInMillis();
					if (regTimeRemaining > 0)
					{
						_scheduledStartSiegeTask = ThreadPool.schedule(new ScheduleStartSiegeTask(_castleInst), regTimeRemaining);
						return;
					}
					endTimeRegistration(true);
				}
				
				long timeRemaining = getSiegeDate().getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
				
				if (timeRemaining > 86400000)
					_scheduledStartSiegeTask = ThreadPool.schedule(new ScheduleStartSiegeTask(_castleInst), timeRemaining - 86400000);
				else if (timeRemaining <= 86400000 && timeRemaining > 13600000)
				{
					Broadcast.toAllOnlinePlayers(SystemMessage.getSystemMessage(SystemMessageId.REGISTRATION_TERM_FOR_S1_ENDED).addString(getCastle().getName()));
					changeStatus(SiegeStatus.REGISTRATION_OVER);
					clearSiegeWaitingClan();
					_scheduledStartSiegeTask = ThreadPool.schedule(new ScheduleStartSiegeTask(_castleInst), timeRemaining - 13600000);
				}
				else if (timeRemaining <= 13600000 && timeRemaining > 600000)
					_scheduledStartSiegeTask = ThreadPool.schedule(new ScheduleStartSiegeTask(_castleInst), timeRemaining - 600000);
				else if (timeRemaining <= 600000 && timeRemaining > 300000)
					_scheduledStartSiegeTask = ThreadPool.schedule(new ScheduleStartSiegeTask(_castleInst), timeRemaining - 300000);
				else if (timeRemaining <= 300000 && timeRemaining > 10000)
					_scheduledStartSiegeTask = ThreadPool.schedule(new ScheduleStartSiegeTask(_castleInst), timeRemaining - 10000);
				else if (timeRemaining <= 10000 && timeRemaining > 0)
					_scheduledStartSiegeTask = ThreadPool.schedule(new ScheduleStartSiegeTask(_castleInst), timeRemaining);
				else
					_castleInst.getSiege().startSiege();
			}
			catch (Exception e)
			{
				_log.log(Level.SEVERE, "", e);
			}
		}
	}
	
	public void addQuestEvent(Quest quest)
	{
		if (_questEvents.isEmpty())
			_questEvents = new ArrayList<>(3);
		
		_questEvents.add(quest);
	}
	
	public List<Quest> getQuestEvents()
	{
		return _questEvents;
	}
	
	public SiegeStatus getStatus()
	{
		return _siegeStatus;
	}
	
	protected void changeStatus(SiegeStatus status)
	{
		_siegeStatus = status;
		
		for (Quest quest : _questEvents)
			quest.onSiegeEvent();
	}
}