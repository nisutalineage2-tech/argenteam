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
package net.sf.l2j.gameserver.handler.itemhandlers;

import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.model.actor.L2Playable;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;

/**
 * @author Bluur
 */
public class ClanFullSkill implements IItemHandler
{
    @Override
    public void useItem(L2Playable playable, ItemInstance item, boolean forceUse)
    {    
        if (!(playable instanceof L2PcInstance))
            return;
        
        final L2PcInstance player = (L2PcInstance) playable;
        
        if (player.isClanLeader())
        {
            for (int i = 370; i <= 391; i++){
                player.getClan().addNewSkill(SkillTable.getInstance().getInfo(i, SkillTable.getInstance().getMaxLevel(i)));            
            }
            player.sendPacket(new ExShowScreenMessage("Now your clan is Full Skill!" , 10000, 0x02, true));
            player.destroyItem("", item.getObjectId(), 1, null, true);    
        }
        else
            player.sendMessage("Only leaders of the clans can use this item!");
    }
}