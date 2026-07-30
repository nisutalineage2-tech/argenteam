package net.sf.l2j.gameserver.handler.itemhandlers;

import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.model.actor.L2Playable;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.zone.ZoneId;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;

public class BuffsCustom implements IItemHandler
{
    @Override
    public void useItem(L2Playable playable, ItemInstance item, boolean forceUse)
    {    
        if (!(playable instanceof L2PcInstance))
            return;
        
        final L2PcInstance player = (L2PcInstance) playable;
        
        sendHtml(player);
    }
    public static boolean check(L2PcInstance p)
    {        
        return p.isInsideZone(ZoneId.PEACE) && !p.isInCombat() && !p.isInOlympiadMode() && !p.isDead();
    }
    public static void sendHtml(L2PcInstance player){
        NpcHtmlMessage html = new NpcHtmlMessage(0);
        html.setFile("data/html/mods/buffs/buff.htm");
        player.sendPacket(html);
    }
}