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
package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.datatables.ArmorSetsTable;
import net.sf.l2j.gameserver.datatables.EnchantTable;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.model.L2EnchantScroll;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.item.ArmorSet;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.kind.Armor;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.item.kind.Weapon;
import net.sf.l2j.gameserver.model.item.type.WeaponType;
import net.sf.l2j.gameserver.model.itemcontainer.Inventory;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.EnchantResult;
import net.sf.l2j.gameserver.network.serverpackets.InventoryUpdate;
import net.sf.l2j.gameserver.network.serverpackets.ItemList;
import net.sf.l2j.gameserver.network.serverpackets.StatusUpdate;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.util.Util;

import main.EngineModsManager;

public final class RequestEnchantItem extends L2GameClientPacket
{
	private int _objectId = 0;
	
	@Override
	protected void readImpl()
	{
		_objectId = readD();
	}
	
	@Override
	protected void runImpl()
	{
		// get player
		final L2PcInstance activeChar = getClient().getActiveChar();
		if (activeChar == null || _objectId == 0)
			return;
		// player online and active
		if (!activeChar.isOnline() || getClient().isDetached())
		{
			activeChar.setActiveEnchantItem(null);
			return;
		}
		// player on shop/craft
		if (activeChar.isProcessingTransaction() || activeChar.isInStoreMode())
		{
			activeChar.sendPacket(SystemMessageId.CANNOT_ENCHANT_WHILE_STORE);
			activeChar.setActiveEnchantItem(null);
			activeChar.sendPacket(EnchantResult.CANCELLED);
			return;
		}
		
		          // player trading
		          if (activeChar.getActiveTradeList() != null)
		          {
		                  activeChar.cancelActiveTrade();
		                  activeChar.sendPacket(SystemMessageId.TRADE_ATTEMPT_FAILED);
		                  activeChar.setActiveEnchantItem(null);
		                  activeChar.sendPacket(EnchantResult.CANCELLED);
		                  return;
		          }
		               
		          // get item and enchant scroll
		
		ItemInstance item = activeChar.getInventory().getItemByObjectId(_objectId);
		ItemInstance scroll = activeChar.getActiveEnchantItem();
		
		if (item == null || scroll == null)
		{
			activeChar.setActiveEnchantItem(null);
			activeChar.sendPacket(SystemMessageId.ENCHANT_SCROLL_CANCELLED);
			activeChar.sendPacket(EnchantResult.CANCELLED);
			return;
		}
		
		             // get scroll enchant data
		             L2EnchantScroll enchant = EnchantTable.getInstance().getEnchantScroll(scroll);
		             if (enchant == null)
			return;
		
		                       // validation check
		                       if (!isEnchantable(item) || !enchant.isValid(item) || item.getOwnerId() != activeChar.getObjectId())
		{
			activeChar.sendPacket(SystemMessageId.INAPPROPRIATE_ENCHANT_CONDITION);
			activeChar.setActiveEnchantItem(null);
			activeChar.sendPacket(EnchantResult.CANCELLED);
			return;
		}
		
		                    // destroy enchant scroll
		scroll = activeChar.getInventory().destroyItem("Enchant", scroll.getObjectId(), 1, activeChar, item);
		if (scroll == null)
		{
			activeChar.sendPacket(SystemMessageId.NOT_ENOUGH_ITEMS);
			Util.handleIllegalPlayerAction(activeChar, activeChar.getName() + " tried to enchant without scroll.", Config.DEFAULT_PUNISH);
			activeChar.setActiveEnchantItem(null);
			activeChar.sendPacket(EnchantResult.CANCELLED);
			return;
		}
		

		
		synchronized (item)
		{
			
			// success
			if (Rnd.get(100) < enchant.getChance(item))
			{
				// send message
				SystemMessage sm;
				
				if (item.getEnchantLevel() == 0)
				{
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_SUCCESSFULLY_ENCHANTED);

				}
				else
				{
					sm = SystemMessage.getSystemMessage(SystemMessageId.S1_S2_SUCCESSFULLY_ENCHANTED);
					sm.addNumber(item.getEnchantLevel());

				}
				                             sm.addItemName(item.getItemId());
               
				                              // update item				
				item.setEnchantLevel(item.getEnchantLevel() + 1);
				item.updateDatabase();
				
				// If item is equipped, verify the skill obtention (+4 duals, +6 armorset).
				if (item.isEquipped())
				{
					final Item it = item.getItem();
					
					// Add skill bestowed by +4 duals.
					if (it instanceof Weapon && item.getEnchantLevel() == 4)
					{
						final L2Skill enchant4Skill = ((Weapon) it).getEnchant4Skill();
						if (enchant4Skill != null)
						{
							activeChar.addSkill(enchant4Skill, false);
							activeChar.sendSkillList();
						}
					}
					// Add skill bestowed by +6 armorset.
					else if (it instanceof Armor && item.getEnchantLevel() == 6)
					{
						// Checks if player is wearing a chest item
						final ItemInstance chestItem = activeChar.getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
						if (chestItem != null)
						{
							final ArmorSet armorSet = ArmorSetsTable.getInstance().getSet(chestItem.getItemId());
							if (armorSet != null && armorSet.isEnchanted6(activeChar)) // has all parts of set enchanted to 6 or more
							{
								final int skillId = armorSet.getEnchant6skillId();
								if (skillId > 0)
								{
									final L2Skill skill = SkillTable.getInstance().getInfo(skillId, 1);
									if (skill != null)
									{
										activeChar.addSkill(skill, false);
										activeChar.sendSkillList();
									}
								}
							}
						}
					}
				}
				activeChar.sendPacket(EnchantResult.SUCCESS);
			}
			else
			{
				// Drop passive skills from items.
				if (item.isEquipped())
				{
					final Item it = item.getItem();
					
					// Remove skill bestowed by +4 duals.
					if (it instanceof Weapon && item.getEnchantLevel() >= 4)
					{
						final L2Skill enchant4Skill = ((Weapon) it).getEnchant4Skill();
						if (enchant4Skill != null)
						{
							activeChar.removeSkill(enchant4Skill, false);
							activeChar.sendSkillList();
						}
					}
					// Add skill bestowed by +6 armorset.
					else if (it instanceof Armor && item.getEnchantLevel() >= 6)
					{
						// Checks if player is wearing a chest item
						final ItemInstance chestItem = activeChar.getInventory().getPaperdollItem(Inventory.PAPERDOLL_CHEST);
						if (chestItem != null)
						{
							final ArmorSet armorSet = ArmorSetsTable.getInstance().getSet(chestItem.getItemId());
							if (armorSet != null && armorSet.isEnchanted6(activeChar)) // has all parts of set enchanted to 6 or more
							{
								final int skillId = armorSet.getEnchant6skillId();
								if (skillId > 0)
								{
									final L2Skill skill = SkillTable.getInstance().getInfo(skillId, 1);
									if (skill != null)
									{
										activeChar.removeSkill(skill, false);
										activeChar.sendSkillList();
									}
								}
							}
						}
					}
				}
				
				if (!enchant.canBreak())
				{
					// keep item
					activeChar.sendPacket(SystemMessageId.BLESSED_ENCHANT_FAILED);
					
					                                      if (!enchant.canMaintain())
						                                      {
						                                              item.setEnchantLevel(0);
					                                               item.updateDatabase();
						                                     }
					activeChar.sendPacket(EnchantResult.UNSUCCESS);
				}
				else
				{
					                                      // destroy item
					
					ItemInstance destroyItem = activeChar.getInventory().destroyItem("Enchant", item, activeChar, null);
					if (destroyItem == null)
					{
						// unable to destroy item, cheater ?
						Util.handleIllegalPlayerAction(activeChar, "Unable to delete item on enchant failure from player " + activeChar.getName() + ", possible cheater !", Config.DEFAULT_PUNISH);
						activeChar.setActiveEnchantItem(null);
						activeChar.sendPacket(EnchantResult.CANCELLED);
						return;
					}
					
					                                     // add crystals, if item crystalizable
					                                     int crystalType = item.getItem().getCrystalItemId();
					                                     ItemInstance crystals = null;                                  
					                                     if (crystalType != 0)
					{
					                                    	  // get crystals count
					                                    	  int crystalCount = item.getCrystalCount() - (item.getItem().getCrystalCount() + 1) / 2;
					                                    	  if (crystalCount < 1)
					                                    	          crystalCount = 1;
					                                    	                                         
					                                    	  // add crystals to inventory
					                                    	  crystals = activeChar.getInventory().addItem("Enchant", crystalType, crystalCount, activeChar, destroyItem);
					                                    	  activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.EARNED_S2_S1_S).addItemName(crystals.getItemId()).addItemNumber(crystalCount));
					}
					                                  // update inventory				
					InventoryUpdate iu = new InventoryUpdate();
					if (destroyItem.getCount() == 0)
						iu.addRemovedItem(destroyItem);
					else
						iu.addModifiedItem(destroyItem);
					
					activeChar.sendPacket(iu);
					
					// Messages.
					 // remove item
					 World.getInstance().removeObject(destroyItem);
					
					 // send message					
					if (item.getEnchantLevel() > 0)
						activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ENCHANTMENT_FAILED_S1_S2_EVAPORATED).addNumber(item.getEnchantLevel()).addItemName(item.getItemId()));
					else
						activeChar.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.ENCHANTMENT_FAILED_S1_EVAPORATED).addItemName(item.getItemId()));
					
					                               // send enchant result
					                                  if (crystalType == 0)
						activeChar.sendPacket(EnchantResult.UNK_RESULT_4);
					else
						activeChar.sendPacket(EnchantResult.UNK_RESULT_1);
					                               // update weight
					StatusUpdate su = new StatusUpdate(activeChar);
					su.addAttribute(StatusUpdate.CUR_LOAD, activeChar.getCurrentLoad());
					activeChar.sendPacket(su);
				}
			}
			
			// custom by fissban
			EngineModsManager.onEnchant(activeChar);
			
			activeChar.sendPacket(new ItemList(activeChar, false));
			activeChar.broadcastUserInfo();
			activeChar.setActiveEnchantItem(null);
		}
	}
	     /**
	      * @param item The instance of item to make checks on.
	      * @return true if item can be enchanted.
	      */
	     private static final boolean isEnchantable(ItemInstance item)
	     {
	             if (item.isHeroItem() || item.isShadowItem() || item.isEtcItem() || item.getItem().getItemType() == WeaponType.FISHINGROD)
	                     return false;
	            
	             // only equipped items or in inventory can be enchanted
	             if (item.getLocation() != ItemInstance.ItemLocation.INVENTORY && item.getLocation() != ItemInstance.ItemLocation.PAPERDOLL)
	                     return false;
	            
	             return true;
	     }  	
}