-- MySQL dump 10.13  Distrib 8.0.29, for macos12.4 (arm64)
--
-- Host: localhost    Database: bunny_code
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
-- Table structure for table `battle`
--
CREATE DATABASE bunny_code;
use bunny_code

DROP TABLE IF EXISTS `battle`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `battle` (
  `battle_id` int unsigned NOT NULL AUTO_INCREMENT,
  `battle_name` varchar(30) NOT NULL,
  `watch_count` int unsigned NOT NULL DEFAULT '0',
  `star_count` int unsigned NOT NULL DEFAULT '0',
  `first_user_id` int unsigned NOT NULL,
  `second_user_id` int unsigned NOT NULL,
  `is_public` tinyint(1) NOT NULL,
  `winner_id` int DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`battle_id`),
  UNIQUE KEY `battle_name` (`battle_name`),
  KEY `first_user_id` (`first_user_id`),
  KEY `second_user_id` (`second_user_id`),
  CONSTRAINT `battle_ibfk_1` FOREIGN KEY (`first_user_id`) REFERENCES `user` (`user_id`),
  CONSTRAINT `battle_ibfk_2` FOREIGN KEY (`second_user_id`) REFERENCES `user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `battle`
--

LOCK TABLES `battle` WRITE;
/*!40000 ALTER TABLE `battle` DISABLE KEYS */;
INSERT INTO `battle` VALUES (1,'頂尖對決',0,0,10,11,1,NULL,0);
/*!40000 ALTER TABLE `battle` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `chat_room`
--

DROP TABLE IF EXISTS `chat_room`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_room` (
  `message_id` int unsigned NOT NULL AUTO_INCREMENT,
  `message_time` datetime NOT NULL,
  `stream_id` int unsigned NOT NULL,
  PRIMARY KEY (`message_id`),
  KEY `stream_id` (`stream_id`),
  CONSTRAINT `chat_room_ibfk_1` FOREIGN KEY (`stream_id`) REFERENCES `stream` (`stream_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `chat_room`
--

LOCK TABLES `chat_room` WRITE;
/*!40000 ALTER TABLE `chat_room` DISABLE KEYS */;
/*!40000 ALTER TABLE `chat_room` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `file`
--

LOCK TABLES `file` WRITE;
/*!40000 ALTER TABLE `file` DISABLE KEYS */;
INSERT INTO `file` VALUES (1,'test.js','www.google.com','1662774310972',7,0,0),(2,'test.js','www.google.com','1662774310972',11,0,0),(3,'test.js','www.google.com','1662774310972',12,0,0),(4,'test.js','www.google.com','1662774310972',13,0,0),(5,'test.js','www.google.com','1662774310972',14,0,0),(6,'test.js','www.google.com','1662774310972',15,0,1),(7,'test.js','www.google.com','1662774310972',16,0,0);
/*!40000 ALTER TABLE `file` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project`
--

LOCK TABLES `project` WRITE;
/*!40000 ALTER TABLE `project` DISABLE KEYS */;
INSERT INTO `project` VALUES (1,'bunny_code',0,0,1,1,'2022-09-04 15:12:02',0,''),(2,'Test',0,0,0,1,'2022-09-09 20:28:59',0,''),(4,'Test_2',0,0,0,1,'2022-09-09 20:30:18',0,''),(6,'hello_test',0,0,1,1,'2022-09-10 11:50:06',0,'A big bunny'),(7,'測試Project',0,0,1,1,'2022-09-10 12:00:30',0,'來測試來測試來測試來測試來測試來測試來測試');
/*!40000 ALTER TABLE `project` ENABLE KEYS */;
UNLOCK TABLES;

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
  PRIMARY KEY (`record_id`),
  KEY `version_id` (`version_id`),
  CONSTRAINT `record_ibfk_1` FOREIGN KEY (`version_id`) REFERENCES `version` (`version_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `record`
--

LOCK TABLES `record` WRITE;
/*!40000 ALTER TABLE `record` DISABLE KEYS */;
INSERT INTO `record` VALUES (1,'2022-09-06 11:29:52','2022-09-06 11:29:54',2,0);
/*!40000 ALTER TABLE `record` ENABLE KEYS */;
UNLOCK TABLES;

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
-- Dumping data for table `relationship`
--

LOCK TABLES `relationship` WRITE;
/*!40000 ALTER TABLE `relationship` DISABLE KEYS */;
/*!40000 ALTER TABLE `relationship` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stream`
--

DROP TABLE IF EXISTS `stream`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stream` (
  `stream_id` int unsigned NOT NULL AUTO_INCREMENT,
  `viewer_counts` int unsigned NOT NULL DEFAULT '0',
  `project_id` int unsigned NOT NULL,
  PRIMARY KEY (`stream_id`),
  KEY `project_id` (`project_id`),
  CONSTRAINT `stream_ibfk_1` FOREIGN KEY (`project_id`) REFERENCES `project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stream`
--

LOCK TABLES `stream` WRITE;
/*!40000 ALTER TABLE `stream` DISABLE KEYS */;
/*!40000 ALTER TABLE `stream` ENABLE KEYS */;
UNLOCK TABLES;

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
  `picture` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `user_name` (`user_name`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1,'domingo','domingo0204@gmail.com',_binary '$2b$05$0KGh1pdnInnyJnUZmfNMnO/Y2S7g5CLm0tscMMpvKy11HC/FTxIo6',0,'Test 帳號',NULL),(7,'domingo1','domingo@gmail.com',_binary '$2b$05$UUxFiQO0KFKQR4F7n5LH3.UllhPQnNAAIfSZNrtQNaJS480AXtJI6',0,NULL,NULL),(10,'hello','test123@gmail.com',_binary '$2b$05$imOoho284aAkEBsErdeEi.B7wD2nZKyYUdMDKDvmXxpuhCqKKEJCm',0,NULL,NULL),(11,'hello2','test1234@gmail.com',_binary '$2b$05$3lcFRIFpEwJeUNIBQJlZBuJuXvDZtcEDho/yoGHXcrgToPSnAl/Bq',0,NULL,NULL);
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `version`
--

LOCK TABLES `version` WRITE;
/*!40000 ALTER TABLE `version` DISABLE KEYS */;
INSERT INTO `version` VALUES (2,'first version',0,1,0,0),(3,'Test_2',1,4,0,0),(4,'Test_2',1,4,0,0),(5,'Test_2',2,4,0,0),(6,'Test_2',3,4,0,0),(7,'Test_2',4,4,0,0),(8,'versionTest',5,4,0,0),(9,'versionTest',6,4,0,0),(10,'versionTest',7,4,0,0),(11,'versionTest',8,4,0,0),(12,'version2',9,4,0,0),(13,'version2',10,4,0,0),(14,'testTransaction',11,4,0,0),(15,'testTransaction',12,4,0,0),(16,'testest',13,4,0,0);
/*!40000 ALTER TABLE `version` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2022-09-10 17:14:54
