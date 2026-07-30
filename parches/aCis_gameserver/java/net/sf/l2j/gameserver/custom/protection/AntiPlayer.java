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

package net.sf.l2j.gameserver.custom.protection;

import java.util.logging.Logger;

import net.sf.l2j.commons.concurrent.ThreadPool;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;

public class AntiPlayer
{
	private static final Logger _log = Logger.getLogger(AntiPlayer.class.getName());
	
	public static void getInstance()
	{
		ThreadPool.schedule(new Runnable()
		{
			@Override
			public void run()
			{
				antiplayer();
			}
		}, 60 * 1000 * Config.ANTI_PLAYER_TIME);
		
		_log.info("AntiPlayer: Loaded protection.");
	}
	
	static void antiplayer()
	{
		for (L2PcInstance player : World.getInstance().getPlayers())
		{
			player.getX = player.getX();
			player.getY = player.getY();
			player.getZ = player.getZ();
		}
		waitSecs(60 * Config.ANTI_PLAYER_TIME);
		
		for (L2PcInstance player : World.getInstance().getPlayers())
		{
			if (player.getX == player.getX() && player.getY == player.getY() && player.getZ == player.getZ() && !player.isGM())
			{
				player.logout(true);
				
				_log.info("AntiPlayer: Player is now disconnected.");
			}
		}
		waitSecs(60 * Config.ANTI_PLAYER_TIME);
		antiplayer();
	}
	
	public static void waitSecs(int i)
	{
		try
		{
			Thread.sleep(i * 1000);
		}
		catch (InterruptedException ie)
		{
			ie.printStackTrace();
		}
	}
}