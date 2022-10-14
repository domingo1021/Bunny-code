-- MySQL dump 10.13  Distrib 8.0.29, for macos12.4 (arm64)
--
-- Host: localhost    Database: test
-- ------------------------------------------------------
-- Server version	8.0.29

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `answer`
--

DROP TABLE IF EXISTS `answer`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `answer` (
  `answer_id` int unsigned NOT NULL AUTO_INCREMENT,
  `answer_number` int NOT NULL,
  `test_case` varchar(100) NOT NULL,
  `output` varchar(50) NOT NULL,
  `question_id` int unsigned NOT NULL,
  PRIMARY KEY (`answer_id`),
  KEY `question_id` (`question_id`),
  CONSTRAINT `answer_ibfk_1` FOREIGN KEY (`question_id`) REFERENCES `question` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `battle`
--

DROP TABLE IF EXISTS `battle`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `battle` (
  `battle_id` int unsigned NOT NULL AUTO_INCREMENT,
  `battle_name` varchar(30) NOT NULL,
  `watch_count` int unsigned NOT NULL DEFAULT '0',
  `first_user_id` int unsigned NOT NULL,
  `second_user_id` int unsigned NOT NULL,
  `is_public` tinyint(1) NOT NULL DEFAULT '1',
  `winner_id` int DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `is_finish` tinyint(1) NOT NULL DEFAULT '0',
  `question_id` int unsigned NOT NULL,
  `winner_url` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`battle_id`),
  UNIQUE KEY `battle_name` (`battle_name`),
  KEY `first_user_id` (`first_user_id`),
  KEY `second_user_id` (`second_user_id`),
  KEY `question_id` (`question_id`),
  CONSTRAINT `battle_ibfk_1` FOREIGN KEY (`first_user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `battle_ibfk_2` FOREIGN KEY (`second_user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `battle_ibfk_3` FOREIGN KEY (`question_id`) REFERENCES `question` (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=210 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `file`
--

DROP TABLE IF EXISTS `file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file` (
  `file_id` int unsigned NOT NULL AUTO_INCREMENT,
  `file_name` varchar(30) NOT NULL,
  `file_url` varchar(100) NOT NULL,
  `log` char(13) NOT NULL,
  `version_id` int unsigned NOT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `hided` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`file_id`),
  KEY `version_id` (`version_id`),
  CONSTRAINT `file_ibfk_1` FOREIGN KEY (`version_id`) REFERENCES `version` (`version_id`)
) ENGINE=InnoDB AUTO_INCREMENT=209 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `project`
--

DROP TABLE IF EXISTS `project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project` (
  `project_id` int unsigned NOT NULL AUTO_INCREMENT,
  `project_name` varchar(30) NOT NULL,
  `watch_count` int unsigned NOT NULL DEFAULT '0',
  `star_count` int unsigned NOT NULL DEFAULT '0',
  `is_public` tinyint(1) NOT NULL,
  `user_id` int unsigned NOT NULL,
  `create_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `project_description` varchar(50) NOT NULL DEFAULT '',
  PRIMARY KEY (`project_id`),
  UNIQUE KEY `project_name` (`project_name`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `project_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=67 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `question`
--

DROP TABLE IF EXISTS `question`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question` (
  `question_id` int unsigned NOT NULL AUTO_INCREMENT,
  `question_name` varchar(30) NOT NULL,
  `question_url` varchar(100) NOT NULL,
  `question_level` tinyint(1) NOT NULL,
  `base_url` varchar(100) NOT NULL,
  PRIMARY KEY (`question_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `record`
--

DROP TABLE IF EXISTS `record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `record` (
  `record_id` int unsigned NOT NULL AUTO_INCREMENT,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `version_id` int unsigned NOT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  `base_url` varchar(100) NOT NULL,
  PRIMARY KEY (`record_id`),
  KEY `version_id` (`version_id`),
  CONSTRAINT `record_ibfk_1` FOREIGN KEY (`version_id`) REFERENCES `version` (`version_id`)
) ENGINE=InnoDB AUTO_INCREMENT=105 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `relationship`
--

DROP TABLE IF EXISTS `relationship`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `relationship` (
  `relationship_id` int unsigned NOT NULL AUTO_INCREMENT,
  `master_id` int unsigned NOT NULL,
  `follower_id` int unsigned NOT NULL,
  PRIMARY KEY (`relationship_id`),
  KEY `master_id` (`master_id`),
  KEY `follower_id` (`follower_id`),
  CONSTRAINT `relationship_ibfk_1` FOREIGN KEY (`master_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `relationship_ibfk_2` FOREIGN KEY (`follower_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `user_id` int unsigned NOT NULL AUTO_INCREMENT,
  `user_name` varchar(30) NOT NULL,
  `email` varchar(255) CHARACTER SET utf8mb3 COLLATE utf8_general_ci NOT NULL,
  `password` binary(60) NOT NULL,
  `follower_count` int unsigned NOT NULL DEFAULT '0',
  `profile` varchar(100) DEFAULT NULL,
  `picture` varchar(100) NOT NULL DEFAULT '/user/user_icon.png',
  `level` int unsigned NOT NULL DEFAULT '1',
  `experience` int unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `user_name` (`user_name`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `version`
--

DROP TABLE IF EXISTS `version`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `version` (
  `version_id` int unsigned NOT NULL AUTO_INCREMENT,
  `version_name` varchar(30) NOT NULL,
  `version_number` int unsigned NOT NULL DEFAULT '0',
  `project_id` int unsigned NOT NULL,
  `editing` tinyint(1) NOT NULL DEFAULT '0',
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`version_id`),
  KEY `project_id` (`project_id`),
  CONSTRAINT `version_ibfk_1` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`)
) ENGINE=InnoDB AUTO_INCREMENT=157 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2022-10-14 14:03:32
