package net.sf.l2j.gameserver.event;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.skills.L2Skill;

public class MutantEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(MutantEvent.class.getName());
	
	private EventPlayer _currentMutant;
	private int _mutantSkillId = 9007;
	
	public MutantEvent(EventConfig.EventData data)
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
		// Pick first mutant
		pickNewMutant();
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
		if (killer == null)
			return;
		
		// Only the mutant scores (AbstractEvent.onKill already called killer.addKill())
		if (killer == _currentMutant)
		{
			if (killer.isOnline())
			{
				killer.getPlayer().setTitle("[Mutant] " + killer.getKills() + " kills");
				killer.getPlayer().broadcastTitleInfo();
			}
			broadcastToPlayers("[Mutant] The Mutant " + killer.getName() + " killed " + (victim != null ? victim.getName() : "someone") + "!");
		}
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		
		// If the mutant died, pick a new one and respawn
		if (victim == _currentMutant)
		{
			removeMutant(victim);
			broadcastToPlayers("[Mutant] " + victim.getName() + " was killed! A new Mutant rises!");
			
			player.teleportTo(getData().getPositionAll().getX(), getData().getPositionAll().getY(), getData().getPositionAll().getZ(), 0);
			pickNewMutant();
		}
		else
		{
			// Regular player dies - respawn normally
			player.sendMessage("[Mutant] You died! Respawning...");
			final EventTeam team = getTeam(victim.getTeamId());
			if (team != null && team.getSpawnLocation() != null)
			{
				player.teleportTo(team.getSpawnLocation().getX(), team.getSpawnLocation().getY(), team.getSpawnLocation().getZ(), 0);
				player.startAbnormalEffect(AbnormalEffect.HOLD_1);
			}
		}
	}
	
	@Override
	protected void onStop()
	{
		if (_currentMutant != null)
			removeMutant(_currentMutant);
		_currentMutant = null;
	}
	
	private void pickNewMutant()
	{
		// Find a random alive player who isn't already mutant
		final java.util.List<EventPlayer> candidates = new java.util.ArrayList<>();
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			if (ep == _currentMutant)
				continue;
			final Player p = ep.getPlayer();
			if (p.isDead() || p.isAlikeDead())
				continue;
			candidates.add(ep);
		}
		
		if (candidates.isEmpty())
		{
			// No candidates - use any online player
			for (EventPlayer ep : getAllPlayers())
			{
				if (ep.isOnline())
					candidates.add(ep);
			}
		}
		
		if (candidates.isEmpty())
			return;
		
		_currentMutant = candidates.get(net.sf.l2j.commons.random.Rnd.get(candidates.size()));
		
		if (_currentMutant.isOnline())
		{
			final Player p = _currentMutant.getPlayer();
			p.setTitle("[Mutant] Kills: 0");
			p.broadcastTitleInfo();
			
			// Apply mutant buff skill
			final L2Skill skill = p.getSkill(_mutantSkillId);
			if (skill != null)
				skill.getEffects(p, p);
			
			// Visual effect
			p.startAbnormalEffect(AbnormalEffect.FLAME);
			
			broadcastToPlayers("[Mutant] " + _currentMutant.getName() + " is now the MUTANT!");
		}
	}
	
	private void removeMutant(EventPlayer ep)
	{
		if (!ep.isOnline())
			return;
		final Player p = ep.getPlayer();
		p.stopSkillEffects(_mutantSkillId);
		p.stopAbnormalEffect(AbnormalEffect.FLAME);
	}
	
	@Override
	protected String getScorebar()
	{
		return "[Mutant] " + (_currentMutant != null ? _currentMutant.getName() + " (" + _currentMutant.getKills() + ")" : "-") + " | Players: " + getAllPlayers().size();
	}
}
