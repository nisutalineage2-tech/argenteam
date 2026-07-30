package net.sf.l2j.gameserver.phantom;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;

public final class PhantomEquipment
{
	private static final String MEMO_KEY = "PhantomAutoEquipTier";
	
	private static final int[][] FIGHTER_WEAPONS =
	{
		{ 123, 129, 130, 225 },
		{ 975, 215, 226, 182 },
		{ 142, 191, 194, 234 },
		{ 142, 182, 191, 194, 234 }
	};
	private static final int[][] MAGE_WEAPONS =
	{
		{ 179, 186, 90 },
		{ 195, 84, 90 },
		{ 84, 195, 206 },
		{ 84, 206, 195 }
	};
	private static final int[][] HEAVY_SETS =
	{
		{ 23, 43 },
		{ 352, 2425, 2449 },
		{ 58, 59, 499, 61, 62 },
		{ 60, 517, 568, 107 },
		{ 357, 383, 503, 612, 5726, 633 }
	};
	private static final int[][] LIGHT_SETS =
	{
		{ 22, 2422, 605 },
		{ 395, 417, 2424, 2448 },
		{ 400, 420, 2436, 2460 },
		{ 401, 2437 },
		{ 9073, 9074, 9075, 9076 }
	};
	private static final int[][] ROBE_SETS =
	{
		{ 1101, 1104, 2423 },
		{ 437, 470, 2426, 608 },
		{ 439, 471, 2430, 2454 },
		{ 441, 472, 2435, 2459 },
		{ 9077, 9078, 9079, 9080 }
	};
	private static final int[] MIX_PARTS =
	{
		43, 499, 517, 2422, 2423, 2424, 2425, 2426, 2429, 2430, 2435, 2436, 2448, 2449, 2454, 2459, 2460, 568, 600, 601, 608, 612
	};
	
	private static final int[][] SOULSHOTS =
	{
		{ 1835, 2509 }, // Tier 0: No grade
		{ 1463, 2510 }, // Tier 1: D
		{ 1464, 2511 }, // Tier 2: C
		{ 1465, 2512 }, // Tier 3: B
	};
	
	private PhantomEquipment()
	{
	}
	
	public static void equip(Player phantom)
	{
		if (phantom == null)
			return;
		
		final int tier = getTier(phantom.getStatus().getLevel());
		if (!PhantomConfig.autoEquipRefresh() && phantom.getMemos().getInteger(MEMO_KEY, -1) == tier)
			return;
		
		final boolean mage = phantom.isMageClass();
		final int[] weaponPool = mage ? MAGE_WEAPONS[tier] : FIGHTER_WEAPONS[tier];
		equipItem(phantom, pick(weaponPool));
		
		final int[] baseSet = pickSet(mage ? ROBE_SETS : (Rnd.get(100) < 65 ? HEAVY_SETS : LIGHT_SETS), tier);
		for (int itemId : baseSet)
		{
			if (Rnd.get(100) >= PhantomConfig.equipmentIncompleteChance())
				equipItem(phantom, maybeMixedPart(itemId));
		}
		
		// Soulshots / Spiritshots
		giveShots(phantom, tier, mage);
		
		phantom.getMemos().set(MEMO_KEY, tier);
		phantom.broadcastUserInfo();
	}
	
	private static void giveShots(Player phantom, int tier, boolean mage)
	{
		if (tier < 0 || tier >= SOULSHOTS.length)
			tier = 0;
		
		final int soulshotId = SOULSHOTS[tier][0];
		final int spiritshotId = SOULSHOTS[tier][1];
		
		// Give soulshots for physical classes, spiritshots for mages
		if (mage)
			phantom.getInventory().addItem(spiritshotId, 100000);
		else
			phantom.getInventory().addItem(soulshotId, 100000);
		
		// Enable auto-use
		if (mage)
			phantom.addAutoSoulShot(spiritshotId);
		else
			phantom.addAutoSoulShot(soulshotId);
	}
	
	private static int getTier(int level)
	{
		if (level < 20)
			return 0;
		if (level < 40)
			return 1;
		if (level < 52)
			return 2;
		return 3;
	}
	
	private static int[] pickSet(int[][] sets, int tier)
	{
		final int max = Math.min(sets.length - 1, tier + 1);
		final int min = Math.max(0, tier - 1);
		return sets[Rnd.get(min, max)];
	}
	
	private static int maybeMixedPart(int itemId)
	{
		return (Rnd.get(100) < PhantomConfig.equipmentMixedChance()) ? pick(MIX_PARTS) : itemId;
	}
	
	private static int pick(int[] ids)
	{
		return ids[Rnd.get(ids.length)];
	}
	
	private static void equipItem(Player phantom, int itemId)
	{
		final ItemInstance item = phantom.getInventory().addItem(itemId, 1);
		if (item != null && item.isEquipable())
			phantom.getInventory().equipItemAndRecord(item);
	}
}
