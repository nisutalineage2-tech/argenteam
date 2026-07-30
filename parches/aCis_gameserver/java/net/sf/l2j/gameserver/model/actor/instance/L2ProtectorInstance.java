/* This program is free software; you can redistribute it and/or modify
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
package net.sf.l2j.gameserver.model.actor.instance;

import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;

/**
 * @author Ederik
 */
public class L2ProtectorInstance extends L2NpcInstance
{
	private ScheduledFuture<?> _aiTask;
	private int _factionId = 0;
	public String[] _texts =
	{
		", get the fuck out of here!",
		", you again?! Take this!",
		", who's your daddy?",
		", may the fail be with you!",
		", shit in your face!"
	};
	
	public void setFationId(int a)
	{
		_factionId = a;
	}
	
	public int getFactionId()
	{
		return _factionId;
	}
	
	private class ProtectorAI implements Runnable
	{
		private L2ProtectorInstance _caster;
		
		protected ProtectorAI(L2ProtectorInstance caster)
		{
			_caster = caster;
		}
		
		@Override
		public void run()
		{
			/**
			 * For each known player in range, cast sleep if pvpFlag != 0 or Karma >0 Skill use is just for buff animation
			 */
			for (L2PcInstance player : getKnownType(L2PcInstance.class))
			{
				if (player.getFactionId() != getFactionId())
					handleCast(player, 1092, 19);
			}
		}
		
		private boolean handleCast(L2PcInstance player, int skillId, int skillLevel)
		{
			if (player.isGM() || player.isDead() || !player.isVisible() || !isInsideRadius(player, 1250, false, false))
				return false;
			
			L2Skill skill = SkillTable.getInstance().getInfo(skillId, skillLevel);
			
			if (player.getFirstEffect(skill) == null)
			{
				int objId = _caster.getObjectId();
				skill.getEffects(_caster, player);
				broadcastPacket(new MagicSkillUse(_caster, player, skillId, skillLevel, 5, 0));
				broadcastPacket(new CreatureSay(objId, 0, String.valueOf(getName()), player.getName() + _texts[Rnd.get(0, 4)]));
				player.doDie(_caster);
				
				skill = null;
				return true;
			}
			
			return false;
		}
	}
	
	public L2ProtectorInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
		
		if (_aiTask != null)
		{
			_aiTask.cancel(true);
		}
		
		_aiTask = ThreadPool.scheduleAtFixedRate(new ProtectorAI(this), 3000, 3000);
	}
	
	@Override
	public void deleteMe()
	{
		if (_aiTask != null)
		{
			_aiTask.cancel(true);
			_aiTask = null;
		}
		
		super.deleteMe();
	}
	
	@Override
	public boolean isAutoAttackable(L2Character attacker)
	{
		return false;
	}
}
