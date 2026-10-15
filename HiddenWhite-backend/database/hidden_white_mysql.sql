-- =====================================================================
-- Hidden White / MySQL 8.0.46 —— 整合版
-- 本文件已整合原 migrations/V2__member_password_hash.sql 与
-- migrations/V3__create_users_table.sql 的全部变更。
-- 全新安装和旧库升级都可以直接运行本文件，且可重复执行（幂等），
-- 无需再单独执行 V2 / V3。
-- 执行方式：mysql -uroot -p < hidden_white_mysql.sql
-- 或在 Navicat 中右键连接 -> 运行 SQL 文件
-- =====================================================================
CREATE DATABASE IF NOT EXISTS hidden_white
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
USE hidden_white;

-- ---------------------------------------------------------------------
-- 1. 建表（IF NOT EXISTS：旧库已有的表不会被改动，升级部分见第 2 节）
-- ---------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS stores (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    short_address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10,7) NOT NULL,
    longitude DECIMAL(10,7) NOT NULL,
    distance_label VARCHAR(32) NOT NULL,
    opening_time TIME NOT NULL,
    closing_time TIME NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(40) NOT NULL,
    name VARCHAR(80) NOT NULL,
    subtitle VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL,
    detail_description VARCHAR(200) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    image VARCHAR(255) NOT NULL,
    detail_image VARCHAR(255) NOT NULL,
    category INT NOT NULL,
    has_milk BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_products_category_enabled (category, enabled)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS product_options (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    option_type ENUM('SIZE','MILK','TEMPERATURE') NOT NULL,
    label VARCHAR(80) NOT NULL,
    extra_price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_options_type_label (option_type, label)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS members (
    id VARCHAR(64) NOT NULL,
    display_name VARCHAR(60) NOT NULL,
    phone VARCHAR(30) NULL DEFAULT NULL,
    member_level VARCHAR(30) NOT NULL DEFAULT '普通会员',
    card_number VARCHAR(30) NOT NULL,
    points INT NOT NULL DEFAULT 0,
    coupons INT NOT NULL DEFAULT 0,
    growth_value INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_card_number (card_number),
    UNIQUE KEY uk_members_phone (phone)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) NOT NULL,
    member_id VARCHAR(64) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_member_id (member_id),
    UNIQUE KEY uk_users_phone (phone),
    CONSTRAINT fk_users_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS member_benefits (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    icon VARCHAR(8) NOT NULL,
    title VARCHAR(60) NOT NULL,
    description VARCHAR(120) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_benefits_title (title)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
    id CHAR(36) NOT NULL,
    member_id VARCHAR(64) NOT NULL DEFAULT 'guest',
    subtotal DECIMAL(10,2) NOT NULL,
    discount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    packaging_fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total DECIMAL(10,2) NOT NULL,
    item_count INT NOT NULL,
    pickup VARCHAR(3) NOT NULL,
    order_number VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    ready_at DATETIME(6) NOT NULL,
    status ENUM('IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'IN_PROGRESS',
    note VARCHAR(200) NOT NULL DEFAULT '',
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_number (order_number),
    KEY idx_orders_member_created (member_id, created_at),
    KEY idx_orders_member_status (member_id, status),
    CONSTRAINT fk_orders_member FOREIGN KEY (member_id) REFERENCES members(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_id CHAR(36) NOT NULL,
    product_id VARCHAR(40) NOT NULL,
    product_name VARCHAR(80) NOT NULL,
    size_label VARCHAR(80) NOT NULL,
    temperature VARCHAR(20) NOT NULL,
    milk VARCHAR(40) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- 2. 旧库升级（整合自 V2 / V3，全部按条件执行，可重复运行不报错）
-- ---------------------------------------------------------------------

-- V2：空手机号置 NULL，phone 列允许 NULL（多次执行结果一致）
UPDATE members SET phone = NULL WHERE phone = '';
ALTER TABLE members MODIFY COLUMN phone VARCHAR(30) NULL DEFAULT NULL;

-- V2：旧唯一索引 idx_members_phone 改名为 uk_members_phone（存在才删/缺才加）
SET @old_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'members'
                   AND INDEX_NAME = 'idx_members_phone');
SET @sql := IF(@old_idx > 0,
    'ALTER TABLE members DROP INDEX idx_members_phone', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @uk := (SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'members'
              AND INDEX_NAME = 'uk_members_phone');
SET @sql := IF(@uk = 0,
    'ALTER TABLE members ADD UNIQUE KEY uk_members_phone (phone)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- V3：若旧库曾在 members 表上执行过老版 V2（存在 password_hash 临时列），
--     先把密码迁入 users 表，再删除该临时列
SET @has_ph := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'members'
                  AND COLUMN_NAME = 'password_hash');
SET @sql := IF(@has_ph > 0,
    'INSERT INTO users (id, member_id, phone, password_hash) SELECT UUID(), id, phone, password_hash FROM members WHERE password_hash IS NOT NULL AND phone IS NOT NULL ON DUPLICATE KEY UPDATE phone = VALUES(phone), password_hash = VALUES(password_hash)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_ph > 0,
    'ALTER TABLE members DROP COLUMN password_hash', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 3. 演示数据（按主键/唯一键更新，可重复执行）
-- ---------------------------------------------------------------------

INSERT INTO stores (id, name, address, short_address, latitude, longitude, distance_label, opening_time, closing_time)
VALUES (1, '隐白咖啡 · 静安嘉里店', '上海市静安区南京西路 1515 号 · 一层',
        '上海市静安区南京西路1515号 静安嘉里中心', 31.2247200, 121.4461800, '320 米', '08:00:00', '20:00:00')
ON DUPLICATE KEY UPDATE name = VALUES(name), address = VALUES(address), short_address = VALUES(short_address),
    latitude = VALUES(latitude), longitude = VALUES(longitude), distance_label = VALUES(distance_label),
    opening_time = VALUES(opening_time), closing_time = VALUES(closing_time);

INSERT INTO products (id, name, subtitle, description, detail_description, price, image, detail_image, category, has_milk)
VALUES
('latte', '招牌馥白', '双份浓缩 · 丝绒奶感', '浓郁咖啡与细腻奶泡的平衡。坚果香气，绵长回甘，每一杯现点现做。',
 '双份精萃浓缩 · 中深烘焙', 32.00, '/static/coffee/latte.png', '/static/coffee/detail.png', 0, TRUE),
('americano', '经典美式', '纯粹浓郁 · 干净回甘', '双份浓缩与纯净水的恰好融合。醇厚香气，干净回甘，每一杯现点现做。',
 '纯粹浓郁 · 中深烘焙', 26.00, '/static/coffee/americano.png', '/static/coffee/americano.png', 1, FALSE)
ON DUPLICATE KEY UPDATE name = VALUES(name), subtitle = VALUES(subtitle), description = VALUES(description),
    detail_description = VALUES(detail_description), price = VALUES(price), image = VALUES(image),
    detail_image = VALUES(detail_image), category = VALUES(category), has_milk = VALUES(has_milk), enabled = TRUE;

INSERT INTO product_options (option_type, label, extra_price, sort_order)
VALUES
('SIZE', '标准杯 240 毫升', 0.00, 1), ('SIZE', '大杯 360 毫升', 6.00, 2),
('MILK', '全脂奶', 0.00, 1), ('MILK', '燕麦奶', 4.00, 2), ('MILK', '脱脂奶', 0.00, 3),
('TEMPERATURE', '热', 0.00, 1), ('TEMPERATURE', '冰', 0.00, 2)
ON DUPLICATE KEY UPDATE extra_price = VALUES(extra_price), sort_order = VALUES(sort_order), enabled = TRUE;

INSERT INTO members (id, display_name, phone, member_level, card_number, points, coupons, growth_value)
VALUES ('guest', '游客', NULL, '普通会员', '0000 0000 0000', 0, 0, 0),
       ('demo', '隐白客人', NULL, '金卡会员', '8888 8888 8888', 1280, 5, 1280)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), member_level = VALUES(member_level),
    card_number = VALUES(card_number), points = VALUES(points), coupons = VALUES(coupons), growth_value = VALUES(growth_value);

INSERT INTO member_benefits (icon, title, description, sort_order)
VALUES ('◇', '积分兑换', '好礼随心兑', 1), ('✧', '生日礼遇', '生日专属惊喜', 2),
       ('％', '会员折扣', '指定商品专享', 3), ('＋', '免费升杯', '每月专属礼遇', 4)
ON DUPLICATE KEY UPDATE title = VALUES(title), description = VALUES(description), sort_order = VALUES(sort_order), enabled = TRUE;

-- ---------------------------------------------------------------------
-- 4. 只读检查
-- ---------------------------------------------------------------------
SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE';
