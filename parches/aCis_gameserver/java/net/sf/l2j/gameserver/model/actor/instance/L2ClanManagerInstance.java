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

import net.sf.l2j.commons.random.Rnd;


import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MyTargetSelected;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.network.serverpackets.ValidateLocation;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;


/**
 * @author Debian
 *
 */
public class L2ClanManagerInstance extends L2NpcInstance
{
	public L2ClanManagerInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2PcInstance player, String command)
	{
		if (command.startsWith("levelup"))
		{
			if(player.getClan() != null)
			{
				if (!player.isClanLeader())
				{
					player.sendMessage("Only clan leaders, can use this service.");
				}
				if (player.isClanLeader() && player.getClan().getReputationScore() > 50000)
				{
					player.getClan().takeReputationScore(50000);
					player.getClan().changeLevel(8);
					player.sendMessage("Your clan successfully changed to level 8.");
				}
				else
				{
					player.sendMessage("Your clan must have 50000 clan reputation points in order to buy level 8.");
				}
			}
			else
			{
				player.sendMessage("You don't have a clan.");
			}
		}
	}
	
    @Override
    public void onAction(L2PcInstance player)
    {
		if (this != player.getTarget()) {
			player.setTarget(this);
			player.sendPacket(new MyTargetSelected(getObjectId(), player.getLevel() - getLevel()));
			player.sendPacket(new ValidateLocation(this));
		}
		else if (isInsideRadius(player, INTERACTION_DISTANCE, false, false)) {
			SocialAction sa = new SocialAction(this, Rnd.get(8));
			broadcastPacket(sa);
			player.setCurrentFolkNPC(this);
			showMessageWindow(player);
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
		else {
			player.getAI().setIntention(CtrlIntention.INTERACT, this);
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
    }
    
    private void showMessageWindow(L2PcInstance player)
    {
        NpcHtmlMessage debian = new NpcHtmlMessage(5);
        StringBuilder tb = new StringBuilder("");
            
        tb.append("<html><head><title>Clan Manager</title></head><body>");
        tb.append("<center>");
        tb.append("<img src=\"L2Font-e.replay_logo-e\" width=256 height=80>");
        tb.append("<br>");
        tb.append("<img src=\"l2ui_ch3.herotower_deco\" width=256 height=32 align=center>"); 
        tb.append("<br>");
        tb.append("<font color=\"FFAA00\">Clan Manager</font>");
        tb.append("<br>");
        tb.append("</center>");
        tb.append("<center>");
        tb.append("<a action=\"bypass -h npc_" + getObjectId() + "_levelup\">Get your clan at level 8.</a><br>");
        tb.append("<font color=\"FFAA00\">It costs 50.000 clan reputation points.</font>");
        tb.append("<br>");
        tb.append("</center>");
        tb.append("<center>");
        tb.append("<img src=\"l2ui_ch3.herotower_deco\" width=256 height=32 align=center>");
        tb.append("<br>");

        tb.append("</center>");
        tb.append("</body></html>");
            
        debian.setHtml(tb.toString());
        player.sendPacket(debian);
    }	
}