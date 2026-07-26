package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.Faction;
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
		"admin_phantom_online",
		"admin_phantom_status",
		"admin_phantom_faction",
		"admin_phantom_factions"
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
			if (!Config.ENABLE_FACTION_SYSTEM)
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
			final int loaded = PhantomEngine.startConfigured(player);
			showPanel(player, "Restored saved phantoms: " + loaded + ".");
			return;
		}
		
		if (cmd.equals("admin_phantom_create"))
		{
			final int count = parseCount(st, 1);
			int loaded = 0;
			for (Player phantom : PhantomFactory.create(count))
			{
				if (PhantomEngine.load(phantom.getObjectId(), player, false) != null)
					loaded++;
			}
			showPanel(player, "Created NEW racial phantoms: " + loaded + ".");
			return;
		}
		
		if (cmd.equals("admin_phantom_bring"))
		{
			showPanel(player, "Brought phantoms: " + PhantomEngine.bringAll(player) + ".");
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
				case "off" -> showPanel(player, "AI stopped for: " + PhantomEngine.stopAi());
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
			if (!Config.ENABLE_FACTION_SYSTEM)
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
		final StringBuilder sb = new StringBuilder(16384);
		sb.append("<html><body><center><font color=LEVEL>Phantom Manager</font></center><br>");
		if (message != null)
			sb.append("<font color=99FF99>").append(message).append("</font><br1>");
		
		sb.append("Active: <font color=LEVEL>").append(allPhantoms.size()).append("</font> | Config IDs: <font color=LEVEL>").append(PhantomConfig.getPhantomIds().size()).append("</font><br>");
		sb.append("AI: ").append(PhantomConfig.aiEnabled()).append(" | Tick: ").append(PhantomConfig.aiTickMs()).append("ms | Zone: ").append(PhantomConfig.levelZoneProfile()).append("<br>");
		sb.append("Skills: ").append(PhantomConfig.advancedSkillUsage()).append(" | Mage: ").append(PhantomConfig.mageNeverMelee()).append(" | Herbs: ").append(PhantomConfig.autoLootHerbs()).append("<br>");
		sb.append("PVP: ").append(PhantomConfig.pvpEnabled()).append(" | PK: ").append(PhantomConfig.pkEnabled()).append(" | Chat: ").append(PhantomConfig.phantomChatEnabled()).append("<br>");
		sb.append("Factions: ").append(Config.ENABLE_FACTION_SYSTEM).append("<br>");
		
		sb.append("<br>");
		buttonRow(sb, "Restore", "admin_phantom start", "New 1", "admin_phantom create 1", "New 10", "admin_phantom create 10");
		buttonRow(sb, "AI On", "admin_phantom ai on", "AI Off", "admin_phantom ai off", "Set Home", "admin_phantom ai home");
		buttonRow(sb, "Bring", "admin_phantom bring", "Radar", "admin_phantom radar phantoms", "Reload", "admin_phantom reload");
		buttonRow(sb, "Stop All", "admin_phantom stop", "Clear", "admin_phantom radar clear", "Online", "admin_phantom online 0");
		
		if (allPhantoms.isEmpty())
		{
			sb.append("<br><center><font color=808080>No phantoms active.</font></center>");
		}
		else
		{
			final Map<Integer, List<Player>> groups = groupByFaction(allPhantoms);
			final List<Integer> sortedKeys = new ArrayList<>(groups.keySet().stream().sorted().toList());
			sb.append("<br><font color=B0C4DE>--- Phantoms by Faction ---</font><br>");
			
			for (int fKey : sortedKeys)
			{
				final List<Player> list = groups.get(fKey);
				final Faction faction = (Config.ENABLE_FACTION_SYSTEM && fKey > 0) ? FactionData.getInstance().getFaction(fKey) : null;
				
				sb.append("<br><font color=FFD700> ");
				if (fKey == 0)
					sb.append("No Faction");
				else
					sb.append("Faction ").append(fKey).append(faction != null ? " (" + faction.getName() + ")" : "");
				sb.append(" [").append(list.size()).append("]</font><br>");
				
				sb.append("<table width=310>");
				for (Player phantom : list)
				{
					final int oid = phantom.getObjectId();
					final int nextFaction = nextFactionId(fKey);
					sb.append("<tr><td width=90>").append(shortText(phantom.getName(), 10)).append("</td>");
					sb.append("<td width=18>L").append(phantom.getStatus().getLevel()).append("</td>");
					sb.append("<td width=50>").append(shortText(PhantomState.label(oid), 6)).append("</td>");
					sb.append("<td width=22>");
					miniButton(sb, "K", "admin_phantom kill 0 0 " + oid, 22);
					sb.append("</td><td width=22>");
					miniButton(sb, "S", "admin_phantom stop 0 0 " + oid, 22);
					sb.append("</td><td width=22>");
					miniButton(sb, "X", "admin_phantom delete 0 0 " + oid, 22);
					sb.append("</td><td width=28>");
					if (Config.ENABLE_FACTION_SYSTEM)
						miniButton(sb, String.valueOf(nextFaction == 0 ? "-" : nextFaction), "admin_phantom faction 0 0 " + oid + " " + nextFaction, 28);
					sb.append("</td></tr>");
				}
				sb.append("</table>");
			}
		}
		
		sb.append("<br><font color=808080>K=Kill S=Stop X=Del Fct=Cycle faction</font>");
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
	
	private static int nextFactionId(int current)
	{
		final int maxFaction = FactionData.getInstance().getFactionCount();
		if (!Config.ENABLE_FACTION_SYSTEM || maxFaction <= 0)
			return 0;
		
		if (current < 1)
			return 1;
		if (current >= maxFaction)
			return 0;
		return current + 1;
	}
	
	private static void showOnline(Player player, int page, int filterFaction, String message)
	{
		List<Player> phantoms = PhantomEngine.getActivePhantomsSorted();
		final String title;
		if (filterFaction > 0)
		{
			phantoms = filterByFaction(phantoms, filterFaction);
			final Faction f = FactionData.getInstance().getFaction(filterFaction);
			title = "Phantoms - Faction " + filterFaction + (f != null ? " (" + f.getName() + ")" : "");
		}
		else
		{
			title = "Phantoms Online";
		}
		
		final int maxPage = phantoms.isEmpty() ? 0 : (phantoms.size() - 1) / PAGE_SIZE;
		page = Math.max(0, Math.min(page, maxPage));
		
		final StringBuilder sb = new StringBuilder(8192);
		sb.append("<html><body><center><font color=LEVEL>").append(title).append("</font></center><br>");
		if (message != null)
			sb.append("<font color=99FF99>").append(message).append("</font><br1>");
		
		sb.append("Count: <font color=LEVEL>").append(phantoms.size()).append("</font> | Page: ").append(page + 1).append("/").append(maxPage + 1).append("<br1>");
		sb.append("<table width=310>");
		sb.append("<tr><td width=60>Name</td><td width=16>Lv</td><td width=22>Fct</td><td width=26>Mode</td><td width=44>State</td><td width=22>K</td><td width=30>Stop</td><td width=22>Del</td><td width=28>Fct</td></tr>");
		
		final int start = page * PAGE_SIZE;
		final int end = Math.min(start + PAGE_SIZE, phantoms.size());
		for (int i = start; i < end; i++)
		{
			final Player phantom = phantoms.get(i);
			final int objectId = phantom.getObjectId();
			final int fId = phantom.getFactionId();
			final String factionTag = (Config.ENABLE_FACTION_SYSTEM && fId > 0) ? String.valueOf(fId) : "-";
			sb.append("<tr><td>").append(shortText(phantom.getName(), 10)).append("</td><td>").append(phantom.getStatus().getLevel()).append("</td><td>").append(factionTag).append("</td><td>").append(shortText(PhantomState.label(objectId), 5)).append("</td><td>").append(shortText(PhantomAI.getLastAction(phantom), 6)).append("</td>");
			sb.append("<td>");
			miniButton(sb, "K", "admin_phantom kill " + page + " " + filterFaction + " " + objectId, 22);
			sb.append("</td><td>");
			miniButton(sb, "S", "admin_phantom stop " + page + " " + filterFaction + " " + objectId, 30);
			sb.append("</td><td>");
			miniButton(sb, "X", "admin_phantom delete " + page + " " + filterFaction + " " + objectId, 22);
			sb.append("</td><td>");
			if (Config.ENABLE_FACTION_SYSTEM)
			{
				final int nextFaction = (fId == 0) ? 1 : (fId >= countByFaction().size()) ? 0 : fId + 1;
				miniButton(sb, (fId == 0) ? "+" : String.valueOf(fId), "admin_phantom faction " + page + " " + filterFaction + " " + objectId + " " + nextFaction, 28);
			}
			sb.append("</td></tr>");
		}
		sb.append("</table><br>");
		
		sb.append("<table width=300><tr>");
		if (filterFaction > 0)
		{
			button(sb, "Prev", "admin_phantom factions " + filterFaction + " " + Math.max(0, page - 1));
			button(sb, "All", "admin_phantom online 0");
			button(sb, "Next", "admin_phantom factions " + filterFaction + " " + Math.min(maxPage, page + 1));
		}
		else
		{
			button(sb, "Prev", "admin_phantom online " + Math.max(0, page - 1));
			button(sb, "Main", "admin_phantom");
			button(sb, "Next", "admin_phantom online " + Math.min(maxPage, page + 1));
		}
		sb.append("</tr></table>");
		sb.append("</body></html>");
		sendHtml(player, sb);
	}
	
	private static String shortText(String text, int max)
	{
		if (text == null)
			return "-";
		return text.length() <= max ? text : text.substring(0, max);
	}
	
	private static void sendHtml(Player player, StringBuilder sb)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
	
	private static void buttonRow(StringBuilder sb, String label1, String bypass1, String label2, String bypass2, String label3, String bypass3)
	{
		sb.append("<table width=300><tr>");
		button(sb, label1, bypass1);
		button(sb, label2, bypass2);
		button(sb, label3, bypass3);
		sb.append("</tr></table>");
	}
	
	private static void button(StringBuilder sb, String label, String bypass)
	{
		sb.append("<td><button value=\"").append(label).append("\" action=\"bypass -h ").append(bypass).append("\" width=90 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
	}
	
	private static void miniButton(StringBuilder sb, String label, String bypass, int width)
	{
		sb.append("<button value=\"").append(label).append("\" action=\"bypass -h ").append(bypass).append("\" width=").append(width).append(" height=17 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\">");
	}
	
	private static void link(StringBuilder sb, String label, String bypass)
	{
		sb.append("<a action=\"bypass -h ").append(bypass).append("\">").append(label).append("</a>");
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
