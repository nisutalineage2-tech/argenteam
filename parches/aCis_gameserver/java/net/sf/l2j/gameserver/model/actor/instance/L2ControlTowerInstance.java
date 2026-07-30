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
package net.sf.l2j.gameserver.model.actor.instance;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.datatables.NpcTable;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.model.L2Spawn;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.entity.Siege;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MoveToPawn;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

public class L2ControlTowerInstance extends L2Npc
{
	private final List<L2Spawn> _guards = new ArrayList<>();
	
	private boolean _isActive = true;
	
	public L2ControlTowerInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public boolean isAttackable()
	{
		// Attackable during siege by attacker only
		return getCastle() != null && getCastle().getSiege().isInProgress();
	}
	
	@Override
	public boolean isAutoAttackable(L2Character attacker)
	{
		// Attackable during siege by attacker only
		return attacker instanceof L2PcInstance && getCastle() != null && getCastle().getSiege().isInProgress() && getCastle().getSiege().checkIsAttacker(((L2PcInstance) attacker).getClan());
	}
	
	@Override
	public void onForcedAttack(L2PcInstance player)
	{
		onAction(player);
	}
	
	@Override
	public void onAction(L2PcInstance player)
	{
		// Set the target of the L2PcInstance player
		if (player.getTarget() != this)
			player.setTarget(this);
		else
		{
			if (isAutoAttackable(player) && Math.abs(player.getZ() - getZ()) < 100 && GeoEngine.getInstance().canSeeTarget(player, this))
			{
				// Notify the L2PcInstance AI with INTERACT
				player.getAI().setIntention(CtrlIntention.ATTACK, this);
			}
			else
			{
				// Rotate the player to face the instance
				player.sendPacket(new MoveToPawn(player, this, L2Npc.INTERACTION_DISTANCE));
				
				// Send ActionFailed to the player in order to avoid he stucks
				player.sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}
	
	@Override
	public boolean doDie(L2Character killer)
	{
		if (getCastle() != null)
		{
			final Siege siege = getCastle().getSiege();
			if (siege.isInProgress())
			{
				_isActive = false;
				
				for (L2Spawn spawn : _guards)
					spawn.setRespawnState(false);
				
				_guards.clear();
				
				// If siege life controls reach 0, broadcast a message to defenders.
				if (siege.getControlTowerCount() == 0)
					siege.announceToPlayer(SystemMessage.getSystemMessage(SystemMessageId.TOWER_DESTROYED_NO_RESURRECTION), false);
				
				// Spawn a little version of it. This version is a simple NPC, cleaned on siege end.
				try
				{
					final L2Spawn spawn = new L2Spawn(NpcTable.getInstance().getTemplate(13003));
					spawn.setLoc(getPosition());
					
					final L2Npc tower = spawn.doSpawn(false);
					tower.setCastle(getCastle());
					
					siege.getDestroyedTowers().add(tower);
				}
				catch (Exception e)
				{
					_log.warning(getClass().getName() + ": Cannot spawn control tower! " + e);
				}
			}
		}
		return super.doDie(killer);
	}
	
	public void registerGuard(L2Spawn guard)
	{
		_guards.add(guard);
	}
	
	public final List<L2Spawn> getGuards()
	{
		return _guards;
	}
	
	public final boolean isActive()
	{
		return _isActive;
	}
}