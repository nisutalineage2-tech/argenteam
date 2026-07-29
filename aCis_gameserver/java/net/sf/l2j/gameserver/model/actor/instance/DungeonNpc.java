package net.sf.l2j.gameserver.model.actor.instance;

import java.util.StringTokenizer;

import net.sf.l2j.gameserver.dungeon.DungeonEngine;
import net.sf.l2j.gameserver.dungeon.DungeonTemplate;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class DungeonNpc extends Folk
{
	public DungeonNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		final StringTokenizer st = new StringTokenizer(command, " ");
		final String cmd = st.nextToken();
		
		if (cmd.startsWith("dungeon_main") || cmd.startsWith("dungeon_menu"))
		{
			final DungeonEngine de = DungeonEngine.getInstance();
			final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			
			if (!de.isEnabled())
			{
				showMessage(player, "Dungeons are currently disabled.", "FF4444");
				return;
			}
			
			final StringBuilder sb = new StringBuilder();
			sb.append("<html><title>Dungeon Manager</title><body>");
			sb.append("<center><font color=LEVEL>Dungeon Manager</font></center><br>");
			sb.append("<img src=\"L2UI.SquareGray\" width=\"290\" height=\"1\"><br>");
			
			if (!de.getTemplates().isEmpty())
			{
				for (DungeonTemplate t : de.getTemplates())
				{
					sb.append("<table width=290 bgcolor=000000>");
					sb.append("<tr><td><font color=00AAFF>" + t.getName() + "</font></td></tr>");
					sb.append("<tr><td><font color=808080>Level: " + t.getMinLevel() + "-" + t.getMaxLevel() + "</font></td></tr>");
					sb.append("<tr><td><font color=808080>Players: " + t.getMinPlayers() + "-" + t.getMaxPlayers() + "</font></td></tr>");
					sb.append("<tr><td><font color=808080>Cooldown: " + t.getCooldownHours() + "h</font></td></tr>");
					
					if (de.isRegistrationOpen())
						sb.append("<tr><td><button value=\"Enter\" action=\"bypass -h npc_%objectId%_dungeon_enter_" + t.getId() + "\" width=120 height=22 back=\"L2UI_CH3.Minimap.mapbutton_zoomin1_over\" fore=\"L2UI_CH3.Minimap.mapbutton_zoomin1\"></td></tr>");
					else
						sb.append("<tr><td><font color=FF4444>Registration closed</font></td></tr>");
					
					sb.append("</table><br>");
				}
			}
			else
			{
				sb.append("No dungeons configured.<br>");
			}
			
			sb.append("<br>");
			sb.append("Status: <font color=" + (de.isRegistrationOpen() ? "00FF00\">Registration Open" : "FF4444\">Registration Closed") + "</font><br>");
			sb.append("Running: " + de.getRunning().size() + "<br>");
			
			if (de.isInDungeon(player))
			{
				sb.append("<br><font color=FFCC00>You are currently in a dungeon!</font>");
			}
			
			sb.append("</body></html>");
			
			html.setHtml(sb.toString());
			player.sendPacket(html);
		}
		else if (cmd.equals("dungeon_status"))
		{
			final DungeonEngine de = DungeonEngine.getInstance();
			final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
			
			final StringBuilder sb = new StringBuilder();
			sb.append("<html><title>Dungeon Status</title><body>");
			sb.append("<center><font color=LEVEL>My Dungeon Status</font></center><br>");
			
			for (DungeonTemplate t : de.getTemplates())
			{
				final long remaining = de.getRemainingCooldownSeconds(player, t.getId());
				sb.append(t.getName() + ": ");
				if (remaining > 0)
					sb.append("<font color=FF4444>Cooldown " + (remaining / 60) + "m " + (remaining % 60) + "s</font>");
				else
					sb.append("<font color=00FF00>Available</font>");
				sb.append("<br>");
			}
			
			sb.append("<br><center><button value=\"Back\" action=\"bypass -h npc_%objectId%_dungeon_menu\" width=120 height=22 back=\"L2UI_CH3.btn\" fore=\"L2UI_CH3.btn\"></center>");
			sb.append("</body></html>");
			
			html.setHtml(sb.toString());
			player.sendPacket(html);
		}
		else if (cmd.startsWith("dungeon_enter_"))
		{
			try
			{
				final int dungeonId = Integer.parseInt(cmd.substring("dungeon_enter_".length()));
				DungeonEngine.getInstance().enterDungeon(dungeonId, player);
			}
			catch (NumberFormatException e)
			{
				showMessage(player, "Invalid dungeon ID.", "FF4444");
			}
		}
		else if (cmd.equals("dungeon_exit"))
		{
			final DungeonEngine de = DungeonEngine.getInstance();
			if (de.isInDungeon(player))
			{
				de.setPlayerDungeon(player, null);
				de.getParticipants().remove(player.getObjectId());
				player.teleportTo(de.getSpawnX(), de.getSpawnY(), de.getSpawnZ(), 25);
				player.sendMessage("You left the dungeon.");
			}
		}
		else
		{
			super.onBypassFeedback(player, command);
		}
	}
	
	private void showMessage(Player player, String msg, String color)
	{
		final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
		final StringBuilder sb = new StringBuilder();
		sb.append("<html><body><center>");
		sb.append("<font color=LEVEL>Dungeon Manager</font><br><br>");
		sb.append("<font color=\"" + color + "\">" + msg + "</font><br><br>");
		sb.append("<button value=\"Back\" action=\"bypass -h npc_%objectId%_dungeon_menu\" width=120 height=22 back=\"L2UI_CH3.btn\" fore=\"L2UI_CH3.btn\">");
		sb.append("</center></body></html>");
		html.setHtml(sb.toString());
		player.sendPacket(html);
	}
	
	@Override
	public String getHtmlPath(int npcId, int val)
	{
		return "data/html/mods/dungeon/manager_main.htm";
	}
	
	@Override
	public void showChatWindow(Player player, int val)
	{
		onBypassFeedback(player, "dungeon_menu");
	}
}
