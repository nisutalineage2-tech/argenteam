package net.sf.l2j.gameserver.model.actor.instance;

import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.commons.util.SysUtil;

import net.sf.l2j.gameserver.guildmission.GuildMission;
import net.sf.l2j.gameserver.guildmission.GuildMissionManager;
import net.sf.l2j.gameserver.guildmission.GuildMissionProgress;
import net.sf.l2j.gameserver.guildmission.MissionObjective;
import net.sf.l2j.gameserver.guildmission.GuildMissionReward;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class GuildMissionNpc extends Folk
{
	private static final int PAGE_SIZE = 4;
	
	public GuildMissionNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		final StringTokenizer st = new StringTokenizer(command, " ");
		final String cmd = st.nextToken();
		
		switch (cmd)
		{
			case "gmList":
				showMissionList(player, 0);
				break;
			case "gmPage":
				int page = 0;
				if (st.hasMoreTokens())
				{
					try
					{
						page = Integer.parseInt(st.nextToken());
					}
					catch (NumberFormatException e)
					{
					}
				}
				showMissionList(player, page);
				break;
			case "gmClaim":
				if (st.hasMoreTokens())
				{
					try
					{
						final int missionId = Integer.parseInt(st.nextToken());
						final GuildMission mission = GuildMissionManager.getInstance().getMission(missionId);
						if (mission != null)
						{
							GuildMissionManager.getInstance().claimRewards(player, missionId);
							player.sendMessage("[Guild Missions] Recompensas de '" + mission.getName() + "' reclamadas.");
						}
					}
					catch (NumberFormatException e)
					{
					}
				}
				showMissionList(player, 0);
				break;
			default:
				super.onBypassFeedback(player, command);
				break;
		}
	}
	
	@Override
	public void showChatWindow(Player player, int val)
	{
		showMissionList(player, 0);
	}
	
	private void showMissionList(Player player, int page)
	{
		if (player.getClan() == null)
		{
			final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			html.setHtml("<html><body><center><font color=FFD700>Guild Missions</font></center><br1>"
				+ "Necesitas un clan para aceptar misiones.<br1>"
				+ "<center><button value=\"Cerrar\" action=\"bypass -h npc_" + getObjectId() + "_Chat\" width=80 height=25 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></center>"
				+ "</body></html>");
			player.sendPacket(html);
			return;
		}
		
		final GuildMissionManager manager = GuildMissionManager.getInstance();
		final int clanId = player.getClanId();
		final List<GuildMission> missions = manager.getAvailableMissions(player);
		
		final int totalPages = Math.max(1, (missions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		final int currentPage = Math.max(0, Math.min(page, totalPages - 1));
		final int start = currentPage * PAGE_SIZE;
		final int end = Math.min(start + PAGE_SIZE, missions.size());
		
		final StringBuilder sb = new StringBuilder(4096);
		sb.append("<html><body>");
		sb.append("<center><font color=FFD700>Guild Missions</font></center>");
		sb.append("<img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\"><br>");
		sb.append("<font color=808080>Clan: </font><font color=LEVEL>").append(SysUtil.escapeHtml(player.getClan().getName())).append("</font>");
		sb.append(" <font color=808080>| Nivel: ").append(player.getClan().getLevel()).append(" | Miembros: ").append(player.getClan().getMembersCount()).append("</font><br>");
		sb.append("<img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\"><br1>");
		
		if (missions.isEmpty())
		{
			sb.append("<center><font color=FFCC00>No hay misiones disponibles para tu clan.</font></center>");
		}
		
		for (int i = start; i < end; i++)
		{
			final GuildMission mission = missions.get(i);
			final GuildMissionProgress progress = manager.getProgress(clanId, mission.getId());
			
			sb.append("<table width=\"270\" bgcolor=\"1A1A2E\"><tr><td>");
			sb.append("<font color=LEVEL>").append(SysUtil.escapeHtml(mission.getName())).append("</font>");
			sb.append(" <font color=808080>[").append(mission.getCategory()).append("]</font><br1>");
			sb.append("<font color=808080 size=10>").append(SysUtil.escapeHtml(mission.getDescription())).append("</font><br1>");
			
			for (MissionObjective objective : mission.getObjectives())
			{
				final long current = progress.getObjectiveProgress(objective.getId());
				final long target = objective.getQuantity();
				final String color = (current >= target) ? "00FF00" : "FFCC00";
				sb.append("<font color=").append(color).append(">").append(objective.getType()).append(": ")
					.append(Math.min(current, target)).append(" / ").append(target).append("</font><br1>");
			}
			
			if (progress.isCompleted())
			{
				sb.append("<button value=\"Reclamar\" action=\"bypass -h npc_" + getObjectId() + "_gmClaim " + mission.getId() + "\" width=90 height=22 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
			}
			else
			{
				sb.append("<font color=808080>En progreso...</font>");
			}
			sb.append("</td></tr></table>");
			sb.append("<img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\">");
		}
		
		if (totalPages > 1)
		{
			sb.append("<br>");
			if (currentPage > 0)
			{
				sb.append("<button value=\"Anterior\" action=\"bypass -h npc_" + getObjectId() + "_gmPage " + (currentPage - 1) + "\" width=70 height=20 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
			}
			sb.append("<font color=808080 size=10>Pagina ").append(currentPage + 1).append("/").append(totalPages).append("</font>");
			if (currentPage < totalPages - 1)
			{
				sb.append("<button value=\"Siguiente\" action=\"bypass -h npc_" + getObjectId() + "_gmPage " + (currentPage + 1) + "\" width=70 height=20 back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
			}
		}
		
		sb.append("</body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
}
