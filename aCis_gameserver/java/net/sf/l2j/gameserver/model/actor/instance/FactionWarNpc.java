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
			case "warGoToBase" -> handleGoToBase(player);
			case "warLeave" -> handleLeave(player);
			case "warCheckpoint" -> handleCheckpoint(player, st);
			case "fwVote" -> handleVote(player, st);
			case "warVoteMenu" -> handleVoteMenu(player);
			default -> super.onBypassFeedback(player, command);
		}
	}
	
	private void handleRegister(Player player)
	{
		// Check: player must have a faction
		if (player.getFactionId() <= 0)
		{
			showPanel(player, "You must join a faction first. Speak to the Faction Manager in the neutral zone.");
			return;
		}
		
		// Check: player must be in neutral zone to register
		if (!FactionWarManager.getInstance().isInNeutralZone(player))
		{
			showPanel(player, "You must be in the NEUTRAL ZONE to register for the Faction War. Go to Talking Island or Aden.");
			return;
		}
		
		// Check: war must be running or in voting phase
		if (!FactionWarManager.getInstance().isRunning() && !FactionWarManager.getInstance().isVotingPhaseActive())
		{
			showPanel(player, "There is no Faction War in progress at the moment.");
			return;
		}
		
		// Check: not already registered
		if (FactionWarRegistry.getInstance().isRegistered(player))
		{
			showPanel(player, "You are already registered. Use 'Go to My Base' to teleport.");
			return;
		}
		
		// Register and teleport to base
		FactionWarRegistry.getInstance().register(player);
		player.sendMessage("[Faction War] You have been registered for the Faction War!");
		
		// Teleport to base if war is running
		if (FactionWarManager.getInstance().isRunning())
		{
			FactionWarManager.getInstance().teleportToWarMap(player);
			showPanel(player, "Registered! Teleported to your faction's base. Fight for glory!");
		}
		else
		{
			showPanel(player, "Registered! The war will begin soon. Wait for the countdown.");
		}
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
	
	private void handleGoToBase(Player player)
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
		
		if (player.getFactionId() <= 0)
		{
			showPanel(player, "No faction selected.");
			return;
		}
		
		// Teleport to faction's base on the war map
		FactionWarManager.getInstance().teleportToWarMap(player);
		player.sendMessage("Teleported to your faction's base!");
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
	
	private void handleVote(Player player, StringTokenizer st)
	{
		if (!st.hasMoreTokens())
			return;
		
		try
		{
			final int mapIndex = Integer.parseInt(st.nextToken());
			FactionWarManager.getInstance().onPlayerVote(player, mapIndex);
		}
		catch (NumberFormatException e)
		{
			player.sendMessage("Invalid vote.");
		}
	}
	
	private void handleVoteMenu(Player player)
	{
		if (!FactionWarManager.getInstance().isVotingPhaseActive() && !FactionWarManager.getInstance().isRunning())
		{
			showPanel(player, "No active voting phase.");
			return;
		}
		
		FactionWarManager.getInstance().sendVoteHtml(player);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		return "data/html/mods/factionwar/" + npcId + ".htm";
	}
	
	@Override
	public void showChatWindow(Player player, int val)
	{
		showPanel(player, null);
	}
	
	public void showPanel(Player player, String message)
	{
		final StringBuilder sb = new StringBuilder(4096);
		sb.append("<html><body><center><font color=LEVEL>Faction War</font></center><br>");
		
		if (message != null)
			sb.append("<font color=99FF99>").append(message).append("</font><br1>");
		
		final boolean running = FactionWarManager.getInstance().isRunning();
		final boolean votingPhase = FactionWarManager.getInstance().isVotingPhaseActive();
		final boolean registered = FactionWarRegistry.getInstance().isRegistered(player);
		final int factionId = player.getFactionId();
		
		if (running || votingPhase)
		{
			sb.append("Status: <font color=00FF00>").append(running ? "RUNNING" : "VOTING PHASE").append("</font><br>");
			sb.append("Good: <font color=0000FF>").append(FactionWarManager.getInstance().getScore(FactionWarConfig.getGoodFactionId())).append("</font> | ");
			sb.append("Evil: <font color=FF0000>").append(FactionWarManager.getInstance().getScore(FactionWarConfig.getEvilFactionId())).append("</font><br>");
			sb.append("Score to win: ").append(FactionWarConfig.getScoreToWin()).append("<br>");
		}
		else
		{
			sb.append("Status: <font color=FF0000>STOPPED</font><br>");
		}
		
		if (factionId > 0)
		{
			final Faction faction = FactionData.getInstance().getFaction(factionId);
			sb.append("Your faction: <font color=").append(String.format("%06X", faction != null ? faction.getNameColor() : 0xFFFFFF)).append(">").append(faction != null ? faction.getName() : "Unknown").append("</font><br>");
		}
		else
		{
			sb.append("<font color=FF4444>No faction selected. Speak to the Faction Manager first!</font><br>");
		}
		
		sb.append("<br>");
		
		if (!running && !votingPhase)
		{
			sb.append("<font color=808080>There is no Faction War in progress at the moment.<br>Please wait for the next war to begin.</font><br>");
		}
		else if (!registered)
		{
			// Voting phase: show vote button + register
			if (votingPhase)
			{
				sb.append("<font color=FFCC00>Voting phase is active! Click below to vote for the next map.</font><br>");
			sb.append("<table width=280><tr>");
			sb.append("<td><button value=\"View Maps & Vote\" action=\"bypass -h npc_%objectId%_warVoteMenu\" width=160 height=25 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
			sb.append("</tr></table><br>");
			}
			
			sb.append("<table width=280><tr>");
			sb.append("<td><button value=\"Register for War\" action=\"bypass -h npc_%objectId%_warRegister\" width=180 height=25 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
			sb.append("</tr></table>");
		}
		else
		{
			sb.append("<font color=00FF00>You are REGISTERED</font><br><br>");
			
			if (running)
			{
				final FactionWarCheckpoint cp = FactionWarManager.getInstance().getCheckpoints();
				final List<Location> locs = cp.getLocations();
				
				if (!locs.isEmpty())
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
				
				sb.append("<table width=280><tr>");
				sb.append("<td><button value=\"Go to My Base\" action=\"bypass -h npc_%objectId%_warGoToBase\" width=130 height=25 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
				sb.append("</tr></table><br>");
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
