package net.sf.l2j.gameserver.phantom;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.model.actor.Player;

public final class PhantomProgression
{
	private static final Map<Integer, Integer> LAST_SKILL_LEVEL = new ConcurrentHashMap<>();
	
	private PhantomProgression()
	{
	}
	
	public static void think(Player phantom)
	{
		if (phantom == null || phantom.isDead())
			return;
		
		if (PhantomConfig.autoClassEnabled())
			tryAutoClass(phantom);
		
		if (PhantomConfig.autoSkillEnabled())
			tryAutoSkills(phantom);
	}
	
	public static void forget(int objectId)
	{
		LAST_SKILL_LEVEL.remove(objectId);
	}
	
	private static void tryAutoSkills(Player phantom)
	{
		final int level = phantom.getStatus().getLevel();
		final Integer last = LAST_SKILL_LEVEL.get(phantom.getObjectId());
		if (last != null && last == level)
			return;
		
		phantom.rewardSkills();
		phantom.broadcastUserInfo();
		phantom.store();
		LAST_SKILL_LEVEL.put(phantom.getObjectId(), level);
		PhantomLog.info("AutoSkills updated for " + phantom.getName() + " level " + level + " class " + phantom.getClassId());
	}
	
	private static void tryAutoClass(Player phantom)
	{
		final ClassId current = phantom.getClassId();
		final ClassId next = nextClassFor(phantom);
		if (next == null || next == current)
			return;
		
		phantom.setClassId(next.getId());
		if (phantom.getClassIndex() == 0)
			phantom.setBaseClass(next);
		
		phantom.rewardSkills();
		phantom.broadcastUserInfo();
		phantom.store();
		LAST_SKILL_LEVEL.remove(phantom.getObjectId());
		PhantomLog.info("AutoClass changed " + phantom.getName() + " from " + current + " to " + next + ".");
	}
	
	private static ClassId nextClassFor(Player phantom)
	{
		final int level = phantom.getStatus().getLevel();
		final ClassId current = phantom.getClassId();
		if (current.getLevel() == 0 && level >= PhantomConfig.autoFirstClassLevel())
			return firstClass(current);
		
		if (current.getLevel() == 1 && level >= PhantomConfig.autoSecondClassLevel())
			return secondClass(current);
		
		if (current.getLevel() == 2 && PhantomConfig.autoThirdClassEnabled() && level >= PhantomConfig.autoThirdClassLevel())
			return thirdClass(current);
		
		return null;
	}
	
	private static ClassId firstClass(ClassId current)
	{
		return switch (current)
		{
			case HUMAN_FIGHTER -> ClassId.WARRIOR;
			case HUMAN_MYSTIC -> ClassId.HUMAN_WIZARD;
			case ELVEN_FIGHTER -> ClassId.ELVEN_SCOUT;
			case ELVEN_MYSTIC -> ClassId.ELVEN_WIZARD;
			case DARK_FIGHTER -> ClassId.ASSASSIN;
			case DARK_MYSTIC -> ClassId.DARK_WIZARD;
			case ORC_FIGHTER -> ClassId.ORC_RAIDER;
			case ORC_MYSTIC -> ClassId.ORC_SHAMAN;
			case DWARVEN_FIGHTER -> ClassId.SCAVENGER;
			default -> null;
		};
	}
	
	private static ClassId secondClass(ClassId current)
	{
		return switch (current)
		{
			case WARRIOR -> ClassId.GLADIATOR;
			case HUMAN_WIZARD -> ClassId.SORCERER;
			case CLERIC -> ClassId.PROPHET;
			case ELVEN_KNIGHT -> ClassId.SWORD_SINGER;
			case ELVEN_SCOUT -> ClassId.SILVER_RANGER;
			case ELVEN_WIZARD -> ClassId.SPELLSINGER;
			case ELVEN_ORACLE -> ClassId.ELVEN_ELDER;
			case PALUS_KNIGHT -> ClassId.BLADEDANCER;
			case ASSASSIN -> ClassId.PHANTOM_RANGER;
			case DARK_WIZARD -> ClassId.SPELLHOWLER;
			case SHILLIEN_ORACLE -> ClassId.SHILLIEN_ELDER;
			case ORC_RAIDER -> ClassId.DESTROYER;
			case MONK -> ClassId.TYRANT;
			case ORC_SHAMAN -> ClassId.WARCRYER;
			case SCAVENGER -> ClassId.BOUNTY_HUNTER;
			case ARTISAN -> ClassId.WARSMITH;
			default -> null;
		};
	}
	
	private static ClassId thirdClass(ClassId current)
	{
		return switch (current)
		{
			case GLADIATOR -> ClassId.DUELIST;
			case WARLORD -> ClassId.DREADNOUGHT;
			case PALADIN -> ClassId.PHOENIX_KNIGHT;
			case DARK_AVENGER -> ClassId.HELL_KNIGHT;
			case TREASURE_HUNTER -> ClassId.ADVENTURER;
			case HAWKEYE -> ClassId.SAGGITARIUS;
			case SORCERER -> ClassId.ARCHMAGE;
			case NECROMANCER -> ClassId.SOULTAKER;
			case WARLOCK -> ClassId.ARCANA_LORD;
			case BISHOP -> ClassId.CARDINAL;
			case PROPHET -> ClassId.HIEROPHANT;
			case TEMPLE_KNIGHT -> ClassId.EVAS_TEMPLAR;
			case SWORD_SINGER -> ClassId.SWORD_MUSE;
			case PLAINS_WALKER -> ClassId.WIND_RIDER;
			case SILVER_RANGER -> ClassId.MOONLIGHT_SENTINEL;
			case SPELLSINGER -> ClassId.MYSTIC_MUSE;
			case ELEMENTAL_SUMMONER -> ClassId.ELEMENTAL_MASTER;
			case ELVEN_ELDER -> ClassId.EVAS_SAINT;
			case SHILLIEN_KNIGHT -> ClassId.SHILLIEN_TEMPLAR;
			case BLADEDANCER -> ClassId.SPECTRAL_DANCER;
			case ABYSS_WALKER -> ClassId.GHOST_HUNTER;
			case PHANTOM_RANGER -> ClassId.GHOST_SENTINEL;
			case SPELLHOWLER -> ClassId.STORM_SCREAMER;
			case PHANTOM_SUMMONER -> ClassId.SPECTRAL_MASTER;
			case SHILLIEN_ELDER -> ClassId.SHILLIEN_SAINT;
			case DESTROYER -> ClassId.TITAN;
			case TYRANT -> ClassId.GRAND_KHAVATARI;
			case OVERLORD -> ClassId.DOMINATOR;
			case WARCRYER -> ClassId.DOOMCRYER;
			case BOUNTY_HUNTER -> ClassId.FORTUNE_SEEKER;
			case WARSMITH -> ClassId.MAESTRO;
			default -> null;
		};
	}
}
