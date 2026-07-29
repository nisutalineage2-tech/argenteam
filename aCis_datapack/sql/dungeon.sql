-- Dungeon cooldown tracking table (per character)
CREATE TABLE IF NOT EXISTS `dungeon` (
  `dungid` int(11) NOT NULL DEFAULT '0',
  `charId` int(11) NOT NULL DEFAULT '0',
  `lastjoin` bigint(20) NOT NULL DEFAULT '0',
  PRIMARY KEY (`dungid`,`charId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
