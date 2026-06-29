-- Integration test schema init
CREATE TABLE IF NOT EXISTS `user` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `email` VARCHAR(100) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `openid` VARCHAR(128) NULL,
    `nickname` VARCHAR(64) NULL,
    `avatar` VARCHAR(512) NULL,
    `role` VARCHAR(20) NOT NULL DEFAULT 'user'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
