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
package net.sf.l2j.gameserver.model.item;

import java.util.Arrays;

import net.sf.l2j.gameserver.instancemanager.SevenSigns.CabalType;
import net.sf.l2j.gameserver.templates.StatsSet;

public final class MercenaryTicket
{
	public enum TicketType
	{
		SWORD,
		POLE,
		BOW,
		CLERIC,
		WIZARD,
		TELEPORTER
	}
	
	private final int _itemId;
	private final TicketType _type;
	private final boolean _isStationary;
	private final int _npcId;
	private final int _maxAmount;
	private final CabalType[] _ssq;
	
	public MercenaryTicket(StatsSet set)
	{
		_itemId = set.getInteger("itemId");
		_type = set.getEnum("type", TicketType.class);
		_isStationary = set.getBool("stationary");
		_npcId = set.getInteger("npcId");
		_maxAmount = set.getInteger("maxAmount");
		
		final String[] ssq = set.getStringArray("ssq");
		
		_ssq = new CabalType[ssq.length];
		for (int i = 0; i < ssq.length; i++)
			_ssq[i] = Enum.valueOf(CabalType.class, ssq[i]);
	}
	
	public int getItemId()
	{
		return _itemId;
	}
	
	public TicketType getType()
	{
		return _type;
	}
	
	public boolean isStationary()
	{
		return _isStationary;
	}
	
	public int getNpcId()
	{
		return _npcId;
	}
	
	public int getMaxAmount()
	{
		return _maxAmount;
	}
	
	public boolean isSsqType(CabalType type)
	{
		return Arrays.asList(_ssq).contains(type);
	}
}