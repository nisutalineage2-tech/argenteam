package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.gameserver.data.xml.ItemData;
import net.sf.l2j.gameserver.data.xml.RecipeData;
import net.sf.l2j.gameserver.enums.actors.OperateType;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.records.ManufactureItem;
import net.sf.l2j.gameserver.model.records.Recipe;
import net.sf.l2j.gameserver.model.trade.TradeItem;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.phantom.PhantomEngine;

/**
 * Admin panel for active offline stores.<br>
 * <br>
 * Lists every player currently running a store in offline mode (real detached traders and phantoms with an open store),
 * lets the GM inspect the listed items/prices and force-close any store.
 */
public class AdminOffline implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_offline"
	};
	
	@Override
	public void useAdminCommand(String command, Player player)
	{
		final StringTokenizer st = new StringTokenizer(command);
		final String cmd = st.nextToken();
		
		if (cmd.equals("admin_offline"))
		{
			final String action = st.hasMoreTokens() ? st.nextToken() : "list";
			switch (action)
			{
				case "view":
					if (st.hasMoreTokens())
						viewStore(player, st.nextToken());
					else
						showList(player);
					break;
				
				case "close":
					if (st.hasMoreTokens())
						closeStore(player, st.nextToken());
					else
						showList(player);
					break;
				
				default:
					showList(player);
					break;
			}
		}
	}
	
	/**
	 * Main panel: table of every active offline store (real traders + phantoms with store open).
	 */
	private static void showList(Player player)
	{
		final List<Player> traders = new ArrayList<>();
		for (Player p : World.getInstance().getPlayers())
		{
			if (p == null || p.getOperateType() == OperateType.NONE)
				continue;
			
			// Active offline store: detached client (real offline trader) or null client (phantom in store mode).
			if (p.getClient() == null || p.getClient().isDetached())
				traders.add(p);
		}
		
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><body><center>");
		sb.append("<table width=330><tr><td align=center><font color=FFD700>Tiendas Offline Activas</font></td></tr></table><br1>");
		sb.append("<table width=330><tr><td align=center>Total: <font color=LEVEL>").append(traders.size()).append("</font></td></tr></table><br1>");
		
		if (traders.isEmpty())
			sb.append("<table width=330><tr><td align=center>No hay tiendas offline abiertas.</td></tr></table>");
		else
		{
			for (Player t : traders)
			{
				final boolean isPhantom = PhantomEngine.isPhantom(t.getObjectId());
				final String typeName = getTypeName(t.getOperateType());
				final int itemCount = getItemCount(t);
				
				sb.append("<table width=330 bgcolor=303030><tr><td width=200>");
				sb.append("<font color=LEVEL>").append(t.getName()).append("</font>");
				if (isPhantom)
					sb.append(" <font color=808080>[Bot]</font>");
				sb.append("<br1><font color=808080>").append(typeName).append(" | Items: ").append(itemCount).append("</font>");
				sb.append("</td><td width=130 align=center>");
				miniButton(sb, "Ver", "admin_offline view " + t.getObjectId(), 58);
				sb.append("<br1>");
				miniButton(sb, "Cerrar", "admin_offline close " + t.getObjectId(), 58);
				sb.append("</td></tr></table>");
			}
		}
		
		sb.append("</center></body></html>");
		sendHtml(player, sb);
	}
	
	/**
	 * Detail panel: shows the whole store content (title, items, prices).
	 */
	private static void viewStore(Player player, String objectIdStr)
	{
		final int objectId = parseInt(objectIdStr);
		final Player t = World.getInstance().getPlayer(objectId);
		if (t == null)
		{
			player.sendMessage("Jugador no encontrado: " + objectIdStr + ".");
			showList(player);
			return;
		}
		
		if (t.getOperateType() == OperateType.NONE)
		{
			player.sendMessage(t.getName() + " no tiene tienda abierta.");
			showList(player);
			return;
		}
		
		final boolean isPhantom = PhantomEngine.isPhantom(t.getObjectId());
		
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><body><center>");
		sb.append("<table width=330><tr><td align=center><font color=FFD700>Tienda de ").append(t.getName()).append("</font></td></tr></table><br1>");
		
		sb.append("<table width=330>");
		infoRow(sb, "Tipo", getTypeName(t.getOperateType()) + (isPhantom ? " (Bot)" : ""));
		infoRow(sb, "Titulo", getStoreTitle(t));
		infoRow(sb, "Ubicacion", t.getX() + ", " + t.getY() + ", " + t.getZ());
		sb.append("</table><br1>");
		
		sb.append("<table width=330><tr><td width=160><font color=808080>Item</font></td><td width=70><font color=808080>Cant</font></td><td width=100><font color=808080>Precio</font></td></tr>");
		
		final List<String> lines = getStoreLines(t);
		if (lines.isEmpty())
			sb.append("<tr><td colspan=3 align=center>Tienda vacia.</td></tr>");
		else
		{
			for (String line : lines)
				sb.append(line);
		}
		sb.append("</table><br1>");
		
		sb.append("<table width=300><tr>");
		button(sb, "Cerrar", "admin_offline close " + objectId);
		button(sb, "Volver", "admin_offline");
		sb.append("</tr></table>");
		
		sb.append("</center></body></html>");
		sendHtml(player, sb);
	}
	
	/**
	 * Force-closes the store of the given player. Real detached traders are removed from the world by the
	 * offline hook; phantoms (null client) keep their character and simply stand up.
	 */
	private static void closeStore(Player player, String objectIdStr)
	{
		final int objectId = parseInt(objectIdStr);
		final Player t = World.getInstance().getPlayer(objectId);
		if (t == null)
		{
			player.sendMessage("Jugador no encontrado: " + objectIdStr + ".");
			showList(player);
			return;
		}
		
		if (t.getOperateType() == OperateType.NONE)
		{
			player.sendMessage(t.getName() + " no tiene tienda abierta.");
			showList(player);
			return;
		}
		
		t.getSellList().clear();
		t.getBuyList().clear();
		t.getManufactureList().clear();
		t.setOperateType(OperateType.NONE);
		
		// Phantoms are not deleted by the offline hook (null client); stand them up manually.
		// Real detached traders are deleted by the hook when OFFLINE_DISCONNECT_FINISHED is on;
		// otherwise remove them explicitly so they don't linger in the world with an empty store.
		if (World.getInstance().getPlayer(objectId) == t)
		{
			if (t.getClient() != null && t.getClient().isDetached())
				t.deleteMe();
			else
			{
				t.standUp();
				t.broadcastUserInfo();
			}
		}
		
		player.sendMessage("Tienda de " + t.getName() + " cerrada.");
		showList(player);
	}
	
	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------
	
	private static String getTypeName(OperateType type)
	{
		switch (type)
		{
			case SELL:
				return "Venta";
			case PACKAGE_SELL:
				return "Venta x Paquete";
			case BUY:
				return "Compra";
			case MANUFACTURE:
				return "Craft";
			default:
				return type.name();
		}
	}
	
	private static int getItemCount(Player t)
	{
		switch (t.getOperateType())
		{
			case SELL:
			case PACKAGE_SELL:
				return t.getSellList().size();
			case BUY:
				return t.getBuyList().size();
			case MANUFACTURE:
				return t.getManufactureList().size();
			default:
				return 0;
		}
	}
	
	private static String getStoreTitle(Player t)
	{
		switch (t.getOperateType())
		{
			case SELL:
			case PACKAGE_SELL:
				return shortText(t.getSellList().getTitle(), 30);
			case BUY:
				return shortText(t.getBuyList().getTitle(), 30);
			case MANUFACTURE:
				return shortText(t.getManufactureList().getStoreName(), 30);
			default:
				return "-";
		}
	}
	
	private static List<String> getStoreLines(Player t)
	{
		final List<String> lines = new ArrayList<>();
		final OperateType type = t.getOperateType();
		
		if (type == OperateType.SELL || type == OperateType.PACKAGE_SELL)
		{
			for (TradeItem item : t.getSellList())
				lines.add("<tr><td width=160>" + getItemName(item.getItemId()) + "</td><td width=70 align=center>" + item.getCount() + "</td><td width=100 align=center>" + item.getPrice() + "</td></tr>");
		}
		else if (type == OperateType.BUY)
		{
			for (TradeItem item : t.getBuyList())
				lines.add("<tr><td width=160>" + getItemName(item.getItemId()) + "</td><td width=70 align=center>" + item.getCount() + "</td><td width=100 align=center>" + item.getPrice() + "</td></tr>");
		}
		else if (type == OperateType.MANUFACTURE)
		{
			for (ManufactureItem m : t.getManufactureList())
				lines.add("<tr><td width=160>" + getRecipeName(m.recipeId()) + "</td><td width=70 align=center>-</td><td width=100 align=center>" + m.cost() + "</td></tr>");
		}
		return lines;
	}
	
	private static String getItemName(int itemId)
	{
		final Item item = ItemData.getInstance().getTemplate(itemId);
		return (item == null) ? ("Item " + itemId) : item.getName();
	}
	
	private static String getRecipeName(int recipeId)
	{
		final Recipe recipe = RecipeData.getInstance().getRecipeList(recipeId);
		if (recipe == null)
			return "Receta " + recipeId;
		
		return getItemName(recipe.product().getId());
	}
	
	private static int parseInt(String value)
	{
		try
		{
			return Integer.parseInt(value.trim());
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
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
	
	private static void infoRow(StringBuilder sb, String label, Object value)
	{
		sb.append("<tr><td width=140>").append(label).append("</td><td width=190><font color=LEVEL>").append(value).append("</font></td></tr>");
	}
	
	private static void button(StringBuilder sb, String label, String bypass)
	{
		sb.append("<td><button value=\"").append(label).append("\" action=\"bypass -h ").append(bypass).append("\" width=90 height=21 back=\"L2UI_ch3.Btn1_normalOn\" fore=\"L2UI_ch3.Btn1_normal\"></td>");
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
