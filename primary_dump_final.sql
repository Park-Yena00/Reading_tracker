-- MySQL dump 10.13  Distrib 8.0.37, for Win64 (x86_64)
--
-- Host: localhost    Database: reading_tracker
-- ------------------------------------------------------
-- Server version	8.0.37

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
-- Table structure for table `books`
--

DROP TABLE IF EXISTS `books`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `books` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `isbn` varchar(13) NOT NULL,
  `title` varchar(255) NOT NULL,
  `author` varchar(255) NOT NULL,
  `publisher` varchar(255) NOT NULL,
  `description` text,
  `cover_url` varchar(255) DEFAULT NULL,
  `total_pages` int DEFAULT NULL,
  `main_genre` varchar(50) DEFAULT NULL,
  `pub_date` date DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `isbn` (`isbn`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `books`
--

LOCK TABLES `books` WRITE;
/*!40000 ALTER TABLE `books` DISABLE KEYS */;
INSERT INTO `books` VALUES (2,'K482030732','양면의 조개껍데기','김초엽 (지은이)','래빗홀','2010년대 한국 SF의 새 역사를 썼다고 평가받는 작가 김초엽이 데뷔 8년 차를 맞는 2025년 여름 신작 소설집 《양면의 조개껍데기》로 우리를 찾아왔다. 이번 책에는 인간성의 본질에 관해 다각적으로 질문을 던지는 총 7편의 중단편소설이 담겼다.','https://image.aladin.co.kr/product/37024/77/coversum/k482030732_1.jpg',384,'국내도서>소설/시/희곡>과학소설(SF)>한국 과학소설','2025-11-07','2025-11-07 18:21:18','2025-11-07 18:21:18');
INSERT INTO `books` VALUES (3,'K082733434','지구 끝의 온실','김초엽 (지은이)','자이언트북스','김초엽 첫 장편소설. 『우리가 빛의 속도로 갈 수 없다면』을 통해 이미 폭넓은 독자층을 형성하며 열렬한 사랑을 받고 있는 김초엽 작가는 더스트로 멸망한 이후의 세계를 첫 장편소설의 무대로 삼았다.','https://image.aladin.co.kr/product/27692/63/coversum/s222930473_1.jpg',NULL,NULL,NULL,'2025-11-28 04:37:53','2025-11-28 04:37:53');
INSERT INTO `books` VALUES (4,'8932923418','꿀벌의 예언 1','베르나르 베르베르 (지은이), 전미연 (옮긴이)','열린책들','꿀벌이 사라지고 인류 멸종의 위기가 닥친 30년 뒤의 지구를 목격한 르네는 미래를 바꾸기 위해 시공간을 넘나드는 모험을 떠난다. 인류를 구할 방법이 적힌 고대의 예언서 &lt;꿀벌의 예언&gt;을 찾아 과거와 미래를 오가는 르네와 그 일행은 과연 예언서를 찾아 지구를 구할 수 있을까?','https://image.aladin.co.kr/product/31877/64/coversum/8932923418_2.jpg',368,'국내도서>소설/시/희곡>과학소설(SF)>외국 과학소설',NULL,'2025-11-28 07:01:23','2025-11-28 07:01:23');
INSERT INTO `books` VALUES (5,'K322038064','단 한 번의 삶','김영하 (지은이)','복복서가','김영하가 산문 『단 한 번의 삶』을 출간했다. 60만 명이 넘는 독자의 사랑을 받은 『여행의 이유』 이후 6년 만에 선보이는 산문집으로, 유료 이메일 구독 서비스 \'영하의 날씨\'에 2024년 연재되었던 글을 대폭 수정하고 다듬어 묶었다. \'영하의 날씨\'는 초기 구독자의 초대로만 가입이 가능한 서비스로 화제를 모으며 연재 당시 뜨거운 호응을 얻었다.','https://image.aladin.co.kr/product/36114/12/coversum/s602038067_1.jpg',NULL,NULL,NULL,'2025-11-28 08:45:38','2025-11-28 08:45:38');
INSERT INTO `books` VALUES (6,'K392630952','달러구트 꿈 백화점 - 주문하신 꿈은 매진입니다','이미예 (지은이)','팩토리나인','이미예 장편소설. 잠들어야만 입장할 수 있는 독특한 마을. 그곳에 들어온 잠든 손님들에게 가장 인기 있는 곳은, 온갖 꿈을 한데 모아 판매하는 \'달러구트의 꿈 백화점\'이다. 긴 잠을 자는 사람들은 물론이고, 짧은 낮잠을 자는 사람들과 동물들로 매일매일 대성황을 이룬다.','https://image.aladin.co.kr/product/24512/70/coversum/k392630952_2.jpg',300,'국내도서>소설/시/희곡>판타지/환상문학>한국판타지/환상소설',NULL,'2025-11-29 08:47:43','2025-11-29 08:47:43');
INSERT INTO `books` VALUES (7,'K662930932','소년이 온다 - 2024 노벨문학상 수상작가','한강 (지은이)','창비','섬세한 감수성과 치밀한 문장으로 인간 존재의 본질을 탐구해온 작가 한강의 여섯번째 장편소설. \'상처의 구조에 대한 투시와 천착의 서사\'를 통해 한강만이 풀어낼 수 있는 방식으로 1980년 5월을 새롭게 조명한다.','https://image.aladin.co.kr/product/4086/97/coversum/8936434128_2.jpg',216,'국내도서>소설/시/희곡>한국소설>2000년대 이후 한국소설',NULL,'2025-11-29 09:26:19','2025-11-29 09:26:19');
INSERT INTO `books` VALUES (8,'8936434594','채식주의자 (리마스터판) - 2024 노벨문학상 수상작가','한강 (지은이)','창비','2016년 인터내셔널 부커상을 수상하며 한국문학의 입지를 한단계 확장시킨 한강의 장편소설. 상처받은 영혼의 고통과 식물적 상상력의 강렬한 결합을 정교한 구성과 흡인력 있는 문체로 보여주며 섬뜩한 아름다움의 미학을 한강만의 방식으로 완성한 역작이다.','https://image.aladin.co.kr/product/29137/2/coversum/8936434594_2.jpg',276,'국내도서>소설/시/희곡>한국소설>2000년대 이후 한국소설',NULL,'2025-11-29 09:26:52','2025-11-29 09:26:52');
INSERT INTO `books` VALUES (9,'896371991X','서울에 수호신이 있었을 때','이수현 (지은이)','새파란상상(파란미디어)','남들은 못 보는 걸 조금 더 볼 줄 아는 강은지는 성별도 나이도 잘 모르겠고 그저 동그란 눈을 가진 현허의 상담소에서 어쩌다 알바를 뛰게 되는데... 온갖 괴물과 귀신과 수호신들이 뒤엉킨 세상. 신들도 스러지고 변하는데 무엇을 지켜야 할까?','https://image.aladin.co.kr/product/29452/75/coversum/896371991x_1.jpg',448,'국내도서>소설/시/희곡>판타지/환상문학>한국판타지/환상소설',NULL,'2025-11-29 15:41:03','2025-11-29 15:41:03');
INSERT INTO `books` VALUES (10,'K122039199','오역하는 말들 - 황석희 에세이','황석희 (지은이)','북다','대중에게 친근하게 와 닿는 재기발랄한 번역으로 잘 알려진 황석희가 이번에는 영화가 아닌 현실 세계를 번역한다. 오늘날 우리는 서로의 말을 문제없이 이해하며 소통하고 있을까. 황석희 번역가의 신간 《오역하는 말들》은 번역가의 시선에서 조금 더 예민하게 바라본 일과 일상 속 오역들에 대한 이야기다.','https://image.aladin.co.kr/product/36422/36/coversum/k122039199_1.jpg',280,'국내도서>에세이>한국에세이',NULL,'2025-11-30 10:28:13','2025-11-30 10:28:13');
INSERT INTO `books` VALUES (11,'K452031542','우리는 철학에 대해 어느 정도 알고 있다고 생각한다 - 모르진 않지만, 잘 아는 것도 아닌 것들에 대한 철학 개념 쌓기','홍준성 (지은이)','북엔드','철학 연구자이자 『카르마 폴리스』, 『지하 정원』으로 주목받은 소설가 홍준성이 첫 인문서로 돌아왔다. 일상 속 감정과 경험을 토대로 철학을 풀어내며, 독자를 어렵지 않고 흥미로운 사유의 장으로 이끈다. 적절한 인용과 해석을 더해 철학의 깊이를 유지하면서도 부담 없이 다가가는 점이 특징이다.','https://image.aladin.co.kr/product/37297/20/coversum/k452031542_1.jpg',364,'국내도서>인문학>교양 인문학',NULL,'2025-11-30 11:14:59','2025-11-30 11:14:59');
INSERT INTO `books` VALUES (12,'K442831368','미움받을 용기 (200만 부 기념 스페셜 에디션) - 자유롭고 행복한 삶을 위한 아들러의 가르침','기시미 이치로, 고가 후미타케 (지은이), 전경아 (옮긴이), 김정운 (감수)','인플루엔셜(주)','자유로워질 용기, 평범해질 용기 그리고 ‘미움받을 용기’까지, 자유롭고 행복한 삶을 위한 아들러의 가르침을 ‘철학자와 청년의 대화’ 형식으로 엮어, ‘어떻게 행복한 인생을 살 것인가?’라는 인간 본연의 질문에 쉽고 명쾌한 해답을 제시해준다.','https://image.aladin.co.kr/product/30782/55/coversum/k442831368_1.jpg',340,'국내도서>인문학>심리학/정신분석학>교양 심리학',NULL,'2025-11-30 16:48:19','2025-11-30 16:48:19');
INSERT INTO `books` VALUES (13,'8982814477','연금술사','파울로 코엘료 (지은이), 최정수 (옮긴이)','문학동네','세상을 두루두루 여행하기 위해 양치기가 된 청년 산티아고의 \'자아의 신화\' 찾기 여행담. 자칫 딱딱하게 보일 수 있는 제목과는 달리 간결하고 경쾌한 언어들로 쓰여 있어서, 물이 흘러가듯 수월하게 읽히는 작품이다.','https://image.aladin.co.kr/product/30/73/coversum/8982814477_3.jpg',278,'국내도서>소설/시/희곡>스페인/중남미소설',NULL,'2025-11-30 17:08:10','2025-11-30 17:08:10');
INSERT INTO `books` VALUES (14,'K252830652','나미야 잡화점의 기적 (무선)','히가시노 게이고 (지은이), 양윤옥 (옮긴이)','현대문학','히가시노 게이고의 가장 경이로운 대표작 『나미야 잡화점의 기적』이 국내 출간 10주년을 맞아 무선 보급판으로 발간된다. 초판 표지의 감동을 그대로 담아낸 무선판은 다소 무게감 있었던 양장판과 다르게 누구나 가볍게 들고 다니며 읽을 수 있도록 했다.','https://image.aladin.co.kr/product/30768/99/coversum/k252830652_2.jpg',456,'국내도서>소설/시/희곡>일본소설>1950년대 이후 일본소설',NULL,'2025-11-30 17:08:45','2025-11-30 17:08:45');
INSERT INTO `books` VALUES (15,'8965424879','10일 만에 끝내는 해커스 토익스피킹(토스) - 최신 기출 완벽 반영ㅣ한 권으로 토스 고득점 점수 달성ㅣ무료 온라인 실전모의고사+무료 교재 MP3','해커스어학연구소 (엮은이)','해커스어학연구소(Hackers)','각 유형마다 단계별 풀이 전략을 제시해 문제 유형에 따른 답변 노하우를 파악할 수 있다. 유형별로 익힌 전략을 문제에 적용해 풀어보며 어떤 문제가 나와도 당황하지 않고 전략을 활용할 수 있다.','https://image.aladin.co.kr/product/29533/96/coversum/s965424879_1.jpg',388,'국내도서>외국어>토익>토익 Speaking',NULL,'2025-12-01 19:37:08','2025-12-01 19:37:08');
/*!40000 ALTER TABLE `books` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','Create users table','SQL','V1__Create_users_table.sql',-1511502943,'root','2025-10-12 10:23:46',111,1);
INSERT INTO `flyway_schema_history` VALUES (2,'2','Create user devices table','SQL','V2__Create_user_devices_table.sql',-2142739131,'root','2025-10-12 10:23:46',64,1);
INSERT INTO `flyway_schema_history` VALUES (3,'3','Create refresh tokens table','SQL','V3__Create_refresh_tokens_table.sql',-983148971,'root','2025-10-12 10:23:46',89,1);
INSERT INTO `flyway_schema_history` VALUES (4,'4','Alter refresh tokens token size','SQL','V4__Alter_refresh_tokens_token_size.sql',1478452058,'root','2025-10-12 12:51:10',111,1);
INSERT INTO `flyway_schema_history` VALUES (5,'5','Create password reset tokens table','SQL','V5__Create_password_reset_tokens_table.sql',79151805,'root','2025-10-12 13:52:47',137,1);
INSERT INTO `flyway_schema_history` VALUES (6,'6','Create books table','SQL','V6__Create_books_table.sql',877842025,'root','2025-10-15 10:49:02',124,1);
INSERT INTO `flyway_schema_history` VALUES (7,'7','Create user books table','SQL','V7__Create_user_books_table.sql',331693807,'root','2025-10-26 17:59:54',94,1);
INSERT INTO `flyway_schema_history` VALUES (8,'8','Alter user books table','SQL','V8__Alter_user_books_table.sql',-955304106,'root','2025-10-26 17:59:54',109,1);
INSERT INTO `flyway_schema_history` VALUES (9,'9','Rename tables','SQL','V9__Rename_tables.sql',1315907454,'root','2025-11-04 13:22:48',47,1);
INSERT INTO `flyway_schema_history` VALUES (10,'10','Add expectation and purchase type to user books','SQL','V10__Add_expectation_and_purchase_type_to_user_books.sql',-1557670763,'root','2025-11-05 06:25:24',210,1);
INSERT INTO `flyway_schema_history` VALUES (11,'11','Add category manually set to user books','SQL','V11__Add_category_manually_set_to_user_books.sql',530515730,'root','2025-11-07 14:09:24',75,1);
INSERT INTO `flyway_schema_history` VALUES (12,'12','Drop memo column from user books','SQL','V12__Drop_memo_column_from_user_books.sql',-1683695631,'root','2025-11-13 16:38:46',44,1);
INSERT INTO `flyway_schema_history` VALUES (13,'13','Create memo table','SQL','V13__Create_memo_table.sql',1554234572,'root','2025-11-21 16:20:21',206,1);
INSERT INTO `flyway_schema_history` VALUES (14,'14','Create tags table','SQL','V14__Create_tags_table.sql',-1019623215,'root','2025-11-21 16:20:22',59,1);
INSERT INTO `flyway_schema_history` VALUES (15,'15','Create memo tags table','SQL','V15__Create_memo_tags_table.sql',-992727130,'root','2025-11-21 16:20:22',68,1);
INSERT INTO `flyway_schema_history` VALUES (16,'16','Insert tags seed data','SQL','V16__Insert_tags_seed_data.sql',-1364494780,'root','2025-11-21 16:20:22',11,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `memo`
--

DROP TABLE IF EXISTS `memo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `memo` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `book_id` bigint NOT NULL,
  `page_number` int NOT NULL,
  `content` text NOT NULL,
  `memo_start_time` timestamp NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_memo_user_book` (`user_id`,`book_id`),
  KEY `idx_memo_created_at` (`created_at`),
  KEY `idx_memo_page_number` (`book_id`,`page_number`),
  KEY `idx_memo_memo_start_time` (`memo_start_time`),
  CONSTRAINT `memo_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `memo_ibfk_2` FOREIGN KEY (`book_id`) REFERENCES `user_books` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `memo`
--

LOCK TABLES `memo` WRITE;
/*!40000 ALTER TABLE `memo` DISABLE KEYS */;
INSERT INTO `memo` VALUES (21,4,17,2,'test1','2025-11-30 18:11:39','2025-12-01 03:11:40','2025-12-01 03:11:40');
INSERT INTO `memo` VALUES (22,4,17,2,'test2','2025-11-30 18:11:47','2025-12-01 03:11:48','2025-12-01 03:11:48');
INSERT INTO `memo` VALUES (23,4,17,3,'test3','2025-11-30 18:11:57','2025-12-01 03:11:57','2025-12-01 03:11:57');
INSERT INTO `memo` VALUES (24,4,17,4,'test4','2025-11-30 18:12:04','2025-12-01 03:12:04','2025-12-01 03:19:53');
INSERT INTO `memo` VALUES (25,4,17,7,'test6','2025-11-30 18:12:11','2025-12-01 03:12:11','2025-12-01 03:12:11');
INSERT INTO `memo` VALUES (26,4,17,9,'test8.8','2025-11-30 18:12:29','2025-12-01 03:12:29','2025-12-01 03:23:09');
INSERT INTO `memo` VALUES (27,4,15,3,'test1-1','2025-11-30 18:28:21','2025-12-01 03:28:22','2025-12-01 03:28:22');
INSERT INTO `memo` VALUES (28,4,16,5,'test201','2025-12-01 03:36:28','2025-12-01 03:36:28','2025-12-01 03:36:28');
INSERT INTO `memo` VALUES (29,4,18,15,'test10','2025-12-01 10:37:38','2025-12-01 10:37:38','2025-12-01 10:37:38');
/*!40000 ALTER TABLE `memo` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `memo_tags`
--

DROP TABLE IF EXISTS `memo_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `memo_tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `memo_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_memo_tag` (`memo_id`,`tag_id`),
  KEY `idx_memo_tags_memo` (`memo_id`),
  KEY `idx_memo_tags_tag` (`tag_id`),
  CONSTRAINT `memo_tags_ibfk_1` FOREIGN KEY (`memo_id`) REFERENCES `memo` (`id`) ON DELETE CASCADE,
  CONSTRAINT `memo_tags_ibfk_2` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=63 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `memo_tags`
--

LOCK TABLES `memo_tags` WRITE;
/*!40000 ALTER TABLE `memo_tags` DISABLE KEYS */;
INSERT INTO `memo_tags` VALUES (37,21,1,'2025-12-01 03:11:39');
INSERT INTO `memo_tags` VALUES (38,21,10,'2025-12-01 03:11:39');
INSERT INTO `memo_tags` VALUES (39,22,1,'2025-12-01 03:11:48');
INSERT INTO `memo_tags` VALUES (40,22,16,'2025-12-01 03:11:48');
INSERT INTO `memo_tags` VALUES (41,23,6,'2025-12-01 03:11:57');
INSERT INTO `memo_tags` VALUES (42,23,9,'2025-12-01 03:11:57');
INSERT INTO `memo_tags` VALUES (45,25,3,'2025-12-01 03:12:11');
INSERT INTO `memo_tags` VALUES (51,24,6,'2025-12-01 03:19:52');
INSERT INTO `memo_tags` VALUES (52,24,11,'2025-12-01 03:19:52');
INSERT INTO `memo_tags` VALUES (53,26,1,'2025-12-01 03:23:09');
INSERT INTO `memo_tags` VALUES (54,26,4,'2025-12-01 03:23:09');
INSERT INTO `memo_tags` VALUES (55,26,15,'2025-12-01 03:23:09');
INSERT INTO `memo_tags` VALUES (56,27,2,'2025-12-01 03:28:21');
INSERT INTO `memo_tags` VALUES (57,27,16,'2025-12-01 03:28:21');
INSERT INTO `memo_tags` VALUES (58,27,12,'2025-12-01 03:28:21');
INSERT INTO `memo_tags` VALUES (59,28,1,'2025-12-01 03:36:28');
INSERT INTO `memo_tags` VALUES (60,28,15,'2025-12-01 03:36:28');
INSERT INTO `memo_tags` VALUES (61,29,1,'2025-12-01 10:37:38');
INSERT INTO `memo_tags` VALUES (62,29,16,'2025-12-01 10:37:38');
/*!40000 ALTER TABLE `memo_tags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `password_reset_tokens`
--

DROP TABLE IF EXISTS `password_reset_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_reset_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `token` varchar(100) NOT NULL,
  `expires_at` timestamp NOT NULL,
  `used` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`),
  KEY `idx_password_reset_tokens_token` (`token`),
  KEY `idx_password_reset_tokens_user_id` (`user_id`),
  KEY `idx_password_reset_tokens_expires_at` (`expires_at`),
  CONSTRAINT `password_reset_tokens_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `password_reset_tokens`
--

LOCK TABLES `password_reset_tokens` WRITE;
/*!40000 ALTER TABLE `password_reset_tokens` DISABLE KEYS */;
INSERT INTO `password_reset_tokens` VALUES (1,2,'02b2e568-cd42-491f-acd4-f9866b173e23','2025-10-12 04:58:37',1,'2025-10-12 04:53:37');
/*!40000 ALTER TABLE `password_reset_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `refresh_tokens`
--

DROP TABLE IF EXISTS `refresh_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `device_id` varchar(255) NOT NULL,
  `token` text NOT NULL,
  `expires_at` timestamp NOT NULL,
  `revoked` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_refresh_tokens_user_device` (`user_id`,`device_id`),
  KEY `idx_refresh_tokens_revoked_expires` (`revoked`,`expires_at`),
  CONSTRAINT `refresh_tokens_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=80 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `refresh_tokens`
--

LOCK TABLES `refresh_tokens` WRITE;
/*!40000 ALTER TABLE `refresh_tokens` DISABLE KEYS */;
INSERT INTO `refresh_tokens` VALUES (1,2,'50326bfa-caa3-4faf-9dcb-21e7350fce19','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI1MDMyNmJmYS1jYWEzLTRmYWYtOWRjYi0yMWU3MzUwZmNlMTkiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MDI3MzUwMSwiZXhwIjoxNzYwODc4MzAxfQ.9q89ttHSHLaQ4RUG3cGgr6c6i5LShL7Cs2gK2V8WhiwEfuIOThJrX9VJERwJavCdPuT41ntso6U_x1kyDkK2WA','2025-10-19 03:51:41',0,'2025-10-12 03:51:41');
INSERT INTO `refresh_tokens` VALUES (2,2,'f901ead0-5bfc-4495-a8da-3208ba474d90','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJmOTAxZWFkMC01YmZjLTQ0OTUtYThkYS0zMjA4YmE0NzRkOTAiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MDI3NDczMiwiZXhwIjoxNzYwODc5NTMyfQ.I3RQsv7X0BfRDnuMSfy_T6hJ98sEkvZlYJ8TzTmTxRkTJK9rfyMw-FflrQiTr2MiUOvzZdjTGuH4pH7m42JIkg','2025-10-19 04:12:13',0,'2025-10-12 04:12:13');
INSERT INTO `refresh_tokens` VALUES (3,2,'7d5ea5f7-02e0-4325-9b93-fa30f9eb579e','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI3ZDVlYTVmNy0wMmUwLTQzMjUtOWI5My1mYTMwZjllYjU3OWUiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MDI3NzM2NywiZXhwIjoxNzYwODgyMTY3fQ.l9Y7qtqKEEdDSh3u4o1HYNJuyfa5A6dOWQHPj0U96za_IzIFBWO2xfoyFW-fGXptFIWiPQXuMBvSlE9jCAnwYA','2025-10-19 04:56:07',0,'2025-10-12 04:56:07');
INSERT INTO `refresh_tokens` VALUES (4,2,'686429c0-de0f-4b42-a53f-b7652d149138','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI2ODY0MjljMC1kZTBmLTRiNDItYTUzZi1iNzY1MmQxNDkxMzgiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MDI4MDkyNCwiZXhwIjoxNzYwODg1NzI0fQ.8xr93qUpUvZB2R62r96j9-Cr0RSB-X4Wph_IlK19GAT85yTWslqSAhRgT7ZprCzGMNi79f-ngzCEhETxDPdXbA','2025-10-19 05:55:24',0,'2025-10-12 05:55:24');
INSERT INTO `refresh_tokens` VALUES (5,2,'8c8138bc-e9fc-4e7f-a21b-8da0d7bf0dd8','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI4YzgxMzhiYy1lOWZjLTRlN2YtYTIxYi04ZGEwZDdiZjBkZDgiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MDI4MTUzNywiZXhwIjoxNzYwODg2MzM3fQ.W8rtInq-lAiG9khDY2VVBSGfN4rzrLBu1-JxjQtConbsXWAD0nScPd0D4wql_dbcFc-1Fv1Db6rmJRj6EYkZdw','2025-10-19 06:05:37',1,'2025-10-12 06:05:37');
INSERT INTO `refresh_tokens` VALUES (6,2,'8c8138bc-e9fc-4e7f-a21b-8da0d7bf0dd8','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI4YzgxMzhiYy1lOWZjLTRlN2YtYTIxYi04ZGEwZDdiZjBkZDgiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MDI4MTU2OSwiZXhwIjoxNzYwODg2MzY5fQ.htW7bw3xPacl3wFI2O5UY411XXNyU0iRQdc2QXWfs86rZjYp8ZbVE1D-Sw_nJZ3BULq7ZWDTn7JgQ6IWQtRelg','2025-10-19 06:06:09',0,'2025-10-12 06:06:09');
INSERT INTO `refresh_tokens` VALUES (7,2,'f80dfc4f-825c-4c01-9c4e-e3167d0be8a2','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJmODBkZmM0Zi04MjVjLTRjMDEtOWM0ZS1lMzE2N2QwYmU4YTIiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MDI4MTg1MywiZXhwIjoxNzYwODg2NjUzfQ.sLOR8CxGFXJNEZLjzYHYRjhLIyugmDGUbwmLFdpiBsPNz4aetDLPS56f90oirBLZmZn9VBUyAuDwCo2D1GV_Sw','2025-10-19 06:10:53',1,'2025-10-12 06:10:53');
INSERT INTO `refresh_tokens` VALUES (8,2,'f80dfc4f-825c-4c01-9c4e-e3167d0be8a2','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJmODBkZmM0Zi04MjVjLTRjMDEtOWM0ZS1lMzE2N2QwYmU4YTIiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MDI4MTg2OSwiZXhwIjoxNzYwODg2NjY5fQ.ryrozZeSVz89bfV43eIMia_l2vnkn0iC1hQCuxXIbjntMkG80CvpLh9wdRhNmjJ26KyA-CQtFuphCi_JGL3trw','2025-10-19 06:11:10',0,'2025-10-12 06:11:10');
INSERT INTO `refresh_tokens` VALUES (9,2,'d2b0ded7-0062-4035-9dc5-18be1443976c','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJkMmIwZGVkNy0wMDYyLTQwMzUtOWRjNS0xOGJlMTQ0Mzk3NmMiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MDI4MTkxMCwiZXhwIjoxNzYwODg2NzEwfQ.muXjp2DQ1uVf4_4hISx7myf_kAj7fnSmV1q-Li6NXpGFQnhMUhijpHRmUO-xbvA2KTGEcKD51ZUuv-74FVkUVQ','2025-10-19 06:11:51',0,'2025-10-12 06:11:51');
INSERT INTO `refresh_tokens` VALUES (10,2,'288cd620-f19a-4095-a0c8-68d1200f61fb','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiIyODhjZDYyMC1mMTlhLTQwOTUtYTBjOC02OGQxMjAwZjYxZmIiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MjMxNTUxOCwiZXhwIjoxNzYyOTIwMzE4fQ.0-WxrRVxGeFN3Yj1nbyvavEQ_EXdFwhq9Uo9Br2MX8vynyPR4MzUfXZ3w5VZvJHmZiXs7ABjRVa4VcN9rgo2dQ','2025-11-11 19:05:19',0,'2025-11-04 19:05:19');
INSERT INTO `refresh_tokens` VALUES (11,2,'418454c2-9538-4db0-8cdc-e738ec739b80','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI0MTg0NTRjMi05NTM4LTRkYjAtOGNkYy1lNzM4ZWM3MzliODAiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MjMyNDAyNywiZXhwIjoxNzYyOTI4ODI3fQ.YFu_W6TwMwWXSA287IDn5M8qaWA2DxjYAhshmzo-CcoroRKBDkF0B2vtZWWAaXCfnXWnanI9-Os9BxoR102mBQ','2025-11-11 21:27:08',0,'2025-11-04 21:27:08');
INSERT INTO `refresh_tokens` VALUES (12,2,'18d4da1e-9754-4fff-b87c-092bc5d86875','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiIxOGQ0ZGExZS05NzU0LTRmZmYtYjg3Yy0wOTJiYzVkODY4NzUiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MjMyNDYzMCwiZXhwIjoxNzYyOTI5NDMwfQ.DLXz4H70AMObLPqxlB-5mWnaB_2LAVzUn914lg3XzMiOJncR_qLhWo5AvXT9rLHiF_r9Qag809-1cMR2AQTd0g','2025-11-11 21:37:11',0,'2025-11-04 21:37:11');
INSERT INTO `refresh_tokens` VALUES (13,2,'1ae959dd-d049-4702-afea-b2b5e23606a8','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiIxYWU5NTlkZC1kMDQ5LTQ3MDItYWZlYS1iMmI1ZTIzNjA2YTgiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MjMyNDk1MiwiZXhwIjoxNzYyOTI5NzUyfQ.sVBW8y-VWMi3e-EnJH7lCze9GWcOpUvqMI5GLobxxJrAsLzXpDp6-MduoxTIi0E6e6Krjpv2ngJOqjy-zCQJ5Q','2025-11-11 21:42:32',0,'2025-11-04 21:42:32');
INSERT INTO `refresh_tokens` VALUES (14,2,'faae619e-5a37-49e2-92ac-73adc20d6126','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJmYWFlNjE5ZS01YTM3LTQ5ZTItOTJhYy03M2FkYzIwZDYxMjYiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MjQxODM3NiwiZXhwIjoxNzYzMDIzMTc2fQ.X4OwIXKtx_ZyLoqcqH0yaEW5Dj9jNYSCUMEVH7hV7htiP8-nDlCLba_HXYYrUiC8qyHROMQrpf3Nbnr3PhAOkA','2025-11-12 23:39:37',0,'2025-11-05 23:39:37');
INSERT INTO `refresh_tokens` VALUES (15,2,'7f76694a-3cb1-4176-bb73-f8ef0cf27d38','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI3Zjc2Njk0YS0zY2IxLTQxNzYtYmI3My1mOGVmMGNmMjdkMzgiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MjUxMTA0MiwiZXhwIjoxNzYzMTE1ODQyfQ.Yy3JukhCVmEirvHa3LVqIZ9feqMwtR9T4NVsCV_dHlozdr8GMZVz1jgqsYeBX1mm3tlibySBttxb53lpORKmaA','2025-11-14 01:24:02',0,'2025-11-07 01:24:02');
INSERT INTO `refresh_tokens` VALUES (16,2,'51bfdb8d-4a29-4f78-b66a-d8e991b7340b','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI1MWJmZGI4ZC00YTI5LTRmNzgtYjY2YS1kOGU5OTFiNzM0MGIiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MjUxNjg5NCwiZXhwIjoxNzYzMTIxNjk0fQ.Oj3w5umxlwYTkHBmvEORmTvu7XUXxj8Vc5T_2bZiCcmuSSFJbqc8kjfmnt0HmxvOvz1GGHz96X-j5Vu_KknDqA','2025-11-14 03:01:35',0,'2025-11-07 03:01:35');
INSERT INTO `refresh_tokens` VALUES (17,2,'22b98b0b-5f84-46bd-b627-503d32081e17','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiIyMmI5OGIwYi01Zjg0LTQ2YmQtYjYyNy01MDNkMzIwODFlMTciLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MjUyNDY0NiwiZXhwIjoxNzYzMTI5NDQ2fQ.o2Q02nqKnIkP-oBnA5tIPrvH0OuXfKhVlzL9HglXrosLSMLQe-XFI2tVg_62VzE8EwHHbeDT7ZXXlpECw-JsoA','2025-11-14 05:10:47',0,'2025-11-07 05:10:47');
INSERT INTO `refresh_tokens` VALUES (18,2,'56133f4d-d07e-4f53-9519-bb9fa3434d42','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI1NjEzM2Y0ZC1kMDdlLTRmNTMtOTUxOS1iYjlmYTM0MzRkNDIiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MjUyNDY3OCwiZXhwIjoxNzYzMTI5NDc4fQ.Amzi-tUD_cG0YQG6xREIf6hUP4FatTHyp-kwJl2QP_s1FI0wqap5Zpk_SHq2REPLCg6YAK0osBk3RuUXMm6wfA','2025-11-14 05:11:19',0,'2025-11-07 05:11:19');
INSERT INTO `refresh_tokens` VALUES (19,2,'54b4fb70-7c0f-486e-a383-92a5fa5319f1','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI1NGI0ZmI3MC03YzBmLTQ4NmUtYTM4My05MmE1ZmE1MzE5ZjEiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MjUyNzkyMSwiZXhwIjoxNzYzMTMyNzIxfQ.6PJx6yWMEdT4vrHbBrqJdEjLh7ji2s3yn8wgNfvrHZUIr9WPE-y0DWxl8xLMmx4RVAgWcYtWkQLdON8BdUn5lQ','2025-11-14 06:05:21',0,'2025-11-07 06:05:21');
INSERT INTO `refresh_tokens` VALUES (20,2,'c4dd66e1-ae12-471f-bc59-1d8e1e384f04','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJjNGRkNjZlMS1hZTEyLTQ3MWYtYmM1OS0xZDhlMWUzODRmMDQiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2MjUzOTU5NSwiZXhwIjoxNzYzMTQ0Mzk1fQ.obrYd-PdLtMWrKFyavS7EKO7eJJDT-NdctVAZkWVo4CM1zprf84xHCTZtWMFNpDRdFOMo6gkVyT13TXz0fx2Tg','2025-11-14 09:19:56',0,'2025-11-07 09:19:56');
INSERT INTO `refresh_tokens` VALUES (21,2,'d366f717-c05e-43b0-8f86-e6dc55f3eb05','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJkMzY2ZjcxNy1jMDVlLTQzYjAtOGY4Ni1lNmRjNTVmM2ViMDUiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMwNDAyOSwiZXhwIjoxNzY0OTA4ODI5fQ.1Y-_wwAHtvCyysVAHcDGmT060TTKedDiL3AzbILlbT-cU_SXFIPVmHlwsB5nuYMk94rVi4lye0L-F0_1da4bBg','2025-12-04 19:27:10',0,'2025-11-27 19:27:10');
INSERT INTO `refresh_tokens` VALUES (22,2,'887c8af2-28a0-4a99-a0f3-ff3f001df448','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI4ODdjOGFmMi0yOGEwLTRhOTktYTBmMy1mZjNmMDAxZGY0NDgiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMwNDM1NSwiZXhwIjoxNzY0OTA5MTU1fQ.gvBQtu7xUay2muetdXH_OUK_R0WxZMSCIZmbOX7wlBawN3yZ9n9N4YhVMwC9ecfRa1G3y4YicHv6cX-K5KmeUw','2025-12-04 19:32:36',0,'2025-11-27 19:32:36');
INSERT INTO `refresh_tokens` VALUES (23,2,'8b0caa96-70f6-4617-984d-54e3f2b19209','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI4YjBjYWE5Ni03MGY2LTQ2MTctOTg0ZC01NGUzZjJiMTkyMDkiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMwNDQwMywiZXhwIjoxNzY0OTA5MjAzfQ.BptRfhCGViCvUaQlyg3EDvaW-cdp0UvddQWXX3uPxb1Vk6XLNUlKk5tfpS0fucCYiLpBgPci06dkxJfoMxDNFQ','2025-12-04 19:33:23',0,'2025-11-27 19:33:23');
INSERT INTO `refresh_tokens` VALUES (24,2,'bedcb5c6-2e60-4d4b-bc55-12bb9886bb60','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJiZWRjYjVjNi0yZTYwLTRkNGItYmM1NS0xMmJiOTg4NmJiNjAiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMwNDU4NCwiZXhwIjoxNzY0OTA5Mzg0fQ.qRERv2WyfzsHPr5S-IrfT7JiTvLu6tweGS-wLOuQ9uAvyV1jAAh63vZWyNFi-rVf4W1i_7IDc_0K_9rj5WuEcA','2025-12-04 19:36:24',0,'2025-11-27 19:36:24');
INSERT INTO `refresh_tokens` VALUES (25,2,'828cd5c4-14de-45be-bbc8-5ff8d810cfd0','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI4MjhjZDVjNC0xNGRlLTQ1YmUtYmJjOC01ZmY4ZDgxMGNmZDAiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMwNTA4NiwiZXhwIjoxNzY0OTA5ODg2fQ.xAnnd9fMpW1-dUujOS1qPmFbI2JNXq97TCAQXsF41P4vRfzMJUd1sbdAlZrbqheNnS--_DhZgdcoYKl_wXFHFg','2025-12-04 19:44:46',0,'2025-11-27 19:44:46');
INSERT INTO `refresh_tokens` VALUES (26,2,'996179a8-5151-4551-a537-206a21bb4a16','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI5OTYxNzlhOC01MTUxLTQ1NTEtYTUzNy0yMDZhMjFiYjRhMTYiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMxMjM1MCwiZXhwIjoxNzY0OTE3MTUwfQ.RliNq1M0L6rtxu55XsyMkhUw-ex1tAPfOJkQzQ4UZwu2zWS9t9cRADmE9_v2JOZLqaibWFCL5eomXWK9QySvZw','2025-12-04 21:45:51',0,'2025-11-27 21:45:51');
INSERT INTO `refresh_tokens` VALUES (27,2,'b635e971-b0a1-40da-a7b3-88785082330b','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJiNjM1ZTk3MS1iMGExLTQwZGEtYTdiMy04ODc4NTA4MjMzMGIiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMxMjM3NCwiZXhwIjoxNzY0OTE3MTc0fQ.ngbcKGWiyWDxDokLd2gge3X0dKzDAxC8tCPVju97eJ6XG-wUa0NS8eGY3tZMHgwqHI7XdQQdlt79aoH2NCQkoA','2025-12-04 21:46:14',0,'2025-11-27 21:46:14');
INSERT INTO `refresh_tokens` VALUES (28,2,'16553c2f-9931-4748-9f9b-dd2b8315615b','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiIxNjU1M2MyZi05OTMxLTQ3NDgtOWY5Yi1kZDJiODMxNTYxNWIiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMxMzI2MiwiZXhwIjoxNzY0OTE4MDYyfQ.UiahkI5lGoQ-lISvhotnR3Xk9iHT7B8NwuPSF2V_rx6oQ1XWT-oH72HBDTSt-DT87-r4hPyDHh0wWpfxYoEZcA','2025-12-04 22:01:03',0,'2025-11-27 22:01:03');
INSERT INTO `refresh_tokens` VALUES (29,2,'b68383c3-1235-4897-83be-28e14fe535fa','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJiNjgzODNjMy0xMjM1LTQ4OTctODNiZS0yOGUxNGZlNTM1ZmEiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMxNzg2MSwiZXhwIjoxNzY0OTIyNjYxfQ.RXuWiwgQGEupa9Dkmo1n2iqgrh7i39r2wsaB2j-n6d4cKp9fUhj5J5VY1AvYDsxplAJkHkx34EzsQPgUYwqbWw','2025-12-04 23:17:42',0,'2025-11-27 23:17:42');
INSERT INTO `refresh_tokens` VALUES (30,2,'d652eb7d-effe-48f6-8263-cfa41723f365','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJkNjUyZWI3ZC1lZmZlLTQ4ZjYtODI2My1jZmE0MTcyM2YzNjUiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMyMTQzMywiZXhwIjoxNzY0OTI2MjMzfQ.MyjhlRIGryi0WhE77HaKJMA_OBdquyrypNeRjQ3N78KQKsxj9Lyy88txLbS0Hzh0mOYtcYHtYN7PiiBwJT0wvg','2025-12-05 00:17:13',0,'2025-11-28 00:17:13');
INSERT INTO `refresh_tokens` VALUES (31,2,'1e6297ca-cc31-49ae-ac13-952bd573e0a3','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiIxZTYyOTdjYS1jYzMxLTQ5YWUtYWMxMy05NTJiZDU3M2UwYTMiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMyMjI0NCwiZXhwIjoxNzY0OTI3MDQ0fQ.SxG6sJido2g8sF_0UQFQmWRl_GS71uS1Y0OjV90VKYY3IKJviMyZFZF4QBrMJOXZIX1bPJkAUMnBWncGE6iBjQ','2025-12-05 00:30:45',0,'2025-11-28 00:30:45');
INSERT INTO `refresh_tokens` VALUES (32,2,'8dce6240-9aa2-4673-a2d0-9ef0a198dd19','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI4ZGNlNjI0MC05YWEyLTQ2NzMtYTJkMC05ZWYwYTE5OGRkMTkiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMyNTkyMywiZXhwIjoxNzY0OTMwNzIzfQ.0xA2_KZeJ7y7hSU8xWv70apGB6WaHdu0cp-7MWozYsvS2XPTUFjC7PCefcnWON6E6HdTot3XVD8Gk6sXH5iYgw','2025-12-05 01:32:04',0,'2025-11-28 01:32:04');
INSERT INTO `refresh_tokens` VALUES (33,2,'83355d10-c9eb-46cb-9457-7e231e743293','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI4MzM1NWQxMC1jOWViLTQ2Y2ItOTQ1Ny03ZTIzMWU3NDMyOTMiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMyNzEzOSwiZXhwIjoxNzY0OTMxOTM5fQ.vVVDyN9G4mTQCaBtTj_gvNppsyKGYSJKDfDyjgvdI7GCrT4dS_QEUF-Z1A33Jj9uLZAwoyR6UoBNSmpwq2goJA','2025-12-05 01:52:20',0,'2025-11-28 01:52:20');
INSERT INTO `refresh_tokens` VALUES (34,2,'567c4c18-9b57-4a6c-ac09-18447bb46101','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI1NjdjNGMxOC05YjU3LTRhNmMtYWMwOS0xODQ0N2JiNDYxMDEiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMyNzE0OCwiZXhwIjoxNzY0OTMxOTQ4fQ.jZgEZEsYVEZTgK5E-vOF6OaVqMVzwFRangltOqP8pAIx9CEwzp9w95r_bgEkos6JuMYlOPJhUAozHkvitBImaw','2025-12-05 01:52:29',0,'2025-11-28 01:52:29');
INSERT INTO `refresh_tokens` VALUES (35,2,'240cf38b-b491-42df-b26d-2faaa06191c7','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiIyNDBjZjM4Yi1iNDkxLTQyZGYtYjI2ZC0yZmFhYTA2MTkxYzciLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMzNTE2NCwiZXhwIjoxNzY0OTM5OTY0fQ.OO4y7LyKEf3_fiwXjlKuPqOZDj0XKNGJl2Fnt78JYm8ImY3VzZAKDJd0Lr0XK_BiDBPJXewzt4zO31bxajayWg','2025-12-05 04:06:06',0,'2025-11-28 04:06:06');
INSERT INTO `refresh_tokens` VALUES (36,2,'d8b14d42-5547-40ff-af34-64cafdbf358e','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJkOGIxNGQ0Mi01NTQ3LTQwZmYtYWYzNC02NGNhZmRiZjM1OGUiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMzNTE2OSwiZXhwIjoxNzY0OTM5OTY5fQ.jsfdX23A5uR0ypdG3Akry0EoWQLsA10DWYhMg9IFc7oeZa0DkyhEAeXd47MJUx78LFV_QddimjBs3iEq-b-Wfg','2025-12-05 04:06:10',0,'2025-11-28 04:06:10');
INSERT INTO `refresh_tokens` VALUES (37,2,'2a7827ec-bf69-4fdb-be11-4529c9731868','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiIyYTc4MjdlYy1iZjY5LTRmZGItYmUxMS00NTI5Yzk3MzE4NjgiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMzNTE4OSwiZXhwIjoxNzY0OTM5OTg5fQ.OOXrdT85_57vTIL7Cg3HrkpK9OGs_iZ2SD-0TNPGjyzf4UGetnkaCLGiJwKlbYH2-ifrt3iK8js-UtuMEIBbrA','2025-12-05 04:06:30',0,'2025-11-28 04:06:30');
INSERT INTO `refresh_tokens` VALUES (38,2,'5b5c216a-fc6a-4659-953d-384b9b768254','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI1YjVjMjE2YS1mYzZhLTQ2NTktOTUzZC0zODRiOWI3NjgyNTQiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMzNTI2MCwiZXhwIjoxNzY0OTQwMDYwfQ.NRZ_zja0lORMLJRmCqhVU9LpbyXHRVbNF90jc8Efx71j82rrC5iUTqPsUE8QxDvAB3favimwXU9OXn2EHKbeTQ','2025-12-05 04:07:40',0,'2025-11-28 04:07:40');
INSERT INTO `refresh_tokens` VALUES (39,2,'9e4be34a-bee7-48e0-a842-16a59a71a4b6','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiI5ZTRiZTM0YS1iZWU3LTQ4ZTAtYTg0Mi0xNmE1OWE3MWE0YjYiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDMzNTMwNCwiZXhwIjoxNzY0OTQwMTA0fQ.nCkYkTqTNFegRo6W9qMxi9B0w3dl-GSujFkyT_wNwk43N-PnLSwjR8qpavAQKtLQSHiuNOzCvm4mLasPe2OvMA','2025-12-05 04:08:26',0,'2025-11-28 04:08:26');
INSERT INTO `refresh_tokens` VALUES (40,2,'e3a284e0-1a51-4183-9ee6-af719096667d','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6MiwiZGV2aWNlSWQiOiJlM2EyODRlMC0xYTUxLTQxODMtOWVlNi1hZjcxOTA5NjY2N2QiLCJzdWIiOiJkcHNrMTUxNSIsImlhdCI6MTc2NDQwMjg3MywiZXhwIjoxNzY1MDA3NjczfQ.HziyQ3e0voBwnzezdZgqYn-Cv92sWbojVG1HDBrO9gnZqNsZjPTsmnLFfmwMkFFhQM3WZ3Rs2r8MwClCUIvjeQ','2025-12-05 22:54:35',0,'2025-11-28 22:54:35');
INSERT INTO `refresh_tokens` VALUES (41,4,'2bc613dc-3c2e-4616-8012-15c29f351eed','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIyYmM2MTNkYy0zYzJlLTQ2MTYtODAxMi0xNWMyOWYzNTFlZWQiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQwNDIyNSwiZXhwIjoxNzY1MDA5MDI1fQ.VQltD4I8t0jAEJoWOMsgJCS0wQXyfVXshjK5KJwF0hRCr5yHG6yjPeoom9K_jJFaVYZAoKVhQQjL3ci5angnbw','2025-12-05 23:17:06',0,'2025-11-28 23:17:06');
INSERT INTO `refresh_tokens` VALUES (42,4,'de695716-1b95-40c6-ad2b-ba11a9fe26b7','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiJkZTY5NTcxNi0xYjk1LTQwYzYtYWQyYi1iYTExYTlmZTI2YjciLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQwNTYyNCwiZXhwIjoxNzY1MDEwNDI0fQ._1E9ucKiTEpeGlreAUK-05Qaabmj71zbFoeqb8qWYRsuLwGkTenB8esyDRInjy2GCzYf4QRFLR1VjVeW2tovRA','2025-12-05 23:40:26',0,'2025-11-28 23:40:26');
INSERT INTO `refresh_tokens` VALUES (43,4,'70b54c93-3341-4cb1-85e2-fc8fe14cc011','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI3MGI1NGM5My0zMzQxLTRjYjEtODVlMi1mYzhmZTE0Y2MwMTEiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQwODEzMCwiZXhwIjoxNzY1MDEyOTMwfQ.fiXKNNOuSo1vStKz2bbegHIt7TH3Bl3mTBL3xxB4xYpgJiRt65osiBY7-AfIdlvZVOIJu38IrNi3ER71FsjnyQ','2025-12-06 00:22:27',0,'2025-11-29 00:22:27');
INSERT INTO `refresh_tokens` VALUES (44,4,'d1e50287-971e-43fe-8db4-f2c9f254bdf2','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiJkMWU1MDI4Ny05NzFlLTQzZmUtOGRiNC1mMmM5ZjI1NGJkZjIiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQwODE0OCwiZXhwIjoxNzY1MDEyOTQ4fQ.MJTjwDoE8T-EIAj4ok05DmYz56Wcn529o5hIqV6gPd28b1TloiePFuSGjGnrKtT4hmcx5mw6dN2ZQHirTDG9GA','2025-12-06 00:22:29',0,'2025-11-29 00:22:29');
INSERT INTO `refresh_tokens` VALUES (45,4,'774ed263-a837-4f71-9ec1-c13d0c8ab402','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI3NzRlZDI2My1hODM3LTRmNzEtOWVjMS1jMTNkMGM4YWI0MDIiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQxNDQ5MywiZXhwIjoxNzY1MDE5MjkzfQ.gBUN_7tvVGZ0GWxEukHNj3yysc44jnkQv1LtrP0zZtLWbFSKVJhQX5JsUhwfqrCYfAZ_sUK8GwiSx-EXYTlUYQ','2025-12-06 02:08:16',0,'2025-11-29 02:08:16');
INSERT INTO `refresh_tokens` VALUES (46,4,'13b807d3-21f9-4007-93fe-2d62af0d1b64','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIxM2I4MDdkMy0yMWY5LTQwMDctOTNmZS0yZDYyYWYwZDFiNjQiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQxNDkxMiwiZXhwIjoxNzY1MDE5NzEyfQ.NZDH-HgL7HpIh3PQX_0kgfKEUODKNpXsAW3bhgQTTudDRu6ZPEqFw0B-tW0_chMd5MkORSwI7oL_aiFKyq0XRQ','2025-12-06 02:15:14',0,'2025-11-29 02:15:14');
INSERT INTO `refresh_tokens` VALUES (47,4,'7b30d0a7-70da-4695-ba89-141ecb805b1f','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI3YjMwZDBhNy03MGRhLTQ2OTUtYmE4OS0xNDFlY2I4MDViMWYiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQxNTQxOSwiZXhwIjoxNzY1MDIwMjE5fQ.Fa2UY2KMrqEmhYrW3vWySHRgXdwHQEbgXdpxyCJMmhjyINkEgH8QKTPo4jXF-PfpaUKgnW33Lucos2E7mjCgJg','2025-12-06 02:23:40',0,'2025-11-29 02:23:40');
INSERT INTO `refresh_tokens` VALUES (48,4,'6d3a902c-840b-4087-9f4a-5edce7f77a0c','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI2ZDNhOTAyYy04NDBiLTQwODctOWY0YS01ZWRjZTdmNzdhMGMiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQxNzc1NCwiZXhwIjoxNzY1MDIyNTU0fQ.PbpbSQwTmqy34vqpwLtgcX61_oFiNEUd0hOOgPQ_XCfvmncmTWAdMaWaN-OGE3ncjBZJJ29p6mXNrp6ANDK-1w','2025-12-06 03:02:34',0,'2025-11-29 03:02:35');
INSERT INTO `refresh_tokens` VALUES (49,4,'e956eba4-b5ee-48ba-918e-4740e53abb25','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiJlOTU2ZWJhNC1iNWVlLTQ4YmEtOTE4ZS00NzQwZTUzYWJiMjUiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQxNzc3OSwiZXhwIjoxNzY1MDIyNTc5fQ.gkpEBtLTJiSOdT4AZD5bJLH9Q-TpUm94uCZ476ljmhRFtjubk-lqtmzx6HNI0wuJ5LO_RxF5rPooRyU4iy-oPw','2025-12-06 03:02:59',0,'2025-11-29 03:02:59');
INSERT INTO `refresh_tokens` VALUES (50,4,'c8167c7e-6cc5-458d-af52-98f9226fab63','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiJjODE2N2M3ZS02Y2M1LTQ1OGQtYWY1Mi05OGY5MjI2ZmFiNjMiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQxNzgxNSwiZXhwIjoxNzY1MDIyNjE1fQ.l-XfzrlkWHYMDzr1k4hS54CXU57BntT4hKmmbSe4WH8Ir56dXM-4rsnxg_9HwUSVqXNXu8SOUGqrXWj33J6cfQ','2025-12-06 03:03:35',0,'2025-11-29 03:03:35');
INSERT INTO `refresh_tokens` VALUES (51,4,'2ae1c566-8296-4950-879e-7c70cfacc609','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIyYWUxYzU2Ni04Mjk2LTQ5NTAtODc5ZS03YzcwY2ZhY2M2MDkiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQyNDAwMiwiZXhwIjoxNzY1MDI4ODAyfQ.9aPsH32NY2gq3QdMz814pjMIaO-0M9Ztlgx6Wjmz10GGUL2wsWOWG6OLG6LbI_13oTvf4kQnqIx7cEh5eQR-sw','2025-12-06 04:46:43',0,'2025-11-29 04:46:43');
INSERT INTO `refresh_tokens` VALUES (52,4,'3b3c83c7-5c4d-4923-a14e-f82d9f94087a','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIzYjNjODNjNy01YzRkLTQ5MjMtYTE0ZS1mODJkOWY5NDA4N2EiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQyNDY3MCwiZXhwIjoxNzY1MDI5NDcwfQ.NoLwQn6FLUegiZPboYAe1GT69Jj1YTNFkon_T3JxC0G194qVY52WxF3HnEOnmfj3D5nEGWc-vw-50BhNjOsBhA','2025-12-06 04:57:51',0,'2025-11-29 04:57:51');
INSERT INTO `refresh_tokens` VALUES (53,4,'5e362ec6-0d7e-4449-899b-8e7afdf5fd29','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI1ZTM2MmVjNi0wZDdlLTQ0NDktODk5Yi04ZTdhZmRmNWZkMjkiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQyNTA1NiwiZXhwIjoxNzY1MDI5ODU2fQ._VHzp0BnIqBwaDYE-x_fma11e2kNiGC7WcE-62RkEYWaKupJ_c61ERgKuumtEEuVKGT_zLt10Z1EMHJoj5C7QQ','2025-12-06 05:04:16',0,'2025-11-29 05:04:16');
INSERT INTO `refresh_tokens` VALUES (54,4,'d5840fbf-396d-4fd2-8acb-6254e126e773','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiJkNTg0MGZiZi0zOTZkLTRmZDItOGFjYi02MjU0ZTEyNmU3NzMiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQyNTk0OCwiZXhwIjoxNzY1MDMwNzQ4fQ.Ic4I38B1Rk1J1_PYbeKPw6lGXwXtVKC6FATQ9jmW79Zr1tJ13-JPTBhKux9XX5jrI1p7Jsk8ANEDq6i7drUupg','2025-12-06 05:19:08',0,'2025-11-29 05:19:09');
INSERT INTO `refresh_tokens` VALUES (55,4,'55f162cb-29a7-49a6-9a06-f9622db6aa38','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI1NWYxNjJjYi0yOWE3LTQ5YTYtOWEwNi1mOTYyMmRiNmFhMzgiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQyOTg2MywiZXhwIjoxNzY1MDM0NjYzfQ.F4U9utOujes8N9hnfnsrBd-EuJh04E7O2Vu0V5nS4JwO9SEEEYlWpc0ihnTau1_MtsRq5WP_NR0iu846jXxxHg','2025-12-06 06:24:24',0,'2025-11-29 06:24:24');
INSERT INTO `refresh_tokens` VALUES (56,4,'19e48d5d-6e61-426d-bfbd-8eccac99d94e','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIxOWU0OGQ1ZC02ZTYxLTQyNmQtYmZiZC04ZWNjYWM5OWQ5NGUiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQzMjY1OCwiZXhwIjoxNzY1MDM3NDU4fQ.pWv--hQRBu82jl8AoGZ2XaR6jugedmAKcPGykSeOWICrvToIXLmxJhZIqk9XiTVXi9-afJUR84bjqyxUhSqiQA','2025-12-06 07:10:59',0,'2025-11-29 07:10:59');
INSERT INTO `refresh_tokens` VALUES (57,4,'1d9e1d80-a8b7-453b-a0a7-cecd7bc9cda8','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIxZDllMWQ4MC1hOGI3LTQ1M2ItYTBhNy1jZWNkN2JjOWNkYTgiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQzMzk0MiwiZXhwIjoxNzY1MDM4NzQyfQ.oIeN6L4yXFuB7rPbzhyaQkIhTObZeYqcg3pen_qnXpoI8iYtDrfGegeZncbmDZzJvBNquDND9D723-BlTTna2g','2025-12-06 07:32:22',0,'2025-11-29 07:32:22');
INSERT INTO `refresh_tokens` VALUES (58,4,'072d0d7b-0b89-4387-89f2-b91447bac750','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIwNzJkMGQ3Yi0wYjg5LTQzODctODlmMi1iOTE0NDdiYWM3NTAiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQzNDM2MCwiZXhwIjoxNzY1MDM5MTYwfQ.R8HU7SEyp9vRByrVXn_qhdgBXAL3tegZRZVCogXj7Z6U6KN2PvPnTyg7XAt-ne2SDJlPU8C2ZhaL62TfBWa1-Q','2025-12-06 07:39:20',0,'2025-11-29 07:39:20');
INSERT INTO `refresh_tokens` VALUES (59,4,'a5cb6b45-0a58-47c2-aff3-84bb4e432121','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiJhNWNiNmI0NS0wYTU4LTQ3YzItYWZmMy04NGJiNGU0MzIxMjEiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQzNDg2MSwiZXhwIjoxNzY1MDM5NjYxfQ.c5_ubV2Sl119q6fWbO1KMWzswZVt_WuP1cedH4F2aI0za0U1OrGxULvu4xbhSGQZVmPWgy_FPRuBMzgNcmgw4w','2025-12-06 07:47:41',0,'2025-11-29 07:47:41');
INSERT INTO `refresh_tokens` VALUES (60,4,'85061224-48de-43e7-87bb-0e83903e296e','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI4NTA2MTIyNC00OGRlLTQzZTctODdiYi0wZTgzOTAzZTI5NmUiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQ5ODM2MiwiZXhwIjoxNzY1MTAzMTYyfQ.rIJHUtZRhl4vz6AzKg0_CbGYbalgsbJ8-1Do4pb0fR_rfdqZxKfCY4Arp3V5cKC_-PmYFpik45_6hjd2SHfEhA','2025-12-07 01:26:03',0,'2025-11-30 01:26:03');
INSERT INTO `refresh_tokens` VALUES (61,4,'5a05de3e-19b5-47cc-a8a9-05b43a568f5b','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI1YTA1ZGUzZS0xOWI1LTQ3Y2MtYThhOS0wNWI0M2E1NjhmNWIiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDQ5ODM5NiwiZXhwIjoxNzY1MTAzMTk2fQ.-UhrNiADqwoJZB4fKBiO4lJRJmypSDValFIrowqU3imZHgyQHG4_lI_W9vHtaQiUuLd8kBNwwwUcotNQ5fFVIQ','2025-12-07 01:26:36',0,'2025-11-30 01:26:36');
INSERT INTO `refresh_tokens` VALUES (62,4,'9dd59901-3052-4a0e-835d-0caeeaabd2cf','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI5ZGQ1OTkwMS0zMDUyLTRhMGUtODM1ZC0wY2FlZWFhYmQyY2YiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUwMTAwOCwiZXhwIjoxNzY1MTA1ODA4fQ.wS6jaVSuB90dOZJCxTVnoHlESXLD-9jMYjCxQJD4O6PNIQnAUZnwLk5w3GaqKdyVMSXFLVEQgBIYvmGqaaCR7A','2025-12-07 02:10:09',0,'2025-11-30 02:10:09');
INSERT INTO `refresh_tokens` VALUES (63,4,'84e5b7fb-11a2-4ffe-af12-904b1a32f5a9','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI4NGU1YjdmYi0xMWEyLTRmZmUtYWYxMi05MDRiMWEzMmY1YTkiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUwMjg4MiwiZXhwIjoxNzY1MTA3NjgyfQ.iGJ5hdDt4r8ffDZxjLdjuEBsaELjmNb6g07R0SWQLUrXTESMe7hZ4jGZaLF54vzz9in0Tl9oVQz6SjXD0SFrGQ','2025-12-07 02:41:23',0,'2025-11-30 02:41:23');
INSERT INTO `refresh_tokens` VALUES (64,4,'fa0983ae-2836-4839-b98d-4fc2a2566245','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiJmYTA5ODNhZS0yODM2LTQ4MzktYjk4ZC00ZmMyYTI1NjYyNDUiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUwNjUwMSwiZXhwIjoxNzY1MTExMzAxfQ.jIE3ElwGpPB1giieWUYJ8-6Hdyy-eVAvMsogrhdmNeknsfo5wnVMqw8ehJrYwjEmrT2fb7xWaIokdIeIA8pQnA','2025-12-07 03:41:41',0,'2025-11-30 03:41:41');
INSERT INTO `refresh_tokens` VALUES (65,4,'aa99752f-5559-43ba-8029-c27cc003a37c','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiJhYTk5NzUyZi01NTU5LTQzYmEtODAyOS1jMjdjYzAwM2EzN2MiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUwNzEzMywiZXhwIjoxNzY1MTExOTMzfQ.xtHTd94VsjTpQa6vocUZyS12fBKpOUSdf68E9jQxoJLMu59IiWKbgZNabbeGaOfSMd5qEAHlHZN73OwLD3JwYQ','2025-12-07 03:52:14',0,'2025-11-30 03:52:14');
INSERT INTO `refresh_tokens` VALUES (66,4,'051be69f-5a79-4200-9795-34870b0c6618','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIwNTFiZTY5Zi01YTc5LTQyMDAtOTc5NS0zNDg3MGIwYzY2MTgiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUxNTM1NiwiZXhwIjoxNzY1MTIwMTU2fQ.rM1txJmNnj8TnjeoIeE2WqhLTaF1FDnzkSsTmznLLE2CkTR1HBme0YcHgjpJyWOFe2DnNsrDdnrqpfH7FfrGwQ','2025-12-07 06:09:16',0,'2025-11-30 06:09:16');
INSERT INTO `refresh_tokens` VALUES (67,4,'4a0f286b-888b-4076-a98f-50a520aa8b43','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI0YTBmMjg2Yi04ODhiLTQwNzYtYTk4Zi01MGE1MjBhYThiNDMiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUxNTczMiwiZXhwIjoxNzY1MTIwNTMyfQ.LK01qZM9gVmiH4XJempuK_Pm_oE3HNtmrizprw4LI_apthu2_obUbJxfUFgUk3vTa1n73EF-I_J9BXVsLQ8xkg','2025-12-07 06:15:33',0,'2025-11-30 06:15:33');
INSERT INTO `refresh_tokens` VALUES (68,4,'9ac267e8-f722-4211-849f-15c855399c69','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI5YWMyNjdlOC1mNzIyLTQyMTEtODQ5Zi0xNWM4NTUzOTljNjkiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUxNTc0NCwiZXhwIjoxNzY1MTIwNTQ0fQ.-mHon5kqDhbCBBlskIkAYXp6hOR1HgCzjD0AJ8zhl3y6wjKbiqRx4LfIittSJWLuFsftUdCEfe18Rnc4ydTkjg','2025-12-07 06:15:45',0,'2025-11-30 06:15:45');
INSERT INTO `refresh_tokens` VALUES (69,4,'2b51f126-217f-4147-bfff-3a1e290b7320','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIyYjUxZjEyNi0yMTdmLTQxNDctYmZmZi0zYTFlMjkwYjczMjAiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUxNzA3MCwiZXhwIjoxNzY1MTIxODcwfQ.9rB7exY1eQj0y8A8k0r1JUz6k_jduJUYYfQkn_njn3d7TNR_kJwH8ANb7Xoex9QZbUG_dtwFqBwB7CHsFoD8HQ','2025-12-07 06:37:51',0,'2025-11-30 06:37:51');
INSERT INTO `refresh_tokens` VALUES (70,4,'620c2482-bad7-4331-a7dd-c1e72c5d28ef','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI2MjBjMjQ4Mi1iYWQ3LTQzMzEtYTdkZC1jMWU3MmM1ZDI4ZWYiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUyMDcxMywiZXhwIjoxNzY1MTI1NTEzfQ.EeKREdnASZTgT27imupqKhfpGZVJ-zWX-yeg_5zK8BHlUa9dkOBuI0mni98FtiIFEVeGp7rx357FHXmsLOMETg','2025-12-07 07:38:34',0,'2025-11-30 07:38:34');
INSERT INTO `refresh_tokens` VALUES (71,4,'3a0012d7-d4f3-4458-bad9-5df5552e4394','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIzYTAwMTJkNy1kNGYzLTQ0NTgtYmFkOS01ZGY1NTUyZTQzOTQiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUyNzE1NCwiZXhwIjoxNzY1MTMxOTU0fQ.RcJCM5qM4pUTqsMictR16jw8L_kV5mKYHIXENY3EgK9g92ZmGtDNtdLhXq4-I6mJzpqGAn8XmUReInB9-g_4Lw','2025-12-07 09:25:55',0,'2025-11-30 09:25:55');
INSERT INTO `refresh_tokens` VALUES (72,4,'01b3bdc2-83a3-4177-99e4-b88c862b0ac7','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIwMWIzYmRjMi04M2EzLTQxNzctOTllNC1iODhjODYyYjBhYzciLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUzMjU2MywiZXhwIjoxNzY1MTM3MzYzfQ.adyFb1lfDjqlcOtH9zr1g-TGRLFOXp1t16VUygfyk3uWNoSktz8P6oZFAR5NNgbhWG_7bh4bITmilEz7W-5oOg','2025-12-07 10:56:03',0,'2025-11-30 10:56:04');
INSERT INTO `refresh_tokens` VALUES (73,4,'0c9bfc98-057b-4597-9f94-cbda5d81c2a2','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIwYzliZmM5OC0wNTdiLTQ1OTctOWY5NC1jYmRhNWQ4MWMyYTIiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDUzMzg2NiwiZXhwIjoxNzY1MTM4NjY2fQ.5Y0H9ur8KmiLJEUmIiD7_KVrHk5ruVilJRo8g5bEwkA4vAvwobh87EV977_1Konf8wUzwhP9LCJ4_UkbutIveg','2025-12-07 11:17:47',0,'2025-11-30 11:17:47');
INSERT INTO `refresh_tokens` VALUES (74,4,'83c72d62-315d-4017-94fb-f78d45c114eb','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI4M2M3MmQ2Mi0zMTVkLTQwMTctOTRmYi1mNzhkNDVjMTE0ZWIiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDU1NjAzMywiZXhwIjoxNzY1MTYwODMzfQ.NxBKtffmthgkJ-uVIz5xXdn8gIquqHYSaOQOmWvK9G9t5a3DXBxnmE5V0nPXsTp5Wt7jkUPha_Xm0E3lbVSP3Q','2025-12-08 02:27:14',0,'2025-12-01 02:27:14');
INSERT INTO `refresh_tokens` VALUES (75,4,'44cc2522-2815-4820-bd9f-8aa5b3d7beb5','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI0NGNjMjUyMi0yODE1LTQ4MjAtYmQ5Zi04YWE1YjNkN2JlYjUiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDU1NjE0NCwiZXhwIjoxNzY1MTYwOTQ0fQ.ecl6ZFDLUbLC6lJSEu1Gw9Ac-NMNetbvC_7mbVFaYUVTbkElXaEfjgUHzUil4uJmjNO4-rpsVOMKwyDJMynmbQ','2025-12-08 02:29:04',0,'2025-12-01 02:29:04');
INSERT INTO `refresh_tokens` VALUES (76,4,'57d5caf8-3836-47f6-93ab-d658c4757497','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiI1N2Q1Y2FmOC0zODM2LTQ3ZjYtOTNhYi1kNjU4YzQ3NTc0OTciLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDU1NzgyMiwiZXhwIjoxNzY1MTYyNjIyfQ.pYdbXoIZUPrTMQQwWIqxubkBHaSr-XCbaHRZZ44oseQUb-9ozWE6o4KNe74ylZUf_kbheNNhi5PYaeT-zRIHPw','2025-12-08 02:57:03',0,'2025-12-01 02:57:03');
INSERT INTO `refresh_tokens` VALUES (77,4,'ee065874-61a6-42e6-b0ed-cec6965c83ea','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiJlZTA2NTg3NC02MWE2LTQyZTYtYjBlZC1jZWM2OTY1YzgzZWEiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDU1ODE0NiwiZXhwIjoxNzY1MTYyOTQ2fQ.QBrUVApI8jctb73Xzk6_QkAanD5RfP80msqAxEYYm1xyXNEphwcuXI0mZk9IvhQ4QpKcbpCHdNScECggG25-Cg','2025-12-08 03:02:27',0,'2025-12-01 03:02:27');
INSERT INTO `refresh_tokens` VALUES (78,4,'3f29b13b-39d5-463e-8585-1ec8e668cd3e','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiIzZjI5YjEzYi0zOWQ1LTQ2M2UtODU4NS0xZWM4ZTY2OGNkM2UiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDU4NTMzMiwiZXhwIjoxNzY1MTkwMTMyfQ.AXKedtvW0piFyGS1Iqvc_oicJ4kGyu3gSMoWNG1c0gbQKPph0NhYPieyLQ5Dru2XakoRmqZo8duLYZyH-qf7PQ','2025-12-08 10:35:32',0,'2025-12-01 10:35:32');
INSERT INTO `refresh_tokens` VALUES (79,4,'af70b15b-0710-4e8e-951a-97064c715b0c','eyJhbGciOiJIUzUxMiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInVzZXJJZCI6NCwiZGV2aWNlSWQiOiJhZjcwYjE1Yi0wNzEwLTRlOGUtOTUxYS05NzA2NGM3MTViMGMiLCJzdWIiOiJ1c2VyMSIsImlhdCI6MTc2NDY0MjgyNCwiZXhwIjoxNzY1MjQ3NjI0fQ.f5Ab93ZN63KqoT1smu3tsWXDfJifKqFraf5BQ5ruQH6ZC1SktKqpMdOoILiwH5SuqeAqQtM7eJCZ_09VPKy6Qw','2025-12-09 02:33:44',0,'2025-12-02 02:33:44');
/*!40000 ALTER TABLE `refresh_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tags`
--

DROP TABLE IF EXISTS `tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tags` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` enum('TYPE','TOPIC') NOT NULL,
  `code` varchar(50) NOT NULL,
  `sort_order` int NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `idx_tags_category` (`category`),
  KEY `idx_tags_code` (`code`),
  KEY `idx_tags_category_sort` (`category`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tags`
--

LOCK TABLES `tags` WRITE;
/*!40000 ALTER TABLE `tags` DISABLE KEYS */;
INSERT INTO `tags` VALUES (1,'TYPE','summary',1,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (2,'TYPE','quote',2,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (3,'TYPE','feeling',3,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (4,'TYPE','question',4,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (5,'TYPE','connection',5,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (6,'TYPE','critique',6,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (7,'TYPE','idea',7,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (8,'TYPE','action',8,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (9,'TOPIC','character',1,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (10,'TOPIC','plot',2,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (11,'TOPIC','knowledge',3,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (12,'TOPIC','lesson',4,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (13,'TOPIC','emotion',5,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (14,'TOPIC','society',6,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (15,'TOPIC','philosophy',7,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (16,'TOPIC','creation',8,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
INSERT INTO `tags` VALUES (17,'TYPE','etc',99,1,'2025-11-21 16:20:22','2025-11-21 16:20:22');
/*!40000 ALTER TABLE `tags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_books`
--

DROP TABLE IF EXISTS `user_books`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_books` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `book_id` bigint NOT NULL,
  `category` enum('ToRead','Reading','AlmostFinished','Finished') NOT NULL,
  `category_manually_set` tinyint(1) NOT NULL DEFAULT '0',
  `expectation` varchar(500) DEFAULT NULL,
  `reading_start_date` date DEFAULT NULL,
  `reading_progress` int DEFAULT NULL,
  `purchase_type` enum('PURCHASE','RENTAL') DEFAULT NULL,
  `reading_finished_date` date DEFAULT NULL,
  `rating` int DEFAULT NULL,
  `review` text,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_book` (`user_id`,`book_id`),
  KEY `book_id` (`book_id`),
  KEY `idx_user_books_category` (`category`),
  KEY `idx_user_books_created_at` (`created_at`),
  KEY `idx_user_books_category_manually_set` (`category_manually_set`),
  CONSTRAINT `user_books_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `user_books_ibfk_2` FOREIGN KEY (`book_id`) REFERENCES `books` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_books`
--

LOCK TABLES `user_books` WRITE;
/*!40000 ALTER TABLE `user_books` DISABLE KEYS */;
INSERT INTO `user_books` VALUES (6,2,3,'Reading',1,NULL,'2025-11-28',150,'PURCHASE',NULL,NULL,NULL,'2025-11-28 00:41:03','2025-11-28 01:12:29');
INSERT INTO `user_books` VALUES (12,4,7,'Finished',1,'슬플 것 같다','2025-11-29',216,'PURCHASE','2025-11-30',4,'오열','2025-11-29 07:56:24','2025-11-29 07:57:57');
INSERT INTO `user_books` VALUES (13,4,10,'Finished',1,NULL,'2025-11-20',280,'RENTAL','2025-11-30',5,'번역가님 책도 잘 쓰네 존잼','2025-11-30 01:28:13','2025-11-30 02:10:21');
INSERT INTO `user_books` VALUES (14,4,11,'Reading',1,'재밌어보여서 읽으려고 째려보는 중','2025-11-30',1,'RENTAL',NULL,NULL,NULL,'2025-11-30 02:14:59','2025-11-30 02:15:30');
INSERT INTO `user_books` VALUES (15,4,12,'AlmostFinished',1,NULL,'2025-11-27',300,NULL,NULL,NULL,NULL,'2025-11-30 07:48:20','2025-11-30 07:48:20');
INSERT INTO `user_books` VALUES (16,4,13,'Reading',1,NULL,'2025-11-20',10,NULL,NULL,NULL,NULL,'2025-11-30 08:08:10','2025-12-01 03:36:34');
INSERT INTO `user_books` VALUES (17,4,14,'Reading',1,'존잼',NULL,50,NULL,NULL,NULL,NULL,'2025-11-30 08:08:45','2025-12-01 03:27:47');
INSERT INTO `user_books` VALUES (18,4,15,'Reading',1,NULL,'2025-12-01',150,'RENTAL',NULL,NULL,NULL,'2025-12-01 10:37:08','2025-12-01 10:37:48');
/*!40000 ALTER TABLE `user_books` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_devices`
--

DROP TABLE IF EXISTS `user_devices`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_devices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `device_id` varchar(255) NOT NULL,
  `device_name` varchar(100) NOT NULL,
  `platform` enum('WEB','ANDROID','IOS') NOT NULL,
  `last_seen_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_device` (`user_id`,`device_id`),
  KEY `idx_user_devices_user_device` (`user_id`,`device_id`),
  CONSTRAINT `user_devices_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=79 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_devices`
--

LOCK TABLES `user_devices` WRITE;
/*!40000 ALTER TABLE `user_devices` DISABLE KEYS */;
INSERT INTO `user_devices` VALUES (2,2,'50326bfa-caa3-4faf-9dcb-21e7350fce19','Unknown Device','WEB',NULL,'2025-10-12 03:51:41');
INSERT INTO `user_devices` VALUES (3,2,'f901ead0-5bfc-4495-a8da-3208ba474d90','Unknown Device','WEB',NULL,'2025-10-12 04:12:13');
INSERT INTO `user_devices` VALUES (4,2,'7d5ea5f7-02e0-4325-9b93-fa30f9eb579e','Unknown Device','WEB',NULL,'2025-10-12 04:56:07');
INSERT INTO `user_devices` VALUES (5,2,'686429c0-de0f-4b42-a53f-b7652d149138','Unknown Device','WEB',NULL,'2025-10-12 05:55:24');
INSERT INTO `user_devices` VALUES (6,2,'8c8138bc-e9fc-4e7f-a21b-8da0d7bf0dd8','Unknown Device','WEB','2025-10-12 06:06:09','2025-10-12 06:05:37');
INSERT INTO `user_devices` VALUES (7,2,'f80dfc4f-825c-4c01-9c4e-e3167d0be8a2','Unknown Device','WEB','2025-10-12 06:11:10','2025-10-12 06:10:53');
INSERT INTO `user_devices` VALUES (8,2,'d2b0ded7-0062-4035-9dc5-18be1443976c','Unknown Device','WEB',NULL,'2025-10-12 06:11:51');
INSERT INTO `user_devices` VALUES (9,2,'288cd620-f19a-4095-a0c8-68d1200f61fb','Unknown Device','WEB',NULL,'2025-11-04 19:05:19');
INSERT INTO `user_devices` VALUES (10,2,'418454c2-9538-4db0-8cdc-e738ec739b80','Unknown Device','WEB',NULL,'2025-11-04 21:27:08');
INSERT INTO `user_devices` VALUES (11,2,'18d4da1e-9754-4fff-b87c-092bc5d86875','Unknown Device','WEB',NULL,'2025-11-04 21:37:11');
INSERT INTO `user_devices` VALUES (12,2,'1ae959dd-d049-4702-afea-b2b5e23606a8','Unknown Device','WEB',NULL,'2025-11-04 21:42:32');
INSERT INTO `user_devices` VALUES (13,2,'faae619e-5a37-49e2-92ac-73adc20d6126','Unknown Device','WEB',NULL,'2025-11-05 23:39:37');
INSERT INTO `user_devices` VALUES (14,2,'7f76694a-3cb1-4176-bb73-f8ef0cf27d38','Unknown Device','WEB',NULL,'2025-11-07 01:24:02');
INSERT INTO `user_devices` VALUES (15,2,'51bfdb8d-4a29-4f78-b66a-d8e991b7340b','Unknown Device','WEB',NULL,'2025-11-07 03:01:35');
INSERT INTO `user_devices` VALUES (16,2,'22b98b0b-5f84-46bd-b627-503d32081e17','Unknown Device','WEB',NULL,'2025-11-07 05:10:47');
INSERT INTO `user_devices` VALUES (17,2,'56133f4d-d07e-4f53-9519-bb9fa3434d42','Unknown Device','WEB',NULL,'2025-11-07 05:11:19');
INSERT INTO `user_devices` VALUES (18,2,'54b4fb70-7c0f-486e-a383-92a5fa5319f1','Unknown Device','WEB',NULL,'2025-11-07 06:05:21');
INSERT INTO `user_devices` VALUES (19,2,'c4dd66e1-ae12-471f-bc59-1d8e1e384f04','Unknown Device','WEB',NULL,'2025-11-07 09:19:55');
INSERT INTO `user_devices` VALUES (20,2,'d366f717-c05e-43b0-8f86-e6dc55f3eb05','Unknown Device','WEB',NULL,'2025-11-27 19:27:10');
INSERT INTO `user_devices` VALUES (21,2,'887c8af2-28a0-4a99-a0f3-ff3f001df448','Unknown Device','WEB',NULL,'2025-11-27 19:32:36');
INSERT INTO `user_devices` VALUES (22,2,'8b0caa96-70f6-4617-984d-54e3f2b19209','Unknown Device','WEB',NULL,'2025-11-27 19:33:23');
INSERT INTO `user_devices` VALUES (23,2,'bedcb5c6-2e60-4d4b-bc55-12bb9886bb60','Unknown Device','WEB',NULL,'2025-11-27 19:36:24');
INSERT INTO `user_devices` VALUES (24,2,'828cd5c4-14de-45be-bbc8-5ff8d810cfd0','Unknown Device','WEB',NULL,'2025-11-27 19:44:46');
INSERT INTO `user_devices` VALUES (25,2,'996179a8-5151-4551-a537-206a21bb4a16','Unknown Device','WEB',NULL,'2025-11-27 21:45:51');
INSERT INTO `user_devices` VALUES (26,2,'b635e971-b0a1-40da-a7b3-88785082330b','Unknown Device','WEB',NULL,'2025-11-27 21:46:14');
INSERT INTO `user_devices` VALUES (27,2,'16553c2f-9931-4748-9f9b-dd2b8315615b','Unknown Device','WEB',NULL,'2025-11-27 22:01:03');
INSERT INTO `user_devices` VALUES (28,2,'b68383c3-1235-4897-83be-28e14fe535fa','Unknown Device','WEB',NULL,'2025-11-27 23:17:42');
INSERT INTO `user_devices` VALUES (29,2,'d652eb7d-effe-48f6-8263-cfa41723f365','Unknown Device','WEB',NULL,'2025-11-28 00:17:13');
INSERT INTO `user_devices` VALUES (30,2,'1e6297ca-cc31-49ae-ac13-952bd573e0a3','Unknown Device','WEB',NULL,'2025-11-28 00:30:45');
INSERT INTO `user_devices` VALUES (31,2,'8dce6240-9aa2-4673-a2d0-9ef0a198dd19','Unknown Device','WEB',NULL,'2025-11-28 01:32:04');
INSERT INTO `user_devices` VALUES (32,2,'83355d10-c9eb-46cb-9457-7e231e743293','Unknown Device','WEB',NULL,'2025-11-28 01:52:20');
INSERT INTO `user_devices` VALUES (33,2,'567c4c18-9b57-4a6c-ac09-18447bb46101','Unknown Device','WEB',NULL,'2025-11-28 01:52:29');
INSERT INTO `user_devices` VALUES (34,2,'240cf38b-b491-42df-b26d-2faaa06191c7','Unknown Device','WEB',NULL,'2025-11-28 04:06:05');
INSERT INTO `user_devices` VALUES (35,2,'d8b14d42-5547-40ff-af34-64cafdbf358e','Unknown Device','WEB',NULL,'2025-11-28 04:06:10');
INSERT INTO `user_devices` VALUES (36,2,'2a7827ec-bf69-4fdb-be11-4529c9731868','Unknown Device','WEB',NULL,'2025-11-28 04:06:30');
INSERT INTO `user_devices` VALUES (37,2,'5b5c216a-fc6a-4659-953d-384b9b768254','Unknown Device','WEB',NULL,'2025-11-28 04:07:40');
INSERT INTO `user_devices` VALUES (38,2,'9e4be34a-bee7-48e0-a842-16a59a71a4b6','Unknown Device','WEB',NULL,'2025-11-28 04:08:26');
INSERT INTO `user_devices` VALUES (39,2,'e3a284e0-1a51-4183-9ee6-af719096667d','Unknown Device','WEB',NULL,'2025-11-28 22:54:35');
INSERT INTO `user_devices` VALUES (40,4,'2bc613dc-3c2e-4616-8012-15c29f351eed','Unknown Device','WEB',NULL,'2025-11-28 23:17:06');
INSERT INTO `user_devices` VALUES (41,4,'de695716-1b95-40c6-ad2b-ba11a9fe26b7','Unknown Device','WEB',NULL,'2025-11-28 23:40:25');
INSERT INTO `user_devices` VALUES (42,4,'70b54c93-3341-4cb1-85e2-fc8fe14cc011','Unknown Device','WEB',NULL,'2025-11-29 00:22:14');
INSERT INTO `user_devices` VALUES (43,4,'d1e50287-971e-43fe-8db4-f2c9f254bdf2','Unknown Device','WEB',NULL,'2025-11-29 00:22:29');
INSERT INTO `user_devices` VALUES (44,4,'774ed263-a837-4f71-9ec1-c13d0c8ab402','Unknown Device','WEB',NULL,'2025-11-29 02:08:14');
INSERT INTO `user_devices` VALUES (45,4,'13b807d3-21f9-4007-93fe-2d62af0d1b64','Unknown Device','WEB',NULL,'2025-11-29 02:15:13');
INSERT INTO `user_devices` VALUES (46,4,'7b30d0a7-70da-4695-ba89-141ecb805b1f','Unknown Device','WEB',NULL,'2025-11-29 02:23:40');
INSERT INTO `user_devices` VALUES (47,4,'6d3a902c-840b-4087-9f4a-5edce7f77a0c','Unknown Device','WEB',NULL,'2025-11-29 03:02:35');
INSERT INTO `user_devices` VALUES (48,4,'e956eba4-b5ee-48ba-918e-4740e53abb25','Unknown Device','WEB',NULL,'2025-11-29 03:02:59');
INSERT INTO `user_devices` VALUES (49,4,'c8167c7e-6cc5-458d-af52-98f9226fab63','Unknown Device','WEB',NULL,'2025-11-29 03:03:35');
INSERT INTO `user_devices` VALUES (50,4,'2ae1c566-8296-4950-879e-7c70cfacc609','Unknown Device','WEB',NULL,'2025-11-29 04:46:43');
INSERT INTO `user_devices` VALUES (51,4,'3b3c83c7-5c4d-4923-a14e-f82d9f94087a','Unknown Device','WEB',NULL,'2025-11-29 04:57:51');
INSERT INTO `user_devices` VALUES (52,4,'5e362ec6-0d7e-4449-899b-8e7afdf5fd29','Unknown Device','WEB',NULL,'2025-11-29 05:04:16');
INSERT INTO `user_devices` VALUES (53,4,'d5840fbf-396d-4fd2-8acb-6254e126e773','Unknown Device','WEB',NULL,'2025-11-29 05:19:09');
INSERT INTO `user_devices` VALUES (54,4,'55f162cb-29a7-49a6-9a06-f9622db6aa38','Unknown Device','WEB',NULL,'2025-11-29 06:24:24');
INSERT INTO `user_devices` VALUES (55,4,'19e48d5d-6e61-426d-bfbd-8eccac99d94e','Unknown Device','WEB',NULL,'2025-11-29 07:10:59');
INSERT INTO `user_devices` VALUES (56,4,'1d9e1d80-a8b7-453b-a0a7-cecd7bc9cda8','Unknown Device','WEB',NULL,'2025-11-29 07:32:22');
INSERT INTO `user_devices` VALUES (57,4,'072d0d7b-0b89-4387-89f2-b91447bac750','Unknown Device','WEB',NULL,'2025-11-29 07:39:20');
INSERT INTO `user_devices` VALUES (58,4,'a5cb6b45-0a58-47c2-aff3-84bb4e432121','Unknown Device','WEB',NULL,'2025-11-29 07:47:41');
INSERT INTO `user_devices` VALUES (59,4,'85061224-48de-43e7-87bb-0e83903e296e','Unknown Device','WEB',NULL,'2025-11-30 01:26:03');
INSERT INTO `user_devices` VALUES (60,4,'5a05de3e-19b5-47cc-a8a9-05b43a568f5b','Unknown Device','WEB',NULL,'2025-11-30 01:26:36');
INSERT INTO `user_devices` VALUES (61,4,'9dd59901-3052-4a0e-835d-0caeeaabd2cf','Unknown Device','WEB',NULL,'2025-11-30 02:10:09');
INSERT INTO `user_devices` VALUES (62,4,'84e5b7fb-11a2-4ffe-af12-904b1a32f5a9','Unknown Device','WEB',NULL,'2025-11-30 02:41:23');
INSERT INTO `user_devices` VALUES (63,4,'fa0983ae-2836-4839-b98d-4fc2a2566245','Unknown Device','WEB',NULL,'2025-11-30 03:41:41');
INSERT INTO `user_devices` VALUES (64,4,'aa99752f-5559-43ba-8029-c27cc003a37c','Unknown Device','WEB',NULL,'2025-11-30 03:52:14');
INSERT INTO `user_devices` VALUES (65,4,'051be69f-5a79-4200-9795-34870b0c6618','Unknown Device','WEB',NULL,'2025-11-30 06:09:16');
INSERT INTO `user_devices` VALUES (66,4,'4a0f286b-888b-4076-a98f-50a520aa8b43','Unknown Device','WEB',NULL,'2025-11-30 06:15:33');
INSERT INTO `user_devices` VALUES (67,4,'9ac267e8-f722-4211-849f-15c855399c69','Unknown Device','WEB',NULL,'2025-11-30 06:15:45');
INSERT INTO `user_devices` VALUES (68,4,'2b51f126-217f-4147-bfff-3a1e290b7320','Unknown Device','WEB',NULL,'2025-11-30 06:37:51');
INSERT INTO `user_devices` VALUES (69,4,'620c2482-bad7-4331-a7dd-c1e72c5d28ef','Unknown Device','WEB',NULL,'2025-11-30 07:38:34');
INSERT INTO `user_devices` VALUES (70,4,'3a0012d7-d4f3-4458-bad9-5df5552e4394','Unknown Device','WEB',NULL,'2025-11-30 09:25:55');
INSERT INTO `user_devices` VALUES (71,4,'01b3bdc2-83a3-4177-99e4-b88c862b0ac7','Unknown Device','WEB',NULL,'2025-11-30 10:56:04');
INSERT INTO `user_devices` VALUES (72,4,'0c9bfc98-057b-4597-9f94-cbda5d81c2a2','Unknown Device','WEB',NULL,'2025-11-30 11:17:47');
INSERT INTO `user_devices` VALUES (73,4,'83c72d62-315d-4017-94fb-f78d45c114eb','Unknown Device','WEB',NULL,'2025-12-01 02:27:14');
INSERT INTO `user_devices` VALUES (74,4,'44cc2522-2815-4820-bd9f-8aa5b3d7beb5','Unknown Device','WEB',NULL,'2025-12-01 02:29:04');
INSERT INTO `user_devices` VALUES (75,4,'57d5caf8-3836-47f6-93ab-d658c4757497','Unknown Device','WEB',NULL,'2025-12-01 02:57:03');
INSERT INTO `user_devices` VALUES (76,4,'ee065874-61a6-42e6-b0ed-cec6965c83ea','Unknown Device','WEB',NULL,'2025-12-01 03:02:27');
INSERT INTO `user_devices` VALUES (77,4,'3f29b13b-39d5-463e-8585-1ec8e668cd3e','Unknown Device','WEB',NULL,'2025-12-01 10:35:32');
INSERT INTO `user_devices` VALUES (78,4,'af70b15b-0710-4e8e-951a-97064c715b0c','Unknown Device','WEB',NULL,'2025-12-02 02:33:44');
/*!40000 ALTER TABLE `user_devices` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `login_id` varchar(50) NOT NULL,
  `email` varchar(100) NOT NULL,
  `name` varchar(100) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` enum('USER','ADMIN') NOT NULL DEFAULT 'USER',
  `status` enum('ACTIVE','LOCKED','DELETED') NOT NULL DEFAULT 'ACTIVE',
  `failed_login_count` int NOT NULL DEFAULT '0',
  `last_login_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `login_id` (`login_id`),
  UNIQUE KEY `email` (`email`),
  KEY `idx_users_login_id` (`login_id`),
  KEY `idx_users_email` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (2,'dpsk1515','dpsk1515@naver.com','박예나','$2a$10$VbPiIixga6XBLMym766bSO4ixsYVbBnzDD8XGn3uvD9jOUL9hqYx2','USER','ACTIVE',0,'2025-11-28 22:54:31','2025-10-12 03:39:02','2025-11-28 22:54:35');
INSERT INTO `users` VALUES (4,'user1','user1@gmail.com','user one','$2a$10$d97SLlg8hCaO3aOi40kRK.HRyHWdj6v74yBMNqEffr0gzjH0eQ/yi','USER','ACTIVE',0,'2025-12-02 02:33:44','2025-11-28 23:16:56','2025-12-02 02:33:44');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-12-07 17:10:03
