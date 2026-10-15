-- Baseline: the production schema as dumped on 2022-10-16 (previously /schema.sql).
-- Existing databases are adopted with `spring.flyway.baseline-on-migrate=true`, which marks this
-- version as applied without running it. Fresh databases (local, test) run it as-is.
-- Do not edit — add a new migration instead.

CREATE TABLE `user` (
  `user_id`        int unsigned NOT NULL AUTO_INCREMENT,
  `user_name`      varchar(30)  NOT NULL,
  `email`          varchar(255) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL,
  `password`       binary(60)   NOT NULL,
  `follower_count` int unsigned NOT NULL DEFAULT '0',
  `profile`        varchar(100)          DEFAULT NULL,
  `picture`        varchar(100) NOT NULL DEFAULT '/user/user_icon.png',
  `level`          int unsigned NOT NULL DEFAULT '1',
  `experience`     int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `user_name` (`user_name`),
  UNIQUE KEY `email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `question` (
  `question_id`    int unsigned NOT NULL AUTO_INCREMENT,
  `question_name`  varchar(30)  NOT NULL,
  `question_url`   varchar(100) NOT NULL,
  `question_level` tinyint(1)   NOT NULL,
  `base_url`       varchar(100) NOT NULL,
  PRIMARY KEY (`question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `answer` (
  `answer_id`     int unsigned NOT NULL AUTO_INCREMENT,
  `answer_number` int          NOT NULL,
  `test_case`     varchar(100) NOT NULL,
  `output`        varchar(50)  NOT NULL,
  `question_id`   int unsigned NOT NULL,
  PRIMARY KEY (`answer_id`),
  KEY `question_id` (`question_id`),
  CONSTRAINT `answer_ibfk_1` FOREIGN KEY (`question_id`) REFERENCES `question` (`question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `project` (
  `project_id`          int unsigned NOT NULL AUTO_INCREMENT,
  `project_name`        varchar(30)  NOT NULL,
  `watch_count`         int unsigned NOT NULL DEFAULT '0',
  `star_count`          int unsigned NOT NULL DEFAULT '0',
  `is_public`           tinyint(1)   NOT NULL,
  `user_id`             int unsigned NOT NULL,
  `create_at`           datetime              DEFAULT CURRENT_TIMESTAMP,
  `deleted`             tinyint(1)   NOT NULL DEFAULT '0',
  `project_description` varchar(50)  NOT NULL DEFAULT '',
  PRIMARY KEY (`project_id`),
  UNIQUE KEY `project_name` (`project_name`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `project_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `version` (
  `version_id`     int unsigned NOT NULL AUTO_INCREMENT,
  `version_name`   varchar(30)  NOT NULL,
  `version_number` int unsigned NOT NULL DEFAULT '0',
  `project_id`     int unsigned NOT NULL,
  `editing`        tinyint(1)   NOT NULL DEFAULT '0',
  `deleted`        tinyint(1)   NOT NULL DEFAULT '0',
  PRIMARY KEY (`version_id`),
  KEY `project_id` (`project_id`),
  CONSTRAINT `version_ibfk_1` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `file` (
  `file_id`    int unsigned NOT NULL AUTO_INCREMENT,
  `file_name`  varchar(30)  NOT NULL,
  `file_url`   varchar(100) NOT NULL,
  `log`        char(13)     NOT NULL,
  `version_id` int unsigned NOT NULL,
  `deleted`    tinyint(1)   NOT NULL DEFAULT '0',
  `hided`      tinyint(1)   NOT NULL DEFAULT '0',
  PRIMARY KEY (`file_id`),
  KEY `version_id` (`version_id`),
  CONSTRAINT `file_ibfk_1` FOREIGN KEY (`version_id`) REFERENCES `version` (`version_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `record` (
  `record_id`  int unsigned NOT NULL AUTO_INCREMENT,
  `start_time` datetime     NOT NULL,
  `end_time`   datetime     NOT NULL,
  `version_id` int unsigned NOT NULL,
  `deleted`    tinyint(1)   NOT NULL DEFAULT '0',
  `base_url`   varchar(100) NOT NULL,
  PRIMARY KEY (`record_id`),
  KEY `version_id` (`version_id`),
  CONSTRAINT `record_ibfk_1` FOREIGN KEY (`version_id`) REFERENCES `version` (`version_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `battle` (
  `battle_id`      int unsigned NOT NULL AUTO_INCREMENT,
  `battle_name`    varchar(30)  NOT NULL,
  `watch_count`    int unsigned NOT NULL DEFAULT '0',
  `first_user_id`  int unsigned NOT NULL,
  `second_user_id` int unsigned NOT NULL,
  `is_public`      tinyint(1)   NOT NULL DEFAULT '1',
  `winner_id`      int                   DEFAULT NULL,
  `deleted`        tinyint(1)   NOT NULL DEFAULT '0',
  `is_finish`      tinyint(1)   NOT NULL DEFAULT '0',
  `question_id`    int unsigned NOT NULL,
  `winner_url`     varchar(50)  NOT NULL DEFAULT '',
  PRIMARY KEY (`battle_id`),
  UNIQUE KEY `battle_name` (`battle_name`),
  KEY `first_user_id` (`first_user_id`),
  KEY `second_user_id` (`second_user_id`),
  KEY `question_id` (`question_id`),
  CONSTRAINT `battle_ibfk_1` FOREIGN KEY (`first_user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `battle_ibfk_2` FOREIGN KEY (`second_user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `battle_ibfk_3` FOREIGN KEY (`question_id`) REFERENCES `question` (`question_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE `host` (
  `ip_address` varchar(15) NOT NULL,
  PRIMARY KEY (`ip_address`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
