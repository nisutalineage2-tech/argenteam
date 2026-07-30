package net.sf.l2j.gameserver.model.actor.instance;

import net.sf.l2j.gameserver.factionwar.FactionWarConfig;
import net.sf.l2j.gameserver.factionwar.FactionWarManager;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.AbstractNpcInfo.NpcInfo;

/**
 * A capturable checkpoint NPC used in Faction War.
 * <ul>
 *   <li>Initially neutral (no owner) — any faction player can attack it.</li>
 *   <li>When killed, the checkpoint becomes owned by the killer's faction.</li>
 *   <li>While owned, the checkpoint grants periodic score to the owning faction.</li>
 *   <li>Enemy players can attack an owned checkpoint to capture it for themselves.</li>
 * </ul>
 */
public class FactionWarCpFlag extends Monster
{
	private volatile int _ownerFactionId;
	
	public FactionWarCpFlag(int objectId, NpcTemplate template)
	{
		super(objectId, template);
		_ownerFactionId = 0; // neutral by default
	}
	
	public int getOwnerFactionId()
	{
		return _ownerFactionId;
	}
	
	public void setOwnerFactionId(int factionId)
	{
		_ownerFactionId = factionId;
		
		if (factionId == FactionWarConfig.getGoodFactionId())
			setTitle(FactionWarConfig.getGoodFactionName());
		else if (factionId == FactionWarConfig.getEvilFactionId())
			setTitle(FactionWarConfig.getEvilFactionName());
		else
			setTitle("Neutral");
		
		// Broadcast updated info to all players in range
		broadcastPacket(new NpcInfo(this, null));
	}
	
	@Override
	public boolean isAggressive()
	{
		return false;
	}
	
	@Override
	public boolean isAttackableBy(Creature attacker)
	{
		if (!FactionWarManager.getInstance().isRunning() || isDead())
			return false;
		
		final Player player = attacker.getActingPlayer();
		if (player == null)
			return false;
		
		if (player.getFactionId() <= 0)
			return false;
		
		// If neutral, anyone can attack
		if (_ownerFactionId <= 0)
			return true;
		
		// Only enemy faction can attack an owned checkpoint
		return player.getFactionId() != _ownerFactionId;
	}
	
	@Override
	public void calculateRewards(Creature killer)
	{
		// No individual rewards from killing checkpoints
	}
	
	@Override
	public boolean doDie(Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		if (killer instanceof Player player && player.getFactionId() > 0)
		{
			FactionWarManager.getInstance().onCheckpointCaptured(player.getFactionId(), this);
		}
		
		return true;
	}
}
