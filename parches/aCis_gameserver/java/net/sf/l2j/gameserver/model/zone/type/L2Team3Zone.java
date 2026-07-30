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
package net.sf.l2j.gameserver.model.zone.type;

import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2SummonInstance;
import net.sf.l2j.gameserver.model.zone.L2ZoneType;

/**
 * Team 1 Zone
 * @author DarthVader
 */
public class L2Team3Zone extends L2ZoneType
{
	public L2Team3Zone(int id)
	{
		super(id);
	}

	@Override
	protected void onEnter(L2Character character)
	{
		if (character instanceof L2PcInstance) {
			if (((L2PcInstance) character).getFactionId() != 3) {
				((L2PcInstance) character).doDie(null);
				((L2PcInstance) character).sendMessage("You have entered enemy faction zone and died!");
			}
		}
		else if (character instanceof L2SummonInstance) {
			if (((L2SummonInstance) character).getOwner().getFactionId() != 3) {
				((L2SummonInstance) character).doDie(null);
				((L2SummonInstance) character).getOwner().sendMessage("Your summon have entered enemy faction zone and died!");
			}
		}
	}

	@Override
	protected void onExit(L2Character character) {}

	@Override
	public void onDieInside(L2Character character)
	{
	}

	@Override
	public void onReviveInside(L2Character character) {}
}