/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.util.List;
import java.util.StringTokenizer;

import net.sf.l2j.commons.lang.StringUtil;
import net.sf.l2j.commons.math.MathUtil;

import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

/**
 * Handles visibility over target's knownlist, offering details about current target's vicinity.
 */
public class AdminKnownlist implements IAdminCommandHandler
{
	private static final int PAGE_LIMIT = 15;
	
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_knownlist",
		"admin_knownlist_page",
	};
	
	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar)
	{
		if (command.startsWith("admin_knownlist"))
		{
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();
			
			L2Object target = null;
			
			// Try to parse the parameter as an int, then try to retrieve an objectId ; if it's a string, search for any player name.
			if (st.hasMoreTokens())
			{
				final String parameter = st.nextToken();
				
				try
				{
					final int objectId = Integer.parseInt(parameter);
					target = World.getInstance().getObject(objectId);
				}
				catch (NumberFormatException nfe)
				{
					target = World.getInstance().getPlayer(parameter);
				}
			}
			
			// If no one is found, pick potential activeChar's target or the activeChar himself.
			if (target == null)
			{
				target = activeChar.getTarget();
				if (target == null)
					target = activeChar;
			}
			
			int page = 1;
			
			if (command.startsWith("admin_knownlist_page") && st.hasMoreTokens())
			{
				try
				{
					page = Integer.parseInt(st.nextToken());
				}
				catch (NumberFormatException nfe)
				{
				}
			}
			
			showKnownlist(activeChar, target, page);
		}
		return true;
	}
	
	private static void showKnownlist(L2PcInstance activeChar, L2Object target, int page)
	{
		List<L2Object> knownlist = target.getKnownType(L2Object.class);
		
		// Load static Htm.
		final NpcHtmlMessage html = new NpcHtmlMessage(0);
		html.setFile("data/html/admin/knownlist.htm");
		html.replace("%target%", target.getName());
		html.replace("%size%", knownlist.size());
		
		if (knownlist.isEmpty())
		{
			html.replace("%knownlist%", "<tr><td>No objects in vicinity.</td></tr>");
			html.replace("%pages%", 0);
			activeChar.sendPacket(html);
			return;
		}
		
		final int max = MathUtil.countPagesNumber(knownlist.size(), PAGE_LIMIT);
		if (page > max)
			page = max;
		
		knownlist = knownlist.subList((page - 1) * PAGE_LIMIT, Math.min(page * PAGE_LIMIT, knownlist.size()));
		
		// Generate data.
		final StringBuilder sb = new StringBuilder(knownlist.size() * 150);
		for (L2Object object : knownlist)
			StringUtil.append(sb, "<tr><td>", object.getName(), "</td><td>", object.getClass().getSimpleName(), "</td></tr>");
		
		html.replace("%knownlist%", sb.toString());
		
		sb.setLength(0);
		
		// End of table, open a new table for pages system.
		for (int i = 0; i < max; i++)
		{
			final int pagenr = i + 1;
			if (page == pagenr)
				StringUtil.append(sb, pagenr, "&nbsp;");
			else
				StringUtil.append(sb, "<a action=\"bypass -h admin_knownlist_page ", target.getObjectId(), " ", pagenr, "\">", pagenr, "</a>&nbsp;");
		}
		
		html.replace("%pages%", sb.toString());
		
		activeChar.sendPacket(html);
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}