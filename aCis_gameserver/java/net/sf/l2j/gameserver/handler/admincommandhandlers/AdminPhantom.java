package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.Faction;
import net.sf.l2j.gameserver.enums.actors.OperateType;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.phantom.PhantomAI;
import net.sf.l2j.gameserver.phantom.PhantomConfig;
import net.sf.l2j.gameserver.phantom.PhantomEngine;
import net.sf.l2j.gameserver.phantom.PhantomFactory;
import net.sf.l2j.gameserver.phantom.PhantomState;

public class AdminPhantom implements IAdminCommandHandler
{
	private static final int PAGE_SIZE = 4;
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_phantom",
		"admin_phantom_reload",
		"admin_phantom_start",
		"admin_phantom_load",
		"admin_phantom_create",
		"admin_phantom_ai",
		"admin_phantom_stop",
		"admin_phantom_kill",
		"admin_phantom_delete",
		"admin_phantom_bring",
		"admin_phantom_bringfaction",
		"admin_phantom_resurrect",
		"admin_phantom_deleteall",
		"admin_phantom_online",
		"admin_phantom_status",
		"admin_phantom_radar",
		"admin_phantom_faction",
		"admin_phantom_factions",
		"admin_phantom_party",
		"admin_phantom_store",
		"admin_phantom_factionall"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		final StringTokenizer st = new StringTokenizer(command);
		String cmd = st.nextToken();
		
		if (cmd.equals("admin_phantom") && st.hasMoreTokens())
			cmd = "admin_phantom_" + st.nextToken().toLowerCase();
		
		if (cmd.equals("admin_phantom") || cmd.equals("admin_phantom_status"))
		{
			showPanel(player, null);
			return;
		}
		
		if (cmd.equals("admin_phantom_online"))
		{
			showOnline(player, parsePage(st), 0, null);
			return;
		}
		
		if (cmd.equals("admin_phantom_factions"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}
			
			final int factionId = parseCount(st, 0);
			final int page = parsePage(st);
			showOnline(player, page, factionId, null);
			return;
		}
		
		if (cmd.equals("admin_phantom_reload"))
		{
			PhantomConfig.load();
			showPanel(player, "Config reloaded. IDs: " + PhantomConfig.getPhantomIds().size());
			return;
		}
		
		if (cmd.equals("admin_phantom_start"))
		{
			PhantomEngine.startConfigured(player);
			showPanel(player, "Loading phantoms in background...");
			return;
		}
		
		if (cmd.equals("admin_phantom_create"))
		{
			final int count = parseCount(st, 1);
			int loaded = 0;
			for (Player phantom : PhantomFactory.create(count))
			{
				if (PhantomEngine.load(phantom.getObjectId(), player, true) != null)
					loaded++;
			}
			showPanel(player, "Created NEW phantoms: " + loaded + ".");
			return;
		}
		
		if (cmd.equals("admin_phantom_bring"))
		{
			showPanel(player, "Brought phantoms: " + PhantomEngine.bringAll(player) + ".");
			return;
		}
		
		if (cmd.equals("admin_phantom_bringfaction"))
		{
			final int factionId = parseCount(st, 0);
			final String label = (factionId > 0) ? ("Faction " + factionId) : "all";
			showPanel(player, "Brought " + label + " phantoms: " + PhantomEngine.bringFaction(player, factionId) + ".");
			return;
		}
		
		if (cmd.equals("admin_phantom_resurrect"))
		{
			showPanel(player, "Resurrected phantoms: " + PhantomEngine.resurrectAll() + ".");
			return;
		}
		
		if (cmd.equals("admin_phantom_deleteall"))
		{
			final int deleted = PhantomEngine.deleteAll();
			showPanel(player, "Deleted ALL phantoms: " + deleted + ".");
			return;
		}
		
		if (cmd.equals("admin_phantom_ai"))
		{
			if (!st.hasMoreTokens())
			{
				showPanel(player, "Usage: AI on/off/home");
				return;
			}
			
			final String mode = st.nextToken().toLowerCase();
			switch (mode)
			{
				case "on" -> showPanel(player, "AI started for: " + PhantomEngine.startAi());
				case "off" -> showPanel(player, "AI paused for: " + PhantomEngine.stopAi());
				case "home" -> showPanel(player, "AI home updated for: " + PhantomEngine.setHomes());
				default -> showPanel(player, "Usage: AI on/off/home");
			}
			return;
		}
		
		if (cmd.equals("admin_phantom_radar"))
		{
			markRadar(player, st.hasMoreTokens() ? st.nextToken().toLowerCase() : "phantoms");
			return;
		}
		
		if (cmd.equals("admin_phantom_load"))
		{
			if (!st.hasMoreTokens())
			{
				showPanel(player, "Usage: load objectId [here|db]");
				return;
			}
			
			final int objectId = parseObjectId(st, player, 0, 0);
			if (objectId <= 0)
				return;
			
			boolean spawnHere = true;
			if (st.hasMoreTokens())
				spawnHere = !st.nextToken().equalsIgnoreCase("db");
			
			final Player phantom = PhantomEngine.load(objectId, player, spawnHere);
			showPanel(player, (phantom == null) ? "Couldn't load phantom " + objectId + "." : "Loaded phantom " + phantom.getName() + ".");
			return;
		}
		
		if (cmd.equals("admin_phantom_kill"))
		{
			final int page = parsePage(st);
			final int filterFaction = parseCount(st, 0);
			final int objectId = parseObjectId(st, player, page, filterFaction);
			if (objectId > 0)
				showOnline(player, page, filterFaction, PhantomEngine.kill(objectId) ? "Killed phantom " + objectId + "." : "Phantom " + objectId + " isn't active.");
			return;
		}
		
		if (cmd.equals("admin_phantom_delete"))
		{
			final int page = parsePage(st);
			final int filterFaction = parseCount(st, 0);
			final int objectId = parseObjectId(st, player, page, filterFaction);
			if (objectId > 0)
				showOnline(player, page, filterFaction, PhantomEngine.deleteConfigured(objectId) ? "Deleted phantom " + objectId + "." : "Phantom " + objectId + " not found.");
			return;
		}
		
		if (cmd.equals("admin_phantom_stop"))
		{
			if (st.hasMoreTokens())
			{
				final int page = parsePage(st);
				final int filterFaction = parseCount(st, 0);
				final int objectId = parseObjectId(st, player, page, filterFaction);
				if (objectId > 0)
					showOnline(player, page, filterFaction, PhantomEngine.stop(objectId) ? "Stopped phantom " + objectId + "." : "Phantom " + objectId + " isn't active.");
				return;
			}
			
			showPanel(player, "Stopped phantoms: " + PhantomEngine.stopAll() + ".");
		}
		
		if (cmd.equals("admin_phantom_faction"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}
			
			final int page = parsePage(st);
			final int filterFaction = parseCount(st, 0);
			final int objectId = parseObjectId(st, player, page, filterFaction);
			if (objectId <= 0)
				return;
			
			if (!st.hasMoreTokens())
			{
				showOnline(player, page, filterFaction, "Usage: faction " + page + " " + filterFaction + " " + objectId + " <factionId|0>");
				return;
			}
			
			final int factionId;
			try
			{
				factionId = Integer.parseInt(st.nextToken());
			}
			catch (NumberFormatException e)
			{
				showOnline(player, page, filterFaction, "Invalid factionId.");
				return;
			}
			
			final Player phantom = PhantomEngine.getActivePhantom(objectId);
			if (phantom == null)
			{
				showOnline(player, page, filterFaction, "Phantom " + objectId + " is not active.");
				return;
			}
			
			if (factionId == 0)
			{
				phantom.setFactionId(0);
				FactionData.getInstance().removeData(phantom);
				showOnline(player, page, filterFaction, phantom.getName() + " removed from faction.");
			}
			else
			{
				final Faction faction = FactionData.getInstance().getFaction(factionId);
				if (faction == null)
				{
					showOnline(player, page, filterFaction, "Faction " + factionId + " not found in faction.xml.");
					return;
				}
				phantom.setFactionId(factionId);
				FactionData.getInstance().storeData(phantom);
				showOnline(player, page, filterFaction, phantom.getName() + " -> " + faction.getName() + ".");
			}
			return;
		}
		
		if (cmd.equals("admin_phantom_party"))
		{
			PhantomAI.ensureFactionParties();
			final java.util.Map<Integer, Integer> partyCount = new java.util.HashMap<>();
			int partied = 0;
			for (Player p : PhantomEngine.getActivePhantoms())
			{
				if (p != null && p.getParty() != null)
				{
					final int leaderId = p.getParty().getLeaderObjectId();
					partyCount.merge(leaderId, 1, Integer::sum);
					partied++;
				}
			}
			showPanel(player, "Parties formed. " + partied + " phantoms in " + partyCount.size() + " parties.");
			return;
		}
		
		if (cmd.equals("admin_phantom_store"))
		{
			int opened = 0;
			int closed = 0;
			for (Player p : PhantomEngine.getActivePhantoms())
			{
				if (p == null || p.isDead())
					continue;
				
				if (p.getOperateType() == OperateType.NONE)
				{
					p.sitDown();
					p.setOperateType(OperateType.SELL);
					p.broadcastUserInfo();
					opened++;
				}
				else
				{
					p.setOperateType(OperateType.NONE);
					p.standUp();
					p.broadcastUserInfo();
					closed++;
				}
			}
			showPanel(player, "Store toggle: " + opened + " opened, " + closed + " closed.");
			return;
		}
		
		if (cmd.equals("admin_phantom_factionall"))
		{
			if (!FactionWarConfig.isEnabled())
			{
				showPanel(player, "Faction system is disabled.");
				return;
			}
			
			final int factionId;
			try
			{
				factionId = Integer.parseInt(st.nextToken());
			}
			catch (Exception e)
			{
				showPanel(player, "Usage: factionall <factionId> - assigns ALL phantoms without faction to the given faction.");
				return;
			}
			
			final Faction faction = FactionData.getInstance().getFaction(factionId);
			if (faction == null)
			{
				showPanel(player, "Faction " + factionId + " not found.");
				return;
			}
			
			int assigned = 0;
			for (Player p : PhantomEngine.getActivePhantoms())
			{
				if (p != null && p.getFactionId() <= 0)
				{
					p.setFactionId(factionId);
					FactionData.getInstance().storeData(p);
					assigned++;
				}
			}
			showPanel(player, "Assigned " + assigned + " phantoms to " + faction.getName() + ".");
			return;
		}
	}
	
	private static List<Player> filterByFaction(List<Player> phantoms, int factionId)
	{
		if (factionId <= 0)
			return phantoms;
		
		final List<Player> filtered = new ArrayList<>();
		for (Player p : phantoms)
		{
			if (p != null && p.getFactionId() == factionId)
				filtered.add(p);
		}
		return filtered;
	}
	
	private static int parsePage(StringTokenizer st)
	{
		if (!st.hasMoreTokens())
			return 0;
		
		try
		{
			return Math.max(0, Integer.parseInt(st.nextToken()));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
	
	private static int parseObjectId(StringTokenizer st, Player player, int page, int filterFaction)
	{
		if (!st.hasMoreTokens())
		{
			showOnline(player, page, filterFaction, "Missing objectId.");
			return 0;
		}
		
		try
		{
			return Integer.parseInt(st.nextToken());
		}
		catch (NumberFormatException e)
		{
			showOnline(player, page, filterFaction, "Invalid objectId.");
			return 0;
		}
	}
	
	private static int parseCount(StringTokenizer st, int defaultValue)
	{
		if (!st.hasMoreTokens())
			return defaultValue;
		
		try
		{
			return Math.max(0, Math.min(50, Integer.parseInt(st.nextToken())));
		}
		catch (NumberFormatException e)
		{
			return defaultValue;
		}
	}
	
	private static void markRadar(Player player, String mode)
	{
		if (mode.equals("clear"))
		{
			player.getRadarList().removeAllMarkers();
			showPanel(player, "Radar cleared.");
			return;
		}
		
		player.getRadarList().removeAllMarkers();
		int markers = 0;
		for (Player phantom : PhantomEngine.getActivePhantoms())
		{
			if (phantom == null)
				continue;
			
			final Location loc = mode.equals("targets") ? PhantomAI.getLastTarget(phantom) : phantom.getPosition();
			if (loc != null)
			{
				player.getRadarList().addMarker(loc);
				markers++;
			}
		}
		showPanel(player, "Radar markers: " + markers + " (" + mode + ").");
	}
	
	private static void showPanel(Player player, String message)
	{
		final List<Player> allPhantoms = PhantomEngine.getActivePhantomsSorted();
		final boolean warEnabled = FactionWarConfig.isEnabled();
		final boolean warRunning = warEnabled && FactionWarManager.getInstance().isRunning();
		final int warGoodScore = warEnabled ? FactionWarManager.getInstance().getScore(FactionWarConfig.getGoodFactionId()) : 0;
		final int warEvilScore = warEnabled ? FactionWarManager.getInstance().getScore(FactionWarConfig.getEvilFactionId()) : 0;
		final StringBuilder sb = new StringBuilder(8192);
		
		// Header
		sb.append("<html><body>");
		sb.append("<center><table width=330><tr><td align=center bgcolor=333333><font color=LEVEL>Phantom Manager</font></td></tr></table></center>");
		if (message != null)
			sb.append("<center><font color=99FF99>").append(message).append("</font></center>");
		sb.append("<br>");
		
		// Faction War status band
		if (warEnabled)
		{
			sb.append("<table width=330><tr><td align=center bgcolor=").append(warRunning ? "003300" : "330000").append("><font color=").append(warRunning ? "00FF00" : "FF0000").append(">").append(warRunning ? "FACTION WAR - EN CURSO" : "FACTION WAR - DETENIDA").append("</font></td></tr></table>");
			sb.append("<table width=330><tr>");
			sb.append("<td width=110 align=center><font color=0000FF>").append(htmlSafe(FactionWarConfig.getGoodFactionName())).append(": ").append(warGoodScore).append("</font></td>");
			sb.append("<td width=110 align=center>");
			if (!warRunning)
				sb.append("<a action=\"bypass -h admin_factionwar start\">Iniciar</a>");
			else
				sb.append("<a action=\"bypass -h admin_factionwar stop\">Detener</a>");
			sb.append(" | <a action=\"bypass -h admin_factionwar reload\">Recargar</a>");
			sb.append("</td>");
			sb.append("<td width=110 align=center><font color=FF0000>").append(htmlSafe(FactionWarConfig.getEvilFactionName())).append(": ").append(warEvilScore).append("</font></td>");
			sb.append("</tr></table>");
		}
		else
			sb.append("<center><font color=808080>Sistema de facciones desactivado</font></center>");
		
		sb.append("<br>");
		
		// Section: Estado
		sectionTitle(sb, "Estado");
		sb.append("<table width=330>");
		infoRow(sb, "Phantoms activos", allPhantoms.size());
		infoRow(sb, "IDs configurados", PhantomConfig.getPhantomIds().size());
		sb.append("<tr><td width=140>IA</td><td width=190><font color=").append(PhantomAI.isAiPaused() ? "FF0000" : "00FF00").append(">").append(PhantomAI.isAiPaused() ? "OFF (pausada)" : "ON").append("</font></td></tr>");
		infoRow(sb, "Tick IA", PhantomConfig.aiTickMs() + " ms");
		infoRow(sb, "Zona de nivel", PhantomConfig.levelZoneProfile());
		if (warEnabled)
			infoRow(sb, "Participan en guerra", PhantomConfig.warParticipationChance() + "%, max " + PhantomConfig.warMaxPerFaction() + "/fac, rango " + PhantomConfig.warNearbyOnlyRange());
		sb.append("</table>");
		
		// Section: Configuracion IA
		sectionTitle(sb, "Configuracion IA");
		sb.append("<table width=330>");
		infoRow(sb, "Skills avanzados", siNo(PhantomConfig.advancedSkillUsage()));
		infoRow(sb, "Mago a distancia", siNo(PhantomConfig.mageNeverMelee()));
		infoRow(sb, "Hierbas", siNo(PhantomConfig.autoLootHerbs()));
		infoRow(sb, "PvP", siNo(PhantomConfig.pvpEnabled()));
		infoRow(sb, "PK", siNo(PhantomConfig.pkEnabled()));
		infoRow(sb, "Chat IA", siNo(PhantomConfig.phantomChatEnabled()));
		sb.append("</table>");
		
		// Section: Acciones
		sectionTitle(sb, "Acciones");
		buttonRow2(sb, "Cargar config", "admin_phantom start", "Crear 1", "admin_phantom create 1");
		buttonRow2(sb, "Crear 10", "admin_phantom create 10", "IA On", "admin_phantom ai on");
		buttonRow2(sb, "IA Off", "admin_phantom ai off", "Fijar Home", "admin_phantom ai home");
		buttonRow2(sb, "Traer todos", "admin_phantom bring", "Radar", "admin_phantom radar phantoms");
		buttonRow2(sb, "Recargar", "admin_phantom reload", "Revivir", "admin_phantom resurrect");
		
		// Faction bring buttons (auto-detect available factions)
		if (warEnabled && FactionData.getInstance().getFactionCount() > 0)
		{
			final int[] factionIds = FactionData.getInstance().getFactionIds();
			int col = 0;
			for (int fid : factionIds)
			{
				if (col % 3 == 0)
					sb.append(col == 0 ? "<table width=300><tr>" : "</tr></table><table width=300><tr>");
				
				final Faction f = FactionData.getInstance().getFaction(fid);
				final String label = (f != null) ? f.getName() : ("F" + fid);
				final String shortLabel = (label.length() > 6) ? label.substring(0, 6).trim() : label;
				button(sb, "Traer " + htmlSafe(shortLabel), "admin_phantom bringfaction " + fid, 100);
				col++;
			}
			while (col % 3 != 0)
			{
				sb.append("<td width=100></td>");
				col++;
			}
			sb.append("</tr></table>");
		}
		else
		{
			buttonRowSkipEmpty(sb, "Traer todos", "admin_phantom bringfaction 0", "", "", "", "");
		}
		
		buttonRow2(sb, "Parar todo", "admin_phantom stop", "Limpiar radar", "admin_phantom radar clear");
		buttonRow2(sb, "Lista online", "admin_phantom online 0", "Borrar todos", "admin_phantom deleteall");
		buttonRow2(sb, "Formar party", "admin_phantom party", "Tiendas on/off", "admin_phantom store");
		
		// Asignar faccion a todos los phantoms sin faccion (factionall)
		if (warEnabled && FactionData.getInstance().getFactionCount() > 0)
		{
			final int[] factionIds = FactionData.getInstance().getFactionIds();
			int col = 0;
			for (int fid : factionIds)
			{
				if (col % 3 == 0)
					sb.append(col == 0 ? "<table width=300><tr>" : "</tr></table><table width=300><tr>");
				
				final Faction f = FactionData.getInstance().getFaction(fid);
				final String label = (f != null) ? f.getName() : ("F" + fid);
				final String shortLabel = (label.length() > 6) ? label.substring(0, 6).trim() : label;
				button(sb, "Todos->" + htmlSafe(shortLabel), "admin_phantom factionall " + fid, 100);
				col++;
			}
			while (col % 3 != 0)
			{
				sb.append("<td width=100></td>");
				col++;
			}
			sb.append("</tr></table>");
		}
		else
		{
			buttonRowSkipEmpty(sb, "Tiendas on/off", "admin_phantom store", "", "", "", "");
		}
		
		if (warEnabled)
			buttonRow2(sb, "Panel Guerra", "admin_factionwar", warRunning ? "Detener guerra" : "Iniciar guerra", warRunning ? "admin_factionwar stop" : "admin_factionwar start");
		
		if (!allPhantoms.isEmpty())
		{
			sectionTitle(sb, "Resumen por Faccion");
			
			final Map<Integer, List<Player>> groups = groupByFaction(allPhantoms);
			final List<Integer> sortedKeys = new ArrayList<>(groups.keySet().stream().sorted().toList());
			
			sb.append("<table width=330>");
			for (int fKey : sortedKeys)
			{
				final List<Player> list = groups.get(fKey);
				final Faction faction = (warEnabled && fKey > 0) ? FactionData.getInstance().getFaction(fKey) : null;
				
				sb.append("<tr><td width=210><font color=FFD700>");
				if (fKey == 0)
					sb.append("Sin faccion");
				else
					sb.append("Faccion ").append(fKey).append(faction != null ? " (" + htmlSafe(faction.getName()) + ")" : "");
				sb.append("</font></td><td width=40 align=center><font color=00FF00>").append(list.size()).append("</font></td><td width=80 align=center><a action=\"bypass -h admin_phantom factions ").append(fKey).append(" 0\">Ver</a></td></tr>");
			}
			sb.append("</table>");
			
			// Good vs Evil proportion bar
			if (warEnabled)
			{
				final int total = warGoodScore + warEvilScore;
				
				int goodW = (total > 0) ? Math.max(1, (int) (330.0 * warGoodScore / total)) : 165;
				int evilW = 330 - goodW;
				if (total > 0 && evilW < 1)
				{
					evilW = 1;
					goodW = 329;
				}
				
				sb.append("<br><table width=330><tr><td width=165 align=center><font color=0000FF>").append(htmlSafe(shortText(FactionWarConfig.getGoodFactionName(), 14))).append("</font></td><td width=165 align=center><font color=FF0000>").append(htmlSafe(shortText(FactionWarConfig.getEvilFactionName(), 14))).append("</font></td></tr></table>");
				sb.append("<table width=330><tr>");
				sb.append("<td width=").append(goodW).append(" bgcolor=0000FF align=center><font color=FFFFFF>").append(warGoodScore).append("</font></td>");
				sb.append("<td width=").append(evilW).append(" bgcolor=FF0000 align=center><font color=FFFFFF>").append(warEvilScore).append("</font></td>");
				sb.append("</tr></table>");
			}
			
			sb.append("<br><center><font color=808080>Usa 'Lista online' para gestionar cada phantom.</font></center>");
		}
		else
		{
			sb.append("<br><center><font color=808080>No hay phantoms activos.</font></center>");
		}
		
		sb.append("</body></html>");
		sendHtml(player, sb);
	}
	
	private static Map<Integer, List<Player>> groupByFaction(List<Player> phantoms)
	{
		final Map<Integer, List<Player>> groups = new HashMap<>();
		for (Player p : phantoms)
		{
			if (p == null)
				continue;
			final int fId = p.getFactionId();
			groups.computeIfAbsent(fId, k -> new ArrayList<>()).add(p);
		}
		return groups;
	}
	
	private static void showOnline(Player player, int page, int filterFaction, String message)
	{
		List<Player> phantoms = PhantomEngine.getActivePhantomsSorted();
		final String title;
		if (filterFaction > 0)
		{
			phantoms = filterByFaction(phantoms, filterFaction);
			final Faction f = FactionData.getInstance().getFaction(filterFaction);
			title = "Phantoms - Faccion " + filterFaction + (f != null ? " (" + htmlSafe(f.getName()) + ")" : "");
		}
		else
		{
			title = "Phantoms en linea";
		}
		
		final int maxPage = phantoms.isEmpty() ? 0 : (phantoms.size() - 1) / PAGE_SIZE;
		page = Math.max(0, Math.min(page, maxPage));
		
		final StringBuilder sb = new StringBuilder(8192);
		sb.append("<html><body><center><font color=LEVEL>").append(title).append("</font></center><br>");
		if (message != null)
			sb.append("<font color=99FF99>").append(message).append("</font><br1>");
		
		sb.append("Total: <font color=LEVEL>").append(phantoms.size()).append("</font> | Pagina: ").append(page + 1).append("/").append(maxPage + 1).append("<br1>");
		sb.append("<table width=310>");
		sb.append("<tr><td width=70>Nombre</td><td width=16>Nv</td><td width=20>Fac</td><td width=26>Modo</td><td width=44>Accion</td><td width=22>K</td><td width=30>S</td><td width=22>X</td><td width=30>Fac</td></tr>");
		
		final int start = page * PAGE_SIZE;
		final int end = Math.min(start + PAGE_SIZE, phantoms.size());
		for (int i = start; i < end; i++)
		{
			final Player phantom = phantoms.get(i);
			final int objectId = phantom.getObjectId();
			final int fId = phantom.getFactionId();
			final String factionTag = (FactionWarConfig.isEnabled() && fId > 0) ? String.valueOf(fId) : "-";
			sb.append("<tr><td>").append(htmlSafe(shortText(phantom.getName(), 12))).append("</td><td>").append(phantom.getStatus().getLevel()).append("</td><td>").append(factionTag).append("</td><td>").append(shortText(PhantomState.label(objectId), 5)).append("</td><td>").append(shortText(PhantomAI.getLastAction(phantom), 8)).append("</td>");
			sb.append("<td>");
			miniButton(sb, "K", "admin_phantom kill " + page + " " + filterFaction + " " + objectId, 22);
			sb.append("</td><td>");
			miniButton(sb, "S", "admin_phantom stop " + page + " " + filterFaction + " " + objectId, 30);
			sb.append("</td><td>");
			miniButton(sb, "X", "admin_phantom delete " + page + " " + filterFaction + " " + objectId, 22);
			sb.append("</td><td>");
			if (FactionWarConfig.isEnabled())
			{
				final int nextFaction = getNextFactionId(fId);
				miniButton(sb, (fId == 0) ? "+" : String.valueOf(fId), "admin_phantom faction " + page + " " + filterFaction + " " + objectId + " " + nextFaction, 30);
			}
			sb.append("</td></tr>");
		}
		sb.append("</table>");
		sb.append("<center><font color=808080>K = matar | S = detener | X = borrar | Fac = cambiar faccion</font></center><br>");
		
		sb.append("<table width=300><tr>");
		if (filterFaction > 0)
		{
			button(sb, "Anterior", "admin_phantom factions " + filterFaction + " " + Math.max(0, page - 1));
			button(sb, "Todos", "admin_phantom online 0");
			button(sb, "Siguiente", "admin_phantom factions " + filterFaction + " " + Math.min(maxPage, page + 1));
		}
		else
		{
			button(sb, "Anterior", "admin_phantom online " + Math.max(0, page - 1));
			button(sb, "Principal", "admin_phantom");
			button(sb, "Siguiente", "admin_phantom online " + Math.min(maxPage, page + 1));
		}
		sb.append("</tr></table>");
		sb.append("</body></html>");
		sendHtml(player, sb);
	}
	
	/**
	 * Cycles to the next real faction id (from faction.xml), wrapping back to 0 after
	 * the last one. Unlike the old fId+1 logic, this works with non-contiguous ids.
	 */
	private static int getNextFactionId(int current)
	{
		if (!FactionWarConfig.isEnabled())
			return 0;
		
		final int[] factionIds = FactionData.getInstance().getFactionIds();
		if (factionIds.length == 0)
			return 0;
		
		if (current == 0)
			return factionIds[0];
		
		for (int i = 0; i < factionIds.length; i++)
		{
			if (factionIds[i] == current)
				return (i + 1 < factionIds.length) ? factionIds[i + 1] : 0;
		}
		
		return factionIds[0];
	}
	
	private static String siNo(boolean value)
	{
		return value ? "Si" : "No";
	}
	
	private static String shortText(String text, int max)
	{
		if (text == null)
			return "-";
		return text.length() <= max ? text : text.substring(0, max);
	}
	
	private static String htmlSafe(String text)
	{
		if (text == null)
			return "";
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
	
	private static void sendHtml(Player player, StringBuilder sb)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
	
	private static void sectionTitle(StringBuilder sb, String title)
	{
		sb.append("<table width=330><tr><td bgcolor=3A3A3A><font color=FFD700>").append(title).append("</font></td></tr></table><br1>");
	}
	
	private static void infoRow(StringBuilder sb, String label, Object value)
	{
		sb.append("<tr><td width=140>").append(label).append("</td><td width=190><font color=LEVEL>").append(value).append("</font></td></tr>");
	}
	

	private static void buttonRow2(StringBuilder sb, String label1, String bypass1, String label2, String bypass2)
	{			sb.append("<table width=300><tr>");
			button(sb, label1, bypass1, 150);
			button(sb, label2, bypass2, 150);
			sb.append("</tr></table>");
	}
	
	private static void buttonRowSkipEmpty(StringBuilder sb, String label1, String bypass1, String label2, String bypass2, String label3, String bypass3)
	{
		sb.append("<table width=300><tr>");
		if (!label1.isEmpty())
			button(sb, label1, bypass1, 150);
		if (!label2.isEmpty())
			button(sb, label2, bypass2, 150);
		if (!label3.isEmpty())
			button(sb, label3, bypass3, 150);
		sb.append("</tr></table>");
	}
	
	private static void button(StringBuilder sb, String label, String bypass)
	{
		sb.append("<td><button value=\"").append(label).append("\" action=\"bypass -h ").append(bypass).append("\" width=90 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
	}
	
	private static void button(StringBuilder sb, String label, String bypass, int width)
	{
		sb.append("<td><button value=\"").append(label).append("\" action=\"bypass -h ").append(bypass).append("\" width=").append(width).append(" height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
	}
	
	private static void miniButton(StringBuilder sb, String label, String bypass, int width)
	{
		sb.append("<button value=\"").append(label).append("\" action=\"bypass -h ").append(bypass).append("\" width=").append(width).append(" height=17 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\">");
	}
	

	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
