package net.sf.l2j.gameserver.dungeon;

import java.util.Map;

/**
 * Immutable template that defines a dungeon: ID, name, required players,
 * fixed rewards, reward HTML, cooldown hours, and ordered stages.
 */
public class DungeonTemplate
{
	private final int _id;
	private final String _name;
	private final int _minPlayers;
	private final int _maxPlayers;
	private final int _minLevel;
	private final int _maxLevel;
	private final int _cooldownHours;
	private final Map<Integer, Integer> _rewards;
	private final String _rewardHtm;
	private final String _enterHtm;
	private final Map<Integer, DungeonStage> _stages;
	
	public DungeonTemplate(int id, String name, int minPlayers, int maxPlayers, int minLevel, int maxLevel, int cooldownHours, Map<Integer, Integer> rewards, String rewardHtm, String enterHtm, Map<Integer, DungeonStage> stages)
	{
		_id = id;
		_name = name;
		_minPlayers = minPlayers;
		_maxPlayers = maxPlayers;
		_minLevel = minLevel;
		_maxLevel = maxLevel;
		_cooldownHours = cooldownHours;
		_rewards = rewards;
		_rewardHtm = rewardHtm;
		_enterHtm = enterHtm;
		_stages = stages;
	}
	
	public int getId() { return _id; }
	public String getName() { return _name; }
	public int getMinPlayers() { return _minPlayers; }
	public int getMaxPlayers() { return _maxPlayers; }
	public int getMinLevel() { return _minLevel; }
	public int getMaxLevel() { return _maxLevel; }
	public int getCooldownHours() { return _cooldownHours; }
	public Map<Integer, Integer> getRewards() { return _rewards; }
	public String getRewardHtm() { return _rewardHtm; }
	public String getEnterHtm() { return _enterHtm; }
	public Map<Integer, DungeonStage> getStages() { return _stages; }
	
	/** @return the first stage (order 1), or null if no stages defined. */
	public DungeonStage getFirstStage()
	{
		return _stages.isEmpty() ? null : _stages.get(1);
	}
	
	/** @return total number of stages in this dungeon. */
	public int getStageCount() { return _stages.size(); }
}
