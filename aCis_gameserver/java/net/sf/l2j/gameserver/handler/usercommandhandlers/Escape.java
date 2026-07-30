package net.sf.l2j.gameserver.handler.usercommandhandlers;

import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.enums.ZoneId;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.handler.IUserCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;

public class Escape implements IUserCommandHandler
{
	private static final int[] COMMAND_IDS =
	{
		52
	};
	
	@Override
	public void useUserCommand(int id, Player player)
	{
		if (player.isInOlympiadMode() || player.isInObserverMode() || player.isFestivalParticipant() || player.isInJail() || player.isInsideZone(ZoneId.BOSS))
		{
			player.sendPacket(SystemMessageId.NO_UNSTUCK_PLEASE_SEND_PETITION);
			return;
		}
		
		// GM: instant escape with skill 2100
		if (player.isGM())
		{
			player.getAI().tryToCast(player, 2100, 1);
			return;
		}
		
		// Faction War mode: use configurable delay + neutral zone teleport
		if (Config.ENABLE_FACTION_SYSTEM && FactionWarConfig.isEnabled())
		{
			final int delaySeconds = FactionWarConfig.getUnstuckDelaySeconds();
			final Location neutralLoc = FactionWarConfig.getNeutralSpawnLoc();
			
			player.sendPacket(new PlaySound("systemmsg_e.809"));
			player.sendMessage("[Faction War] Teletransportándote a la zona neutral en " + delaySeconds + " segundos... Espera sin moverte.");
			
			// Schedule teleport after the configurable delay
			ThreadPool.schedule(() ->
			{
				if (player == null || !player.isOnline())
					return;
				
				if (neutralLoc != null)
					player.teleportTo(neutralLoc, 50);
				else
					player.teleportTo(-84300, 243000, -3450, 50); // fallback to Talking Island
				
				player.sendMessage("[Faction War] Has sido teletransportado a la zona neutral.");
			}, delaySeconds * 1000L);
		}
		else
		{
			// Original behavior: 5-minute escape skill for non-FW servers
			player.sendPacket(new PlaySound("systemmsg_e.809"));
			player.sendPacket(SystemMessageId.STUCK_TRANSPORT_IN_FIVE_MINUTES);
			
			player.getAI().tryToCast(player, 2099, 1);
		}
	}
	
	@Override
	public int[] getUserCommandList()
	{
		return COMMAND_IDS;
	}
}