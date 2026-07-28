package net.sf.l2j.gameserver.communitybbs.manager;

import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.factionwar.FactionWarManager.FactionWarStats;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.actor.Player;

public class FactionWarBBSManager extends BaseBBSManager
{
	protected FactionWarBBSManager()
	{
	}
	
	@Override
	public void parseCmd(String command, Player player)
	{
		if (command.equals("_bbsfactionwar"))
			showFactionWarBoard(player);
		else if (command.startsWith("_bbsfactionwar;"))
		{
			final StringTokenizer st = new StringTokenizer(command, ";");
			st.nextToken();
			
			final String action = st.hasMoreTokens() ? st.nextToken() : "";
			if (action.equals("refresh"))
				showFactionWarBoard(player);
			else
				showFactionWarBoard(player);
		}
		else
			super.parseCmd(command, player);
	}
	
	private void showFactionWarBoard(Player player)
	{
		final FactionWarManager fwm = FactionWarManager.getInstance();
		final boolean running = fwm.isRunning();
		final int goodId = FactionWarConfig.getGoodFactionId();
		final int evilId = FactionWarConfig.getEvilFactionId();
		
		final StringBuilder sb = new StringBuilder(4096);
		sb.append("<html><body>");
		sb.append("<center><font color=LEVEL size=18>Faction War</font></center><br>");
		
		// Status header
		sb.append("<table width=600 bgcolor=333333>");
		sb.append("<tr><td width=300><font color=00FF00>Estado: ").append(running ? "EN GUERRA" : "DETENIDO").append("</font></td>");
		if (running)
			sb.append("<td width=300 align=right><font color=FF6600>Tiempo restante: ").append(fwm.getRemainingTimeStr()).append("</font></td>");
		sb.append("</tr></table><br>");
		
		// Faction scores
		final int goodScore = fwm.getScore(goodId);
		final int evilScore = fwm.getScore(evilId);
		final int winScore = FactionWarConfig.getScoreToWin();
		
		final Faction goodFaction = FactionData.getInstance().getFaction(goodId);
		final Faction evilFaction = FactionData.getInstance().getFaction(evilId);
		final String goodName = goodFaction != null ? goodFaction.getName() : "Good";
		final String evilName = evilFaction != null ? evilFaction.getName() : "Evil";
		
		sb.append("<table width=600>");
		sb.append("<tr><td width=300 align=center bgcolor=003366><font color=00BFFF size=14>").append(goodName).append("</font></td>");
		sb.append("<td width=300 align=center bgcolor=330000><font color=FF4444 size=14>").append(evilName).append("</font></td></tr>");
		sb.append("<tr><td width=300 align=center><font color=00FF00 size=20>").append(goodScore).append("</font></td>");
		sb.append("<td width=300 align=center><font color=FF0000 size=20>").append(evilScore).append("</font></td></tr>");
		sb.append("<tr><td width=300 align=center><font color=808080>Para ganar: ").append(winScore).append("</font></td>");
		sb.append("<td width=300 align=center><font color=808080>Para ganar: ").append(winScore).append("</font></td></tr>");
		sb.append("</table><br>");
		
		// Winning faction (if war ended)
		final int winningFaction = fwm.getWinningFaction();
		if (!running && winningFaction > 0)
		{
			final Faction winF = FactionData.getInstance().getFaction(winningFaction);
			sb.append("<center><font color=FFD700 size=16>¡GANADOR: ").append(winF != null ? winF.getName() : "Faction " + winningFaction).append("!</font></center><br>");
		}
		else if (!running)
		{
			sb.append("<center><font color=808080>No hay guerra activa. La próxima comenzará automáticamente.</font></center><br>");
		}
		
		// Top 10 leaderboard
		final List<FactionWarStats> top10 = fwm.getTopPlayers(10);
		if (!top10.isEmpty())
		{
			sb.append("<br><font color=LEVEL size=14>Top 10 Jugadores</font><br>");
			sb.append("<table width=600 bgcolor=222222>");
			sb.append("<tr><td width=40><font color=808080>#</font></td>");
			sb.append("<td width=240><font color=808080>Jugador</font></td>");
			sb.append("<td width=120><font color=808080>Facción</font></td>");
			sb.append("<td width=60 align=center><font color=808080>Kills</font></td>");
			sb.append("<td width=60 align=center><font color=808080>Muertes</font></td>");
			sb.append("<td width=80 align=center><font color=808080>Puntos</font></td></tr>");
			sb.append("<img src=\"L2UI.SquareGray\" width=\"600\" height=\"1\">");
			
			for (int i = 0; i < top10.size(); i++)
			{
				final FactionWarStats s = top10.get(i);
				final String rankColor = (i < 3) ? "FFD700" : "FFFFFF";
				final String bgColor = (i % 2 == 0) ? "1a1a2e" : "16213e";
				
				final Faction f = FactionData.getInstance().getFaction(s.factionId);
				final String factionColor = (s.factionId == goodId) ? "00BFFF" : "FF4444";
				final String factionDisplay = f != null ? f.getName() : ("Faction " + s.factionId);
				
				sb.append("<tr bgcolor=").append(bgColor).append(">");
				sb.append("<td width=40><font color=").append(rankColor).append(">").append(i + 1).append("</font></td>");
				sb.append("<td width=240>").append(s.playerName).append("</td>");
				sb.append("<td width=120><font color=").append(factionColor).append(">").append(factionDisplay).append("</font></td>");
				sb.append("<td width=60 align=center>").append(s.kills).append("</td>");
				sb.append("<td width=60 align=center>").append(s.deaths).append("</td>");
				sb.append("<td width=80 align=center><font color=FFD700>").append(s.points).append("</font></td></tr>");
			}
			sb.append("</table>");
		}
		else if (running)
		{
			sb.append("<br><center><font color=808080>Aún no hay estadísticas. ¡Participa en la guerra!</font></center>");
		}
		
		// Reward info
		sb.append("<br><table width=600 bgcolor=111111>");
		sb.append("<tr><td><font color=LEVEL>Recompensas</font></td></tr>");
		sb.append("<tr><td><font color=808080>#1: </font><font color=FFD700>").append(FactionWarConfig.getTop1Reward()).append("x Adena</font></td></tr>");
		sb.append("<tr><td><font color=808080>#2: </font><font color=C0C0C0>").append(FactionWarConfig.getTop2Reward()).append("x Adena</font></td></tr>");
		sb.append("<tr><td><font color=808080>#3: </font><font color=CD7F32>").append(FactionWarConfig.getTop3Reward()).append("x Adena</font></td></tr>");
		sb.append("<tr><td><font color=808080>Facción ganadora: </font><font color=FFD700>").append(FactionWarConfig.getWinningFactionReward()).append("x Adena</font> cada miembro</td></tr>");
		sb.append("</table>");
		
		// Refresh button
		sb.append("<br><center><button value=\"Actualizar\" action=\"_bbsfactionwar;refresh\" width=120 height=22 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></center>");
		
		sb.append("</body></html>");
		
		separateAndSend(sb.toString(), player);
	}
	
	@Override
	protected String getFolder()
	{
		return "";
	}
	
	public static FactionWarBBSManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final FactionWarBBSManager INSTANCE = new FactionWarBBSManager();
	}
}
