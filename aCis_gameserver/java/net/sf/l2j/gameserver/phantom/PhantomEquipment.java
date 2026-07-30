package net.sf.l2j.gameserver.phantom;

import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;

public final class PhantomEquipment
{
	private static final String MEMO_KEY = "PhantomAutoEquipTier";
	
	// Weapons por tier: 0=NoGrade, 1=D, 2=C, 3=B, 4=A, 5=S
	private static final int[][] FIGHTER_WEAPONS =
	{
		{ 123, 129, 130, 225 },            // 0: No grade
		{ 975, 215, 226, 182 },            // 1: D
		{ 94, 95, 311, 260 },              // 2: C
		{ 182, 191, 240, 290 },            // 3: B
		{ 80, 301, 8680, 305, 291 },       // 4: A (Tallum Blade, Sword of Nightmare, Barakiel's Axe, Tallum Glaive, Carnage Bow)
		{ 6367, 6369, 6370, 6371, 6372, 7575 }  // 5: S (Angel Slayer, Dragon Hunter Axe, Saint Spear, Demon Splinter, Heaven's Divider, Draconic Bow)
	};
	private static final int[][] MAGE_WEAPONS =
	{
		{ 179, 186, 90 },                  // 0: No grade
		{ 195, 84, 90 },                   // 1: D
		{ 84, 195, 206 },                  // 2: C
		{ 194, 196, 234 },                 // 3: B
		{ 212, 213 },                      // 4: A (Dasparion's Staff, Branch of Mother Tree)
		{ 214, 6579 }                      // 5: S (The Staff, Arcana Mace)
	};
	
	// Heavy armor sets por tier
	private static final int[][] HEAVY_SETS =
	{
		{ 23, 43 },                                // 0: No grade
		{ 352, 2425, 2449 },                       // 1: D
		{ 58, 59, 499, 61, 62 },                   // 2: C
		{ 60, 517, 568, 107 },                     // 3: B
		{ 365, 388, 512, 2478, 563 },              // 4: A (Dark Crystal Heavy)
		{ 6373, 6374, 6378, 6375, 6376 }           // 5: S (Imperial Crusader)
	};
	
	// Light armor sets por tier
	private static final int[][] LIGHT_SETS =
	{
		{ 22, 2422, 605 },                         // 0: No grade
		{ 395, 417, 2424, 2448 },                  // 1: D
		{ 400, 420, 2436, 2460 },                  // 2: C
		{ 401, 2437 },                             // 3: B
		{ 2385, 2389, 512, 2472, 563 },            // 4: A (Dark Crystal Light)
		{ 6379, 6382, 6380, 6381 }                 // 5: S (Draconic Leather)
	};
	
	// Robe sets por tier
	private static final int[][] ROBE_SETS =
	{
		{ 1101, 1104, 2423 },                      // 0: No grade
		{ 437, 470, 2426, 608 },                   // 1: D
		{ 439, 471, 2430, 2454 },                  // 2: C
		{ 441, 472, 2435, 2459 },                  // 3: B
		{ 2400, 2405, 547, 2478, 5782 },           // 4: A (Tallum Robe)
		{ 6383, 6386, 6384, 6385 }                 // 5: S (Major Arcana)
	};
	
	// Pieces sueltas para mezclar
	private static final int[] MIX_PARTS =
	{
		43, 499, 517, 2422, 2423, 2424, 2425, 2426, 2429, 2430, 2435, 2436, 2448, 2449, 2454, 2459, 2460, 568, 600, 601, 608, 612
	};
	
	// Soulshots/Spiritshots por tier
	private static final int[][] SOULSHOTS =
	{
		{ 1835, 2509 },  // 0: No grade
		{ 1463, 2510 },  // 1: D
		{ 1464, 2511 },  // 2: C
		{ 1465, 2512 },  // 3: B
		{ 1466, 2513 },  // 4: A
		{ 1467, 2514 },  // 5: S
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
		
		if (mage)
		{
			final int[] robeSet = pickSet(ROBE_SETS, tier);
			for (int itemId : robeSet)
			{
				if (Rnd.get(100) >= PhantomConfig.equipmentIncompleteChance())
					equipItem(phantom, maybeMixedPart(itemId));
			}
		}
		else if (isDaggerClass(phantom) || isArcherClass(phantom))
		{
			final int[] lightSet = pickSet(LIGHT_SETS, tier);
			for (int itemId : lightSet)
			{
				if (Rnd.get(100) >= PhantomConfig.equipmentIncompleteChance())
					equipItem(phantom, maybeMixedPart(itemId));
			}
		}
		else
		{
			// Heavy armor for melee fighters (65%), light armor otherwise (35%)
			final boolean useHeavy = Rnd.get(100) < 65;
			final int[][] armorPool = useHeavy ? HEAVY_SETS : LIGHT_SETS;
			final int[] armorSet = pickSet(armorPool, tier);
			for (int itemId : armorSet)
			{
				if (Rnd.get(100) >= PhantomConfig.equipmentIncompleteChance())
					equipItem(phantom, maybeMixedPart(itemId));
			}
		}
		
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
		
		if (mage)
			phantom.getInventory().addItem(spiritshotId, 100000);
		else
			phantom.getInventory().addItem(soulshotId, 100000);
		
		if (mage)
			phantom.addAutoSoulShot(spiritshotId);
		else
			phantom.addAutoSoulShot(soulshotId);
	}
	
	private static boolean isDaggerClass(Player phantom)
	{
		final int classId = phantom.getClassId();
		return classId >= 35 && classId <= 39; // Dark Avenger -> Ghost Sentinel? No, daggers are 7-8, 17-18, 35-36...
	}
	
	private static boolean isArcherClass(Player phantom)
	{
		final int classId = phantom.getClassId();
		return classId >= 31 && classId <= 34; // Hawkeye, Silver Ranger, Moonlight Sentinel, Ghost Sentinel
	}
	
	private static int getTier(int level)
	{
		if (level < 20)
			return 0;
		if (level < 40)
			return 1;
		if (level < 52)
			return 2;
		if (level < 66)
			return 3;
		if (level < 76)
			return 4;
		return 5;
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
