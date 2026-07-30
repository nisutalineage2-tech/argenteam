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

import java.io.File;
import java.util.Map.Entry;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.custom.entity.FactionMaps;
import net.sf.l2j.gameserver.datatables.NpcTable;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.MyTargetSelected;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.network.serverpackets.StatusUpdate;
import net.sf.l2j.gameserver.network.serverpackets.ValidateLocation;
import net.sf.l2j.gameserver.util.Broadcast;
import net.sf.l2j.gameserver.util.Util;
import net.sf.l2j.gameserver.xmlfactory.XMLDocumentFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public final class L2TpFlagInstance extends L2Npc
{
	public static int _goddard_owners = 0;
	public static int _benom_spawn_x = 0;
	public static int _benom_spawn_y = 0;
	public static int _benom_spawn_z = 0;
	private int _faction = 0;
	private int _occupayable = 0;
	
	private String _flagPlace = "";
	
	public L2TpFlagInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	public static void spawnFlags()
	{
		try
		{

			File f = new File("./data/xml/faction_flags.xml");
			Document doc = XMLDocumentFactory.getInstance().loadDocument(f);
			
			int mapId = 0, factionId = 0, x = 0, y = 0, z = 0;
			boolean capturable = false;
			String flagType = "default", flagName = "";
			
			for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
			{
				if ("list".equalsIgnoreCase(n.getNodeName()))
				{
					for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
					{
						if (d.getNodeName().equalsIgnoreCase("flag"))
						{
							mapId = Integer.valueOf(d.getAttributes().getNamedItem("mapId").getNodeValue());
							flagType = d.getAttributes().getNamedItem("flag_type").getNodeValue();
							flagName = d.getAttributes().getNamedItem("flag_name").getNodeValue();
							factionId = Integer.valueOf(d.getAttributes().getNamedItem("faction_id").getNodeValue());
							capturable = Boolean.valueOf(d.getAttributes().getNamedItem("isCapturable").getNodeValue());
							x = Integer.valueOf(d.getAttributes().getNamedItem("x").getNodeValue());
							y = Integer.valueOf(d.getAttributes().getNamedItem("y").getNodeValue());
							z = Integer.valueOf(d.getAttributes().getNamedItem("z").getNodeValue());
							
							if (mapId == FactionMaps.getMapId())
							{
								String _titlea = "";
								switch (factionId)
								{
									case 1:
										_titlea = Config.FACTION_TEAM1_NAME + " FACTION";
										break;
									case 2:
										_titlea = Config.FACTION_TEAM2_NAME + " FACTION";
										break;
									default:
										_titlea = "NOT CAPTURED";
										break;
								}
								if (flagType.equalsIgnoreCase("antharas"))
								{
									L2GrandBossInstance antharas = new L2GrandBossInstance(IdFactory.getInstance().getNextId(), NpcTable.getInstance().getTemplate(29019));
									antharas.setCurrentHpMp(antharas.getMaxHp(), antharas.getMaxMp());
									antharas.setHeading(32791);
									antharas.spawnMe(x, y, z);
									
									L2NpcInstance tele = new L2NpcInstance(IdFactory.getInstance().getNextId(), NpcTable.getInstance().getTemplate(13001));
									tele.setHeading(0);
									tele.spawnMe(154695, 121097, -3757);
									
									L2FactTeleporterInstance._bosses.add(antharas);
									L2FactTeleporterInstance._blazers.add(tele);
								}
								if (flagType.equalsIgnoreCase("goddard"))
								{
									L2GrandBossInstance benom = new L2GrandBossInstance(IdFactory.getInstance().getNextId(), NpcTable.getInstance().getTemplate(29054));
									benom.setCurrentHpMp(benom.getMaxHp(), benom.getMaxMp());
									benom.setHeading(32791);
									benom.spawnMe(x, y, z);
									_benom_spawn_x = x;
									_benom_spawn_y = y;
									_benom_spawn_z = z;
									
									L2FactTeleporterInstance._bosses.add(benom);
								}
								if (flagType.equalsIgnoreCase("default"))
								{
									L2TpFlagInstance flag = new L2TpFlagInstance(IdFactory.getInstance().getNextId(), NpcTable.getInstance().getTemplate(35062));
									flag.setTitle(_titlea);
									flag.setCurrentHpMp(flag.getMaxHp(), flag.getMaxMp());
									flag.setHeading(0);
									flag.setName(flagName);
									flag.setFlagName(flagName);
									flag.setFlagFactionId(factionId);
									flag.spawnMe(x, y, z + 50);
									if (factionId != 0)
									{
										L2NpcInstance blazer = new L2NpcInstance(IdFactory.getInstance().getNextId(), NpcTable.getInstance().getTemplate(32027));
										blazer.setName(flagName);
										blazer.setHeading(0);
										blazer.spawnMe(x, y, z + 50);
										L2FactTeleporterInstance._blazers.add(blazer);
										switch (factionId)
										{
											case 1:
												L2FactTeleporterInstance._tpTeam1Flags.add(flag);
												if (Config.FACTION_ENABLE_GUARDS)
												{
													L2ProtectorInstance guard = new L2ProtectorInstance(IdFactory.getInstance().getNextId(), NpcTable.getInstance().getTemplate(32033));
													guard.setTitle(Config.FACTION_TEAM1_NAME + " GUARD");
													guard.setFationId(1);
													guard.setHeading(guard.getHeading());
													guard.spawnMe(x + 50, y + 50, z + 50);
													L2FactTeleporterInstance._guards.add(guard);
												}
												break;
											case 2:
												L2FactTeleporterInstance._tpTeam2Flags.add(flag);
												if (Config.FACTION_ENABLE_GUARDS)
												{
													L2ProtectorInstance guard1 = new L2ProtectorInstance(IdFactory.getInstance().getNextId(), NpcTable.getInstance().getTemplate(31845));
													guard1.setTitle(Config.FACTION_TEAM2_NAME + " GUARD");
													guard1.setFationId(2);
													guard1.setHeading(guard1.getHeading());
													guard1.spawnMe(x + 50, y + 50, z + 50);
													L2FactTeleporterInstance._guards.add(guard1);
												}
												break;
										}
										if (!capturable)
										{
											flag.setIsInvul(true);
											flag.setIsUnoccupayable(1);
										}
									}
									else
									{
										L2FactTeleporterInstance._not_captured.add(flag);
									}
									_log.info("Spawned flag: " + flagName);
								}
							}
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			_log.warning("Couldn't load faction event flags: " + e);
		}
	}
	
	public void setFlagName(String a)
	{
		_flagPlace = a;
	}
	
	public String getFlagName()
	{
		return _flagPlace;
	}
	
	@Override
	public boolean isAttackable()
	{
		return true;
	}
	
	@Override
	public boolean isAutoAttackable(L2Character attacker)
	{
		return true;
	}
	
	@Override
	public boolean doDie(L2Character killer)
	{
		if (!super.doDie(killer))
		{
			return false;
		}
		
		L2PcInstance player = killer.getActingPlayer();
		
		if (player != null)
		{
			String _name = "";
			
			int _factionId = player.getFactionId();
			
			switch (getFlagFactionId())
			{
				case 1:
					Broadcast.sendMessToAllTeam1Players("[" + Config.FACTION_TEAM1_NAME + "] We losing " + _flagPlace + " flag on " + FactionMaps.getMapName() + ". Go retrieve it!");
					switch (_factionId)
					{
						case 2:
							Broadcast.sendMessToAllTeam2Players("[" + Config.FACTION_TEAM2_NAME + "] " + player.getName() + " trying to take control of  " + _flagPlace + " flag on " + FactionMaps.getMapName());
							break;
					}
					break;
				case 2:
					Broadcast.sendMessToAllTeam2Players("[" + Config.FACTION_TEAM2_NAME + "] We losing " + _flagPlace + " flag on " + FactionMaps.getMapName() + ". Go retrieve it!");
					switch (_factionId)
					{
						case 1:
							Broadcast.sendMessToAllTeam1Players("[" + Config.FACTION_TEAM1_NAME + "] " + player.getName() + " trying to take control of " + _flagPlace + " flag on " + FactionMaps.getMapName());
							break;
					}
					break;
				default:
					switch (_factionId)
					{
						case 1:
							Broadcast.announceToOnlinePlayers("[" + Config.FACTION_TEAM1_NAME + "] " + player.getName() + " successfuly captured " + _flagPlace + " flag on " + FactionMaps.getMapName());
							break;
						case 2:
							Broadcast.announceToOnlinePlayers("[" + Config.FACTION_TEAM2_NAME + "] " + player.getName() + " successfuly captured " + _flagPlace + " flag on " + FactionMaps.getMapName());
							break;
					}
					break;
			}
			
			if (getFlagFactionId() > 0)
			{
				switch (getFlagFactionId())
				{
					case 1:
						L2FactTeleporterInstance._tpTeam1Flags.remove(this);
						break;
					case 2:
						L2FactTeleporterInstance._tpTeam2Flags.remove(this);
						break;
				}
				L2TpFlagInstance flag = new L2TpFlagInstance(IdFactory.getInstance().getNextId(), NpcTable.getInstance().getTemplate(99));
				_name = this.getName();
				// flag.setTitle("NOT CAPTURED");
				flag.setCurrentHpMp(flag.getMaxHp(), flag.getMaxMp());
				flag.setHeading(0);
				flag.setName(_name);
				flag.setFlagName(_name);
				flag.setIsUnoccupayable(0);
				flag.setFlagFactionId(0);
				flag.spawnMe(this.getX(), this.getY(), this.getZ());
				for (L2PcInstance play : World.getInstance().getPlayers())
				{
					play.removeKnownObject(this);
				}
				deleteMe();
			}
			else
			{
				L2FactTeleporterInstance._not_captured.remove(this);
				player.sendPacket(new PlaySound("skillsound7.pig_skill"));
				
				L2TpFlagInstance flag = new L2TpFlagInstance(IdFactory.getInstance().getNextId(), NpcTable.getInstance().getTemplate(96));
				_name = this.getName();
				// flag.setTitle(_title);
				flag.setCurrentHpMp(flag.getMaxHp(), flag.getMaxMp());
				flag.setHeading(0);
				flag.setName(_name);
				flag.setFlagName(_name);
				flag.setIsUnoccupayable(0);
				flag.setFlagFactionId(player.getFactionId());
				flag.spawnMe(this.getX(), this.getY(), this.getZ());
				for (L2PcInstance play : World.getInstance().getPlayers())
				{
					play.removeKnownObject(this);
				}
				deleteMe();
				
				if (Config.FACTION_ALLOW_SP_REWARD_FLAG)
				{
					if (player.getClan() != null)
					{
						if (player.getClan().getLevel() < 5)
						{
							player.addExpAndSp(Config.FACTION_EXP_REWARD_PVP_FIRST, Config.FACTION_SP_REWARD_FIRST_FLAG);
							for (Entry<Integer, Integer> id : Config.FACTION_FLAG_REWARDS_3.entrySet())
							{
								player.addItem("Loot", id.getKey(), id.getValue(), this, true);
							}
						}
						else if (player.getClan().getLevel() >= 5 && player.getClan().getLevel() < 7)
						{
							player.addExpAndSp(Config.FACTION_EXP_REWARD_PVP_SECOND, Config.FACTION_SP_REWARD_SECOND_FLAG);
							for (Entry<Integer, Integer> id : Config.FACTION_FLAG_REWARDS_3.entrySet())
							{
								player.addItem("Loot", id.getKey(), id.getValue(), this, true);
							}
						}
						else
						{
							player.addExpAndSp(Config.FACTION_EXP_REWARD_PVP_THIRD, Config.FACTION_SP_REWARD_THIRD_FLAG);
							for (Entry<Integer, Integer> id : Config.FACTION_FLAG_REWARDS_3.entrySet())
							{
								player.addItem("Loot", id.getKey(), id.getValue(), this, true);
							}
						}
					}
					else
					{
						player.addExpAndSp(0, Config.FACTION_SP_REWARD_THIRD_FLAG);
						for (Entry<Integer, Integer> id : Config.FACTION_FLAG_REWARDS_3.entrySet())
						{
							player.addItem("Loot", id.getKey(), id.getValue(), this, true);
						}
					}
				}
				if (Config.FACTION_ALLOW_ITEM_REWARD_PARTY && player.getParty() != null)
				{
					if (player.getFactionId() != 0)
					{
						for (L2PcInstance member : player.getParty().getMembers())
						{
							if (member != player && Util.checkIfInRange(2000, player, member, true) && !member.IP.equals(player.IP))
							{
								member.addExpAndSp(Config.FACTION_EXP_REWARD_PVP_THIRD, Config.FACTION_SP_REWARD_THIRD_FLAG);
								member.sendMessage("You have earned a party reward!");
								for (Entry<Integer, Integer> id : Config.FACTION_FLAG_REWARDS_3.entrySet())
								{
									player.addItem("Loot", id.getKey(), id.getValue(), this, true);
								}
							}
						}
					}
				}
				if (player.getClan() != null)
				{
					player.getClan().addReputationScore(250);
					player.sendMessage("Your clan received 250 reputation points.");
				}
				if (player.isVip())
				{
					player.setCurrentPts(player.getCurrentPts() + (Config.FACTION_POINTS_FLAG) * 2);
					player.setTotalPts(player.getTotalPts() + (Config.FACTION_POINTS_FLAG * 2));
					player.sendMessage("You received " + (Config.FACTION_POINTS_FLAG * 2) + " points for capturing enemy base!");
					
				}
				else
				{
					player.setCurrentPts(player.getCurrentPts() + Config.FACTION_POINTS_FLAG);
					player.setTotalPts(player.getTotalPts() + Config.FACTION_POINTS_FLAG);
					player.sendMessage("You received " + Config.FACTION_POINTS_FLAG + " points for capturing enemy base!");
				}
				switch (player.getFactionId())
				{
					case 1:
						if (!player.isVip())
						{
							FactionMaps.setTeam1Pts(FactionMaps.getTeam1Pts() + Config.FACTION_POINTS_FLAG);
						}
						else
						{
							FactionMaps.setTeam1Pts(FactionMaps.getTeam1Pts() + (Config.FACTION_POINTS_FLAG * 2));
						}
						break;
					case 2:
						if (!player.isVip())
						{
							FactionMaps.setTeam2Pts(FactionMaps.getTeam2Pts() + Config.FACTION_POINTS_FLAG);
						}
						else
						{
							FactionMaps.setTeam2Pts(FactionMaps.getTeam2Pts() + (Config.FACTION_POINTS_FLAG * 2));
						}
						break;
				}
				
				switch (_factionId)
				{
					case 1:
						L2FactTeleporterInstance._tpTeam1Flags.add(flag);
						break;
					case 2:
						L2FactTeleporterInstance._tpTeam2Flags.add(flag);
						break;
				}
			}
		}
		
		return true;
	}
	
	@Override
	public void onForcedAttack(L2PcInstance player)
	{
		onAction(player);
	}
	
	public void setIsUnoccupayable(int a)
	{
		_occupayable = a;
	}
	
	public int isUnoccupayable()
	{
		return _occupayable;
	}
	
	@Override
	public void onAction(L2PcInstance player)
	{
		if (player == null)
		{
			return;
		}
		
		// Check if the L2PcInstance already target the L2NpcInstance
		if (this != player.getTarget())
		{
			// Set the target of the L2PcInstance player
			player.setTarget(this);
			
			// Send a Server->Client packet MyTargetSelected to the L2PcInstance player
			MyTargetSelected my = new MyTargetSelected(getObjectId(), player.getLevel() - getLevel());
			player.sendPacket(my);
			
			// Send a Server->Client packet StatusUpdate of the L2NpcInstance to the L2PcInstance to update its HP bar
			StatusUpdate su = new StatusUpdate(this);
			su.addAttribute(StatusUpdate.CUR_HP, (int) getStatus().getCurrentHp());
			su.addAttribute(StatusUpdate.MAX_HP, getMaxHp());
			player.sendPacket(su);
			
			// Send a Server->Client packet ValidateLocation to correct the L2NpcInstance position and heading on the client
			player.sendPacket(new ValidateLocation(this));
		}
		else
		{
			if (isAutoAttackable(player) && Math.abs(player.getZ() - getZ()) < 100 && getFlagFactionId() != player.getFactionId())
			{
				player.getAI().setIntention(CtrlIntention.ATTACK, this);
			}
			else
			{
				// Send a Server->Client ActionFailed to the L2PcInstance in order to avoid that the client wait another packet
				player.sendPacket(ActionFailed.STATIC_PACKET);
			}
		}
	}
	
	public int getFlagFactionId()
	{
		return _faction;
	}
	
	public void setFlagFactionId(int i)
	{
		_faction = i;
	}
	
	@Override
	public void reduceCurrentHp(double damage, L2Character attacker, L2Skill skill)
	{
		L2PcInstance gamer = attacker.getActingPlayer();
		boolean cord = false;
		
		if (gamer.getFactionId() != getFlagFactionId() && gamer.getFactionId() != 0)
		{
			cord = true;
		}
		
		if (cord)
		{
			super.reduceCurrentHp(damage, attacker, skill);
		}
	}
}