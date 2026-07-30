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
package net.sf.l2j.gameserver.handler.skillhandlers;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.handler.ISkillHandler;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.instance.L2MonsterInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.manor.Seed;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.scripting.QuestState;
import net.sf.l2j.gameserver.templates.skills.L2SkillType;

public class Sow implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS =
	{
		L2SkillType.SOW
	};
	
	@Override
	public void useSkill(L2Character activeChar, L2Skill skill, L2Object[] targets)
	{
		if (!(activeChar instanceof L2PcInstance))
			return;
		
		final L2Object object = targets[0];
		if (!(object instanceof L2MonsterInstance))
			return;
		
		final L2PcInstance player = (L2PcInstance) activeChar;
		final L2MonsterInstance target = (L2MonsterInstance) object;
		
		if (target.isDead() || !target.isSeeded() || target.getSeederId() != activeChar.getObjectId())
			return;
		
		final Seed seed = target.getSeed();
		if (seed == null)
			return;
		
		// Consuming used seed
		if (!activeChar.destroyItemByItemId("Consume", seed.getSeedId(), 1, target, false))
			return;
		
		SystemMessageId smId;
		if (calcSuccess(activeChar, target, seed))
		{
			player.sendPacket(new PlaySound(QuestState.SOUND_ITEMGET));
			target.setSeeded(activeChar.getObjectId());
			smId = SystemMessageId.THE_SEED_WAS_SUCCESSFULLY_SOWN;
		}
		else
			smId = SystemMessageId.THE_SEED_WAS_NOT_SOWN;
		
		final Party party = player.getParty();
		if (party == null)
			player.sendPacket(smId);
		else
			party.broadcastMessage(smId);
		
		target.getAI().setIntention(CtrlIntention.IDLE);
	}
	
	private static boolean calcSuccess(L2Character activeChar, L2Character target, Seed seed)
	{
		final int minlevelSeed = seed.getLevel() - 5;
		final int maxlevelSeed = seed.getLevel() + 5;
		
		final int levelPlayer = activeChar.getLevel(); // Attacker Level
		final int levelTarget = target.getLevel(); // target Level
		
		int basicSuccess = (seed.isAlternative()) ? 20 : 90;
		
		// Seed level
		if (levelTarget < minlevelSeed)
			basicSuccess -= 5 * (minlevelSeed - levelTarget);
		
		if (levelTarget > maxlevelSeed)
			basicSuccess -= 5 * (levelTarget - maxlevelSeed);
		
		// 5% decrease in chance if player level is more than +/- 5 levels to _target's_ level
		int diff = (levelPlayer - levelTarget);
		if (diff < 0)
			diff = -diff;
		
		if (diff > 5)
			basicSuccess -= 5 * (diff - 5);
		
		// Chance can't be less than 1%
		if (basicSuccess < 1)
			basicSuccess = 1;
		
		return Rnd.get(99) < basicSuccess;
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}