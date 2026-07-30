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



import net.sf.l2j.Config;

import net.sf.l2j.gameserver.NewbiesSystem.NewbiesNpc;

import net.sf.l2j.gameserver.datatables.CharTemplateTable;

import net.sf.l2j.gameserver.datatables.SkillTable;

import net.sf.l2j.gameserver.model.L2Skill;

import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;

import net.sf.l2j.gameserver.model.base.ClassId;

import net.sf.l2j.gameserver.model.base.Experience;

import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;

import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;

import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;



/**

 * @author Baggos

 */

public class L2NewbieNpcInstance extends L2NpcInstance

{

    public L2NewbieNpcInstance(int objectId, NpcTemplate template)

    {

        super(objectId, template);

    }

    

    @Override

    public void onBypassFeedback(L2PcInstance player, String command)

    {

        

        if (player == null)

            return;

        

        if (!Config.ALLOW_CLASS_MASTERS)

            return;

        

        if (command.equalsIgnoreCase("change"))

        {

            String filename = "data/html/mods/NewbieNpc/changeclass.htm";

            

            if (Config.ALLOW_CLASS_MASTERS)

                filename = "data/html/mods/NewbieNpc/changeclass.htm";

            

            final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());

            html.setFile(filename);

            html.replace("%objectId%", getObjectId());

            player.sendPacket(html);

        }

        if (command.startsWith("1stClass"))

            L2ClassMasterInstance.showHtmlMenu(player, getObjectId(), 1);

        else if (command.startsWith("2ndClass"))

            L2ClassMasterInstance.showHtmlMenu(player, getObjectId(), 2);

        else if (command.startsWith("3rdClass"))

            L2ClassMasterInstance.showHtmlMenu(player, getObjectId(), 3);

        else if (command.startsWith("change_class"))

        {

            int val = Integer.parseInt(command.substring(13));

            

            if (L2ClassMasterInstance.checkAndChangeClass(player, val))

            {

                final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());

                html.setFile("data/html/classmaster/ok.htm");

                html.replace("%name%", CharTemplateTable.getInstance().getClassNameById(val));

                player.sendPacket(html);

            }

        }

        else if (command.equalsIgnoreCase("LevelUp"))

        {

            player.addExpAndSp(Experience.LEVEL[Config.NEWBIE_LVL], 0);

        }

        

        else if (command.equalsIgnoreCase("items"))

        {

            final ClassId currentClassId = player.getClassId();

            if (currentClassId.level() < 3)

            {

                player.sendMessage("First Complete Your Third Class!");

                return;

            }

            

            ClassId classes = player.getClassId();

            switch (classes)

            {

                case ADVENTURER:

                case SAGGITARIUS:

                case DUELIST:

                case TITAN:

                case GRAND_KHAVATARI:

                case PHOENIX_KNIGHT:

                case MOONLIGHT_SENTINEL:

                case FORTUNE_SEEKER:

                case MAESTRO:

                case DREADNOUGHT:

                case HELL_KNIGHT:

                case EVAS_TEMPLAR:

                case SWORD_MUSE:

                case WIND_RIDER:

                case SHILLIEN_TEMPLAR:

                case SPECTRAL_DANCER:

                case GHOST_HUNTER:

                case GHOST_SENTINEL:

                case SOULTAKER:

                case MYSTIC_MUSE:

                case ARCHMAGE:

                case ARCANA_LORD:

                case ELEMENTAL_MASTER:

                case CARDINAL:

                case STORM_SCREAMER:

                case SPECTRAL_MASTER:

                case SHILLIEN_SAINT:

                case DOMINATOR:

                case DOOMCRYER:

                    NewbiesNpc.giveItems(0, player);

                    break;

            }

        }

        

        else if (command.equalsIgnoreCase("buffs"))

        {

            if (player.isMageClass() || player.getClassId() == ClassId.DOMINATOR || player.getClassId() == ClassId.DOOMCRYER)

            {

                int mageSet[] = Config.NEWBIE_MAGE_BUFFS;

                L2Skill buff;

                for (int id : mageSet)

                {

                    buff = SkillTable.getInstance().getInfo(id, SkillTable.getInstance().getMaxLevel(id));

                    buff.getEffects(this, player);

                    player.setCurrentHp(player.getMaxHp());

                    player.setCurrentCp(player.getMaxCp());

                    player.setCurrentMp(player.getMaxMp());

                    player.broadcastPacket(new MagicSkillUse(this, player, id, buff.getLevel(), 0, 0));

                }

            }

            else

            {

                int fighterSet[] = Config.NEWBIE_FIGHTER_BUFFS;

                L2Skill buff;

                for (int id : fighterSet)

                {

                    buff = SkillTable.getInstance().getInfo(id, SkillTable.getInstance().getMaxLevel(id));

                    buff.getEffects(this, player);

                    player.setCurrentHp(player.getMaxHp());

                    player.setCurrentCp(player.getMaxCp());

                    player.setCurrentMp(player.getMaxMp());

                    player.broadcastPacket(new MagicSkillUse(this, player, id, buff.getLevel(), 0, 0));

                }

            }

        }

        else if (command.equalsIgnoreCase("teleport"))

        {

            final ClassId currentClassId = player.getClassId();

            if (currentClassId.level() < 3)

            {

                player.sendMessage("You Can't Leave! Your Character Isin't Complete!");

                return;

            }

            player.teleToLocation(Config.SETX, Config.SETY, Config.SETZ, 0);

            player.sendPacket(new ExShowScreenMessage("seconds until auto respawn", 4000, 2, true));

        }

    }

    

    @Override

    public String getHtmlPath(int npcId, int val)

    {

        String filename = "";

        

        if (val == 0)

            filename = "" + npcId;

        else

            filename = npcId + "-" + val;

        

        return "data/html/mods/newbieNpc/" + filename + ".htm";

    }

}