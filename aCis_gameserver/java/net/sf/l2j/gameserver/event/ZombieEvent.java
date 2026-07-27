package net.sf.l2j.gameserver.event;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.skills.L2Skill;

public class ZombieEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(ZombieEvent.class.getName());
	
	private final java.util.List<EventPlayer> _zombies = new java.util.ArrayList<>();
	private int _humanBowId = 9999;
	private int _zombieSkillId = 9008;
	
	public ZombieEvent(EventConfig.EventData data)
	{
		super(data);
	}
	
	@Override
	protected void onStartRegistering()
	{
	}
	
	@Override
	protected void onStartMatch()
	{
		// All players start as humans with bow
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			final Player p = ep.getPlayer();
			equipBow(p);
			p.setTitle("[ZH] Human");
			p.broadcastTitleInfo();
		}
		
		// Pick initial zombies
		int zombieCount = Math.max(1, Math.min(2, getAllPlayers().size() / 3));
		final java.util.List<EventPlayer> shuffled = new java.util.ArrayList<>(getAllPlayers());
		java.util.Collections.shuffle(shuffled);
		
		for (int i = 0; i < zombieCount && i < shuffled.size(); i++)
			turnIntoZombie(shuffled.get(i));
		
		broadcastToPlayers("[ZH] " + zombieCount + " players turned into ZOMBIES! Run!");
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
		if (killer == null || victim == null)
			return;
		
		if (isZombie(killer) && !isZombie(victim))
		{
			// Zombie infected a human!
			turnIntoZombie(victim);
			broadcastToPlayers("[ZH] " + victim.getName() + " was INFECTED and turned into a Zombie!");
			
			// Check if any humans remain
			checkHumansRemaining();
		}
		
		// Respawn killer at their team spawn
		if (killer.isOnline())
		{
			final Player p = killer.getPlayer();
			if (p.isDead() || p.isAlikeDead())
			{
				final EventTeam team = getTeam(killer.getTeamId());
				if (team != null && team.getSpawnLocation() != null)
					p.teleportTo(team.getSpawnLocation().getX(), team.getSpawnLocation().getY(), team.getSpawnLocation().getZ(), 0);
			}
		}
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		player.sendMessage("[ZH] You died! Respawning...");
		player.teleportTo(getData().getPositionAll().getX(), getData().getPositionAll().getY(), getData().getPositionAll().getZ(), 0);
	}
	
	@Override
	protected void onStop()
	{
		// Remove zombie skills
		for (EventPlayer ep : _zombies)
		{
			if (ep.isOnline())
				ep.getPlayer().stopSkillEffects(_zombieSkillId);
		}
		_zombies.clear();
	}
	
	private void turnIntoZombie(EventPlayer ep)
	{
		if (!ep.isOnline())
			return;
		
		if (isZombie(ep))
			return;
		
		final Player p = ep.getPlayer();
		p.setTitle("[ZH] Zombie");
		p.broadcastTitleInfo();
		
		// Remove bow, give zombie skill
		final net.sf.l2j.gameserver.model.item.instance.ItemInstance bowItem = p.getInventory().getItemByItemId(_humanBowId);
		if (bowItem != null)
			p.getInventory().destroyItemByItemId(_humanBowId, bowItem.getCount());
		p.stopSkillEffects(_zombieSkillId);
		final L2Skill skill = p.getSkill(_zombieSkillId);
		if (skill != null)
			skill.getEffects(p, p);
		
		_zombies.add(ep);
		ep.setTeamId(1); // Zombies = team 1
		
		p.sendMessage("[ZH] You are now a ZOMBIE! Hunt humans!");
	}
	
	private boolean isZombie(EventPlayer ep)
	{
		return _zombies.contains(ep);
	}
	
	private void equipBow(Player player)
	{
		player.getInventory().addItem(_humanBowId, 1);
		player.sendMessage("[ZH] You received an Anti Zombie Bow!");
	}
	
	private void checkHumansRemaining()
	{
		int humans = 0;
		EventPlayer lastHuman = null;
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			if (!isZombie(ep))
			{
				humans++;
				lastHuman = ep;
			}
		}
		
		if (humans == 0)
		{
			broadcastToPlayers("[ZH] All humans infected! Zombies win!");
			endMatch();
		}
		else if (humans == 1 && lastHuman != null)
		{
			broadcastToPlayers("[ZH] " + lastHuman.getName() + " is the LAST HUMAN STANDING! Humans win!");
			endMatch();
		}
	}
	
	@Override
	protected String getScorebar()
	{
		int humans = 0, zombies = 0;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			if (isZombie(ep))
				zombies++;
			else
				humans++;
		}
		return "[ZH] Humans: " + humans + " | Zombies: " + zombies;
	}
}
