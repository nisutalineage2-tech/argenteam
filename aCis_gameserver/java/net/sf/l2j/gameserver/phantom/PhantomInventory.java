package net.sf.l2j.gameserver.phantom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;

public final class PhantomInventory
{
	private static final Map<Integer, Integer> TICKS = new ConcurrentHashMap<>();
	
	private PhantomInventory()
	{
	}
	
	public static void cleanup(Player phantom)
	{
		if (phantom == null || !PhantomConfig.autoCleanupInventory())
			return;
		
		final int tick = TICKS.merge(phantom.getObjectId(), 1, Integer::sum);
		if ((tick % PhantomConfig.cleanupIntervalTicks()) != 0)
			return;
		
		if (getInventoryLoadPercent(phantom) < PhantomConfig.cleanupInventoryLoadPercent())
			return;
		
		int removed = 0;
		for (ItemInstance item : phantom.getInventory().getItems())
		{
			if (item == null || item.isEquipped() || PhantomConfig.contains(PhantomConfig.cleanupKeepItemIds(), item.getItemId()))
				continue;
			
			if (PhantomConfig.contains(PhantomConfig.cleanupItemIds(), item.getItemId()) || (PhantomConfig.cleanupAllNonEquipable() && !item.isEquipable()))
			{
				if (phantom.destroyItem(item, false))
					removed++;
			}
		}
		
		if (removed > 0)
			PhantomLog.info("Cleaned " + removed + " inventory stacks from " + phantom.getName() + " at " + getInventoryLoadPercent(phantom) + "% load.");
	}
	
	private static int getInventoryLoadPercent(Player phantom)
	{
		final int limit = Math.max(1, phantom.getWeightLimit());
		return (int) ((phantom.getInventory().getTotalWeight() * 100L) / limit);
	}
}
