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

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance.ItemLocation;

public class PcFreight extends ItemContainer
{
	private final L2PcInstance _owner;
	
	private int _activeLocationId;
	private int _tempOwnerId = 0;
	
	public PcFreight(L2PcInstance owner)
	{
		_owner = owner;
	}
	
	@Override
	public String getName()
	{
		return "Freight";
	}
	
	@Override
	public L2PcInstance getOwner()
	{
		return _owner;
	}
	
	@Override
	public ItemLocation getBaseLocation()
	{
		return ItemLocation.FREIGHT;
	}
	
	public void setActiveLocation(int locationId)
	{
		_activeLocationId = locationId;
	}
	
	@Override
	public int getSize()
	{
		int size = 0;
		for (ItemInstance item : _items)
		{
			if (item.getLocationSlot() == 0 || _activeLocationId == 0 || item.getLocationSlot() == _activeLocationId)
				size++;
		}
		return size;
	}
	
	@Override
	public Set<ItemInstance> getItems()
	{
		if (_items.isEmpty())
			return Collections.emptySet();
		
		return _items.stream().filter(i -> i.getLocationSlot() == 0 || i.getLocationSlot() == _activeLocationId).collect(Collectors.toSet());
	}
	
	@Override
	public ItemInstance getItemByItemId(int itemId)
	{
		for (ItemInstance item : _items)
		{
			if (item.getItemId() == itemId && (item.getLocationSlot() == 0 || _activeLocationId == 0 || item.getLocationSlot() == _activeLocationId))
				return item;
		}
		return null;
	}
	
	@Override
	protected void addItem(ItemInstance item)
	{
		super.addItem(item);
		
		if (_activeLocationId > 0)
			item.setLocation(item.getLocation(), _activeLocationId);
	}
	
	@Override
	public void restore()
	{
		int locationId = _activeLocationId;
		_activeLocationId = 0;
		
		super.restore();
		
		_activeLocationId = locationId;
	}
	
	@Override
	public boolean validateCapacity(int slots)
	{
		return getSize() + slots <= ((_owner == null) ? Config.FREIGHT_SLOTS : _owner.getFreightLimit());
	}
	
	@Override
	public int getOwnerId()
	{
		return (_owner == null) ? _tempOwnerId : super.getOwnerId();
	}
	
	/**
	 * This provides support to load a new PcFreight without owner so that transactions can be done
	 * @param val The id of the owner.
	 */
	public void doQuickRestore(int val)
	{
		_tempOwnerId = val;
		
		restore();
	}
}