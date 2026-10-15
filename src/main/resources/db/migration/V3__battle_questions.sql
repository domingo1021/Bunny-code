-- Battle questions as data. The test cases come from the v1 harness scripts
-- (Docker/sandbox/{hello,twoSum,subsequence}.js); expected outputs were reconstructed from
-- the problem definitions because v1 kept them only in the production database:
--   * helloWorld(s)     returns s unchanged          (ASSUMPTION — confirm against production)
--   * twoSum(nums, t)   returns the first index pair, [] when none exists
--   * getLCS(a, b)      returns the LCS length
-- Idempotent: existing rows with the same question name are reused; their answers are replaced.

INSERT INTO `question` (`question_name`, `question_url`, `question_level`, `base_url`)
SELECT 'Hello world', '/question/hello_world/question.md', 0, '/question/hello_world/base.js'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `question` WHERE `question_name` = 'Hello world');

INSERT INTO `question` (`question_name`, `question_url`, `question_level`, `base_url`)
SELECT 'Two sum', '/question/two_sum/question.md', 1, '/question/two_sum/base.js'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `question` WHERE `question_name` = 'Two sum');

INSERT INTO `question` (`question_name`, `question_url`, `question_level`, `base_url`)
SELECT 'Longest common subsequence', '/question/lcs/question.md', 2, '/question/lcs/base.js'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `question` WHERE `question_name` = 'Longest common subsequence');

UPDATE `question` SET `entry_function` = 'helloWorld' WHERE `question_name` = 'Hello world';
UPDATE `question` SET `entry_function` = 'twoSum' WHERE `question_name` = 'Two sum';
UPDATE `question` SET `entry_function` = 'getLCS' WHERE `question_name` = 'Longest common subsequence';

ALTER TABLE `question`
  MODIFY `entry_function` varchar(50) NOT NULL;

DELETE a FROM `answer` a
  JOIN `question` q ON q.`question_id` = a.`question_id`
WHERE q.`question_name` IN ('Hello world', 'Two sum', 'Longest common subsequence');

INSERT INTO `answer` (`answer_number`, `arguments_json`, `expected_json`, `question_id`)
SELECT t.n, t.args, t.expected, q.`question_id`
FROM `question` q
  JOIN (SELECT 1 AS n, '["Hello world !"]' AS args, '"Hello world !"' AS expected
        UNION ALL SELECT 2, '["Appwork school"]', '"Appwork school"'
        UNION ALL SELECT 3, '["Bunny code"]', '"Bunny code"'
        UNION ALL SELECT 4, '["Stinky tofu"]', '"Stinky tofu"'
        UNION ALL SELECT 5, '["Happy coding !"]', '"Happy coding !"') t
WHERE q.`question_name` = 'Hello world';

INSERT INTO `answer` (`answer_number`, `arguments_json`, `expected_json`, `question_id`)
SELECT t.n, t.args, t.expected, q.`question_id`
FROM `question` q
  JOIN (SELECT 1 AS n, '[[2,7,11,15],9]' AS args, '[0,1]' AS expected
        UNION ALL SELECT 2, '[[2,7,11,15],26]', '[2,3]'
        UNION ALL SELECT 3, '[[1,1,1,1],2]', '[0,1]'
        UNION ALL SELECT 4, '[[-1],10]', '[]'
        UNION ALL SELECT 5, '[[1314,2234,1234,4243],3468]', '[1,2]') t
WHERE q.`question_name` = 'Two sum';

INSERT INTO `answer` (`answer_number`, `arguments_json`, `expected_json`, `question_id`)
SELECT t.n, t.args, t.expected, q.`question_id`
FROM `question` q
  JOIN (SELECT 1 AS n, '["abcde","ace"]' AS args, '3' AS expected
        UNION ALL SELECT 2, '["ilovetaiwan","taiwan"]', '6'
        UNION ALL SELECT 3, '["helloworld","playword"]', '5'
        UNION ALL SELECT 4, '["abcdgh","aedfhr"]', '3'
        UNION ALL SELECT 5, '["abcdef","ijklmn"]', '0') t
WHERE q.`question_name` = 'Longest common subsequence';

ALTER TABLE `answer`
  ADD UNIQUE KEY `uk_answer_question_number` (`question_id`, `answer_number`);
