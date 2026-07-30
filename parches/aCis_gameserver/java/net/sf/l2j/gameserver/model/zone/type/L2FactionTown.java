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
package net.sf.l2j.gameserver.model.zone.type;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.commons.concurrent.ThreadPool;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.L2Effect;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PetInstance;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.zone.L2ZoneType;
import net.sf.l2j.gameserver.model.zone.ZoneId;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.InventoryUpdate;

/**
 * @author rapfersan92
 */
public class L2FactionTown extends L2ZoneType
{
	private static boolean _isNoRestart;
	private static boolean _isNoStore;
	private static boolean _isNoSummonFriend;
	private static boolean _isFlagEnabled;
	private static boolean _isReviveEnabled;
	private static boolean _isHealEnabled;
	private static int _revivePower;
	private static int _reviveDelay;
	private static List<Location> _town2 = new ArrayList<>();
	private static List<Location> _town1 = new ArrayList<>();
	private static List<Integer> _restrictedItems = new ArrayList<>();
	private static List<Integer> _restrictedSkills = new ArrayList<>();
	
	public L2FactionTown(int id)
	{
		super(id);
		
		_isNoRestart = false;
		_isNoStore = false;
		_isNoSummonFriend = false;
		_isFlagEnabled = false;
		_isReviveEnabled = false;
		_isHealEnabled = false;
		_revivePower = 0;
		_reviveDelay = 0;
	}

	@Override
	public void setParameter(String name, String value)
	{
		if (name.equals("isNoRestart"))
			_isNoRestart = Boolean.parseBoolean(value);
		else if (name.equals("isNoStore"))
			_isNoStore = Boolean.parseBoolean(value);
		else if (name.equals("isNoSummonFriend"))
			_isNoSummonFriend = Boolean.parseBoolean(value);
		else if (name.equals("isFlagEnabled"))
			_isFlagEnabled = Boolean.parseBoolean(value);
		else if (name.equals("isReviveEnabled"))
			_isReviveEnabled = Boolean.parseBoolean(value);
		else if (name.equals("isHealEnabled"))
			_isHealEnabled = Boolean.parseBoolean(value);
		else if (name.equals("revivePower"))
			_revivePower = Integer.parseInt(value);
		else if (name.equals("reviveDelay"))
			_reviveDelay = Integer.parseInt(value);
		else if (name.equals("town1"))
		{
			String[] property = value.split(";");
			for (String data : property)
			{
				String[] coordinates = data.split(",");
				_town1.add(new Location(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]), Integer.parseInt(coordinates[2])));
			}
		}
		else if (name.equals("town2"))
		{
			String[] property = value.split(";");
			for (String data : property)
			{
				String[] coordinates = data.split(",");
				_town2.add(new Location(Integer.parseInt(coordinates[0]), Integer.parseInt(coordinates[1]), Integer.parseInt(coordinates[2])));
			}
		}
		else if (name.equals("restrictedItems"))
		{
			String[] property = value.split(",");
			for (String itemId : property)
				_restrictedItems.add(Integer.parseInt(itemId));
		}
		else if (name.equals("restrictedSkills"))
		{
			String[] property = value.split(",");
			for (String skillId : property)
				_restrictedSkills.add(Integer.parseInt(skillId));
		}
		else
			super.setParameter(name, value);
	}
	
	@Override
	protected void onEnter(L2Character character)
	{
		character.setInsideZone(ZoneId.MULTI, true);
		
		if (_isNoRestart)
			character.setInsideZone(ZoneId.NO_RESTART, true);
		
		if (_isNoStore)
			character.setInsideZone(ZoneId.NO_STORE, true);
		
		if (_isNoSummonFriend)
			character.setInsideZone(ZoneId.NO_SUMMON_FRIEND, true);
		
		if (character instanceof L2PcInstance || character instanceof L2PetInstance)
		{
			final L2PcInstance player = (L2PcInstance) character;

			if (Config.FACTION_ENABLE_MEM_BALANCE_ONLOG && player.getFactionId() > 0)
			{
				switch (player.getFactionId())
				{
					case 1:
						World.getInstance().getAllTeam1().remove(player.getName().toLowerCase());
						break;
					case 2:
						World.getInstance().getAllTeam2().remove(player.getName().toLowerCase());
						break;
				}

				player.getAppearance().setNameColor(0xFFFFFF);
				player.getAppearance().setTitleColor(0xFFFFFF);
				player.setTeam(0);
				player.setFactionId(0);
			}
			
			if (_isFlagEnabled)
				player.updatePvPFlag(1);
			
			checkItemRestriction(character);
			checkSkillRestriction(character);
		}
	}
	
	@Override
	protected void onExit(L2Character character)
	{
		character.setInsideZone(ZoneId.MULTI, false);
		
		if (_isNoRestart)
			character.setInsideZone(ZoneId.NO_RESTART, false);
		
		if (_isNoStore)
			character.setInsideZone(ZoneId.NO_STORE, false);
		
		if (_isNoSummonFriend)
			character.setInsideZone(ZoneId.NO_SUMMON_FRIEND, false);
		
		if (character instanceof L2PcInstance)
		{
			final L2PcInstance player = (L2PcInstance) character;
			player.sendPacket(new ExShowScreenMessage("¿Ready for battle!!?.", 5000));
			
			if (_isFlagEnabled)
				player.updatePvPFlag(0);
		}
	}
	
	@Override
	public void onDieInside(L2Character character)
	{
		if (character instanceof L2PcInstance && _isReviveEnabled)
		{
			ThreadPool.schedule(() -> respawnCharacter(character), _reviveDelay * 1000);
			character.sendPacket(new ExShowScreenMessage("You will be revived in " + _reviveDelay + " second(s).", 5000));
		}
	}
	
	@Override
	public void onReviveInside(L2Character character)
	{
		if (character instanceof L2PcInstance && _isHealEnabled)
		{
			character.setCurrentCp(character.getMaxCp());
			character.setCurrentHp(character.getMaxHp());
			character.setCurrentMp(character.getMaxMp());
			character.sendPacket(new ExShowScreenMessage("Your CP, HP and MP have been restored.", 5000));
		}
	}

	
	private static void playerLastAccess(L2PcInstance activeChar){
		    activeChar.teleToLocation(45692, 49942, -3062, 0);}
	
	private static void respawnCharacter(L2Character character)
	{
		if (character.isDead()) 
		if (character instanceof L2PcInstance) { L2PcInstance activeChar = ((L2PcInstance)character);
		if (activeChar.getFactionId() == 1)
		{
			character.doRevive(_revivePower);
			character.teleToLocation(Rnd.get(_town1), 20);
		}
		
		else if (character.isDead())
			if (activeChar.getFactionId() == 2)
		{
			character.doRevive(_revivePower);
			character.teleToLocation(Rnd.get(_town2), 20);
		}
		}
	}
	
	private static void checkItemRestriction(L2Character character)
	{
		for (ItemInstance item : character.getInventory().getPaperdollItems())
		{
			if (item == null || !isRestrictedItem(item.getItemId()))
				continue;
			
			character.getInventory().unEquipItemInSlot(item.getLocationSlot());
			InventoryUpdate iu = new InventoryUpdate();
			iu.addModifiedItem(item);
			character.sendPacket(iu);
		}
	}
	
	private static void checkSkillRestriction(L2Character character)
	{
		for (L2Effect effect : character.getAllEffects())
		{
			if (effect == null || !isRestrictedSkill(effect.getSkill().getId()))
				continue;
			
			effect.exit(true);
		}
	}
	
	public static boolean isFlagEnabled()
	{
		return _isFlagEnabled;
	}
	
	public static boolean isReviveEnabled()
	{
		return _isReviveEnabled;
	}
	
	public static boolean isRestrictedItem(int itemId)
	{
		return _restrictedItems.contains(itemId);
	}
	
	public static boolean isRestrictedSkill(int skillId)
	{
		return _restrictedSkills.contains(skillId);
	}
	

	}