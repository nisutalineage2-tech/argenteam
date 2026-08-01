package net.sf.l2j.gameserver.guildmission;

import java.util.Set;

public class GuildMissionContext
{
	private int _clanLevel;
	private int _memberCount;
	private boolean _hasCastle;
	private boolean _hasAlliance;
	private int _level;
	private int _subClass;
	private String _className;
	private Set<String> _completedQuests;
	
	public int getClanLevel() { return _clanLevel; }
	public void setClanLevel(int clanLevel) { _clanLevel = clanLevel; }
	
	public int getMemberCount() { return _memberCount; }
	public void setMemberCount(int memberCount) { _memberCount = memberCount; }
	
	public boolean isHasCastle() { return _hasCastle; }
	public void setHasCastle(boolean hasCastle) { _hasCastle = hasCastle; }
	
	public boolean isHasAlliance() { return _hasAlliance; }
	public void setHasAlliance(boolean hasAlliance) { _hasAlliance = hasAlliance; }
	
	public int getLevel() { return _level; }
	public void setLevel(int level) { _level = level; }
	
	public int getSubClass() { return _subClass; }
	public void setSubClass(int subClass) { _subClass = subClass; }
	
	public String getClassName() { return _className; }
	public void setClassName(String className) { _className = className; }
	
	public Set<String> getCompletedQuests() { return _completedQuests; }
	public void setCompletedQuests(Set<String> completedQuests) { _completedQuests = completedQuests; }
}
