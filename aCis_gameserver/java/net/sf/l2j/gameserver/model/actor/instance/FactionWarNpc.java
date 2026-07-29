package net.sf.l2j.gameserver.model.actor.instance;

import java.util.List;
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
		final String cmd = st.nextToken();
		
		switch (cmd)
		{
			case "warGoToBase" -> handleGoToBase(player);
			case "warCheckpoint" -> handleCheckpoint(player, st);
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
				showBufferPanel(player, "<font color=\"00FF00\">¡Paquete de buffs aplicado con éxito!</font>");
		}
		catch (NumberFormatException e)
		{
			showBufferPanel(player, "<font color=\"FF4444\">Error: buff inválido.</font>");
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
	 * Buffer: Muestra la página de buffs individuales con paginación.
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
