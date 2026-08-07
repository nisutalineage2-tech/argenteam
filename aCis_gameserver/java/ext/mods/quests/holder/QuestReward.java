package ext.mods.quests.holder;

public class QuestReward
{
	private final int _classId;
	private final int _itemId;
	private final int _count;

	public QuestReward(int classId, int itemId, int count)
	{
		_classId = classId;
		_itemId = itemId;
		_count = count;
	}

	public int getClassId()
	{
		return _classId;
	}

	public int getItemId()
	{
		return _itemId;
	}

	public int getCount()
	{
		return _count;
	}
}