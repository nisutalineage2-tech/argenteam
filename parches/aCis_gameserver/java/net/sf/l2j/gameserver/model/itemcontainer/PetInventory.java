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
package net.sf.l2j.gameserver.model.itemcontainer;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PetInstance;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance.ItemLocation;
import net.sf.l2j.gameserver.model.item.type.EtcItemType;

public class PetInventory extends Inventory
{
	private final L2PetInstance _owner;
	
	public PetInventory(L2PetInstance owner)
	{
		_owner = owner;
	}
	
	@Override
	public L2PetInstance getOwner()
	{
		return _owner;
	}
	
	@Override
	public int getOwnerId()
	{
		int id;
		try
		{
			id = _owner.getOwner().getObjectId();
		}
		catch (NullPointerException e)
		{
			return 0;
		}
		return id;
	}
	
	@Override
	protected void refreshWeight()
	{
		super.refreshWeight();
		
		getOwner().updateAndBroadcastStatus(1);
		getOwner().sendPetInfosToOwner();
	}
	
	public boolean validateCapacity(ItemInstance item)
	{
		int slots = 0;
		
		if (!(item.isStackable() && getItemByItemId(item.getItemId()) != null) && item.getItemType() != EtcItemType.HERB)
			slots++;
		
		return validateCapacity(slots);
	}
	
	@Override
	public boolean validateCapacity(int slots)
	{
		return _items.size() + slots <= _owner.getInventoryLimit();
	}
	
	public boolean validateWeight(ItemInstance item, int count)
	{
		return validateWeight(count * item.getItem().getWeight());
	}
	
	@Override
	public boolean validateWeight(int weight)
	{
		return _totalWeight + weight <= _owner.getMaxLoad();
	}
	
	@Override
	protected ItemLocation getBaseLocation()
	{
		return ItemLocation.PET;
	}
	
	@Override
	protected ItemLocation getEquipLocation()
	{
		return ItemLocation.PET_EQUIP;
	}
	
	@Override
	public void deleteMe()
	{
		final L2PcInstance petOwner = getOwner().getOwner();
		if (petOwner != null)
		{
			for (ItemInstance item : _items)
			{
				if (petOwner.getInventory().validateCapacity(1))
					getOwner().transferItem("return", item.getObjectId(), item.getCount(), petOwner.getInventory(), petOwner, getOwner());
				else
				{
					final ItemInstance droppedItem = dropItem("drop", item.getObjectId(), item.getCount(), petOwner, getOwner());
					droppedItem.dropMe(getOwner(), getOwner().getX() + Rnd.get(-70, 70), getOwner().getY() + Rnd.get(-70, 70), getOwner().getZ() + 30);
				}
				
			}
		}
		_items.clear();
	}
}