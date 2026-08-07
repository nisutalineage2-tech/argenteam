package ext.mods.quests.holder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QuestHolder
{
	private final int _id;
	private final boolean _repeatable;
	private String _name;
	private String _desc;
	private String _icon;
	private String _sound;
	private final Set<Integer> _requiredClasses = new HashSet<>();
	private final List<QuestObjective> _objectives = new ArrayList<>();
	private final List<QuestReward> _rewards = new ArrayList<>();

	public QuestHolder(int id, boolean repeatable)
	{
		_id = id;
		_repeatable = repeatable;
	}

	public int getId()
	{
		return _id;
	}

	public boolean isRepeatable()
	{
		return _repeatable;
	}

	public void setName(String name)
	{
		_name = name;
	}

	public void setDesc(String desc)
	{
		_desc = desc;
	}

	public void setIcon(String icon)
	{
		_icon = icon;
	}

	public void setSound(String sound)
	{
		_sound = sound;
	}

	public String getName()
	{
		return _name;
	}

	public String getDesc()
	{
		return _desc;
	}

	public String getIcon()
	{
		return _icon;
	}

	public String getSound()
	{
		return _sound;
	}

	public void addRequiredClass(int classId)
	{
		_requiredClasses.add(classId);
	}

	public Set<Integer> getRequiredClasses()
	{
		return _requiredClasses;
	}

	public void addObjective(QuestObjective obj)
	{
		_objectives.add(obj);
	}

	public List<QuestObjective> getObjectives()
	{
		return _objectives;
	}

	public void addReward(QuestReward reward)
	{
		_rewards.add(reward);
	}

	public List<QuestReward> getRewards()
	{
		return _rewards;
	}

	public List<QuestReward> getRewardsForClass(int classId)
	{
		List<QuestReward> list = new ArrayList<>();
		for (QuestReward reward : _rewards)
		{
			if (reward.getClassId() == classId)
			{
				list.add(reward);
			}
		}
		return list;
	}

	public List<QuestObjective> getObjectivesForClass(int classId)
	{
		List<QuestObjective> list = new ArrayList<>();
		for (QuestObjective obj : _objectives)
		{
			if (obj.getClassId() == classId)
			{
				list.add(obj);
			}
		}
		return list;
	}
}