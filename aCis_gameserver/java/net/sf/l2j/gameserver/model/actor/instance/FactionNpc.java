package net.sf.l2j.gameserver.model.actor.instance;

import java.util.StringTokenizer;

import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;

public class FactionNpc extends Folk
{
	public FactionNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		StringTokenizer st = new StringTokenizer(command, " ");
		String currentCommand = st.nextToken();
		
		if (currentCommand.startsWith("joinFaction"))
		{
			if (!st.hasMoreTokens())
				return;
			
			int id = Integer.parseInt(st.nextToken());
			
			if (player.getFactionId() > 0)
			{
				player.sendMessage("You've already decided to fight for a faction.");
				return;
			}
			
			final Faction faction = FactionData.getInstance().getFaction(id);
			if (faction == null)
				return;
			
			player.getAppearance().setNameColor(faction.getNameColor());
			player.getAppearance().setTitleColor(faction.getTitleColor());
			player.setTitle(faction.getName());
			player.setFactionId(id);
			FactionData.getInstance().storeData(player);
			
			if (player.isInParty())
				player.getParty().disband();
			
			player.broadcastTitleInfo();
			player.broadcastUserInfo();
			
			player.sendMessage("You've decided to fight for " + faction.getName() + ".");
			player.sendMessage("You'll be teleported to your base in 6 second(s).");
			ThreadPool.schedule(() ->
			{
				final Faction f = FactionData.getInstance().getFaction(id);
				if (f != null)
					player.teleportTo(f.getHomeLocation(), 0);
			}, 6000);
		}
		else if (currentCommand.startsWith("leaveFaction"))
		{
			if (player.getFactionId() > 0)
			{
				player.getAppearance().setNameColor(0xFFFFFF);
				player.getAppearance().setTitleColor(0xFFFF77);
				player.setTitle("");
				FactionData.getInstance().removeData(player);
				FactionData.getInstance().storeData(player);
				
				if (player.isInParty())
					player.getParty().disband();
				
				player.broadcastTitleInfo();
				player.broadcastUserInfo();
				
				player.sendMessage("Your previous faction has been removed.");
			}
			else
				player.sendMessage("You're not a member of a faction.");
		}
		else
			super.onBypassFeedback(player, command);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		String filename;
		if (val == 0)
			filename = "" + npcId;
		else
			filename = npcId + "-" + val;
		
		return "data/html/mods/faction/" + filename + ".htm";
	}
}
