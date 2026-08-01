package net.sf.l2j.gameserver.model.actor.instance;

import java.util.StringTokenizer;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.data.manager.SpawnManager;
import net.sf.l2j.gameserver.model.actor.Player;

import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.spawn.ASpawn;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * Grand Boss teleporter located in the neutral zone.<br>
 * Shows the status of the 7 Grand Bosses (online or dead with respawn countdown)
 * and teleports the player to the lair when the boss is alive.
 */
public class GrandBossTeleporterNpc extends Folk
{
	private static final class BossInfo
	{
		private final int npcId;
		private final String name;
		private final int x;
		private final int y;
		private final int z;
		
		private BossInfo(int npcId, String name, int x, int y, int z)
		{
			this.npcId = npcId;
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}
	
	// Lair entry coordinates for each Grand Boss.
	private static final BossInfo[] BOSSES =
	{
		new BossInfo(29066, "Antharas", 179700, 113800, -7709),
		new BossInfo(29020, "Baium", 115203, 16620, 10078),
		new BossInfo(29006, "Core", 17726, 108915, -6480),
		new BossInfo(29014, "Orfen", 55024, 17368, -5412),
		new BossInfo(29001, "Queen Ant", -21610, 181594, -5734),
		new BossInfo(29028, "Valakas", 183813, -115157, -3303),
		new BossInfo(29022, "Zaken", 55312, 219168, -3223)
	};
	
	public GrandBossTeleporterNpc(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(Player player, String command)
	{
		final StringTokenizer st = new StringTokenizer(command, " ");
		if (!st.hasMoreTokens())
		{
			super.onBypassFeedback(player, command);
			return;
		}
		
		final String currentCommand = st.nextToken();
		
		if (currentCommand.equals("teleport"))
		{
			if (!st.hasMoreTokens())
				return;
			
			final int bossId = Integer.parseInt(st.nextToken());
			final BossInfo boss = getBoss(bossId);
			if (boss == null)
				return;
			
			if (isBossAlive(boss.npcId))
			{
				player.teleportTo(boss.x + Rnd.get(-200, 200), boss.y + Rnd.get(-200, 200), boss.z, 20);
				player.sendMessage("[Grand Boss] Te has teletransportado al lair de " + boss.name + ".");
			}
			else
			{
				player.sendMessage("[Grand Boss] " + boss.name + " no esta disponible en este momento.");
				showMenu(player);
			}
		}
		else if (currentCommand.equals("refresh"))
			showMenu(player);
		else
			super.onBypassFeedback(player, command);
	}
	
	@Override
	public void showChatWindow(Player player, int val)
	{
		showMenu(player);
	}
	
	private void showMenu(Player player)
	{
		final StringBuilder sb = new StringBuilder(2048);
		sb.append("<html><body>");
		sb.append("<center><font color=\"FFD700\">Grand Boss Teleporter</font></center>");
		sb.append("<img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\"><br>");
		
		for (BossInfo boss : BOSSES)
		{
			sb.append("<table width=\"270\" cellpadding=\"2\" cellspacing=\"1\" bgcolor=\"1A1A2E\"><tr>");
			sb.append("<td width=\"130\"><font color=\"FFFFFF\">").append(boss.name).append("</font></td>");
			
			if (isBossAlive(boss.npcId))
			{
				sb.append("<td width=\"40\" align=\"center\"><font color=\"00FF00\">Online</font></td>");
				sb.append("<td width=\"100\" align=\"right\"><button value=\"Ir\" action=\"bypass -h npc_").append(getObjectId()).append("_teleport ").append(boss.npcId).append("\" width=\"55\" height=\"20\" back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></td>");
			}
			else
			{
				final long remaining = getRespawnRemaining(boss.npcId);
				sb.append("<td width=\"140\" colspan=\"2\" align=\"right\"><font color=\"FF0000\" size=\"10\">").append(formatRemaining(remaining)).append("</font></td>");
			}
			
			sb.append("</tr></table>");
		}
		
		sb.append("<img src=\"L2UI.SquareGray\" width=\"270\" height=\"1\"><br>");
		sb.append("<center><button value=\"Actualizar\" action=\"bypass -h npc_").append(getObjectId()).append("_refresh\" width=\"120\" height=\"22\" back=\"L2UI_ch3.smallbutton2_over\" fore=\"L2UI_ch3.smallbutton2\"></center>");
		sb.append("</body></html>");
		
		final NpcHtmlMessage msg = new NpcHtmlMessage(getObjectId());
		msg.setHtml(sb.toString());
		player.sendPacket(msg);
	}
	
	private static BossInfo getBoss(int npcId)
	{
		for (BossInfo boss : BOSSES)
			if (boss.npcId == npcId)
				return boss;
		
		return null;
	}
	
	/**
	 * @param npcId : The boss {@link Npc} ID.
	 * @return True if the boss is currently spawned and alive.
	 */
	private static boolean isBossAlive(int npcId)
	{
		return net.sf.l2j.gameserver.phantom.PhantomEngine.isGrandBossAlive(npcId);
	}
	
	/**
	 * @param npcId : The boss {@link Npc} ID.
	 * @return Milliseconds remaining until respawn, or 0 if unknown/alive.
	 */
	private static long getRespawnRemaining(int npcId)
	{
		final ASpawn spawn = SpawnManager.getInstance().getSpawn(npcId);
		if (spawn == null || spawn.getSpawnData() == null)
			return 0;
		
		if (!spawn.getSpawnData().checkDead())
			return 0;
		
		return Math.max(0, spawn.getSpawnData().getRespawnTime() - System.currentTimeMillis());
	}
	
	private static String formatRemaining(long millis)
	{
		final long totalMinutes = millis / 60000;
		final long hours = totalMinutes / 60;
		final long minutes = totalMinutes % 60;
		
		if (hours > 0)
			return "Muerto " + hours + "h " + minutes + "m";
		
		if (minutes > 0)
			return "Muerto " + minutes + "m";
		
		return "Muerto";
	}
}
