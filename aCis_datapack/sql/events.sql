-- Event Engine tables
-- Requires: aCis_datapack/tools/database_installer.bat must reference this file

CREATE TABLE IF NOT EXISTS `event_stats` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `player` int(11) NOT NULL,
  `event` int(2) NOT NULL,
  `num` int(11) NOT NULL DEFAULT 0,
  `wins` int(11) NOT NULL DEFAULT 0,
  `losses` int(11) NOT NULL DEFAULT 0,
  `kills` int(11) NOT NULL DEFAULT 0,
  `deaths` int(11) NOT NULL DEFAULT 0,
  `scores` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `player_event` (`player`,`event`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `event_buffs` (
  `player` int(11) NOT NULL,
  `buffs` varchar(500) NOT NULL DEFAULT '',
  PRIMARY KEY (`player`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
