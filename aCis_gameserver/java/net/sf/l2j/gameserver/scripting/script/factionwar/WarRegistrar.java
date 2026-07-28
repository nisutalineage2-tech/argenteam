package net.sf.l2j.gameserver.scripting.script.factionwar;

import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.factionwar.FactionWarRegistry;
import net.sf.l2j.gameserver.model.actor.Npc;
import net.sf.l2j.gameserver.model.actor.Player;
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
		if (!FactionWarConfig.isEnabled() || !FactionWarManager.getInstance().isRunning())
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
		FactionWarManager.getInstance().teleportToWarMap(player);
		player.sendMessage("Te has registrado en la Faction War! Buena suerte.");
		
		return null;
	}
	
	private void showHtml(Player player, String filename)
	{
		final NpcHtmlMessage msg = new NpcHtmlMessage(0);
		msg.setFile("./data/html/script/" + getDescr() + "/" + getName() + "/" + filename);
		player.sendPacket(msg);
	}
}
