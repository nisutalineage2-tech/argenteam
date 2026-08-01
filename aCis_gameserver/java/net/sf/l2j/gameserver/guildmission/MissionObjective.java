package net.sf.l2j.gameserver.guildmission;

public class MissionObjective
{
	private int _id;
	private MissionType _type;
	private int _targetId;
	private long _quantity;
	private String _metadata;
	
	public MissionObjective()
	{
	}
	
	public MissionObjective(int id, MissionType type, int targetId, long quantity, String metadata)
	{
		_id = id;
		_type = type;
		_targetId = targetId;
		_quantity = quantity;
		_metadata = metadata;
	}
	
	public int getId() { return _id; }
	public void setId(int id) { _id = id; }
	
	public MissionType getType() { return _type; }
	public void setType(MissionType type) { _type = type; }
	
	public int getTargetId() { return _targetId; }
	public void setTargetId(int targetId) { _targetId = targetId; }
	
	public long getQuantity() { return _quantity; }
	public void setQuantity(long quantity) { _quantity = quantity; }
	
	public String getMetadata() { return _metadata; }
	public void setMetadata(String metadata) { _metadata = metadata; }
}
