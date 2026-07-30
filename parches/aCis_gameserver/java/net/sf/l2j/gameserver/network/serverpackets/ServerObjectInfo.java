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
package net.sf.l2j.gameserver.network.serverpackets;

import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.L2Npc;

public final class ServerObjectInfo extends L2GameServerPacket
{
	private final L2Npc _npc;
	
	private final int _idTemplate;
	private final String _name;
	
	private final int _x;
	private final int _y;
	private final int _z;
	private final int _heading;
	
	private final double _collisionHeight;
	private final double _collisionRadius;
	
	private final boolean _isAttackable;
	
	public ServerObjectInfo(L2Npc npc, L2Character actor)
	{
		_npc = npc;
		
		_idTemplate = _npc.getTemplate().getIdTemplate();
		_name = _npc.getName();
		
		_x = _npc.getX();
		_y = _npc.getY();
		_z = _npc.getZ();
		_heading = _npc.getHeading();
		
		_collisionHeight = _npc.getCollisionHeight();
		_collisionRadius = _npc.getCollisionRadius();
		
		_isAttackable = _npc.isAutoAttackable(actor);
	}
	
	@Override
	protected final void writeImpl()
	{
		writeC(0x8C);
		writeD(_npc.getObjectId());
		writeD(_idTemplate + 1000000);
		writeS(_name);
		writeD(_isAttackable ? 1 : 0);
		writeD(_x);
		writeD(_y);
		writeD(_z);
		writeD(_heading);
		writeF(1.0); // movement multiplier
		writeF(1.0); // attack speed multiplier
		writeF(_collisionRadius);
		writeF(_collisionHeight);
		writeD((int) (_isAttackable ? _npc.getCurrentHp() : 0));
		writeD(_isAttackable ? _npc.getMaxHp() : 0);
		writeD(0x01); // object type
		writeD(0x00); // special effects
	}
}