package net.sf.l2j.gameserver.phantom;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.sf.l2j.commons.logging.CLogger;
import net.sf.l2j.commons.random.Rnd;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.data.sql.PlayerInfoTable;
import net.sf.l2j.gameserver.data.xml.FactionData;
import net.sf.l2j.gameserver.data.xml.PlayerData;
import net.sf.l2j.gameserver.data.xml.ScriptData;
import net.sf.l2j.gameserver.enums.QuestStatus;
import net.sf.l2j.gameserver.enums.ShortcutType;
import net.sf.l2j.gameserver.enums.actors.ClassId;
import net.sf.l2j.gameserver.enums.actors.Sex;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.model.Shortcut;
import net.sf.l2j.gameserver.model.World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.PlayerTemplate;
import net.sf.l2j.gameserver.model.holder.skillnode.GeneralSkillNode;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.records.NewbieItem;
import net.sf.l2j.gameserver.scripting.Quest;

public final class PhantomFactory
{
	private static final CLogger LOGGER = new CLogger(PhantomFactory.class.getName());
	private static final String ACCOUNT_NAME = "phantom_ai";
	private static final AtomicInteger CLASS_CURSOR = new AtomicInteger();
	private static final AtomicInteger SEX_CURSOR = new AtomicInteger();
	
	private static final ClassId[] BASE_CLASSES =
	{
		ClassId.HUMAN_FIGHTER,
		ClassId.ELVEN_FIGHTER,
		ClassId.DARK_FIGHTER,
		ClassId.ORC_FIGHTER,
		ClassId.DWARVEN_FIGHTER,
		ClassId.HUMAN_MYSTIC,
		ClassId.ELVEN_MYSTIC,
		ClassId.DARK_MYSTIC,
		ClassId.ORC_MYSTIC
	};
	private static final String[] MALE_NAMES =
	{
		"Alejandro", "Alvaro", "Andres", "Antonio", "Carlos", "Cristian", "David", "Diego", "Eduardo", "Fernando", "Francisco", "Gonzalo", "Guillermo", "Hector", "Ignacio", "Ivan", "Jaime", "Javier", "Jorge", "Jose", "Juan", "Julian", "Kevin", "Leandro", "Lucas", "Luis", "Manuel", "Marcelo", "Marco", "Martin", "Miguel", "Nicolas", "Pablo", "Pedro", "Rafael", "Ricardo", "Roberto", "Rodrigo", "Santiago", "Sergio", "Tomas", "Victor", "Xavier"
	};
	private static final String[] FEMALE_NAMES =
	{
		"Alejandra", "Andrea", "Angela", "Beatriz", "Camila", "Carla", "Carolina", "Catalina", "Clara", "Daniela", "Diana", "Elena", "Gabriela", "Isabel", "Laura", "Lorena", "Luna", "Maria", "Marta", "Monica", "Nadia", "Natalia", "Noelia", "Patricia", "Paula", "Rosa", "Sandra", "Selene", "Sofia", "Tania", "Valentina", "Valeria", "Vera", "Veronica", "Victoria", "Ximena"
	};
	private static final String[] LAST_PARTS =
	{
		"Stone", "Blade", "River", "Storm", "Cross", "Wolf", "Knight", "Raven", "Frost", "Light", "Shade", "Dawn", "Night", "Shadow", "Soul", "Fury", "Wind", "Fire", "Crow", "Hawk", "Fang", "Heart", "Wing", "Bane", "Ark", "Void", "Gem", "Song", "Mourn", "Wrath", "Crest", "Shield", "Spear", "Caster"
	};
	private static final String[] FANTASY_NAMES =
	{
		"Astaroth", "Belial", "Cronos", "Darkar", "Eclipse", "Fenix", "Gorath", "Havoc", "Inferno", "Jade", "Kael", "Legion", "Mystic", "Nemesis", "Onyx", "Phantom", "Quasar", "Ragnar", "Spectral", "Titan", "Umbra", "Valak", "Warden", "Xenith", "Yggdrasil", "Zarath", "Drako", "Ether", "Falcon", "Grimm", "Hyperion", "Icarus", "Kraken", "Lynx", "Morpheus", "Noctis", "Orion", "Phoenix", "Ravager", "Shadow", "Tempest", "Vortex", "Wraith", "Zero", "Arkham", "Berserk", "Doom", "Exodus", "Frost", "Ghost"
	};
	
	private PhantomFactory()
	{
	}
	
	public static List<Player> create(int count)
	{
		final List<Player> created = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			final Player phantom = createOne();
			if (phantom != null)
				created.add(phantom);
		}
		return created;
	}
	
	private static Player createOne()
	{
		final ClassId classId = BASE_CLASSES[Math.floorMod(CLASS_CURSOR.getAndIncrement(), BASE_CLASSES.length)];
		final PlayerTemplate template = PlayerData.getInstance().getTemplate(classId);
		if (template == null)
			return null;
		
		final Sex sex = (SEX_CURSOR.getAndIncrement() % 2 == 0) ? Sex.MALE : Sex.FEMALE;
		final String name = buildUniqueName(sex);
		final byte hairStyle = (byte) Rnd.get(sex == Sex.MALE ? 5 : 7);
		final byte hairColor = (byte) Rnd.get(4);
		final byte face = (byte) Rnd.get(3);
		
		final Player phantom = Player.create(IdFactory.getInstance().getNextId(), template, ACCOUNT_NAME, name, hairStyle, hairColor, face, sex);
		if (phantom == null)
			return null;
		
		phantom.getStatus().setMaxHpMp();
		World.getInstance().addObject(phantom);
		phantom.getPosition().set(template.getRandomSpawn());
		phantom.setTitle("");
		
		phantom.getShortcutList().addShortcut(new Shortcut(0, 0, ShortcutType.ACTION, 2, -1, 1));
		phantom.getShortcutList().addShortcut(new Shortcut(3, 0, ShortcutType.ACTION, 5, -1, 1));
		phantom.getShortcutList().addShortcut(new Shortcut(10, 0, ShortcutType.ACTION, 0, -1, 1));
		
		for (NewbieItem holder : template.getItems())
		{
			final ItemInstance item = phantom.getInventory().addItem(holder.id(), holder.count());
			if (holder.id() == 5588)
				phantom.getShortcutList().addShortcut(new Shortcut(11, 0, ShortcutType.ITEM, item.getObjectId(), -1, 1));
			
			if (item.isEquipable() && holder.isEquipped())
				phantom.getInventory().equipItemAndRecord(item);
		}
		
		for (GeneralSkillNode skill : phantom.getAvailableAutoGetSkills())
		{
			if (skill.getId() == 1001 || skill.getId() == 1177)
				phantom.getShortcutList().addShortcut(new Shortcut(1, 0, ShortcutType.SKILL, skill.getId(), 1, 1), false);
			
			if (skill.getId() == 1216)
				phantom.getShortcutList().addShortcut(new Shortcut(9, 0, ShortcutType.SKILL, skill.getId(), 1, 1), false);
		}
		
		// Assign random faction if faction system is enabled
		if (Config.ENABLE_FACTION_SYSTEM)
		{
			final int[] factionIds = FactionData.getInstance().getFactionIds();
			if (factionIds.length > 0)
			{
				final int randomFactionId = factionIds[Rnd.get(factionIds.length)];
				phantom.setFactionId(randomFactionId);
				FactionData.getInstance().storeData(phantom);
				LOGGER.info("Assigned random faction {} to new phantom {} ({}).", randomFactionId, phantom.getName(), phantom.getObjectId());
			}
		}
		
		final Quest tutorial = ScriptData.getInstance().getQuest("Tutorial");
		if (tutorial != null)
			tutorial.newQuestState(phantom).setState(QuestStatus.STARTED);
		
		phantom.setOnlineStatus(true, false);
		phantom.deleteMe();
		PhantomConfig.addPhantomId(phantom.getObjectId());
		LOGGER.info("Created phantom {} ({}) class {} sex {}.", phantom.getName(), phantom.getObjectId(), classId, sex);
		return phantom;
	}
	
	private static String buildUniqueName(Sex sex)
	{
		final String[] firstNames = (sex == Sex.MALE) ? MALE_NAMES : FEMALE_NAMES;
		for (int i = 0; i < 100; i++)
		{
			final int roll = Rnd.get(100);
			String name;
			
			if (roll < 30) // 30%: single-word fantasy name
				name = FANTASY_NAMES[Rnd.get(FANTASY_NAMES.length)];
			else if (roll < 45) // 15%: first name only
				name = firstNames[Rnd.get(firstNames.length)];
			else // 55%: compound FirstName+LastPart
				name = firstNames[Rnd.get(firstNames.length)] + LAST_PARTS[Rnd.get(LAST_PARTS.length)];
			
			if (name.length() > 16)
				name = name.substring(0, 16);
			
			if (PlayerInfoTable.getInstance().getPlayerObjectId(name) <= 0)
				return name;
		}
		// Fallback: add numbers if everything is taken
		for (int i = 0; i < 100; i++)
		{
			final String name = firstNames[Rnd.get(firstNames.length)] + (Rnd.get(10, 999));
			if (PlayerInfoTable.getInstance().getPlayerObjectId(name) <= 0)
				return name;
		}
		return "Phantom" + (System.currentTimeMillis() % 100000000L);
	}
}
