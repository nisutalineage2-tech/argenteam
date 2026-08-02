package net.sf.l2j.gameserver.scripting.script.factionwar;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.factionwar.FactionWarRegistry;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.scripting.Quest;

public class WarRegistrar extends Quest
{
	public WarRegistrar()
	{
		super(-1, "factionwar");
		
		addTalkId(FactionWarConfig.getWarRegistrarNpcId());
	}
	
	@Override
	public String onTalk(Npc npc, Player player)
	{
		final FactionWarManager manager = FactionWarManager.getInstance();
		
		// The registrar is active during BOTH the voting phase and the running war.
		// Players can register in the neutral zone while the vote is open; they are
		// only teleported once the war is actually running.
		if (!FactionWarConfig.isEnabled() || (!manager.isRunning() && !manager.isVotingPhaseActive()))
		{
			showHtml(player, "war_registrar_not_active.htm");
			return null;
		}
		
		if (player.getFactionId() == 0)
		{
			showHtml(player, "war_registrar_no_faction.htm");
			return null;
		}
		
		FactionWarRegistry.getInstance().register(player);
		
		if (manager.isRunning())
		{
			manager.teleportToWarMap(player);
			player.sendMessage("Te has registrado en la Faction War. Buena suerte.");
		}
		else
		{
			player.sendMessage("Te has registrado en la Faction War. Seras teletransportado cuando comience la guerra.");
		}
		
		return null;
	}
	
	/**
	 * Handles the "lastFlag" quest event triggered by the "Ir a la Ultima Bandera" button
	 * of the WarRegistrar (bypass: Quest WarRegistrar lastFlag).
	 * <p>
	 * Teleports the player LIVE (no death required) to the last flag captured by their
	 * faction during the running war, then shows the confirmation HTML.
	 */
	@Override
	public String onAdvEvent(String event, Npc npc, Player player)
	{
		if (!event.equals("lastFlag"))
			return null;
		
		final FactionWarManager manager = FactionWarManager.getInstance();
		
		if (!FactionWarConfig.isEnabled() || !manager.isRunning())
		{
			player.sendMessage("La Faction War no esta activa en este momento.");
			return null;
		}
		
		if (player.getFactionId() <= 0)
		{
			player.sendMessage("No tienes una faccion. Habla con el Faction Manager primero.");
			return null;
		}
		
		if (player.isDead())
		{
			player.sendMessage("Debes estar vivo para teletransportarte a la bandera.");
			return null;
		}
		
		final Location flag = manager.getLastCapturedFlag(player.getFactionId());
		if (flag == null)
		{
			player.sendMessage("Tu faccion no ha capturado ninguna bandera todavia.");
			return null;
		}
		
		// Small random spread so players don't land exactly on top of the flag NPC.
		player.teleportTo(flag.getX() + Rnd.get(-200, 200), flag.getY() + Rnd.get(-200, 200), flag.getZ(), 50);
		player.sendMessage("Teletransportado a la ultima bandera capturada por tu faccion.");
		
		// Show the confirmation HTML after the teleport.
		return "war_registrar_last_flag.htm";
	}
	
	private void showHtml(Player player, String filename)
	{
		final NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setFile("./data/html/script/" + getDescr() + "/" + getName() + "/" + filename);
		player.sendPacket(msg);
	}
}
