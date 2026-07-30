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
package net.sf.l2j.gameserver.model.group;

import java.util.List;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.L2GameServerPacket;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public abstract class AbstractGroup
{
	private L2PcInstance _leader;
	private int _level;
	
	public AbstractGroup(L2PcInstance leader)
	{
		_leader = leader;
	}
	
	/**
	 * @return a list of all members of this group.
	 */
	public abstract List<L2PcInstance> getMembers();
	
	/**
	 * @return the count of all players in this group.
	 */
	public abstract int getMembersCount();
	
	/**
	 * Check if this group contains a given player.
	 * @param player : the player to check.
	 * @return {@code true} if this group contains the specified player, {@code false} otherwise.
	 */
	public abstract boolean containsPlayer(final L2Object player);
	
	/**
	 * Broadcast a packet to every member of this group.
	 * @param packet : the packet to broadcast.
	 */
	public abstract void broadcastPacket(final L2GameServerPacket packet);
	
	/**
	 * Broadcast a CreatureSay packet to every member of this group. Similar to broadcastPacket, but with an embbed BlockList check.
	 * @param msg : the msg to broadcast.
	 * @param broadcaster : the player who broadcasts the message.
	 */
	public abstract void broadcastCreatureSay(final CreatureSay msg, final L2PcInstance broadcaster);
	
	/**
	 * Recalculate the group level.
	 */
	public abstract void recalculateLevel();
	
	/**
	 * Destroy that group, resetting all possible values, leading to that group object destruction.
	 */
	public abstract void disband();
	
	/**
	 * @return the level of this group.
	 */
	public int getLevel()
	{
		return _level;
	}
	
	/**
	 * Change the level of this group. <b>Used only when the group is created.</b>
	 * @param level : the level to set.
	 */
	public void setLevel(int level)
	{
		_level = level;
	}
	
	/**
	 * @return the leader of this group.
	 */
	public L2PcInstance getLeader()
	{
		return _leader;
	}
	
	/**
	 * Change the leader of this group to the specified player.
	 * @param leader : the player to set as the new leader of this group.
	 */
	public void setLeader(L2PcInstance leader)
	{
		_leader = leader;
	}
	
	/**
	 * @return the leader objectId.
	 */
	public int getLeaderObjectId()
	{
		return _leader.getObjectId();
	}
	
	/**
	 * Check if a given player is the leader of this group.
	 * @param player : the player to check.
	 * @return {@code true} if the specified player is the leader of this group, {@code false} otherwise.
	 */
	public boolean isLeader(L2PcInstance player)
	{
		return _leader.getObjectId() == player.getObjectId();
	}
	
	/**
	 * Broadcast a system message to this group.
	 * @param message : the system message to broadcast.
	 */
	public void broadcastMessage(SystemMessageId message)
	{
		broadcastPacket(SystemMessage.getSystemMessage(message));
	}
	
	/**
	 * Broadcast a custom text message to this group.
	 * @param text : the custom string to broadcast.
	 */
	public void broadcastString(String text)
	{
		broadcastPacket(SystemMessage.sendString(text));
	}
	
	/**
	 * @return a random member of this group.
	 */
	public L2PcInstance getRandomPlayer()
	{
		return Rnd.get(getMembers());
	}
}