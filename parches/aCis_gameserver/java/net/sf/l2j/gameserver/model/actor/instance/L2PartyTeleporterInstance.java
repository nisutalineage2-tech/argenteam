package net.sf.l2j.gameserver.model.actor.instance;

import java.util.StringTokenizer;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.datatables.ItemTable;
import net.sf.l2j.gameserver.instancemanager.ZoneManager;
import net.sf.l2j.gameserver.model.actor.L2Character;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.group.Party;
import net.sf.l2j.gameserver.model.zone.L2ZoneType;
import net.sf.l2j.gameserver.network.gameserverpackets.PlayerInGame;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.InventoryUpdate;
import net.sf.l2j.gameserver.network.serverpackets.ItemList;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.StatusUpdate;

/**
 * @author `Heroin Adapter Gandalf PartyTeleporter, Lucas Fernandes
 */
public class L2PartyTeleporterInstance extends L2NpcInstance
{
	// -------------------------------------
	// Teleport Location Coordinates X,Y,Z.
	// Use /loc command in game to find them.
	private static final int locationX = Config.NPC_PT_TELEPORTER_X; // npc id
	private static final int locationY = Config.NPC_PT_TELEPORTER_Y; // npc id
	private static final int locationZ = Config.NPC_PT_TELEPORTER_Z; // npc id
	// -------------------------------------
	// -------------------------------------
	// Select the id of your zone.
	// If you dont know how to find your zone id is simple.
	// Go to data/zones/(your zone file).xml and find your zone
	// E.g: <zone name="dion_monster_pvp" id="6" type="ArenaZone" shape="NPoly" minZ="-3596" maxZ="0">
	/** The id of your zone is id="6" */
	/** --------------------------------------------------------------------------- */
	/** WARNING: If your zone does not have any id or your location is not on any zone in data/zones/ folder, you have to add one by your self */
	// required to calculate parties & players
	/** --------------------------------------------------------------------------- */
	private static final int ZoneId = Config.NPC_PT_ZONEID; // Here you have to set your zone Id
	// -------------------------------------
	private static final int MinPtMembers = Config.NPC_PT_MINPT_MEMBERS; // Minimum Party Members Count For Enter on Zone.
	private static final int ItemConsumeId = Config.NPC_PT_ITEMCONSUME_ID; // Item Consume id.
	private static final int ItemConsumeNum = Config.NPC_PT_ITEMCOMSUME_QT; // Item Consume Am.ount.
	private static final boolean ShowPlayersInside = Config.NPC_PT_SHOWINSIDE_PLAYERS; // If you set it true, NPC will show how many players are inside area.
	private static final boolean ShowPartiesInside = Config.NPC_PT_SHOWINSIDE_PARTIES; // If you set it true, NPC will show how many parties are inside area.
	private static String ItemName = ItemTable.getInstance().getTemplate(ItemConsumeId).getName(); // Item name, Dont Change this
	private String htmContent;
	
	public L2PartyTeleporterInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
	}
	
	@Override
	public void onBypassFeedback(L2PcInstance player, String command)
	{
		player.sendPacket(ActionFailed.STATIC_PACKET);
		
		StringTokenizer st = new StringTokenizer(command, " ");
		String actualCommand = st.nextToken(); // Get actual command
		
		if (actualCommand.equalsIgnoreCase("partytp"))
		{
			TP(player);
		}
		super.onBypassFeedback(player, command);
	}
	
	public int getPartiesInside(int zoneId)// Calculating parties inside party area.
	{
		int i = 0;
		for (L2ZoneType zone : ZoneManager.getInstance().getZones(locationX, locationY, locationZ))
		{
			if (zone.getId() == zoneId)
			{
				for (L2Character character : zone.getCharactersInside())
				
				{
					if ((character instanceof L2PcInstance) && (!((L2PcInstance) character).getClient().isDetached()) && (((L2PcInstance) character).getParty() != null) && ((L2PcInstance) character).getParty().isLeader((L2PcInstance) character))
					{
						i++;
					}
				}
			}
		}
		return i;
	}
	
	public int getPlayerInside(int zoneId)// Calculating players inside party area.
	{
		int i = 0;
		for (L2ZoneType zone : ZoneManager.getInstance().getZones(locationX, locationY, locationZ))
		{
			if (zone.getId() == zoneId)
			{
				for (L2Character character : zone.getCharactersInside())
				{
					if ((character instanceof L2PcInstance) && (!((L2PcInstance) character).getClient().isDetached()))
					{
						i++;
					}
				}
			}
		}
		return i;
	}
	
	private static boolean PartyItemsOk(L2PcInstance player)
	// Checks if all party members have the item in their inventory.
	// If pt member has not enough items, party not allowed to enter.
	{
		try
		{
			for (L2PcInstance member : player.getParty().getMembers())
			{
				if (member.getInventory().getItemByItemId(ItemConsumeId) == null)
				
				{
					player.sendMessage("Your party member " + member.getName() + " does not have enough items.");
					return false;
				}
				if (member.getInventory().getItemByItemId(ItemConsumeId).getCount() < ItemConsumeNum)
				{
					player.sendMessage("Your party member " + member.getName() + " does not have enough items.");
					return false;
				}
			}
			return true;
			
		}
		catch (Exception e)
		{
			player.sendMessage("Something went wrong try again.");
			return true;
		}
	}
	
	private static void proccessTP(L2PcInstance player) // Teleporting party members to zone
	{
		for (L2PcInstance member : player.getParty().getMembers())
		{
			member.teleToLocation(locationX + Rnd.get(100), locationY + Rnd.get(100), locationZ, 0);// Location X, Y ,Z
			
		}
	}
	
	private static void TP(L2PcInstance player) // Teleport player & his party
	{
		try
		{
			Party pt = player.getParty();
			if (pt == null)
			{
				player.sendMessage("You are not currently on party.");
				return;
			}
			if (!pt.isLeader(player))
			{
				player.sendMessage("You are not party leader.");
				return;
			}
			if (PlayerInGame.getMembers() < MinPtMembers)
			{
				player.sendMessage("You are going to need a bigger party " + "in order to enter party area.");
				return;
			}
			if (!PartyItemsOk(player))
			{
				return;
			}
			proccessTP(player);
			for (L2PcInstance ppl : pt.getMembers())
			{
				if (ppl.getObjectId() != player.getObjectId()) // Dont send this message to pt leader.
				{
					ppl.sendMessage("Your party leader asked to teleport on party area!");// Message only to party members
				}
				ppl.sendMessage(ItemConsumeNum + " " + ItemName + " have been dissapeared.");// Item delete from inventory message
				ppl.getInventory().destroyItemByItemId("Party_Teleporter", ItemConsumeId, ItemConsumeNum, ppl, ppl);// remove item from inventory
				ppl.sendPacket(new InventoryUpdate());// Update
				ppl.sendPacket(new ItemList(ppl, false));// Update
				ppl.sendPacket(new StatusUpdate(ppl));// Update
				
			}
			// Sends message to party leader.
			player.sendMessage((ItemConsumeNum * player.getParty().getMembersCount()) + " " + ItemName + " dissapeard from your party.");
			
		}
		catch (Exception e)
		{
			player.sendMessage("Something went wrong try again.");
		}
	}
	
	@Override
	public void showChatWindow(L2PcInstance player)
	{
		
		htmContent = "data/html/mods/PartyTeleporter/PartyTeleporter.htm";
		if (htmContent != null)
		{
			final NpcHtmlMessage npcHtmlMessage = new NpcHtmlMessage(getObjectId());
			npcHtmlMessage.setFile(htmContent);
			npcHtmlMessage.replace("%objectId%", String.valueOf(getObjectId()));
			npcHtmlMessage.replace("%player%", player.getName());// Replaces %player% with player name on html
			npcHtmlMessage.replace("%itemname%", ItemName);// Item name replace on html
			npcHtmlMessage.replace("%price%", player.getParty() != null ? "" + (ItemConsumeNum * player.getParty().getMembersCount()) + "" : "0");// Price calculate replace
			npcHtmlMessage.replace("%minmembers%", "" + MinPtMembers);// Mimum entry party members replace
			npcHtmlMessage.replace("%allowed%", isAllowedEnter(player) ? "<font color=00FF00>allowed</font>" : "<font color=FF0000>not allowed</font>");// Condition checker replace on html
			npcHtmlMessage.replace("%parties%", ShowPartiesInside ? "<font color=FFA500>Parties Inside: " + getPartiesInside(ZoneId) + "</font><br>" : "");// Parties inside
			npcHtmlMessage.replace("%players%", ShowPlayersInside ? "<font color=FFA500>Players Inside: " + getPlayerInside(ZoneId) + "</font><br>" : "");// Players Inside
			player.sendPacket(npcHtmlMessage);
		}
		
	}
	
	private static boolean isAllowedEnter(L2PcInstance player) // Checks if player & his party is allowed to teleport.
	{
		if (player.getParty() != null)
		{
			if ((player.getParty().getMembersCount() >= MinPtMembers) && PartyItemsOk(player)) // Party Length & Item Checker
			{
				return true;
			}
			return false;
		}
		return false;
	}
	
}