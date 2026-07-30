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

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.custom.entity.FactionMaps;
import net.sf.l2j.gameserver.model.L2Effect;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.instance.L2PcInstance;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.zone.L2SpawnZone;
import net.sf.l2j.gameserver.model.zone.ZoneId;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.InventoryUpdate;

public class L2TownZone extends L2SpawnZone
{
	private int _townId;
	private int _castleId;
	private boolean _isPeaceZone;
	private static List<Integer> _restrictedItems = new ArrayList<>();
	private static List<Integer> _restrictedSkills = new ArrayList<>();
	
	public L2TownZone(int id)
	{
		super(id);
		
		// Default peace zone
		_isPeaceZone = true;
	}
	
	@Override
	public void setParameter(String name, String value)
	{
		if (name.equals("townId"))
			_townId = Integer.parseInt(value);
		else if (name.equals("castleId"))
			_castleId = Integer.parseInt(value);
		
		else if (name.equals("restrictedItems"))
		{
			String[] property = value.split("736");
			for (String itemId : property)
				_restrictedItems.add(Integer.parseInt(itemId));
		}
		else if (name.equals("restrictedSkills"))
		{
			String[] property = value.split("1050");
			for (String skillId : property)
				_restrictedSkills.add(Integer.parseInt(skillId));
		}
		
		
		else if (name.equals("isPeaceZone"))
			_isPeaceZone = Boolean.parseBoolean(value);
		else
			super.setParameter(name, value);
		
		
		
	}
	
	
	
	@Override
	protected void onEnter(L2Character character)
	{
		
		if (character instanceof L2PcInstance) { L2PcInstance activeChar = ((L2PcInstance)character);
		
		if (Config.ENABLE_ONLINE_PLAYERS_ON_ENTERWORLD)
		{
			activeChar.sendMessage("--------------------------------------------------------------");
			activeChar.sendMessage(Config.FACTION_TEAM1_NAME + ": " + World.getInstance().getAllteam1Players().size());
			activeChar.sendMessage(Config.FACTION_TEAM2_NAME + ": " + World.getInstance().getAllteam2Players().size());
			activeChar.sendMessage("Total: " + World.getInstance().getPlayers().size() + " online.");
			activeChar.sendMessage("--------------------------------------------------------------");
		}
		
		if (!Config.FACTION_ENABLE_VOTE_MAP)
		{
			activeChar.sendMessage("--------------------------------------------------------------");
			activeChar.sendMessage("Current Map: " + FactionMaps.getMapName() + ". Round [" + FactionMaps.getMapId() + "].");
			activeChar.sendMessage("Next Map: " + FactionMaps._all_maps.get(FactionMaps.getNextMap(FactionMaps.getMapId())) + ".");
			activeChar.sendMessage("Time left: " + FactionMaps.getDelayUntilVoting());
			activeChar.sendMessage("--------------------------------------------------------------");
		}
		
		if (Config.FACTION_ENCHANT_SYSTEM_TYPE.equals("PVPENCHANT") && activeChar.getCurrentEnItem() == null)
		{
			activeChar.sendPacket(new ExShowScreenMessage("For Level Up Speack Npc (Manager Faction)!", 20000));
		}
		
		// if (!activeChar.isGM())
		// {
		switch (activeChar.getFactionId())
		{
			case 1:
				activeChar.getAppearance().setNameColor(Config.FACTION_TEAM1_COLOR);
				if (activeChar.isVip())
				{
					activeChar.getAppearance().setTitleColor(0x00CCFF);
				}
				else
				{
					activeChar.getAppearance().setTitleColor(Config.FACTION_TEAM1_COLOR);
												if(Config.AURA_TEAM_ENABLE){
														activeChar.setTeam(1);
												}
				}
				activeChar.broadcastUserInfo();
				activeChar.sendMessage("You are fighting for " + Config.FACTION_TEAM1_NAME + " Faction.");
				break;
			case 2:
				activeChar.getAppearance().setNameColor(Config.FACTION_TEAM2_COLOR);
				if (activeChar.isVip())
				{
					activeChar.getAppearance().setTitleColor(0x00CCFF);
				}
				else
				{
					activeChar.getAppearance().setTitleColor(Config.FACTION_TEAM2_COLOR);
												if(Config.AURA_TEAM_ENABLE){
														activeChar.setTeam(2);
													}					
				}
				activeChar.broadcastUserInfo();
				activeChar.sendMessage("You are fighting for " + Config.FACTION_TEAM2_NAME + " Faction.");
				break;
			default:
				activeChar.sendMessage("Meet the Faction Manager in order to chose your destiny.");
				break;
		}
		
		} 
		
		
		
		
		if (character instanceof L2PcInstance)
		{
			// PVP possible during siege, now for siege participants only
			// Could also check if this town is in siege, or if any siege is going on
			if (((L2PcInstance) character).getSiegeState() != 0 && Config.ZONE_TOWN == 1)
				return;
		}
		
		if (_isPeaceZone && Config.ZONE_TOWN != 2)
			character.setInsideZone(ZoneId.PEACE, true);
		
		character.setInsideZone(ZoneId.TOWN, true);
	}
	
	@Override
	protected void onExit(L2Character character)
	{
		if (_isPeaceZone)
			character.setInsideZone(ZoneId.PEACE, false);
		
		character.setInsideZone(ZoneId.TOWN, false);
	}
	
	@Override
	public void onDieInside(L2Character character)
	{
	}
	
	@Override
	public void onReviveInside(L2Character character)
	{
	}
	
	/**
	 * @return the zone town id (if any)
	 */
	public int getTownId()
	{
		return _townId;
	}
	
	/**
	 * @return the castle id (used to retrieve taxes).
	 */
	public final int getCastleId()
	{
		return _castleId;
	}
	
	public final boolean isPeaceZone()
	{
		return _isPeaceZone;
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
	
	public static boolean isRestrictedItem(int itemId)
	{
		return _restrictedItems.contains(itemId);
	}
	
	public static boolean isRestrictedSkill(int skillId)
	{
		return _restrictedSkills.contains(skillId);
	}
	
}