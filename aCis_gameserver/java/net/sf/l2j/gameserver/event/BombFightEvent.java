package net.sf.l2j.gameserver.event;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.skills.L2Skill;

public class BombFightEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(BombFightEvent.class.getName());
	
	private static final int BOMB_SKILL_ID = 5220;
	
	public BombFightEvent(EventConfig.EventData data)
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
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			final Player p = ep.getPlayer();
			final L2Skill bombSkill = SkillTable.getInstance().getInfo(BOMB_SKILL_ID, 1);
			if (bombSkill != null)
				p.addSkill(bombSkill, false);
			p.sendMessage("[Bomb] ¡Tienes la habilidad de Bomba! ¡Úsala para matar enemigos!");
			p.setTitle("[Bomb] " + p.getName());
			p.broadcastTitleInfo();
		}
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
		if (killer == null || victim == null)
			return;
		
		final EventTeam killerTeam = getTeam(killer.getTeamId());
		if (killerTeam != null)
			killerTeam.addScore(1);
		
		checkTeamAlive();
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		player.setTitle("[Bomb] Muerto");
		player.broadcastTitleInfo();
		player.sendMessage("[Bomb] ¡Has sido eliminado! Espera a la próxima ronda.");
	}
	
	@Override
	protected void onStop()
	{
		for (EventPlayer ep : getAllPlayers())
		{
			if (ep.isOnline())
				ep.getPlayer().removeSkill(BOMB_SKILL_ID, false);
		}
	}
	
	@Override
	protected String getScorebar()
	{
		int aliveBlue = 0, aliveRed = 0;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			final Player p = ep.getPlayer();
			if (p.isDead() || p.isAlikeDead())
				continue;
			if (ep.getTeamId() == 0) aliveBlue++;
			else if (ep.getTeamId() == 1) aliveRed++;
		}
		return "[Bomb] Azul: " + aliveBlue + " vivos | Rojo: " + aliveRed + " vivos";
	}
	
	private void checkTeamAlive()
	{
		int aliveBlue = 0, aliveRed = 0;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			final Player p = ep.getPlayer();
			if (p.isDead() || p.isAlikeDead())
				continue;
			if (ep.getTeamId() == 0) aliveBlue++;
			else if (ep.getTeamId() == 1) aliveRed++;
		}
		
		if (aliveBlue == 0 && aliveRed > 0)
		{
			broadcastToPlayers("[Bomb] ¡El equipo Rojo gana! ¡Todos los jugadores Azules eliminados!");
			endMatch();
		}
		else if (aliveRed == 0 && aliveBlue > 0)
		{
			broadcastToPlayers("[Bomb] ¡El equipo Azul gana! ¡Todos los jugadores Rojos eliminados!");
			endMatch();
		}
		else if (aliveBlue == 0 && aliveRed == 0)
		{
			broadcastToPlayers("[Bomb] ¡Ambos equipos eliminados! ¡Es un empate!");
			endMatch();
		}
	}
}
