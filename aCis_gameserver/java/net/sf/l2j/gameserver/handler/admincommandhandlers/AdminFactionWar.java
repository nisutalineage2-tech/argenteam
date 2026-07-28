package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.StringTokenizer;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.factionwar.FactionWarRegistry;
import net.sf.l2j.gameserver.model.World;

public class AdminFactionWar implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_factionwar"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		final StringTokenizer st = new StringTokenizer(command);
		st.nextToken();
		
		if (!st.hasMoreTokens())
		{
			showPanel(player, null);
			return;
		}
		
		final String action = st.nextToken().toLowerCase();
		
		switch (action)
		{
		case "start" ->
		{
			if (FactionWarManager.getInstance().isRunning())
			{
				showPanel(player, "Faction War already running!");
				return;
			}
			final int score = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : FactionWarConfig.getScoreToWin();
			final int duration = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
			FactionWarManager.getInstance().start(score, duration);
			showPanel(player, "Faction War started! Score: " + score + (duration > 0 ? " Duration: " + duration + "min" : ""));
		}
			case "stop" ->
			{
				FactionWarManager.getInstance().stop();
				showPanel(player, "Faction War stopped.");
			}
			case "register" ->
			{
				if (!FactionWarManager.getInstance().isRunning())
				{
					showPanel(player, "Faction War is not running!");
					return;
				}
				final String targetName = st.hasMoreTokens() ? st.nextToken() : null;
				if (targetName == null)
				{
					FactionWarRegistry.getInstance().register(player);
					FactionWarManager.getInstance().teleportToWarMap(player);
					showPanel(player, "You registered and teleported to war.");
				}
				else
				{
					final Player target = World.getInstance().getPlayer(targetName);
					if (target == null)
					{
						showPanel(player, "Player not found: " + targetName);
						return;
					}
					FactionWarRegistry.getInstance().register(target);
					FactionWarManager.getInstance().teleportToWarMap(target);
					target.sendMessage("You have been registered for Faction War by an admin.");
					showPanel(player, target.getName() + " registered and teleported.");
				}
			}
			case "registerall" ->
			{
				if (!FactionWarManager.getInstance().isRunning())
				{
					showPanel(player, "Faction War is not running!");
					return;
				}
				int count = 0;
				for (Player p : World.getInstance().getPlayers())
				{
					if (p != null && p.isOnline() && p.getFactionId() != 0 && !FactionWarRegistry.getInstance().isRegistered(p))
					{
						FactionWarRegistry.getInstance().register(p);
						FactionWarManager.getInstance().teleportToWarMap(p);
						count++;
					}
				}
				showPanel(player, count + " players registered and teleported.");
			}
			case "score" ->
			{
				showPanel(player, FactionWarManager.getInstance().getScoreboard());
			}
			case "reload" ->
			{
				FactionWarConfig.load();
				showPanel(player, "Faction War config reloaded.");
			}
			default -> showPanel(player, "Usage: factionwar start|stop|register|registerall|score|reload");
		}
	}
	
	private void showPanel(Player player, String message)
	{
		final StringBuilder sb = new StringBuilder(2048);
		sb.append("<html><body><center><font color=LEVEL>Faction War Manager</font></center><br>");
		
		if (message != null)
			sb.append("<font color=99FF99>").append(message).append("</font><br1>");
		
		final boolean running = FactionWarManager.getInstance().isRunning();
		sb.append("Status: <font color=").append(running ? "00FF00" : "FF0000").append(">").append(running ? "RUNNING" : "STOPPED").append("</font><br>");
		sb.append("Good Score: <font color=0000FF>").append(FactionWarManager.getInstance().getScore(FactionWarConfig.getGoodFactionId())).append("</font> | ");
		sb.append("Evil Score: <font color=FF0000>").append(FactionWarManager.getInstance().getScore(FactionWarConfig.getEvilFactionId())).append("</font><br>");
		sb.append("Score to Win: ").append(FactionWarConfig.getScoreToWin()).append("<br>");
		sb.append("Maps: ").append(FactionWarConfig.getMaps().size()).append("<br>");
		
		if (running)
		{
			final String timeLeft = FactionWarManager.getInstance().getRemainingTimeStr();
			if (!timeLeft.isEmpty())
				sb.append("Time Left: <font color=FF6600>").append(timeLeft).append("</font><br>");
		}
		
		sb.append("<br>");
		
		sb.append("<table width=290><tr>");
		sb.append("<td><button value=\"Start\" action=\"bypass -h admin_factionwar start\" width=85 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
		sb.append("<td><button value=\"Stop\" action=\"bypass -h admin_factionwar stop\" width=85 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
		sb.append("<td><button value=\"Reload\" action=\"bypass -h admin_factionwar reload\" width=85 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
		sb.append("</tr></table>");
		
		sb.append("<table width=290><tr>");
		sb.append("<td><button value=\"Register Me\" action=\"bypass -h admin_factionwar register\" width=85 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
		sb.append("<td><button value=\"Register All\" action=\"bypass -h admin_factionwar registerall\" width=85 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
		sb.append("</tr></table>");
		
		sb.append("</body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
