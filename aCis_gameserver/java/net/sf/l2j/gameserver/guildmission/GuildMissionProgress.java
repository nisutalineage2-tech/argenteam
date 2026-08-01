package net.sf.l2j.gameserver.guildmission;

import java.util.HashMap;
import java.util.Map;

public class GuildMissionProgress
{
	private int _clanId;
	private int _missionId;
	private final Map<Integer, Long> _objectiveProgress = new HashMap<>();
	private boolean _completed;
	private long _lastCompleted;
	private int _repeatCount;
	
	public void addProgress(int objectiveId, long amount)
	{
		_objectiveProgress.merge(objectiveId, amount, Long::sum);
	}
	
	public long getObjectiveProgress(int objectiveId)
	{
		return _objectiveProgress.getOrDefault(objectiveId, 0L);
	}
	
	public void setObjectiveProgress(int objectiveId, long value)
	{
		_objectiveProgress.put(objectiveId, value);
	}
	
	public int getClanId() { return _clanId; }
	public void setClanId(int clanId) { _clanId = clanId; }
	
	public int getMissionId() { return _missionId; }
	public void setMissionId(int missionId) { _missionId = missionId; }
	
	public boolean isCompleted() { return _completed; }
	public void setCompleted(boolean completed) { _completed = completed; }
	
	public long getLastCompleted() { return _lastCompleted; }
	public void setLastCompleted(long lastCompleted) { _lastCompleted = lastCompleted; }
	
	public int getRepeatCount() { return _repeatCount; }
	public void setRepeatCount(int repeatCount) { _repeatCount = repeatCount; }
	
	public Map<Integer, Long> getObjectiveProgressMap() { return _objectiveProgress; }
}
