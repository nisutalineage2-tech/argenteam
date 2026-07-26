package net.sf.l2j.gameserver.factionwar;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.commons.data.ExProperties;
import net.sf.l2j.Config;
import net.sf.l2j.gameserver.model.location.Location;

public class FactionWarConfig
{
	private static boolean _enabled;
	private static int _goodFactionId;
	private static int _evilFactionId;
	private static int _scoreToWin;
	private static int _pointsPerFlagKill;
	private static int _pointsPerPvpKill;
	private static int _flagNpcId;
	private static long _flagRespawnDelay;
	private static int _guardNpcId;
	private static long _guardRespawnDelay;
	private static int _warRegistrarNpcId;
	private static int _checkpointNpcId;
	private static int _checkpointCount;
	private static int _checkpointRadius;
	private static int _mapRotationMinutes;
	private static final List<WarMap> _maps = new ArrayList<>();
	private static Location _goodGuardLoc;
	private static Location _evilGuardLoc;
	private static Location _goodSpawnLoc;
	private static Location _evilSpawnLoc;
	private static boolean _announceStart;
	private static boolean _announceEnd;
	private static boolean _announceFlagKill;
	private static boolean _announcePvpKill;
	private static boolean _announceScore;
	private static boolean _announceMapSwitch;
	
	public static void load()
	{
		final ExProperties props = Config.initProperties("./config/factionwar.properties");
		
		_enabled = props.getProperty("Enabled", false);
		_goodFactionId = props.getProperty("GoodFactionId", 1);
		_evilFactionId = props.getProperty("EvilFactionId", 2);
		_scoreToWin = props.getProperty("ScoreToWin", 100);
		_pointsPerFlagKill = props.getProperty("PointsPerFlagKill", 1);
		_pointsPerPvpKill = props.getProperty("PointsPerPvpKill", 1);
		_flagNpcId = props.getProperty("FlagNpcId", 90000);
		_flagRespawnDelay = props.getProperty("FlagRespawnDelay", 30) * 1000L;
		_guardNpcId = props.getProperty("GuardNpcId", 90001);
		_guardRespawnDelay = props.getProperty("GuardRespawnDelay", 60) * 1000L;
		_warRegistrarNpcId = props.getProperty("WarRegistrarNpcId", 90002);
		_checkpointNpcId = props.getProperty("CheckpointNpcId", 90003);
		_checkpointCount = props.getProperty("CheckpointCount", 3);
		_checkpointRadius = props.getProperty("CheckpointRadius", 2000);
		_mapRotationMinutes = props.getProperty("MapRotationMinutes", 30);
		_announceStart = props.getProperty("AnnounceStart", true);
		_announceEnd = props.getProperty("AnnounceEnd", true);
		_announceFlagKill = props.getProperty("AnnounceFlagKill", true);
		_announcePvpKill = props.getProperty("AnnouncePvpKill", false);
		_announceScore = props.getProperty("AnnounceScore", true);
		_announceMapSwitch = props.getProperty("AnnounceMapSwitch", true);
		
		_goodGuardLoc = parseLoc(props.getProperty("GoodGuardLoc", "-84318,244579,-792"));
		_evilGuardLoc = parseLoc(props.getProperty("EvilGuardLoc", "82218,148561,-3472"));
		_goodSpawnLoc = parseLoc(props.getProperty("GoodSpawn", "-84318,244579,-792"));
		_evilSpawnLoc = parseLoc(props.getProperty("EvilSpawn", "82218,148561,-3472"));
		
		_maps.clear();
		final String[] mapEntries = props.getProperty("Maps", "").split(";");
		for (String entry : mapEntries)
		{
			final String[] parts = entry.split(",");
			if (parts.length >= 4)
			{
				try
				{
					final String name = parts[0].trim();
					final int x = Integer.parseInt(parts[1].trim());
					final int y = Integer.parseInt(parts[2].trim());
					final int z = Integer.parseInt(parts[3].trim());
					final int radius = parts.length > 4 ? Integer.parseInt(parts[4].trim()) : 3000;
					_maps.add(new WarMap(name, x, y, z, radius));
				}
				catch (NumberFormatException e)
				{
				}
			}
		}
		
		if (_maps.isEmpty())
		{
			_maps.add(new WarMap("Gludio", -14300, 123700, -3100, 3000));
			_maps.add(new WarMap("Giran", 83400, 148000, -3400, 3000));
		}
	}
	
	private static Location parseLoc(String s)
	{
		final String[] p = s.split(",");
		return new Location(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
	}
	
	public static boolean isEnabled() { return Config.ENABLE_FACTION_SYSTEM && _enabled; }
	public static int getGoodFactionId() { return _goodFactionId; }
	public static int getEvilFactionId() { return _evilFactionId; }
	public static int getScoreToWin() { return _scoreToWin; }
	public static int getPointsPerFlagKill() { return _pointsPerFlagKill; }
	public static int getPointsPerPvpKill() { return _pointsPerPvpKill; }
	public static int getFlagNpcId() { return _flagNpcId; }
	public static long getFlagRespawnDelay() { return _flagRespawnDelay; }
	public static int getGuardNpcId() { return _guardNpcId; }
	public static long getGuardRespawnDelay() { return _guardRespawnDelay; }
	public static int getWarRegistrarNpcId() { return _warRegistrarNpcId; }
	public static int getCheckpointNpcId() { return _checkpointNpcId; }
	public static int getCheckpointCount() { return _checkpointCount; }
	public static int getCheckpointRadius() { return _checkpointRadius; }
	public static int getMapRotationMinutes() { return _mapRotationMinutes; }
	public static List<WarMap> getMaps() { return _maps; }
	public static Location getGoodGuardLoc() { return _goodGuardLoc; }
	public static Location getEvilGuardLoc() { return _evilGuardLoc; }
	public static Location getGoodSpawnLoc() { return _goodSpawnLoc; }
	public static Location getEvilSpawnLoc() { return _evilSpawnLoc; }
	public static boolean isAnnounceStart() { return _announceStart; }
	public static boolean isAnnounceEnd() { return _announceEnd; }
	public static boolean isAnnounceFlagKill() { return _announceFlagKill; }
	public static boolean isAnnouncePvpKill() { return _announcePvpKill; }
	public static boolean isAnnounceScore() { return _announceScore; }
	public static boolean isAnnounceMapSwitch() { return _announceMapSwitch; }
	
	public static class WarMap
	{
		private final String _name;
		private final int _x;
		private final int _y;
		private final int _z;
		private final int _radius;
		
		public WarMap(String name, int x, int y, int z, int radius)
		{
			_name = name;
			_x = x;
			_y = y;
			_z = z;
			_radius = radius;
		}
		
		public String getName() { return _name; }
		public int getX() { return _x; }
		public int getY() { return _y; }
		public int getZ() { return _z; }
		public int getRadius() { return _radius; }
		public Location getCenter() { return new Location(_x, _y, _z); }
	}
}
