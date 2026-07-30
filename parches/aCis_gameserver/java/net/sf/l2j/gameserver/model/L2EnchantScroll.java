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
package net.sf.l2j.gameserver.model;

import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.item.type.CrystalType;

public class L2EnchantScroll
{
       private final CrystalType _grade;
       private final boolean _weapon;
       private final boolean _breaks;
       private final boolean _maintain;
       private final byte[] _chance;
      
       public L2EnchantScroll(CrystalType grade, boolean weapon, boolean breaks, boolean maintain, byte[] chance)
       {
               _grade = grade;
               _weapon = weapon;
               _breaks = breaks;
               _maintain = maintain;
               _chance = chance;
       }
      
       /**
        * @param enchantItem : The item to enchant.
        * @return the enchant chance under double format.
        */
       public final byte getChance(ItemInstance enchantItem)
       {
               int level = enchantItem.getEnchantLevel();
               if (enchantItem.getItem().getBodyPart() == Item.SLOT_FULL_ARMOR && level != 0)
                       level--;
              
               if (level >= _chance.length)
                       return 0;
                      
               return _chance[level];
       }
      
       public final boolean canBreak()
       {
               return _breaks;
       }
      
       public final boolean canMaintain()
       {
               return _maintain;
       }
      
       // TODO: methods
      
       /**
        * @param enchantItem : The item to enchant.
        * @return True if enchant can be used on selected item.
        */
       public final boolean isValid(ItemInstance enchantItem)
       {
               // check for crystal type
               if (_grade != enchantItem.getItem().getCrystalType())
                       return false;
              
               // check enchant max level
               if (enchantItem.getEnchantLevel() >= _chance.length)
                       return false;
              
               // checking scroll type
               switch (enchantItem.getItem().getType2())
               {
                       case Item.TYPE2_WEAPON:
                               if (!_weapon)
                                       return false;
                               break;
                      
                       case Item.TYPE2_SHIELD_ARMOR:
                       case Item.TYPE2_ACCESSORY:
                               if (_weapon)
                                       return false;
                               break;
                      
                       default:
                               return false;
               }
              
               return true;
       }
}
