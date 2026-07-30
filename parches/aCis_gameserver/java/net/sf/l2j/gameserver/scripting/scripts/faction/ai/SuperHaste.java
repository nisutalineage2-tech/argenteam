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
package net.sf.l2j.gameserver.scripting.scripts.faction.ai;

import net.sf.l2j.commons.concurrent.ThreadPool;

import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.scripting.scripts.ai.L2AttackableAIScript;

public class SuperHaste extends L2AttackableAIScript
{
	private int time = 14400000;
	private static int time_end = 600000;
	
	public SuperHaste()
	{
		super("faction/ai");
		
		ThreadPool.scheduleAtFixedRate(new Runnable()
		{
			@Override
			public void run()
			{
				runEvent();
			}
		}, time, time);
	}
	
	public static void runEvent()
	{
		for (L2PcInstance zaid : World.getInstance().getPlayers())
		{
			zaid.sendMessage("THIS IS MADNESS! SUPER HASTE FOR ALL!");
			zaid.sendPacket(new PlaySound("skillsound7.sound_crystal_smelting"));
			if (zaid.getFactionId() > 0)
			{
				zaid.setCurrentHpMp(zaid.getMaxHp(), zaid.getMaxMp());
				zaid.setCurrentCp(zaid.getMaxCp());
				SkillTable.getInstance().getInfo(7029, 1).getEffects(zaid, zaid);
			}
		}
		ThreadPool.scheduleAtFixedRate(new Runnable()
		{
			@Override
			public void run()
			{
				endEvent();
			}
		}, time_end, time_end);
	}
	
	public static void endEvent()
	{
		for (L2PcInstance zaid : World.getInstance().getPlayers())
		{
			zaid.sendMessage("THE MADNESS EVENT JUST ENDED! Muhahahahaa.");
			zaid.sendPacket(new PlaySound("skillsound7.sound_crystal_smelting"));
			if (zaid.getFactionId() > 0)
				zaid.stopAllToggles();
		}
	}
}