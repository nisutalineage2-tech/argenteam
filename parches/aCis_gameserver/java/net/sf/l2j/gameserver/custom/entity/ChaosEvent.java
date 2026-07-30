/* This program is free software: you can redistribute it and/or modify it under
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
package net.sf.l2j.gameserver.custom.entity;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import net.sf.l2j.commons.concurrent.ThreadPool;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.util.Broadcast;

/**
 * @author DarthVader
 * @version 1.0
 */
public class ChaosEvent
{
	public enum State
	{
		ISJUNGTAS,
		REGISTRACIJA,
		IJUNGTAS
	}
	
	protected static State _state = State.ISJUNGTAS;
	private static int _laikas_aktyv = Config.CHAOS_EVENT_INTERVAL * 60 * 1000;
	private static int _laikas_vykim = Config.CHAOS_EVENT_DURATION_TIME * 60 * 1000;
	
	public static State getState()
	{
		return _state;
	}
	
	public static void setState(State a)
	{
		_state = a;
	}
	
	public static void uzkrautiEventa()
	{
		ThreadPool.schedule(new Runnable()
		{
			@Override
			public void run()
			{
				pradzia(true);
			}
		}, 1000);
	}
	
	public static void pradzia(boolean onload)
	{
		setState(State.ISJUNGTAS);
		if (!onload)
		{
			Broadcast.announceToOnlinePlayers("Chaos Event: Event finished.");
			Broadcast.announceToOnlinePlayers("Chaos Event: Next event in " + (_laikas_aktyv / 1000 / 60 / 60) + " hours.");
			Broadcast.announceToOnlinePlayers("TOP 5 PLAYERS BY PVP in ChaosEvent:");
			int _participated = 0;
			for (L2PcInstance part : World.getInstance().getPlayers())
			{
				if (part.isInChaosEvent())
					_participated++;
			}
			
			if (_participated > 5)
			{
				HashMap<L2PcInstance, Integer> map = new HashMap<>();
				ValueComparator bvc = new ValueComparator(map);
				TreeMap<L2PcInstance, Integer> sorted_map = new TreeMap<>(bvc);
				
				for (L2PcInstance player : World.getInstance().getPlayers())
					map.put(player, player.getInChaosPvps());
				
				sorted_map.putAll(map);
				
				int until = 1;
				L2PcInstance first = null;
				L2PcInstance second = null;
				L2PcInstance third = null;
				L2PcInstance fourth = null;
				L2PcInstance fifth = null;
				while (until <= 5)
				{
					for (L2PcInstance key : sorted_map.keySet())
					{
						switch (until)
						{
							case 1:
								first = key;
								break;
							case 2:
								second = key;
								break;
							case 3:
								third = key;
								break;
							case 4:
								fourth = key;
								break;
							case 5:
								fifth = key;
								break;
						}
						until++;
					}
				}
				Broadcast.announceToOnlinePlayers(first.getName() + " - " + first.getInChaosPvps() + " pvp points.");
				Broadcast.announceToOnlinePlayers(second.getName() + " - " + second.getInChaosPvps() + " pvp points.");
				Broadcast.announceToOnlinePlayers(third.getName() + " - " + third.getInChaosPvps() + " pvp points.");
				Broadcast.announceToOnlinePlayers(fourth.getName() + " - " + fourth.getInChaosPvps() + " pvp points.");
				Broadcast.announceToOnlinePlayers(fifth.getName() + " - " + fifth.getInChaosPvps() + " pvp points.");
				
				first.addItem("reward", Config.CHAOS_EVENT_REWARD_ID, Config.CHAOS_EVENT_REWARD_AMOUNT, first, true);
				Broadcast.announceToOnlinePlayers(fifth.getName() + ", the best chaos event player, were rewarded.");
			}
			
			for (L2PcInstance zaid : World.getInstance().getPlayers())
			{
				if (zaid.isInChaosEvent())
				{
					zaid.stopSkillEffects(7029);
					zaid.setInChaosPvps(0);
					zaid.setIsInChaosEvent(false);
				}
			}
		}
		try
		{
			Thread.sleep(_laikas_aktyv);
		}
		catch (Exception e)
		{
		}
		registracija();
	}
	
	public static void registracija()
	{
		setState(State.REGISTRACIJA);
		Broadcast.announceToOnlinePlayers("Chaos Event: Registration is opened for 5 minutes.");
		Broadcast.announceToOnlinePlayers("Chaos Event: Avaible commands - [.chaosjoin] and [.chaosleave]!");
		try
		{
			Thread.sleep(120000);
		}
		catch (Exception e)
		{
		}
		Broadcast.announceToOnlinePlayers("Chaos Event: Registration will be closed in 3 minutes.");
		try
		{
			Thread.sleep(120000);
		}
		catch (Exception e)
		{
		}
		Broadcast.announceToOnlinePlayers("Chaos Event: Registration will be closed in 1 minute.");
		try
		{
			Thread.sleep(55000);
		}
		catch (Exception e)
		{
		}
		Broadcast.announceToOnlinePlayers("Chaos Event: Registration will be closed in 1 minute.");
		try
		{
			Thread.sleep(1000);
		}
		catch (Exception e)
		{
		}
		Broadcast.announceToOnlinePlayers("Chaos Event: Registration will be closed in 5 sec.");
		try
		{
			Thread.sleep(1000);
		}
		catch (Exception e)
		{
		}
		Broadcast.announceToOnlinePlayers("Chaos Event: Registration will be closed in 4 sec.");
		try
		{
			Thread.sleep(1000);
		}
		catch (Exception e)
		{
		}
		Broadcast.announceToOnlinePlayers("Chaos Event: Registration will be closed in 3 sec.");
		try
		{
			Thread.sleep(1000);
		}
		catch (Exception e)
		{
		}
		Broadcast.announceToOnlinePlayers("Chaos Event: Registration will be closed in 2 sec.");
		try
		{
			Thread.sleep(1000);
		}
		catch (Exception e)
		{
		}
		Broadcast.announceToOnlinePlayers("Chaos Event: Registration will be closed in 1 sec.");
		try
		{
			Thread.sleep(1000);
		}
		catch (Exception e)
		{
		}
		Broadcast.announceToOnlinePlayers("Chaos Event: Registration closed. Event started!");
		
		setState(State.IJUNGTAS);
		
		for (L2PcInstance zaid : World.getInstance().getPlayers())
		{
			zaid.sendPacket(new PlaySound("skillsound7.sound_crystal_smelting"));
			if (zaid.isRegInChaosEvent())
			{
				SkillTable.getInstance().getInfo(7029, Config.CHAOS_EVENT_HASTE_LVL).getEffects(zaid, zaid);
				zaid.setIsInChaosEvent(true);
				zaid.setIsRegInChaosEvent(false);
			}
		}
		
		try
		{
			Thread.sleep(_laikas_vykim);
		}
		catch (Exception e)
		{
		}
		pradzia(false);
	}
	
	public static class ValueComparator implements Comparator<Object>
	{
		Map<L2PcInstance, Integer> base;
		
		public ValueComparator(Map<L2PcInstance, Integer> base1)
		{
			this.base = base1;
		}
		
		@Override
		public int compare(Object a, Object b)
		{
			if (base.get(a) < base.get(b))
				return 1;
			else if (base.get(a) == base.get(b))
				return 0;
			else
				return -1;
		}
	}
}