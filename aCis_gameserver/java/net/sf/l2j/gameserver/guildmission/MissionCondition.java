package net.sf.l2j.gameserver.guildmission;

public class MissionCondition
{
	private int _minClanLevel;
	private int _minMembers;
	private boolean _requiresCastle;
	private boolean _requiresAlliance;
	private String _requiredQuest;
	private String _requiredClass;
	private int _requiredLevel;
	private int _requiredSubClass;
	
	public boolean isSatisfied(GuildMissionContext context)
	{
		if (context == null)
			return false;
		
		if (context.getClanLevel() < _minClanLevel)
			return false;
		if (context.getMemberCount() < _minMembers)
			return false;
		if (_requiresCastle && !context.isHasCastle())
			return false;
		if (_requiresAlliance && !context.isHasAlliance())
			return false;
		if (_requiredLevel > 0 && context.getLevel() < _requiredLevel)
			return false;
		if (_requiredSubClass > 0 && context.getSubClass() != _requiredSubClass)
			return false;
		if (_requiredClass != null && !_requiredClass.isEmpty() && !_requiredClass.equalsIgnoreCase(context.getClassName()))
			return false;
		if (_requiredQuest != null && !_requiredQuest.isEmpty())
		{
			if (context.getCompletedQuests() == null || !context.getCompletedQuests().contains(_requiredQuest))
				return false;
		}
		return true;
	}
	
	public int getMinClanLevel() { return _minClanLevel; }
	public void setMinClanLevel(int minClanLevel) { _minClanLevel = minClanLevel; }
	
	public int getMinMembers() { return _minMembers; }
	public void setMinMembers(int minMembers) { _minMembers = minMembers; }
	
	public boolean isRequiresCastle() { return _requiresCastle; }
	public void setRequiresCastle(boolean requiresCastle) { _requiresCastle = requiresCastle; }
	
	public boolean isRequiresAlliance() { return _requiresAlliance; }
	public void setRequiresAlliance(boolean requiresAlliance) { _requiresAlliance = requiresAlliance; }
	
	public String getRequiredQuest() { return _requiredQuest; }
	public void setRequiredQuest(String requiredQuest) { _requiredQuest = requiredQuest; }
	
	public String getRequiredClass() { return _requiredClass; }
	public void setRequiredClass(String requiredClass) { _requiredClass = requiredClass; }
	
	public int getRequiredLevel() { return _requiredLevel; }
	public void setRequiredLevel(int requiredLevel) { _requiredLevel = requiredLevel; }
	
	public int getRequiredSubClass() { return _requiredSubClass; }
	public void setRequiredSubClass(int requiredSubClass) { _requiredSubClass = requiredSubClass; }
}
