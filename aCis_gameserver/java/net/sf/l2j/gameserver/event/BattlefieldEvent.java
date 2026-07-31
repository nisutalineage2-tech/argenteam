package net.sf.l2j.gameserver.event;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ThreadPool;
import net.sf.l2j.gameserver.data.SkillTable;
import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.location.Location;
import net.sf.l2j.gameserver.skills.L2Skill;

public class BattlefieldEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(BattlefieldEvent.class.getName());
	
	private static final int CAPTURE_SKILL_ID = 5219;
	
	private final java.util.List<Flag> _flags = new java.util.ArrayList<>();
	private int _flagCount = 3;
	
	public BattlefieldEvent(EventConfig.EventData data)
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
			final L2Skill captureSkill = SkillTable.getInstance().getInfo(CAPTURE_SKILL_ID, 1);
			if (captureSkill != null)
				p.addSkill(captureSkill, false);
			p.setTitle("[BF] Pelea.");
			p.broadcastTitleInfo();
			p.sendMessage("[BF] Usa la habilidad Capturar en las banderas para capturarlas para tu equipo.");
		}
		
		final Location center = getData().getPositionAll();
		if (center != null)
		{
			for (int i = 0; i < _flagCount; i++)
			{
				final double angle = (Math.PI * 2 / _flagCount) * i;
				final int dist = 300 + net.sf.l2j.commons.random.Rnd.get(100);
				final int fx = center.getX() + (int)(Math.cos(angle) * dist);
				final int fy = center.getY() + (int)(Math.sin(angle) * dist);
				_flags.add(new Flag(fx, fy, center.getZ()));
			}
		}
	}
	
	public void captureFlag(int flagIndex, Player captor)
	{
		if (getState() != State.RUNNING)
			return;
		
		if (flagIndex < 0 || flagIndex >= _flags.size())
			return;
		
		final Flag flag = _flags.get(flagIndex);
		final EventPlayer ep = getEventPlayer(captor.getObjectId());
		if (ep == null)
			return;
		
		flag.setOwnerTeam(ep.getTeamId());
		
		final EventTeam team = getTeam(ep.getTeamId());
		if (team != null)
		{
			team.addScore(10);
			broadcastToPlayers("[BF] " + ep.getName() + " capturo una bandera para " + team.getName() + ".");
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
		
		broadcastToPlayers("[BF] " + killer.getName() + " mato a " + victim.getName() + ".");
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		final Player player = victim.getPlayer();
		player.sendMessage("[BF] Moriste. Reviviendo en " + getData().getRespawnDelay() + " segundos...");
		
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
	
	@Override
	protected void onStop()
	{
		_flags.clear();
		
		for (EventPlayer ep : getAllPlayers())
		{
			if (ep.isOnline())
				ep.getPlayer().removeSkill(CAPTURE_SKILL_ID, false);
		}
	}
	
	@Override
	protected String getScorebar()
	{
		final java.util.List<EventTeam> teams = getTeams();
		if (teams.size() < 2)
			return null;
		
		int blueFlags = 0, redFlags = 0;
		for (Flag f : _flags)
		{
			if (f.getOwnerTeam() == 0) blueFlags++;
			else if (f.getOwnerTeam() == 1) redFlags++;
		}
		return "[BF] Azul: " + teams.get(0).getScore() + " (" + blueFlags + " banderas) | Rojo: " + teams.get(1).getScore() + " (" + redFlags + " banderas)";
	}
	
	public java.util.List<Flag> getFlags() { return _flags; }
	
	public static class Flag
	{
		private final int _x, _y, _z;
		private int _ownerTeam = -1;
		
		public Flag(int x, int y, int z)
		{
			_x = x; _y = y; _z = z;
		}
		
		public int getX() { return _x; }
		public int getY() { return _y; }
		public int getZ() { return _z; }
		public int getOwnerTeam() { return _ownerTeam; }
		public void setOwnerTeam(int team) { _ownerTeam = team; }
	}
}
