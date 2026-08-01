-- Guild Mission System progress table
CREATE TABLE IF NOT EXISTS `clan_mission_progress` (
  `clanId` INT NOT NULL,
  `missionId` INT NOT NULL,
  `progress` TEXT,
  `completed` TINYINT(1) DEFAULT 0,
  `lastCompleted` BIGINT DEFAULT 0,
  `repeatCount` INT DEFAULT 0,
  PRIMARY KEY (`clanId`, `missionId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
