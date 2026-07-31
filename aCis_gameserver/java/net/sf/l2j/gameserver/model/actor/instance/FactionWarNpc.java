package net.sf.l2j.gameserver.model.actor.instance;

import java.util.StringTokenizer;

import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.factionwar.FactionWarCheckpoint;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.skills.L2Skill;

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
		final String cmd = st.nextToken();			switch (cmd)
		{
			case "warGoToBase" -> handleGoToBase(player);
			case "warCheckpoints" -> handleCheckpointStatus(player);
			case "fwVote" -> handleVote(player, st);
			case "warVoteMenu" -> handleVoteMenu(player);
			case "bufferBuff" -> handleBufferBuff(player, st);
			case "bufferPage" -> handleBufferPage(player, st);
			default -> super.onBypassFeedback(player, command);
		}
	}
	
	private void handleGoToBase(Player player)
	{
		if (!FactionWarManager.getInstance().isRunning())
		{
			showPanel(player, "La guerra de facciones no esta activa en este momento.");
			return;
		}
		
		if (player.getFactionId() <= 0)
		{
			showPanel(player, "No tienes una faccion. Habla con el Faction Manager primero.");
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
		player.sendMessage("Teletransportado a la base de tu faccion. Lucha por la gloria.");
	}
	
	/**
	 * Shows checkpoint ownership status to the player.
	 * Checkpoints are capturable battlegrounds - no direct teleport.
	 */
	private void handleCheckpointStatus(Player player)
	{
		if (!FactionWarManager.getInstance().isRunning())
		{
			showPanel(player, "La guerra de facciones no esta activa.");
			return;
		}
		
		final FactionWarCheckpoint cp = FactionWarManager.getInstance().getCheckpoints();
		final int count = cp.size();
		
		final StringBuilder sb = new StringBuilder(2048);
		sb.append("<html><body><center><font color=LEVEL>Checkpoints Capturables</font></center><br>");
		sb.append("Los checkpoints son puntos de batalla. Ataca y capturalos para tu faccion.<br><br>");
		sb.append("<table width=280>");
		sb.append("<tr><td width=30><font color=808080>#</font></td><td width=150><font color=808080>Dueno</font></td><td width=100><font color=808080>Estado</font></td></tr>");
		
		for (int i = 0; i < count; i++)
		{
			final int owner = cp.getOwner(i);
			final String ownerName;
			final String ownerColor;
			
			if (owner == FactionWarConfig.getGoodFactionId())
			{
				ownerName = FactionWarConfig.getGoodFactionName();
				ownerColor = "0000FF";
			}
			else if (owner == FactionWarConfig.getEvilFactionId())
			{
				ownerName = FactionWarConfig.getEvilFactionName();
				ownerColor = "FF0000";
			}
			else
			{
				ownerName = "Neutral";
				ownerColor = "C0C0C0";
			}
			
			final String status = (owner == player.getFactionId()) ? "<font color=00FF00>Propio</font>" : "<font color=FF6600>Capturable</font>";
			
			sb.append("<tr><td>").append(i + 1).append("</td>");
			sb.append("<td><font color=\"").append(ownerColor).append("\">").append(ownerName).append("</font></td>");
			sb.append("<td>").append(status).append("</td></tr>");
		}
		
		sb.append("</table><br>");
		sb.append("<button value=\"Volver\" action=\"bypass -h npc_%objectId%_Chat 0\" width=100 height=25 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\">");
		sb.append("</body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setHtml(sb.toString());
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
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
			player.sendMessage("Voto invalido.");
		}
	}
	
	private void handleVoteMenu(Player player)
	{
		if (!FactionWarManager.getInstance().isVotingPhaseActive() && !FactionWarManager.getInstance().isRunning())
		{
			showPanel(player, "No hay fase de votacion activa.");
			return;
		}
		
		FactionWarManager.getInstance().sendVoteHtml(player);
	}
	
	/**
	 * Buffer: Aplica un paquete de buffs o un buff individual.
	 */
	private void handleBufferBuff(Player player, StringTokenizer st)
	{
		if (!st.hasMoreTokens())
			return;
		
		try
		{
			final int buffId = Integer.parseInt(st.nextToken());
			
			// Stop all effects first (clean slate)
			if (buffId >= 1 && buffId <= 3)
				player.stopAllEffects();
			
			switch (buffId)
			{
				case 1 -> applyFighterPackage(player);
				case 2 -> applyMagePackage(player);
				case 3 -> applyFullPackage(player);
				default -> applySingleBuff(player, buffId);
			}
			
			if (buffId >= 1 && buffId <= 3)
				showBufferPanel(player, "<font color=\"00FF00\">Paquete de buffs aplicado con exito.</font>");
		}
		catch (NumberFormatException e)
		{
			showBufferPanel(player, "<font color=\"FF4444\">Error: buff invalido.</font>");
		}
	}
	
	private void applyFighterPackage(Player player)
	{
		// Fighter essentials: Might, Shield, Haste, Focus, Death Whisper, Guidance, Agility, Wind Walk
		applyBuff(player, 1068, 3); // Might
		applyBuff(player, 1040, 3); // Shield
		applyBuff(player, 1086, 3); // Haste
		applyBuff(player, 1078, 3); // Focus
		applyBuff(player, 1242, 3); // Death Whisper
		applyBuff(player, 1240, 3); // Guidance
		applyBuff(player, 1087, 3); // Agility
		applyBuff(player, 1204, 2); // Wind Walk
		applyBuff(player, 1045, 6); // Blessed Body
		applyBuff(player, 1259, 4); // Resist Shock
		applyBuff(player, 1268, 3); // Vampiric Rage
		applyBuff(player, 1243, 6); // Bless Shield
		applyBuff(player, 1388, 3); // Greater Might
		applyBuff(player, 1389, 3); // Greater Shield
		applyBuff(player, 1036, 2); // Magic Barrier
	}
	
	private void applyMagePackage(Player player)
	{
		// Mage essentials: Empower, Acumen, Mental Shield, Magic Barrier, Blessed Soul, etc.
		applyBuff(player, 1059, 3); // Empower
		applyBuff(player, 1085, 3); // Acumen
		applyBuff(player, 1035, 3); // Mental Shield
		applyBuff(player, 1036, 2); // Magic Barrier
		applyBuff(player, 1048, 6); // Blessed Soul
		applyBuff(player, 1040, 3); // Shield
		applyBuff(player, 1045, 6); // Blessed Body
		applyBuff(player, 1204, 2); // Wind Walk
		applyBuff(player, 1068, 3); // Might (for staff hits)
		applyBuff(player, 1087, 3); // Agility
		applyBuff(player, 1303, 3); // Wild Magic
		applyBuff(player, 1388, 3); // Greater Might
		applyBuff(player, 1389, 3); // Greater Shield
		applyBuff(player, 1259, 4); // Resist Shock
	}
	
	private void applyFullPackage(Player player)
	{
		// Everything from both packages
		applyFighterPackage(player);
		applyMagePackage(player);
		
		// Extra buffs
		applyBuff(player, 1062, 2); // Berserker Spirit
		applyBuff(player, 1352, 1); // Elemental Protection
		applyBuff(player, 1353, 1); // Divine Protection
		applyBuff(player, 1354, 1); // Arcane Protection
		applyBuff(player, 1397, 3); // Clarity
		applyBuff(player, 1363, 1); // Chant of Victory
		
		// Songs & Dances
		applyBuff(player, 264, 1);  // Song of Earth
		applyBuff(player, 268, 1);  // Song of Wind
		applyBuff(player, 269, 1);  // Song of Hunter
		applyBuff(player, 304, 1);  // Song of Vitality
		applyBuff(player, 271, 1);  // Dance of Warrior
		applyBuff(player, 274, 1);  // Dance of Fire
		applyBuff(player, 275, 1);  // Dance of Fury
		applyBuff(player, 310, 1);  // Dance of Vampire
	}
	
	private void applySingleBuff(Player player, int skillId)
	{
		switch (skillId)
		{
			// Combat buffs
			case 1068 -> applyBuff(player, 1068, 3); // Might
			case 1040 -> applyBuff(player, 1040, 3); // Shield
			case 1388 -> applyBuff(player, 1388, 3); // Greater Might
			case 1389 -> applyBuff(player, 1389, 3); // Greater Shield
			case 1086 -> applyBuff(player, 1086, 3); // Haste
			case 1240 -> applyBuff(player, 1240, 3); // Guidance
			case 1078 -> applyBuff(player, 1078, 3); // Focus
			case 1242 -> applyBuff(player, 1242, 3); // Death Whisper
			case 1087 -> applyBuff(player, 1087, 3); // Agility
			case 1204 -> applyBuff(player, 1204, 2); // Wind Walk
			case 1045 -> applyBuff(player, 1045, 6); // Blessed Body
			case 1048 -> applyBuff(player, 1048, 6); // Blessed Soul
			case 1259 -> applyBuff(player, 1259, 4); // Resist Shock
			case 1268 -> applyBuff(player, 1268, 3); // Vampiric Rage
			case 1243 -> applyBuff(player, 1243, 6); // Bless Shield
			case 1062 -> applyBuff(player, 1062, 2); // Berserker Spirit
			case 1303 -> applyBuff(player, 1303, 3); // Wild Magic
				// Magic buffs
			case 1059 -> applyBuff(player, 1059, 3); // Empower
			case 1085 -> applyBuff(player, 1085, 3); // Acumen
			case 1035 -> applyBuff(player, 1035, 3); // Mental Shield
			case 1036 -> applyBuff(player, 1036, 2); // Magic Barrier
			case 1397 -> applyBuff(player, 1397, 3); // Clarity
			case 1352 -> applyBuff(player, 1352, 1); // Elemental Protection
			case 1353 -> applyBuff(player, 1353, 1); // Divine Protection
			case 1354 -> applyBuff(player, 1354, 1); // Arcane Protection
			case 1363 -> applyBuff(player, 1363, 1); // Chant of Victory
			// Songs
			case 264 -> applyBuff(player, 264, 1);  // Song of Earth
			case 265 -> applyBuff(player, 265, 1);  // Song of Life
			case 266 -> applyBuff(player, 266, 1);  // Song of Water
			case 267 -> applyBuff(player, 267, 1);  // Song of Warding
			case 268 -> applyBuff(player, 268, 1);  // Song of Wind
			case 269 -> applyBuff(player, 269, 1);  // Song of Hunter
			case 304 -> applyBuff(player, 304, 1);  // Song of Vitality
			case 349 -> applyBuff(player, 349, 1);  // Song of Renewal
			// Dances
			case 271 -> applyBuff(player, 271, 1);  // Dance of Warrior
			case 272 -> applyBuff(player, 272, 1);  // Dance of Inspiration
			case 273 -> applyBuff(player, 273, 1);  // Dance of Mystic
			case 274 -> applyBuff(player, 274, 1);  // Dance of Fire
			case 275 -> applyBuff(player, 275, 1);  // Dance of Fury
			case 276 -> applyBuff(player, 276, 1);  // Dance of Concentration
			case 310 -> applyBuff(player, 310, 1);  // Dance of Vampire
			default ->
			{
				showBufferPanel(player, "<font color=\"FF4444\">Buff no reconocido.</font>");
				return;
			}
		}
	}
	
	private void applyBuff(Player player, int skillId, int level)
	{
		final L2Skill skill = SkillTable.getInstance().getInfo(skillId, level);
		if (skill != null)
			skill.getEffects(player, player);
	}
	
	/**
	 * Buffer: Muestra la pagina de buffs individuales con paginacion.
	 */
	private void handleBufferPage(Player player, StringTokenizer st)
	{
		final int page;
		try
		{
			page = Integer.parseInt(st.nextToken());
		}
		catch (Exception e)
		{
			showBufferPanel(player, null);
			return;
		}
		
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile("data/html/script/factionwar/Buffer/buffer_individual.htm");
		
		final StringBuilder page1 = new StringBuilder();
		final StringBuilder page2 = new StringBuilder();
		
		// Page 1: Combat buffs
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1068\">Might +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1388\">Greater Might +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1040\">Shield +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1389\">Greater Shield +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1086\">Haste +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1240\">Guidance +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1078\">Focus +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1242\">Death Whisper +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1087\">Agility +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1204\">Wind Walk +2</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1045\">Blessed Body +6</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1048\">Blessed Soul +6</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1259\">Resist Shock +4</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1268\">Vampiric Rage +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1243\">Bless Shield +6</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1062\">Berserker Spirit +2</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1303\">Wild Magic +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1059\">Empower +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1085\">Acumen +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1035\">Mental Shield +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1036\">Magic Barrier +2</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1397\">Clarity +3</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1363\">Chant of Victory +1</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1352\">Elemental Protection +1</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1353\">Divine Protection +1</a></td></tr>");
		page1.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 1354\">Arcane Protection +1</a></td></tr>");
		
		// Page 2: Songs & Dances
		page2.append("<tr><td width=120>Songs:</td><td width=100></td></tr>");
		page2.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 264\">Song of Earth</a></td><td><a action=\"bypass -h npc_%objectId%_bufferBuff 265\">Song of Life</a></td></tr>");
		page2.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 266\">Song of Water</a></td><td><a action=\"bypass -h npc_%objectId%_bufferBuff 267\">Song of Warding</a></td></tr>");
		page2.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 268\">Song of Wind</a></td><td><a action=\"bypass -h npc_%objectId%_bufferBuff 269\">Song of Hunter</a></td></tr>");
		page2.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 304\">Song of Vitality</a></td><td><a action=\"bypass -h npc_%objectId%_bufferBuff 349\">Song of Renewal</a></td></tr>");
		page2.append("<tr><td>&nbsp;</td></tr>");
		page2.append("<tr><td width=120>Dances:</td><td width=100></td></tr>");
		page2.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 271\">Dance of Warrior</a></td><td><a action=\"bypass -h npc_%objectId%_bufferBuff 272\">Dance of Inspiration</a></td></tr>");
		page2.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 273\">Dance of Mystic</a></td><td><a action=\"bypass -h npc_%objectId%_bufferBuff 274\">Dance of Fire</a></td></tr>");
		page2.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 275\">Dance of Fury</a></td><td><a action=\"bypass -h npc_%objectId%_bufferBuff 276\">Dance of Concentration</a></td></tr>");
		page2.append("<tr><td><a action=\"bypass -h npc_%objectId%_bufferBuff 310\">Dance of Vampire</a></td><td></td></tr>");
		
		if (page == 1)
		{
			html.replace("%PAGE1%", page1.toString());
			html.replace("%PAGE2%", "");
			html.replace("%PREV%", "2");
			html.replace("%NEXT%", "2");
		}
		else
		{
			html.replace("%PAGE1%", "");
			html.replace("%PAGE2%", page2.toString());
			html.replace("%PREV%", "1");
			html.replace("%NEXT%", "1");
		}
		
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
	}
	
	/**
	 * Buffer: Muestra el panel principal del buffer.
	 */
	private void showBufferPanel(Player player, String message)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setFile("data/html/script/factionwar/Buffer/buffer_main.htm");
		if (message != null)
			html.replace("%MESSAGE%", message);
		else
			html.replace("%MESSAGE%", "");
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		if (npcId == 90005)
			return "data/html/script/factionwar/Buffer/buffer_main.htm";
		return "data/html/mods/factionwar/" + npcId + ".htm";
	}
	
	@Override
	public void showChatWindow(Player player, int val)
	{
		showPanel(player, null);
	}
	
	public void showPanel(Player player, String message)
	{
		final boolean running = FactionWarManager.getInstance().isRunning();
		final boolean votingPhase = FactionWarManager.getInstance().isVotingPhaseActive();
		final int factionId = player.getFactionId();
		
		final StringBuilder sb = new StringBuilder(8192);
		sb.append("<html><body>");
		
		// Header
		sb.append("<center><table width=\"270\" bgcolor=\"000000\"><tr>");
		sb.append("<td width=\"270\" height=\"32\" align=\"center\"><font color=\"F0D060\" size=\"16\">Registrador de Guerra</font></td>");
		sb.append("</tr></table></center>");
		sb.append("<img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\"><br>");
		
		// Message
		if (message != null && !message.isEmpty())
		{
			sb.append("<center><table width=\"260\" bgcolor=\"1A1A2E\"><tr>");
			sb.append("<td align=\"center\"><font color=\"99FF99\">").append(message).append("</font></td>");
			sb.append("</tr></table></center><br>");
		}
		
		// War Status Banner
		final String statusColor;
		final String statusText;
		if (running)
		{
			statusColor = "00FF00";
			statusText = "EN GUERRA";
		}
		else if (votingPhase)
		{
			statusColor = "FFCC00";
			statusText = "VOTACION ACTIVA";
		}
		else
		{
			statusColor = "FF4444";
			statusText = "DETENIDO";
		}
		
		sb.append("<table width=\"270\" cellpadding=\"2\" cellspacing=\"2\">");
		sb.append("<tr><td width=\"100\">Estado:</td><td align=\"center\" bgcolor=\"" + (running ? "003300" : votingPhase ? "332200" : "330000") + "\"><font color=\"" + statusColor + "\">" + statusText + "</font></td></tr>");
		
		// Time remaining if running
		if (running)
		{
			final int goodScore = FactionWarManager.getInstance().getScore(FactionWarConfig.getGoodFactionId());
			final int evilScore = FactionWarManager.getInstance().getScore(FactionWarConfig.getEvilFactionId());
			final String timeStr = FactionWarManager.getInstance().getRemainingTimeStr();
			
			sb.append("<tr><td>Tiempo restante:</td><td align=\"center\"><font color=\"FFD700\">" + timeStr + "</font></td></tr>");
			sb.append("<tr><td>Bandera actual:</td><td align=\"center\"><font color=\"" + (FactionWarManager.getInstance().getWinningFaction() == FactionWarConfig.getGoodFactionId() ? "00BFFF" : "FF4444") + "\">" + (FactionWarManager.getInstance().getWinningFaction() > 0 ? getFactionNameShort(FactionWarManager.getInstance().getWinningFaction()) : "Neutral") + "</font></td></tr>");
			sb.append("</table><br>");
			
			// Score table
			sb.append("<table width=\"270\" cellpadding=\"4\" cellspacing=\"1\">");
			sb.append("<tr>");
			sb.append("<td width=\"135\" align=\"center\" bgcolor=\"001133\"><font color=\"00BFFF\" size=\"14\"><b>" + FactionWarConfig.getGoodFactionName() + "</b></font><br1><font color=\"00BFFF\" size=\"18\">" + goodScore + "</font></td>");
			sb.append("<td width=\"135\" align=\"center\" bgcolor=\"330000\"><font color=\"FF5555\" size=\"14\"><b>" + FactionWarConfig.getEvilFactionName() + "</b></font><br1><font color=\"FF5555\" size=\"18\">" + evilScore + "</font></td>");
			sb.append("</tr></table><br>");
			
			// Checkpoint status
			final FactionWarCheckpoint cp = FactionWarManager.getInstance().getCheckpoints();
			if (cp.size() > 0)
			{
				int goodCp = 0, evilCp = 0, neutralCp = 0;
				for (int i = 0; i < cp.size(); i++)
				{
					final int owner = cp.getOwner(i);
					if (owner == FactionWarConfig.getGoodFactionId()) goodCp++;
					else if (owner == FactionWarConfig.getEvilFactionId()) evilCp++;
					else neutralCp++;
				}
				
				sb.append("<table width=\"270\" cellpadding=\"2\" cellspacing=\"0\">");
				sb.append("<tr><td colspan=\"3\" align=\"center\"><font color=\"B0C4DE\">Puestos de Control</font></td></tr>");
				sb.append("<tr align=\"center\">");
				sb.append("<td width=\"90\" bgcolor=\"001133\"><font color=\"00BFFF\">" + goodCp + "</font></td>");
				sb.append("<td width=\"90\" bgcolor=\"222222\"><font color=\"C0C0C0\">" + neutralCp + "</font></td>");
				sb.append("<td width=\"90\" bgcolor=\"330000\"><font color=\"FF5555\">" + evilCp + "</font></td>");
				sb.append("</tr>");
				sb.append("<tr align=\"center\">");
				sb.append("<td><font color=\"808080\" size=\"10\">" + FactionWarConfig.getGoodFactionName() + "</font></td>");
				sb.append("<td><font color=\"808080\" size=\"10\">Neutral</font></td>");
				sb.append("<td><font color=\"808080\" size=\"10\">" + FactionWarConfig.getEvilFactionName() + "</font></td>");
				sb.append("</tr></table><br>");
				
				sb.append("<center><button value=\"Detalle de CPs\" action=\"bypass -h npc_%objectId%_warCheckpoints\" width=\"220\" height=\"22\" back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></center><br>");
			}
		}
		else if (votingPhase)
		{
			sb.append("</table><br>");
			sb.append("<center><font color=\"FFCC00\">Fase de Votacion Activa.</font><br1>");
			sb.append("<font color=\"C0C0C0\" size=\"11\">Vota por el mapa de batalla usando el boton de abajo.</font><br><br></center>");
		}
		else
		{
			sb.append("</table><br>");
			sb.append("<center><font color=\"FF4444\">La guerra de facciones no esta activa.</font><br1>");
			sb.append("<font color=\"808080\" size=\"11\">Espera a que comience la proxima batalla.</font><br><br></center>");
		}
		
		// Faction info & actions
		if (factionId > 0)
		{
			final Faction faction = net.sf.l2j.gameserver.data.xml.FactionData.getInstance().getFaction(factionId);
			final String factionColor = Integer.toHexString(faction != null ? faction.getNameColor() : 0xFFFFFF);
			final String factionName = faction != null ? faction.getName() : "Desconocida";
			
			sb.append("<table width=\"270\" cellpadding=\"2\">");
			sb.append("<tr><td width=\"270\" align=\"center\" bgcolor=\"111111\"><font color=\"#" + factionColor + "\"><b>" + factionName.toUpperCase() + "</b></font></td></tr>");
			sb.append("</table><br>");
			
			if (running)
			{
				sb.append("<center>");
				sb.append("<button value=\"Ir a mi Base\" action=\"bypass -h npc_%objectId%_warGoToBase\" width=\"220\" height=\"24\" back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"><br>");
				
				if (votingPhase)
				{
					sb.append("<button value=\"Ver Mapas y Votar\" action=\"bypass -h npc_%objectId%_warVoteMenu\" width=\"220\" height=\"24\" back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"><br>");
				}
				
				sb.append("<br><font color=\"808080\" size=\"10\">Al morir: \"Volver a la Aldea\" a Zona Neutral.<br>Luego regresa a tu base desde aqui.</font>");
				sb.append("</center>");
			}
			else if (votingPhase)
			{
				sb.append("<center>");
				sb.append("<button value=\"Ver Mapas y Votar\" action=\"bypass -h npc_%objectId%_warVoteMenu\" width=\"220\" height=\"24\" back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\">");
				sb.append("</center>");
			}
		}
		else
		{
			sb.append("<br><center><table width=\"260\" bgcolor=\"330000\"><tr><td align=\"center\">");
			sb.append("<font color=\"FF6666\">No tienes faccion.</font><br1>");
			sb.append("<font color=\"C0C0C0\" size=\"11\">Habla con el Faction Manager en la zona neutral para elegir una.</font>");
			sb.append("</td></tr></table></center>");
		}
		
		sb.append("</body></html>");
		
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		html.setHtml(sb.toString());
		html.replace("%objectId%", String.valueOf(getObjectId()));
		player.sendPacket(html);
	}
	
	/**
	 * Returns a short display name for the given faction.
	 */
	private String getFactionNameShort(int factionId)
	{
		if (factionId == FactionWarConfig.getGoodFactionId())
			return FactionWarConfig.getGoodFactionName();
		if (factionId == FactionWarConfig.getEvilFactionId())
			return FactionWarConfig.getEvilFactionName();
		return "Neutral";
	}
}
