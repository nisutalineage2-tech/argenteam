package net.sf.l2j.gameserver.event;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
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
		pickNewMutant();
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
		if (killer == null)
			return;
		
		if (killer == _currentMutant)
		{
			if (killer.isOnline())
			{
				killer.getPlayer().setTitle("[Mut] " + killer.getKills() + " bajas");
				killer.getPlayer().broadcastTitleInfo();
			}
			broadcastToPlayers("[Mutant] El Mutante " + killer.getName() + " mato a " + (victim != null ? victim.getName() : "alguien") + "!");
		}
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		
		if (victim == _currentMutant)
		{
			removeMutant(victim);
			broadcastToPlayers("[Mutant] " + victim.getName() + " fue eliminado! Un nuevo Mutante surge!");
			pickNewMutant();
		}
		else
		{
			player.sendMessage("[Mutant] Moriste! Reviviendo en " + getData().getRespawnDelay() + " segundos...");
			
			player.disableAllSkills();
			player.setIsImmobilized(true);
			player.startAbnormalEffect(AbnormalEffect.HOLD_1);
			
			final EventTeam team = getTeam(victim.getTeamId());
			final int respawnX = (team != null && team.getSpawnLocation() != null) ? team.getSpawnLocation().getX() : player.getX();
			final int respawnY = (team != null && team.getSpawnLocation() != null) ? team.getSpawnLocation().getY() : player.getY();
			final int respawnZ = (team != null && team.getSpawnLocation() != null) ? team.getSpawnLocation().getZ() : player.getZ();
			
			ThreadPool.schedule(() ->
			{
				if (player == null || !player.isOnline())
					return;
				
				if (player.isDead())
					player.doRevive();
				
				player.getStatus().setCpHpMp(player.getStatus().getMaxCp(), player.getStatus().getMaxHp(), player.getStatus().getMaxMp());
				player.stopAbnormalEffect(AbnormalEffect.HOLD_1);
				player.enableAllSkills();
				player.setIsImmobilized(false);
				player.teleportTo(respawnX, respawnY, respawnZ, 0);
			}, getData().getRespawnDelay() * 1000L);
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
			p.setTitle("[Mut] 0 bajas");
			p.broadcastTitleInfo();
			
			final L2Skill skill = p.getSkill(_mutantSkillId);
			if (skill != null)
				skill.getEffects(p, p);
			
			p.startAbnormalEffect(AbnormalEffect.FLAME);
			
			broadcastToPlayers("[Mutant] " + _currentMutant.getName() + " ahora es el MUTANTE!");
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
		return "[Mutant] " + (_currentMutant != null ? _currentMutant.getName() + " (" + _currentMutant.getKills() + ")" : "-") + " | Jugadores: " + getAllPlayers().size();
	}
}
