/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package net.sf.l2j.gameserver.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.ChangePassword;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.EnchantInfo;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.LeaveChaos;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.MapInfo;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.OnlinePlayers;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.Points;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.RegChaos;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.SetEnchant;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.BuffCommand;
import net.sf.l2j.gameserver.handler.voicedcommandhandlers.StatsVCmd;


public class VoicedCommandHandler
{
	private static Logger _log = Logger.getLogger(ItemHandler.class.getName());
	
	private static VoicedCommandHandler _instance;
	private Map<String, IVoicedCommandHandler> _datatable;
	
	private VoicedCommandHandler()
	{
		
		_datatable = new HashMap<>();
		
		if (Config.ENABLE_ONLINE_VC)
		{
			registerVoicedCommandHandler(new OnlinePlayers());
		}
		if (Config.FACTION_ENCHANT_SYSTEM_TYPE.equals("PVPENCHANT"))
		{
			registerVoicedCommandHandler(new SetEnchant());
			registerVoicedCommandHandler(new EnchantInfo());
		}
		if (!Config.FACTION_ENABLE_VOTE_MAP)
		{
			registerVoicedCommandHandler(new MapInfo());
		}
		registerVoicedCommandHandler(new Points());
		registerVoicedCommandHandler(new ChangePassword());
		// Coloque aqui para registrar los comandos
		if (Config.ALLOW_STATS_COMMAND)
			registerVoicedCommandHandler(new BuffCommand());			
			registerVoicedCommandHandler(new StatsVCmd());				

		if (Config.FACTION_ENABLE_CHAOS_EVENT)
		{
			registerVoicedCommandHandler(new RegChaos());
			registerVoicedCommandHandler(new LeaveChaos());

		}
	}
	
	public void registerVoicedCommandHandler(IVoicedCommandHandler handler)
	{
		String[] ids = handler.getVoicedCommandList();
		for (String id : ids)
		{
			if (Config.DEBUG)
			{
				_log.fine("Adding handler for command " + id);
			}
			_datatable.put(id, handler);
		}
	}
	
	public IVoicedCommandHandler getVoicedCommandHandler(String voicedCommand)
	{
		String command = voicedCommand;
		
		if (voicedCommand.indexOf(" ") != -1)
		{
			command = voicedCommand.substring(0, voicedCommand.indexOf(" "));
		}
		
		if (Config.DEBUG)
		{
			_log.fine("getting handler for command: " + command + " -> " + (_datatable.get(command) != null));
		}
		
		return _datatable.get(command);
	}
	
	public int size()
	{
		return _datatable.size();
	}
	
	public static VoicedCommandHandler getInstance()
	{
		if (_instance == null)
		{
			_instance = new VoicedCommandHandler();
		}
		
		return _instance;
	}
}