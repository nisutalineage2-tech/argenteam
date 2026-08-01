package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.manager.CastleManager;
import net.sf.l2j.gameserver.data.manager.ClanHallManager;
import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.data.xml.RestartPointData;
import net.sf.l2j.gameserver.enums.RestartType;
import net.sf.l2j.gameserver.enums.SiegeSide;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.model.pledge.Clan;
import net.sf.l2j.gameserver.model.residence.castle.Siege;
import net.sf.l2j.gameserver.model.residence.clanhall.ClanHall;
import net.sf.l2j.gameserver.model.residence.clanhall.ClanHallFunction;

public final class RequestRestartPoint extends L2GameClientPacket
{
	protected static final Location JAIL_LOCATION = new Location(-114356, -249645, -2984);
	
	protected int _requestType;
	
	@Override
	protected void readImpl()
	{
		_requestType = readD();
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getPlayer();
		if (player == null)
			return;
		
		// TODO Needed? Possible?
		if (player.isFakeDeath())
		{
			player.stopFakeDeath(true);
			return;
		}
		
		if (!player.isDead())
			return;
		
		// Schedule a respawn delay if player is part of a clan registered in an active siege.
		if (player.getClan() != null)
		{
			final Siege siege = CastleManager.getInstance().getActiveSiege(player);
			if (siege != null && siege.checkSide(player.getClan(), SiegeSide.ATTACKER))
			{
				ThreadPool.schedule(() -> portPlayer(player), Config.ATTACKERS_RESPAWN_DELAY);
				return;
			}
		}
		
		portPlayer(player);
	}
	
	/**
	 * Teleport the {@link Player} to the associated {@link Location}, based on _requestType.
	 * @param player : The player set as parameter.
	 */
	private void portPlayer(Player player)
	{
		final Clan clan = player.getClan();
		
		Location loc = null;
		
		// Enforce type.
		if (player.isInJail())
			_requestType = 27;
		else if (player.isFestivalParticipant())
			_requestType = 4;
		
		// To clanhall.
		if (_requestType == 1)
		{
			if (clan == null || !clan.hasClanHall())
				return;
			
			loc = RestartPointData.getInstance().getLocationToTeleport(player, RestartType.CLAN_HALL);
			
			final ClanHall ch = ClanHallManager.getInstance().getClanHallByOwner(clan);
			if (ch != null)
			{
				final ClanHallFunction function = ch.getFunction(ClanHall.FUNC_RESTORE_EXP);
				if (function != null)
					player.restoreExp(function.getLvl());
			}
		}
		// To castle.
		else if (_requestType == 2)
		{
			final Siege siege = CastleManager.getInstance().getActiveSiege(player);
			if (siege != null)
			{
				final SiegeSide side = siege.getSide(clan);
				if (side == SiegeSide.DEFENDER || side == SiegeSide.OWNER)
					loc = RestartPointData.getInstance().getLocationToTeleport(player, RestartType.CASTLE);
				else if (side == SiegeSide.ATTACKER)
					loc = RestartPointData.getInstance().getLocationToTeleport(player, RestartType.TOWN);
				else
					return;
			}
			else
			{
				if (clan == null || !clan.hasCastle())
					return;
				
				loc = RestartPointData.getInstance().getLocationToTeleport(player, RestartType.CASTLE);
			}
		}
		// To siege flag.
		else if (_requestType == 3)
			loc = RestartPointData.getInstance().getLocationToTeleport(player, RestartType.SIEGE_FLAG);
		// Fixed.
		else if (_requestType == 4)
		{
			// Faction War: the "Fixed" button teleports to the last flag captured by the faction.
			final Location capturedFlag = getLastCapturedFactionFlag(player);
			if (capturedFlag != null)
			{
				// Small random spread so players don't revive exactly on top of the flag NPC.
				loc = new Location(capturedFlag.getX() + Rnd.get(-200, 200), capturedFlag.getY() + Rnd.get(-200, 200), capturedFlag.getZ());
				player.sendMessage("[Faction War] Teletransportado a la ultima bandera capturada por tu faccion.");
			}
			else if (!player.isGM() && !player.isFestivalParticipant())
			{
				if (Config.ENABLE_FACTION_SYSTEM && player.getFactionId() != 0 && FactionWarManager.getInstance().isRunning())
					player.sendMessage("[Faction War] Tu faccion no ha capturado ninguna bandera aun.");
				return;
			}
			else
				loc = player.getPosition();
		}
		// To jail.
		else if (_requestType == 27)
		{
			if (!player.isInJail())
				return;
			
			loc = JAIL_LOCATION;
		}
		// Nothing has been found, use regular "To town" behavior.
		else
		{
			// Faction respawn: if Faction War is running, respawn at the last flag captured by the faction.
			// If the faction captured no flag yet, fall back to the neutral zone.
			// Otherwise, respawn at faction home.
			if (Config.ENABLE_FACTION_SYSTEM)
			{
				if (player.getFactionId() != 0 && FactionWarManager.getInstance().isRunning())
				{
					final Location capturedFlag = getLastCapturedFactionFlag(player);
					if (capturedFlag != null)
					{
						// War is running and the faction holds a captured flag - respawn there.
						// Small random spread so players don't revive exactly on top of the flag NPC.
						loc = new Location(capturedFlag.getX() + Rnd.get(-200, 200), capturedFlag.getY() + Rnd.get(-200, 200), capturedFlag.getZ());
						player.sendMessage("[Faction War] Has muerto en batalla. Reapareces en la ultima bandera capturada por tu faccion.");
					}
					else
					{
						// War is running but no flag captured - send to neutral zone (surrender/rendicion)
						loc = FactionWarConfig.getNeutralSpawnLoc();
						player.sendMessage("[Faction War] Has muerto en batalla. Ve al Registrador de Guerra para volver a tu base.");
					}
				}
				else if (player.getFactionId() != 0)
				{
					// No war - normal faction home respawn
					final Faction faction = FactionData.getInstance().getFaction(player.getFactionId());
					if (faction != null && faction.getHomeLocation() != null)
						loc = faction.getHomeLocation();
				}
			}
			
			if (loc == null)
				loc = RestartPointData.getInstance().getLocationToTeleport(player, RestartType.TOWN);
		}
		
		player.setIsIn7sDungeon(false);
		
		if (player.isDead())
			player.doRevive();
		
		player.teleportTo(loc, 20);
	}
	
	/**
	 * @param player : The player to check.
	 * @return The {@link Location} of the last flag captured by the player's faction during the
	 *         running Faction War, or null if the war is not running or no flag was captured.
	 */
	private static Location getLastCapturedFactionFlag(Player player)
	{
		if (!Config.ENABLE_FACTION_SYSTEM || player == null || player.getFactionId() == 0)
			return null;
		
		final FactionWarManager manager = FactionWarManager.getInstance();
		if (!manager.isRunning())
			return null;
		
		return manager.getLastCapturedFlag(player.getFactionId());
	}
}