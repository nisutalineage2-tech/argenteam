package net.sf.l2j.gameserver.guildmission;

import java.util.ArrayList;
import java.util.List;

public class GuildMission
{
	private int _id;
	private String _name;
	private String _description;
	private MissionCategory _category;
	private MissionType _type;
	private MissionCondition _condition;
	private List<MissionObjective> _objectives = new ArrayList<>();
	private List<GuildMissionReward> _rewards = new ArrayList<>();
	private boolean _repeatable;
	private int _resetHours;
	private String _schedule;
	private boolean _active;
	
	public boolean isAvailableFor(GuildMissionContext context)
	{
		return _active && (_condition == null || _condition.isSatisfied(context));
	}
	
	public boolean isCompleted(GuildMissionProgress progress)
	{
		if (progress == null || _objectives.isEmpty())
			return false;
		
		for (MissionObjective objective : _objectives)
		{
			if (progress.getObjectiveProgress(objective.getId()) < objective.getQuantity())
				return false;
		}
		return true;
	}
	
	public int getId() { return _id; }
	public void setId(int id) { _id = id; }
	
	public String getName() { return _name; }
	public void setName(String name) { _name = name; }
	
	public String getDescription() { return _description; }
	public void setDescription(String description) { _description = description; }
	
	public MissionCategory getCategory() { return _category; }
	public void setCategory(MissionCategory category) { _category = category; }
	
	public MissionType getType() { return _type; }
	public void setType(MissionType type) { _type = type; }
	
	public MissionCondition getCondition() { return _condition; }
	public void setCondition(MissionCondition condition) { _condition = condition; }
	
	public List<MissionObjective> getObjectives() { return _objectives; }
	public void setObjectives(List<MissionObjective> objectives) { _objectives = objectives; }
	
	public List<GuildMissionReward> getRewards() { return _rewards; }
	public void setRewards(List<GuildMissionReward> rewards) { _rewards = rewards; }
	
	public boolean isRepeatable() { return _repeatable; }
	public void setRepeatable(boolean repeatable) { _repeatable = repeatable; }
	
	public int getResetHours() { return _resetHours; }
	public void setResetHours(int resetHours) { _resetHours = resetHours; }
	
	public String getSchedule() { return _schedule; }
	public void setSchedule(String schedule) { _schedule = schedule; }
	
	public boolean isActive() { return _active; }
	public void setActive(boolean active) { _active = active; }
}
