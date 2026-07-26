package net.sf.l2j.gameserver.model.actor.instance;

import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.commons.pool.ThreadPool;

import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.factionwar.FactionWarCheckpoint;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.factionwar.FactionWarRegistry;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class FactionWarNpc extends Folk
{
	public FactionWarNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		if (!FactionWarConfig.isEnabled())
			return;
		
		final StringTokenizer st = new StringTokenizer(command, " ");
		final String cmd = st.nextToken();
		
		switch (cmd)
		{
			case "warRegister" -> handleRegister(player);
			case "warLeave" -> handleLeave(player);
			case "warCheckpoint" -> handleCheckpoint(player, st);
			default -> super.onBypassFeedback(player, command);
		}
	}
	
	private void handleRegister(Player player)
	{
		if (player.getFactionId() <= 0)
		{
			showPanel(player, "You must join a faction first.");
			return;
		}
		
		if (FactionWarRegistry.getInstance().isRegistered(player))
		{
			showPanel(player, "You are already registered.");
			return;
		}
		
		FactionWarRegistry.getInstance().register(player);
		showPanel(player, "Registered for Faction War!");
	}
	
	private void handleLeave(Player player)
	{
		if (!FactionWarRegistry.getInstance().isRegistered(player))
		{
			showPanel(player, "You are not registered.");
			return;
		}
		
		FactionWarRegistry.getInstance().unregister(player);
		showPanel(player, "You left the Faction War.");
	}
	
	private void handleCheckpoint(Player player, StringTokenizer st)
	{
		if (!FactionWarManager.getInstance().isRunning())
		{
			showPanel(player, "Faction War is not running.");
			return;
		}
		
		if (!FactionWarRegistry.getInstance().isRegistered(player))
		{
			showPanel(player, "You must register first.");
			return;
		}
		
		if (!st.hasMoreTokens())
			return;
		
		try
		{
			final int index = Integer.parseInt(st.nextToken());
			final FactionWarCheckpoint cp = FactionWarManager.getInstance().getCheckpoints();
			final List<Location> locs = cp.getLocations();
			
			if (index < 0 || index >= locs.size())
			{
				showPanel(player, "Invalid checkpoint.");
				return;
			}
			
			final Location loc = locs.get(index);
			player.teleportTo(loc.getX(), loc.getY(), loc.getZ(), 0);
			player.sendMessage("Teleported to checkpoint " + (index + 1) + ".");
		}
		catch (NumberFormatException e)
		{
			showPanel(player, "Invalid checkpoint.");
		}
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		return "data/html/mods/factionwar/" + npcId + ".htm";
	}
	
	public void showPanel(Player player, String message)
	{
		final StringBuilder sb = new StringBuilder(4096);
		sb.append("<html><body><center><font color=LEVEL>Faction War</font></center><br>");
		
		if (message != null)
			sb.append("<font color=99FF99>").append(message).append("</font><br1>");
		
		final boolean running = FactionWarManager.getInstance().isRunning();
		final boolean registered = FactionWarRegistry.getInstance().isRegistered(player);
		final int factionId = player.getFactionId();
		
		sb.append("Status: <font color=").append(running ? "00FF00" : "FF0000").append(">").append(running ? "RUNNING" : "STOPPED").append("</font><br>");
		
		if (FactionWarConfig.isEnabled())
		{
			sb.append("Good: <font color=0000FF>").append(FactionWarManager.getInstance().getScore(FactionWarConfig.getGoodFactionId())).append("</font> | ");
			sb.append("Evil: <font color=FF0000>").append(FactionWarManager.getInstance().getScore(FactionWarConfig.getEvilFactionId())).append("</font><br>");
		}
		
		sb.append("Score to win: ").append(FactionWarConfig.getScoreToWin()).append("<br>");
		
		if (factionId > 0)
		{
			final Faction faction = FactionData.getInstance().getFaction(factionId);
			sb.append("Your faction: <font color=").append(String.format("%06X", faction != null ? faction.getNameColor() : 0xFFFFFF)).append(">").append(faction != null ? faction.getName() : "Unknown").append("</font><br>");
		}
		
		sb.append("<br>");
		
		if (!registered)
		{
			sb.append("<table width=280><tr>");
			sb.append("<td><button value=\"Register\" action=\"bypass -h npc_%objectId%_warRegister\" width=130 height=25 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
			sb.append("</tr></table>");
		}
		else
		{
			sb.append("<font color=00FF00>You are REGISTERED</font><br><br>");
			
			if (running)
			{
				final FactionWarCheckpoint cp = FactionWarManager.getInstance().getCheckpoints();
				final List<Location> locs = cp.getLocations();
				
				if (locs.isEmpty())
				{
					sb.append("<font color=808080>No checkpoints available.</font><br>");
				}
				else
				{
					sb.append("<font color=B0C4DE>--- Checkpoints ---</font><br>");
					sb.append("<table width=280>");
					for (int i = 0; i < locs.size(); i++)
					{
						if (i % 2 == 0)
							sb.append("<tr>");
						sb.append("<td><button value=\"CP ").append(i + 1).append("\" action=\"bypass -h npc_%objectId%_warCheckpoint ").append(i).append("\" width=130 height=25 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
						if (i % 2 == 1 || i == locs.size() - 1)
							sb.append("</tr>");
					}
					sb.append("</table><br>");
				}
			}
			
			sb.append("<table width=280><tr>");
			sb.append("<td><button value=\"Leave War\" action=\"bypass -h npc_%objectId%_warLeave\" width=130 height=25 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
			sb.append("</tr></table>");
		}
		
		sb.append("</body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
}
