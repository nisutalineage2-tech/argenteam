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

package net.sf.l2j.gameserver.NewbiesSystem;



import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;

import net.sf.l2j.gameserver.model.base.ClassId;

import net.sf.l2j.gameserver.model.item.instance.ItemInstance;

import net.sf.l2j.gameserver.network.serverpackets.InventoryUpdate;



/**

 * @author Baggos

 */

public class NewbiesNpc

{

    public static void giveItems(int Classes, L2PcInstance player)

    {

        final int[] DaggerArmors =

        {

        	5618,

            2395,

            5787,

            5775,

            2419,

            858,

            858,

            889,

            889,

            920,

        };

        final int[] ArcherArmors =

        {

        	4831,

            2395,

            5787,

            5775,

            2419,

            858,

            858,

            889,

            889,

            920,

        };

        final int[] MageArmors =

        {

        	5643,

            2407,

            5767,

            5779,
            
            6377,
            
            512,

            858,

            858,

            889,

            889,

            920,

        };

        final int[] DuelistArmor =

        {

        	8588,

            2382,

            5768,

            5780,

            547,

            547,

            858,

            858,

            889,

            889,

            920,

        };

        final int[] TitanArmor =

        {

        	5646,

            2382,

            5768,

            5780,

            547,

            547,

            858,

            858,

            889,

            889,

            920,

        };

        final int[] GrandKhaArmors =

        {

        	5623,

            2395,

            5787,

            5775,

            2419,

            858,

            858,

            889,

            889,

            920,

        };

        final int[] TankArmors =

        {

        	5647,

            2382,

            5768,

            5780,

            547,

            6377,

            547,

            858,

            858,

            889,

            889,

            920,

        };

        final int[] DwarfArmors =

        {

        	5603,

            2382,

            5768,

            5780,

            547,

            6377,

            547,

            858,

            858,

            889,

            889,

            920,

        };

        final int[] DreadArmors =

        {

        	5634,

            2382,

            5768,

            5780,

            547,

            858,

            858,

            889,

            889,

            920,

        };

        final int[] DancerArmors =

        {

            6580,

            2395,

            5787,

            5775,

            2419,

            858,

            858,

            889,

            889,

            920,

        };

        

        ClassId classes = player.getClassId();

        switch (classes)

        {

            case ADVENTURER:

            case WIND_RIDER:

            case GHOST_HUNTER:

                if (DaggerArmors.length == 0)

                    return;

                ItemInstance items = null;

                for (int id : DaggerArmors)

                {

                    player.getInventory().addItem("Armors", id, 1, player, null);

                    items = player.getInventory().getItemByItemId(id);

                    player.getInventory().equipItemAndRecord(items);

                    player.getInventory().reloadEquippedItems();

                    player.broadcastCharInfo();

                    new InventoryUpdate();

                }

                break;

            case SAGGITARIUS:

            case GHOST_SENTINEL:

            case MOONLIGHT_SENTINEL:

                if (ArcherArmors.length == 0)

                    return;

                for (int id : ArcherArmors)

                {

                    player.getInventory().addItem("Armors", id, 1, player, null);

                    items = player.getInventory().getItemByItemId(id);

                    player.getInventory().equipItemAndRecord(items);

                    player.getInventory().reloadEquippedItems();

                    player.broadcastCharInfo();

                    new InventoryUpdate();

                }

                break;

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
            	
            case HIEROPHANT:

                if (MageArmors.length == 0)

                    return;

                for (int id : MageArmors)

                {

                    player.getInventory().addItem("Armors", id, 1, player, null);

                    items = player.getInventory().getItemByItemId(id);

                    player.getInventory().equipItemAndRecord(items);

                    player.getInventory().reloadEquippedItems();

                    player.broadcastCharInfo();

                    new InventoryUpdate();

                }

                break;

            case DUELIST:

                if (DuelistArmor.length == 0)

                    return;

                for (int id : DuelistArmor)

                {

                    player.getInventory().addItem("Armors", id, 1, player, null);

                    items = player.getInventory().getItemByItemId(id);

                    player.getInventory().equipItemAndRecord(items);

                    player.getInventory().reloadEquippedItems();

                    player.broadcastCharInfo();

                    new InventoryUpdate();

                }

                break;

            case TITAN:

                if (TitanArmor.length == 0)

                    return;

                for (int id : TitanArmor)

                {

                    player.getInventory().addItem("Armors", id, 1, player, null);

                    items = player.getInventory().getItemByItemId(id);

                    player.getInventory().equipItemAndRecord(items);

                    player.getInventory().reloadEquippedItems();

                    player.broadcastCharInfo();

                    new InventoryUpdate();

                }

                break;

            case GRAND_KHAVATARI:

                if (GrandKhaArmors.length == 0)

                    return;

                for (int id : GrandKhaArmors)

                {

                    player.getInventory().addItem("Armors", id, 1, player, null);

                    items = player.getInventory().getItemByItemId(id);

                    player.getInventory().equipItemAndRecord(items);

                    player.getInventory().reloadEquippedItems();

                    player.broadcastCharInfo();

                    new InventoryUpdate();

                }

                break;

            case PHOENIX_KNIGHT:

            case HELL_KNIGHT:

            case EVAS_TEMPLAR:

            case SHILLIEN_TEMPLAR:

                if (TankArmors.length == 0)

                    return;

                for (int id : TankArmors)

                {

                    player.getInventory().addItem("Armors", id, 1, player, null);

                    items = player.getInventory().getItemByItemId(id);

                    player.getInventory().equipItemAndRecord(items);

                    player.getInventory().reloadEquippedItems();

                    player.broadcastCharInfo();

                    new InventoryUpdate();

                }

                break;

            case FORTUNE_SEEKER:

            case MAESTRO:

                if (DwarfArmors.length == 0)

                    return;

                for (int id : DwarfArmors)

                {

                    player.getInventory().addItem("Armors", id, 1, player, null);

                    items = player.getInventory().getItemByItemId(id);

                    player.getInventory().equipItemAndRecord(items);

                    player.getInventory().reloadEquippedItems();

                    player.broadcastCharInfo();

                    new InventoryUpdate();

                }

                break;

            case DREADNOUGHT:

                if (DreadArmors.length == 0)

                    return;

                for (int id : DreadArmors)

                {

                    player.getInventory().addItem("Armors", id, 1, player, null);

                    items = player.getInventory().getItemByItemId(id);

                    player.getInventory().equipItemAndRecord(items);

                    player.getInventory().reloadEquippedItems();

                    player.broadcastCharInfo();

                    new InventoryUpdate();

                }

                break;

            case SPECTRAL_DANCER:

            case SWORD_MUSE:

                if (DancerArmors.length == 0)

                    return;

                for (int id : DancerArmors)

                {

                    player.getInventory().addItem("Armors", id, 1, player, null);

                    items = player.getInventory().getItemByItemId(id);

                    player.getInventory().equipItemAndRecord(items);

                    player.getInventory().reloadEquippedItems();

                    player.broadcastCharInfo();

                    new InventoryUpdate();

                }

                break;

        }

        

    }

    

}