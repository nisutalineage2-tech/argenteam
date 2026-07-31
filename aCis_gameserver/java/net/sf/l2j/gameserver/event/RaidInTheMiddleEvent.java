package net.sf.l2j.gameserver.event;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.gameserver.data.xml.NpcData;	import net.sf.l2j.gameserver.enums.skills.AbnormalEffect;
	import net.sf.l2j.gameserver.model.actor.Player;
	import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
	import net.sf.l2j.gameserver.model.location.Location;
	import net.sf.l2j.gameserver.model.spawn.Spawn;

public class RaidInTheMiddleEvent extends AbstractEvent
{
	private static final CLogger LOGGER = new CLogger(RaidInTheMiddleEvent.class.getName());
	
	private int _bossNpcId = 29001;
	private int _bossSpawnDelay = 30;
	private Spawn _bossSpawn;
	private boolean _bossAlive;
	
	public RaidInTheMiddleEvent(EventConfig.EventData data)
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
			if (ep.isOnline())
			{
				ep.getPlayer().setTitle("[Raid] Fight!");
				ep.getPlayer().broadcastTitleInfo();
				ep.getPlayer().sendMessage("[Raid] A raid boss will spawn soon! Kill the enemies first, then the boss!");
			}
		}
		
		// Schedule boss spawn
		net.sf.l2j.commons.pool.ThreadPool.schedule(this::spawnBoss, _bossSpawnDelay * 1000L);
	}
	
	private void spawnBoss()
	{
		if (getState() != State.RUNNING)
			return;
		
		final java.util.List<EventTeam> teams = getTeams();
		if (teams.size() < 2)
			return;
		
		// Spawn boss in the middle between blue and red spawns
		final EventTeam blue = teams.get(0);
		final EventTeam red = teams.get(1);
		
		int midX, midY, midZ;
		if (blue.getSpawnLocation() != null && red.getSpawnLocation() != null)
		{
			midX = (blue.getSpawnLocation().getX() + red.getSpawnLocation().getX()) / 2;
			midY = (blue.getSpawnLocation().getY() + red.getSpawnLocation().getY()) / 2;
			midZ = (blue.getSpawnLocation().getZ() + red.getSpawnLocation().getZ()) / 2;
		}
		else
		{
			final var center = getData().getPositionAll();
			if (center == null) return;
			midX = center.getX();
			midY = center.getY();
			midZ = center.getZ();
		}
		
		try
		{
			final NpcTemplate template = NpcData.getInstance().getTemplate(_bossNpcId);
			if (template == null)
			{
				LOGGER.warn("Cannot spawn boss NPC {}. Template not found.", _bossNpcId);
				return;
			}
			
			_bossSpawn = new Spawn(template);
			_bossSpawn.setLoc(midX, midY, midZ, 0);
			_bossSpawn.setRespawnDelay(0);
			_bossSpawn.doSpawn(false);
			_bossAlive = true;
			
			broadcastToPlayers("[Raid] The RAID BOSS has appeared in the middle! Kill it to win!");
		}
		catch (Exception e)
		{
			LOGGER.error("Failed to spawn raid boss.", e);
		}
	}
	
	/**
	 * @return the NPC id of the raid boss (configurable via Event_{id}_BossNpcId).
	 */
	public int getBossNpcId()
	{
		return _bossNpcId;
	}
	
	/**
	 * @return true if the raid boss is currently alive and spawned.
	 */
	public boolean isBossAlive()
	{
		return _bossAlive;
	}
	
	/**
	 * @return the boss spawn location (middle between team spawns), or null if not spawned yet.
	 */
	public Location getBossLocation()
	{
		return (_bossSpawn != null && _bossSpawn.getNpc() != null) ? new Location(_bossSpawn.getNpc().getX(), _bossSpawn.getNpc().getY(), _bossSpawn.getNpc().getZ()) : null;
	}
	
	// Called from RaidBoss.doDie() when the raid boss dies
	public void onBossKilled(Player killer)
	{
		if (!_bossAlive || getState() != State.RUNNING)
			return;
		
		_bossAlive = false;
		
		final EventPlayer ep = getEventPlayer(killer.getObjectId());
		if (ep == null) return;
		
		final EventTeam winnerTeam = getTeam(ep.getTeamId());
		if (winnerTeam != null)
		{
			broadcastToPlayers("[Raid] " + winnerTeam.getName() + " team killed the raid boss! They win!");
			endMatch();
		}
	}
	
	@Override
	protected void onEventKill(EventPlayer killer, EventPlayer victim)
	{
		if (killer == null || victim == null)
			return;
		
		// No respawn for killed players (they stay dead)
		broadcastToPlayers("[Raid] " + killer.getName() + " killed " + victim.getName() + "! No respawn!");
	}
	
	@Override
	protected void onEventDie(EventPlayer victim, EventPlayer killer)
	{
		if (victim == null || !victim.isOnline())
			return;
		
		// Player stays dead - no respawn
		final Player player = victim.getPlayer();
		player.setTitle("[Raid] Dead");
		player.broadcastTitleInfo();
		player.sendMessage("[Raid] You are dead! Wait for your team to kill the boss or be revived!");
	}
	
	@Override
	protected void onStop()
	{
		// Despawn boss if alive
		if (_bossSpawn != null && _bossSpawn.getNpc() != null)
			_bossSpawn.getNpc().deleteMe();
		_bossSpawn = null;
		_bossAlive = false;
	}
	
	@Override
	protected String getScorebar()
	{
		int blueAlive = 0, redAlive = 0;
		for (EventPlayer ep : getAllPlayers())
		{
			if (!ep.isOnline())
				continue;
			final Player p = ep.getPlayer();
			if (p.isDead() || p.isAlikeDead())
				continue;
			if (ep.getTeamId() == 0) blueAlive++;
			else if (ep.getTeamId() == 1) redAlive++;
		}
		return "[Raid] Blue: " + blueAlive + " alive | Red: " + redAlive + " alive | Boss: " + (_bossAlive ? "Alive" : "Dead");
	}
}
