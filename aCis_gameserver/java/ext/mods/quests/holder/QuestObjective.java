package ext.mods.quests.holder;

public class QuestObjective
{
	private final int _classId;
	private final int _npcId;
	private final int _count;

	public QuestObjective(int classId, int npcId, int count)
	{
		_classId = classId;
		_npcId = npcId;
		_count = count;
	}

	public int getClassId()
	{
		return _classId;
	}

	public int getNpcId()
	{
		return _npcId;
	}

	public int getCount()
	{
		return _count;
	}
}