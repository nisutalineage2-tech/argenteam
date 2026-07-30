package net.sf.l2j.gameserver.scripting.scripts.faction.event;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.model.actor.L2Attackable;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.holder.IntIntHolder;
import net.sf.l2j.gameserver.scripting.Quest;
import net.sf.l2j.gameserver.util.Broadcast;

/**
 * @autor: fissban
 */
public class RaidSpawn extends Quest
{
	private static final int _first_event = 1;// min
	private static final int _time_event = 2;// min
	
	private static List<L2Npc> _npc_spawn = new ArrayList<>();
	
	private static final int[] _raids =
	{
		25514,
		22216,
		25286,
		25188,
		25029,
		25044,
	};
	
	private static final String[] _name_raids =
	{
		"Queen Shyeed",
		"Tyrannosaurus",
		"Anakim",
		"Apepi",
		"Korin",
		"Barion"
	
	};
	
	private static final String[] _locations =
	{
		"in giran town EASY",
		"giran castle EASY",
		"oren town EASY",
		"gludin town EASY",
		"Ruins of Despair EASY",
		"Hunter Village EASY",
	};
	
	/**
	 * x, y, z.
	 */
	private static final int[][] _spawns =
	{
		{
			82302,
			148772,
			-3471
		},
		{
			114608,
			145072,
			-2656
		},
		{
			80807,
			55114,
			-1529
		},
		{
			-83063,
			150791,
			-3128
		},
		{
			-18196,
			142453,
			-3901
		},
		{
			116589,
			76268,
			-2729
		}
	
	};
	
	/**
	 * ItemdId, Chance, Max Drop, Min Drop.
	 */
	private static final int[][] DROPLIST =
	{
		{// Giant's Codex
			3481,
			100,
			1,
			1
		},
		{// Giant's Codex
			3481,
			100,
			1,
			1
		},
		{// Giant's Codex
			3481,
			100,
			1,
			1
		},
		{// Giant's Codex
			3481,
			100,
			1,
			1
		},
		{// Revita Pop
			3481,
			100,
			1,
			1
		}
	
	};
	
	public RaidSpawn()
	{
		super(-1, RaidSpawn.class.getSimpleName());
		
		addKillId(_raids);
		
		startQuestTimer("SpawnRaid", _first_event * 60000, null, null, false);
	}
	
	public static void main(String[] args)
	{
		new RaidSpawn();
	}
	
	@Override
	public String onKill(L2Npc npc, L2PcInstance killer, boolean isSummon)
	{
		cancelQuestTimer("DespawnRaid", null, null);
		startQuestTimer("SpawnRaid", _time_event * 60000, null, null, false);
		
		dropItem(npc, killer, DROPLIST);
		_npc_spawn.clear();
		Broadcast.announceToOnlinePlayers("Next Raid Spawn in " + _time_event);
		return super.onKill(npc, killer, isSummon);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, L2PcInstance player)
	{
		if (event.equals("SpawnRaid"))
		{
			final int random = Rnd.get(_raids.length - 1);
			
			L2Npc mobs = addSpawn(_raids[random], _spawns[random][0], _spawns[random][1], _spawns[random][2], 0, false, 0, false);
			_npc_spawn.add(mobs);
			
			Broadcast.announceToOnlinePlayers("Raid " + _name_raids[random] + " Spawn " + _locations[random]);
			Broadcast.announceToOnlinePlayers("Have " + _time_event + " minutes to kill");
			
			startQuestTimer("DespawnRaid", _time_event * 60000, null, null, false);
			return null;
		}
		if (event.equals("DespawnRaid"))
		{
			if (!_npc_spawn.isEmpty())
			{
				for (L2Npc h : _npc_spawn)
				{
					h.deleteMe();
				}
			}
			_npc_spawn.clear();
			startQuestTimer("SpawnRaid", 1000, null, null, false);// 1 min spawn raid
			return null;
		}
		return null;
	}
	
	private static void dropItem(L2Npc mob, L2PcInstance player, int[][] droplist)
	{
		final int chance = Rnd.get(100);
		
		for (int[] drop : droplist)
		{
			if (chance > drop[1])
			{
				((L2Attackable) mob).dropItem(player, new IntIntHolder(drop[0], Rnd.get(drop[2], drop[3])));
				return;
			}
		}
	}
}