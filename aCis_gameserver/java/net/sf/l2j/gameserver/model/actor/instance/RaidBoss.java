package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.commons.random.Rnd;	import net.sf.l2j.gameserver.data.manager.HeroManager;
	import net.sf.l2j.gameserver.data.manager.RaidPointManager;
	import net.sf.l2j.gameserver.data.xml.FactionData;
	import net.sf.l2j.gameserver.event.AbstractEvent;
import net.sf.l2j.gameserver.event.EventEngine;
import net.sf.l2j.gameserver.model.actor.Creature;	import net.sf.l2j.gameserver.model.Faction;
	import net.sf.l2j.gameserver.model.World;
	import net.sf.l2j.gameserver.model.actor.Player;
	import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.group.CommandChannel;
import net.sf.l2j.gameserver.model.group.Party;import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ItemList;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;

/**
 * This class manages all classic raid bosses.<br>
 * <br>
 * Raid Bosses (RB) are mobs which are supposed to be defeated by a party of several players. It extends most of {@link Monster} aspects.<br>
 * <br>
 * They automatically teleport if out of their initial spawn area, and can randomly attack a Player from their Hate List once attacked.<br>
 * <br>
 * Their looting rights are affected by {@link CommandChannel}s. The first who attacks got the priority over loots. Those rights are lost if no attack has been done for 900sec.
 */
public class RaidBoss extends Monster
{
	public RaidBoss(int objectId, NpcTemplate template)
	{
		super(objectId, template);
		
		setRaidRelated();
	}
	
	@Override
	public int getSeeRange()
	{
		return getTemplate().getAggroRange();
	}
	
	@Override
	public boolean isRaidBoss()
	{
		return true;
	}
	
	@Override
	public void onSpawn()
	{
		super.onSpawn();
		
		// Global announcement when a raid boss spawns (disabled by default to avoid spam).
		if (net.sf.l2j.Config.ANNOUNCE_RAIDBOSS_SPAWN)
			World.announceToOnlinePlayers("[Raid Boss]: " + getName() + " esta vivo.");
	}
	
	@Override
	public boolean doDie(Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		if (killer != null)
		{
			final Player player = killer.getActingPlayer();
			if (player != null)
			{
				// Global announcement of the kill, with clan and faction of the killer.
				if (net.sf.l2j.Config.ANNOUNCE_RAIDBOSS_KILL)
				{
					final StringBuilder msg = new StringBuilder("[Raid Boss]: ").append(getName()).append(" fue derrotado por ").append(player.getName());
					if (player.getClan() != null)
						msg.append(" del clan ").append(player.getClan().getName());
					if (net.sf.l2j.Config.ENABLE_FACTION_SYSTEM && player.getFactionId() > 0)
					{
						final Faction faction = FactionData.getInstance().getFaction(player.getFactionId());
						if (faction != null)
							msg.append(" de la faccion ").append(faction.getName());
					}
					World.announceToOnlinePlayers(msg.toString());
				}
				
				// Notify event engine of raid boss kill (for "Raid in the Middle" event)
				final AbstractEvent event = EventEngine.getInstance().getActiveEvent();
				if (event != null && event.getData().getId() == 16 && event instanceof net.sf.l2j.gameserver.event.RaidInTheMiddleEvent raidEvent)
					raidEvent.onBossKilled(player);
				
				broadcastPacket(SystemMessage.getSystemMessage(SystemMessageId.RAID_WAS_SUCCESSFUL));
				broadcastPacket(new PlaySound("systemmsg_e.1209"));
				
				final Party party = player.getParty();
				if (party != null)
				{
					for (Player member : party.getMembers())
					{
						RaidPointManager.getInstance().addPoints(member, getNpcId(), (getStatus().getLevel() / 2) + Rnd.get(-5, 5));
						if (member.isNoble())
							HeroManager.getInstance().setRBkilled(member.getObjectId(), getNpcId());
						
						// Custom: killing Barakiel grants Noblesse status to non-noble members.
						tryGrantNoblesse(member);
					}
				}
				else
				{
					RaidPointManager.getInstance().addPoints(player, getNpcId(), (getStatus().getLevel() / 2) + Rnd.get(-5, 5));
					if (player.isNoble())
						HeroManager.getInstance().setRBkilled(player.getObjectId(), getNpcId());
					
					// Custom: killing Barakiel grants Noblesse status to non-noble players.
					tryGrantNoblesse(player);
				}
			}
		}
		
	// TODO implement NpcSpawnManager or ASpawn notification
	// RaidBossManager.getInstance().onDeath(this);
	return true;
}

/**
 * Custom: when the Barakiel raid boss (npcId 25325) is killed and the mod is enabled,
 * non-noble players receive the Noblesse status, a tiara (item 7694) and a congratulation
 * window.
 * @param member : The player to check (party member or killer).
 */
private void tryGrantNoblesse(Player member)
{
	if (!net.sf.l2j.Config.ENABLE_RAIDBOSS_NOBLES)
		return;
	
	if (getNpcId() != 25325 || member.isNoble())
		return;
	
	member.setNoble(true, true);
	
	final NpcHtmlMessage html = new NpcHtmlMessage(getObjectId());
	html.setHtml("<html><body><title>Congratulations!</title><br><center><font color=\"LEVEL\">Congratulations!</font><br><br>You acquired all<br1>status from a Noblesse.<br><br><font color=\"808080\">You receive the Noblesse Tiara.</font></center></body></html>");
	member.sendPacket(html);
	
	member.addItem(7694, 1, true);
	member.sendPacket(new ItemList(member, true));
	member.sendMessage("You receive the Noblesse Tiara.");
}
}