-- Meeting Room System complete schema.
-- Target database: MySQL 8.x

CREATE DATABASE IF NOT EXISTS `meeting_system`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `meeting_system`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `notification`;
DROP TABLE IF EXISTS `reservation_review`;
DROP TABLE IF EXISTS `reservation_device`;
DROP TABLE IF EXISTS `reservation_participant`;
DROP TABLE IF EXISTS `room_device`;
DROP TABLE IF EXISTS `reservation`;
DROP TABLE IF EXISTS `meeting_room`;
DROP TABLE IF EXISTS `device`;
DROP TABLE IF EXISTS `sys_user`;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `display_name` varchar(64) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `role` tinyint NOT NULL DEFAULT '1' COMMENT '1普通用户，2管理员',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1启用，0禁用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0未删除，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `device` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `device_code` varchar(32) NOT NULL,
  `name` varchar(128) NOT NULL,
  `total` int NOT NULL DEFAULT '0',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1启用，0禁用',
  `description` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0未删除，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_device_code` (`device_code`),
  KEY `idx_device_name` (`name`),
  KEY `idx_device_status` (`status`),
  CONSTRAINT `device_chk_1` CHECK ((`total` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `meeting_room` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_code` varchar(32) NOT NULL,
  `name` varchar(128) NOT NULL,
  `location` varchar(128) NOT NULL,
  `capacity` int NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1可用，2维护中',
  `description` varchar(255) DEFAULT NULL,
  `maintenance_remark` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0未删除，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_room_code` (`room_code`),
  KEY `idx_room_location` (`location`),
  KEY `idx_room_status` (`status`),
  CONSTRAINT `meeting_room_chk_1` CHECK ((`capacity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reservation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reservation_no` varchar(40) NOT NULL,
  `room_id` bigint NOT NULL,
  `organizer_id` bigint NOT NULL,
  `title` varchar(128) NOT NULL,
  `remark` varchar(255) DEFAULT NULL,
  `attendees` int NOT NULL,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1待审核，2已通过，3已结束，4已取消，5已驳回，6异常',
  `cancel_reason` varchar(255) DEFAULT NULL,
  `approval_remark` varchar(255) DEFAULT NULL,
  `reject_reason` varchar(255) DEFAULT NULL,
  `exception_reason` varchar(255) DEFAULT NULL,
  `processed_by` bigint DEFAULT NULL,
  `processed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0未删除，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `reservation_no` (`reservation_no`),
  KEY `idx_resv_room_time` (`room_id`,`start_time`,`end_time`),
  KEY `idx_resv_user_time` (`organizer_id`,`start_time`),
  KEY `idx_resv_status` (`status`),
  KEY `idx_resv_status_endtime` (`status`,`end_time`),
  KEY `idx_reservation_room_id` (`room_id`),
  KEY `idx_reservation_organizer_id` (`organizer_id`),
  KEY `idx_reservation_start_time` (`start_time`),
  CONSTRAINT `fk_resv_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_resv_user` FOREIGN KEY (`organizer_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `reservation_chk_1` CHECK ((`attendees` > 0)),
  CONSTRAINT `reservation_chk_2` CHECK ((`end_time` > `start_time`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `room_device` (
  `room_id` bigint NOT NULL,
  `device_id` bigint NOT NULL,
  `quantity` int NOT NULL DEFAULT '1' COMMENT '会议室静态绑定设备数量',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0未删除，1已删除',
  PRIMARY KEY (`room_id`,`device_id`),
  UNIQUE KEY `uk_room_device_room_device` (`room_id`,`device_id`),
  KEY `idx_room_device_device` (`device_id`),
  CONSTRAINT `fk_room_device_device` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_room_device_room` FOREIGN KEY (`room_id`) REFERENCES `meeting_room` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_room_device_quantity` CHECK ((`quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reservation_participant` (
  `reservation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0未删除，1已删除',
  PRIMARY KEY (`reservation_id`,`user_id`),
  KEY `idx_resv_participant_user` (`user_id`),
  CONSTRAINT `fk_resv_participant_resv` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_resv_participant_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reservation_device` (
  `reservation_id` bigint NOT NULL,
  `device_id` bigint NOT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0未删除，1已删除',
  PRIMARY KEY (`reservation_id`,`device_id`),
  KEY `idx_rdr_device` (`device_id`),
  CONSTRAINT `fk_rdr_device` FOREIGN KEY (`device_id`) REFERENCES `device` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_rdr_resv` FOREIGN KEY (`reservation_id`) REFERENCES `reservation` (`id`) ON DELETE CASCADE,
  CONSTRAINT `reservation_device_chk_1` CHECK ((`quantity` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reservation_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reservation_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `rating` int NOT NULL,
  `content` varchar(300) DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0未删除，1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_reservation_user` (`reservation_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `category` tinyint NOT NULL DEFAULT '1',
  `title` varchar(128) NOT NULL,
  `content` varchar(500) NOT NULL,
  `read_flag` tinyint NOT NULL DEFAULT '0' COMMENT '0未读，1已读',
  `route` varchar(255) DEFAULT NULL,
  `route_query` text,
  `extra` varchar(255) DEFAULT NULL,
  `status` varchar(16) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `read_at` datetime DEFAULT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '0未删除，1已删除',
  PRIMARY KEY (`id`),
  KEY `idx_notification_user_created` (`user_id`,`created_at`),
  KEY `idx_notification_user_read_category` (`user_id`,`read_flag`,`category`),
  CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
