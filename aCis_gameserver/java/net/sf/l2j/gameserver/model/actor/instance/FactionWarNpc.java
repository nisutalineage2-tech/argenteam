package net.sf.l2j.gameserver.model.actor.instance;

import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.factionwar.FactionWarCheckpoint;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
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
			case "warGoToBase" -> handleGoToBase(player);
			case "warCheckpoint" -> handleCheckpoint(player, st);
			case "fwVote" -> handleVote(player, st);
			case "warVoteMenu" -> handleVoteMenu(player);
			default -> super.onBypassFeedback(player, command);
		}
	}
	
	private void handleGoToBase(Player player)
	{
		if (!FactionWarManager.getInstance().isRunning())
		{
			showPanel(player, "La guerra de facciones no está activa en este momento.");
			return;
		}
		
		if (player.getFactionId() <= 0)
		{
			showPanel(player, "No tienes una facción. Habla con el Faction Manager primero.");
			return;
		}
		
		// Must be in neutral zone to teleport to base
		if (!FactionWarManager.getInstance().isInNeutralZone(player))
		{
			showPanel(player, "Debes estar en la ZONA NEUTRAL para ir a tu base.");
			return;
		}
		
		// Teleport to faction's base on the war map
		FactionWarManager.getInstance().teleportToWarMap(player);
		player.sendMessage("¡Teletransportado a la base de tu facción! ¡Lucha por la gloria!");
	}
	
	private void handleCheckpoint(Player player, StringTokenizer st)
	{
		if (!FactionWarManager.getInstance().isRunning())
		{
			showPanel(player, "La guerra de facciones no está activa.");
			return;
		}
		
		if (player.getFactionId() <= 0)
		{
			showPanel(player, "No tienes una facción.");
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
				showPanel(player, "Checkpoint inválido.");
				return;
			}
			
			final Location loc = locs.get(index);
			player.teleportTo(loc.getX(), loc.getY(), loc.getZ(), 0);
			player.sendMessage("Teletransportado al checkpoint " + (index + 1) + ".");
		}
		catch (NumberFormatException e)
		{
			showPanel(player, "Checkpoint inválido.");
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
			player.sendMessage("Voto inválido.");
		}
	}
	
	private void handleVoteMenu(Player player)
	{
		if (!FactionWarManager.getInstance().isVotingPhaseActive() && !FactionWarManager.getInstance().isRunning())
		{
			showPanel(player, "No hay fase de votación activa.");
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
		sb.append("<html><body><center><font color=LEVEL>Registrador de Guerra</font></center><br>");
		
		if (message != null)
			sb.append("<font color=99FF99>").append(message).append("</font><br1>");
		
		final boolean running = FactionWarManager.getInstance().isRunning();
		final boolean votingPhase = FactionWarManager.getInstance().isVotingPhaseActive();
		final int factionId = player.getFactionId();
		
		if (running || votingPhase)
		{
			sb.append("Estado: <font color=00FF00>").append(running ? "EN GUERRA" : "VOTACIÓN").append("</font><br>");
			sb.append("Good: <font color=0000FF>").append(FactionWarManager.getInstance().getScore(FactionWarConfig.getGoodFactionId())).append("</font> | ");
			sb.append("Evil: <font color=FF0000>").append(FactionWarManager.getInstance().getScore(FactionWarConfig.getEvilFactionId())).append("</font><br>");
			sb.append("Puntuación para ganar: ").append(FactionWarConfig.getScoreToWin()).append("<br>");
			
			// Checkpoints available during war
			if (running)
			{
				final FactionWarCheckpoint cp = FactionWarManager.getInstance().getCheckpoints();
				final List<Location> locs = cp.getLocations();
				if (!locs.isEmpty())
				{
					sb.append("<br><font color=B0C4DE>--- Checkpoints Disponibles ---</font><br>");
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
		}
		else
		{
			sb.append("Estado: <font color=FF0000>DETENIDO</font><br>");
		}
		
		if (factionId > 0)
		{
			final Faction faction = FactionData.getInstance().getFaction(factionId);
			sb.append("Tu facción: <font color=").append(String.format("%06X", faction != null ? faction.getNameColor() : 0xFFFFFF)).append(">").append(faction != null ? faction.getName() : "Desconocida").append("</font><br><br>");
			
			if (running)
			{
				// Voting during war
				if (votingPhase)
				{
					sb.append("<table width=280><tr>");
					sb.append("<td><button value=\"Ver Mapas y Votar\" action=\"bypass -h npc_%objectId%_warVoteMenu\" width=200 height=25 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
					sb.append("</tr></table>");
				}
				
				sb.append("<table width=280><tr>");
				sb.append("<td><button value=\"Ir a mi Base\" action=\"bypass -h npc_%objectId%_warGoToBase\" width=200 height=25 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
				sb.append("</tr></table>");
				
				sb.append("<center><font color=808080>Al morir, \"Volver a la Aldea\" te llevará a la Zona Neutral.<br>Luego puedes volver a tu base desde aquí.</font></center>");
			}
			else if (votingPhase)
			{
				sb.append("<font color=FFCC00>¡Fase de votación activa! Vota por el próximo mapa de batalla.</font><br><br>");
				sb.append("<table width=280><tr>");
				sb.append("<td><button value=\"Ver Mapas y Votar\" action=\"bypass -h npc_%objectId%_warVoteMenu\" width=200 height=25 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
				sb.append("</tr></table>");
			}
			else
			{
				sb.append("<font color=808080>No hay guerra en curso. Espera a que comience la próxima batalla.</font>");
			}
		}
		else
		{
			sb.append("<br><font color=FF4444>No tienes facción. Habla con el Faction Manager en la zona neutral para elegir una.</font>");
		}
		
		sb.append("</body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
}
