package net.sf.l2j.gameserver.guildmission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.pool.ConnectionPool;

import net.sf.l2j.gameserver.data.xml.GuildMissionData;
import net.sf.l2j.gameserver.model.actor.Player;

public class GuildMissionManager
{
	private static final CLogger LOGGER = new CLogger(GuildMissionManager.class.getName());
	
	private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS clan_mission_progress ("
		+ "clanId INT NOT NULL, missionId INT NOT NULL, progress TEXT, "
		+ "completed TINYINT(1) DEFAULT 0, lastCompleted BIGINT DEFAULT 0, repeatCount INT DEFAULT 0, "
		+ "PRIMARY KEY (clanId, missionId))";
	
	private static final String SELECT_PROGRESS = "SELECT progress, completed, lastCompleted, repeatCount FROM clan_mission_progress WHERE clanId=? AND missionId=?";
	private static final String SAVE_PROGRESS = "INSERT INTO clan_mission_progress (clanId, missionId, progress, completed, lastCompleted, repeatCount) VALUES (?,?,?,?,?,?) "
		+ "ON DUPLICATE KEY UPDATE progress=VALUES(progress), completed=VALUES(completed), lastCompleted=VALUES(lastCompleted), repeatCount=VALUES(repeatCount)";
	
	private final Map<Integer, Map<Integer, GuildMissionProgress>> _progressCache = new ConcurrentHashMap<>();
	
	protected GuildMissionManager()
	{
		GuildMissionData.getInstance();
		createTable();
	}
	
	private void createTable()
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(CREATE_TABLE))
		{
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't create clan_mission_progress table.", e);
		}
	}
	
	public List<GuildMission> getMissions()
	{
		return GuildMissionData.getInstance().getMissions();
	}
	
	public GuildMission getMission(int missionId)
	{
		return GuildMissionData.getInstance().getMission(missionId);
	}
	
	public GuildMissionProgress getProgress(int clanId, int missionId)
	{
		final Map<Integer, GuildMissionProgress> clanProgress = _progressCache.computeIfAbsent(clanId, k -> new ConcurrentHashMap<>());
		return clanProgress.computeIfAbsent(missionId, k ->
		{
			final GuildMissionProgress progress = new GuildMissionProgress();
			progress.setClanId(clanId);
			progress.setMissionId(missionId);
			loadProgress(progress);
			return progress;
		});
	}
	
	private void loadProgress(GuildMissionProgress progress)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(SELECT_PROGRESS))
		{
			ps.setInt(1, progress.getClanId());
			ps.setInt(2, progress.getMissionId());
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					progress.setCompleted(rs.getBoolean("completed"));
					progress.setLastCompleted(rs.getLong("lastCompleted"));
					progress.setRepeatCount(rs.getInt("repeatCount"));
					
					final String stored = rs.getString("progress");
					if (stored != null && !stored.isEmpty())
					{
						for (String entry : stored.split(";"))
						{
							final String[] parts = entry.split(":");
							if (parts.length == 2)
							{
								try
								{
									progress.setObjectiveProgress(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
								}
								catch (NumberFormatException e)
								{
								}
							}
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't load guild mission progress for clan {} mission {}.", e, progress.getClanId(), progress.getMissionId());
		}
	}
	
	public void saveProgress(int clanId, int missionId)
	{
		final GuildMissionProgress progress = getProgress(clanId, missionId);
		
		final StringBuilder sb = new StringBuilder();
		for (Map.Entry<Integer, Long> entry : progress.getObjectiveProgressMap().entrySet())
		{
			if (sb.length() > 0)
				sb.append(";");
			sb.append(entry.getKey()).append(":").append(entry.getValue());
		}
		
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(SAVE_PROGRESS))
		{
			ps.setInt(1, clanId);
			ps.setInt(2, missionId);
			ps.setString(3, sb.toString());
			ps.setBoolean(4, progress.isCompleted());
			ps.setLong(5, progress.getLastCompleted());
			ps.setInt(6, progress.getRepeatCount());
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			LOGGER.error("Couldn't save guild mission progress for clan {} mission {}.", e, clanId, missionId);
		}
	}
	
	/**
	 * Adds progress to a mission objective. If the mission becomes completed, it is
	 * marked and rewards are granted to the claiming player (see claimRewards).
	 */
	public void updateProgress(int clanId, int missionId, int objectiveId, long amount)
	{
		if (clanId <= 0)
			return;
		
		final GuildMission mission = getMission(missionId);
		if (mission == null || !mission.isActive())
			return;
		
		final GuildMissionProgress progress = getProgress(clanId, missionId);
		progress.addProgress(objectiveId, amount);
		
		if (mission.isCompleted(progress))
		{
			progress.setCompleted(true);
			progress.setLastCompleted(System.currentTimeMillis());
			progress.setRepeatCount(progress.getRepeatCount() + 1);
			saveProgress(clanId, missionId);
		}
	}
	
	/**
	 * Grants the mission rewards to the claiming player and his clan.
	 * Resets the progress so a repeatable mission can be done again.
	 */
	public void claimRewards(Player player, int missionId)
	{
		if (player == null || player.getClan() == null)
			return;
		
		final int clanId = player.getClanId();
		final GuildMission mission = getMission(missionId);
		if (mission == null)
			return;
		
		final GuildMissionProgress progress = getProgress(clanId, missionId);
		if (!progress.isCompleted())
			return;
		
		for (GuildMissionReward reward : mission.getRewards())
		{
			if (reward.getItemId() > 0 && reward.getItemCount() > 0)
				player.addItem(reward.getItemId(), (int) reward.getItemCount(), true);
			
			if (reward.getAdena() > 0)
				player.addItem(57, (int) reward.getAdena(), true);
			
			if (reward.getClanReputation() > 0)
				player.getClan().addReputationScore(reward.getClanReputation());
		}
		
		// Always reset the progress after a claim, so the mission can be completed
		// again (repeatable) and a non-repeatable mission cannot be claimed twice.
		progress.setCompleted(false);
		progress.getObjectiveProgressMap().clear();
		saveProgress(clanId, missionId);
	}
	
	/**
	 * Processes every active mission matching the given type and objective target.
	 */
	private void processMission(int clanId, MissionType type, int targetId, long amount)
	{
		if (clanId <= 0)
			return;
		
		for (GuildMission mission : getMissions())
		{
			if (mission.getType() != type)
				continue;
			
			for (MissionObjective objective : mission.getObjectives())
			{
				if (objective.getType() != type)
					continue;
				
				// If the objective targets a specific id (monster/npc), require a match.
				if (objective.getTargetId() > 0 && objective.getTargetId() != targetId)
					continue;
				
				updateProgress(clanId, mission.getId(), objective.getId(), amount);
			}
		}
	}
	
	public void onMonsterKill(int clanId, int monsterId, int level)
	{
		processMission(clanId, MissionType.KILL_MONSTER, monsterId, 1);
	}
	
	public void onRaidKill(int clanId, int bossId)
	{
		processMission(clanId, MissionType.KILL_RAID_BOSS, bossId, 1);
	}
	
	public void onPlayerKill(int clanId, int victimId, int level)
	{
		processMission(clanId, MissionType.KILL_PLAYER, victimId, 1);
	}
	
	public void onCraft(int clanId, int itemId)
	{
		processMission(clanId, MissionType.CRAFT_ITEM, itemId, 1);
	}
	
	public void onItemPickup(int clanId, int itemId, long count)
	{
		processMission(clanId, MissionType.COLLECT_ITEM, itemId, count);
	}
	
	public void onSiege(int clanId)
	{
		processMission(clanId, MissionType.CASTLE_SIEGE, 0, 1);
	}
	
	public void onEnchant(int clanId, int itemId, int enchant)
	{
		processMission(clanId, MissionType.ENCHANT_ITEM, itemId, enchant);
	}
	
	public List<GuildMission> getAvailableMissions(Player player)
	{
		final List<GuildMission> available = new ArrayList<>();
		if (player == null || player.getClan() == null)
			return available;
		
		final GuildMissionContext context = buildContext(player);
		for (GuildMission mission : getMissions())
		{
			if (mission.isAvailableFor(context))
				available.add(mission);
		}
		return available;
	}
	
	public GuildMissionContext buildContext(Player player)
	{
		final GuildMissionContext context = new GuildMissionContext();
		if (player == null)
			return context;
		
		if (player.getClan() != null)
		{
			context.setClanLevel(player.getClan().getLevel());
			context.setMemberCount(player.getClan().getMembersCount());
			context.setHasCastle(player.getClan().getCastleId() > 0);
			context.setHasAlliance(player.getClan().getAllyId() > 0);
		}
		
		context.setLevel(player.getStatus().getLevel());
		context.setSubClass(player.getClassId().getId());
		context.setClassName(player.getClassId().toString());
		return context;
	}
	
	public static GuildMissionManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final GuildMissionManager INSTANCE = new GuildMissionManager();
	}
}
