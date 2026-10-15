-- v2 domain model. Forward-only; see docs/MIGRATION_PLAN.md (P0.3).

-- Identity: room for DelegatingPasswordEncoder's "{bcrypt}" prefix. Existing un-prefixed
-- bcrypt hashes are exactly 60 ASCII bytes, so the conversion from binary(60) is lossless.
ALTER TABLE `user`
  MODIFY `password` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

-- Workspace: the edit lock lives in Redis only (it used to be duplicated here and drifted).
ALTER TABLE `version`
  DROP COLUMN `editing`;

CREATE INDEX `idx_version_project_number` ON `version` (`project_id`, `version_number`);

-- Storage keys can be longer than the original 100/50 characters.
ALTER TABLE `file`
  MODIFY `file_url` varchar(255) NOT NULL;
ALTER TABLE `record`
  MODIFY `base_url` varchar(255) NOT NULL;

-- Project listing indexes (public feed ordered by creation, "top" ordered by watch count).
CREATE INDEX `idx_project_feed` ON `project` (`deleted`, `is_public`, `create_at`);
CREATE INDEX `idx_project_watch` ON `project` (`deleted`, `is_public`, `watch_count`);

-- Stars are now one per user (they were an unauthenticated counter).
CREATE TABLE `project_star` (
  `project_id` int unsigned NOT NULL,
  `user_id`    int unsigned NOT NULL,
  `created_at` datetime(6)  NOT NULL,
  PRIMARY KEY (`project_id`, `user_id`),
  KEY `idx_project_star_user` (`user_id`),
  CONSTRAINT `fk_project_star_project` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`),
  CONSTRAINT `fk_project_star_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Battles: an explicit lifecycle replaces the is_finish/deleted flag pair.
-- Battles that were still "in progress" belonged to v1 live state that no longer exists,
-- so they are closed as TERMINATED.
ALTER TABLE `battle`
  ADD COLUMN `status`     varchar(16) NOT NULL DEFAULT 'WAITING' AFTER `battle_name`,
  ADD COLUMN `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  MODIFY `winner_id` int unsigned DEFAULT NULL,
  MODIFY `winner_url` varchar(255) NOT NULL DEFAULT '';

UPDATE `battle`
SET `status` = CASE WHEN `deleted` = 0 AND `is_finish` = 1 THEN 'FINISHED' ELSE 'TERMINATED' END;

ALTER TABLE `battle`
  DROP COLUMN `is_finish`,
  DROP COLUMN `deleted`,
  DROP COLUMN `is_public`,
  ADD CONSTRAINT `fk_battle_winner` FOREIGN KEY (`winner_id`) REFERENCES `user` (`user_id`);

CREATE INDEX `idx_battle_status_watch` ON `battle` (`status`, `watch_count`);

-- Judging: test cases become structured JSON (arguments array + expected value) instead of
-- being hard-coded in per-question harness scripts on the sandbox image.
-- question_level holds 0-2; as tinyint(1) the JDBC driver reports it as a boolean BIT.
ALTER TABLE `question`
  ADD COLUMN `entry_function` varchar(50) NULL,
  MODIFY `question_level` tinyint unsigned NOT NULL;

ALTER TABLE `answer`
  CHANGE `test_case` `arguments_json` varchar(1000) NOT NULL,
  CHANGE `output` `expected_json` varchar(500) NOT NULL;
