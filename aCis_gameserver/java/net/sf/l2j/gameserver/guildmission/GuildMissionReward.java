package net.sf.l2j.gameserver.guildmission;

public class GuildMissionReward
{
	private int _itemId;
	private long _itemCount;
	private long _adena;
	private int _clanReputation;
	private int _skillId;
	private int _buffId;
	private long _coins;
	private String _customReward;
	
	public int getItemId() { return _itemId; }
	public void setItemId(int itemId) { _itemId = itemId; }
	
	public long getItemCount() { return _itemCount; }
	public void setItemCount(long itemCount) { _itemCount = itemCount; }
	
	public long getAdena() { return _adena; }
	public void setAdena(long adena) { _adena = adena; }
	
	public int getClanReputation() { return _clanReputation; }
	public void setClanReputation(int clanReputation) { _clanReputation = clanReputation; }
	
	public int getSkillId() { return _skillId; }
	public void setSkillId(int skillId) { _skillId = skillId; }
	
	public int getBuffId() { return _buffId; }
	public void setBuffId(int buffId) { _buffId = buffId; }
	
	public long getCoins() { return _coins; }
	public void setCoins(long coins) { _coins = coins; }
	
	public String getCustomReward() { return _customReward; }
	public void setCustomReward(String customReward) { _customReward = customReward; }
}
