-- =====================================================================
-- 隐白咖啡 Hidden White —— 完整数据库初始化脚本（整合版）
-- 生成时间：2026-10-16
-- 本文件按执行顺序整合以下全部脚本，幂等可重复运行：
--   1. hidden_white_mysql.sql    咖啡业务表 + 演示数据
--   2. admin/V1__init_schema.sql  管理端系统表（16张）
--   3. admin/V2__seed_data.sql    系统种子数据（管理员/角色/菜单/字典/参数）
--   4. admin/V3__coffee_menus.sql 咖啡运营模块菜单与权限
--   5. admin/V4__shop_operation.sql     门店运营表结构（营业状态/时段/日流量）
--   6. admin/V4__shop_operation_menus.sql 门店运营模块菜单与权限
--
-- 执行方式：
--   mysql -uroot -p < init.sql
--   或在 Navicat / DBeaver 中右键连接 -> 运行 SQL 文件
--
-- 注意：也可使用 DbInit 工具自动按版本执行分文件：
--   mvn -q compile exec:java -Dexec.mainClass=com.hiddenwhite.tools.DbInit
-- =====================================================================


-- =====================================================================
-- 一、咖啡业务表与演示数据（hidden_white_mysql.sql）
-- =====================================================================


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
    pickup VARCHAR(3) NOT NULL COMMENT '取餐号：当日 1~999 循环，固定 3 位',
    order_number VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    ready_at DATETIME(6) NOT NULL,
    status ENUM('CREATED','PENDING_PAYMENT','PAID','COMPLETED','CANCELLED') NOT NULL DEFAULT 'PENDING_PAYMENT',
    note VARCHAR(200) NOT NULL DEFAULT '',
    completed_at DATETIME(6) NULL,
    pay_channel VARCHAR(32) NULL COMMENT '支付渠道：WECHAT_JSAPI；发起支付前为空',
    transaction_id VARCHAR(64) NULL COMMENT '微信支付交易号，支付成功后回填，对账与退款必需',
    prepay_id VARCHAR(64) NULL COMMENT '统一下单返回的 prepay_id，唤起支付用，过期作废',
    prepay_expire_at DATETIME(6) NULL COMMENT '预支付会话过期时间，超时任务据此清理未支付订单',
    paid_at DATETIME(6) NULL COMMENT '支付确认时间；已收款的取消订单保留以便对账',
    refund_id VARCHAR(64) NULL COMMENT '微信退款单号，退款成功后回填',
    refunded_at DATETIME(6) NULL COMMENT '退款完成时间',
    cancelled_at DATETIME(6) NULL COMMENT '取消时间',
    cancel_reason VARCHAR(32) NULL COMMENT '取消原因：TIMEOUT_UNPAID / TIMEOUT_UNPICKED / MANUAL / PAY_FAILED',
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


-- =====================================================================
-- 二、管理端系统表（V1__init_schema.sql）
-- =====================================================================


CREATE TABLE IF NOT EXISTS sys_dept (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '部门 ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '上级部门 ID',
    ancestors VARCHAR(500) NOT NULL DEFAULT '' COMMENT '祖级列表',
    dept_name VARCHAR(100) NOT NULL COMMENT '部门名称',
    leader VARCHAR(64) DEFAULT NULL COMMENT '部门负责人',
    phone VARCHAR(32) DEFAULT NULL COMMENT '联系电话',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '部门状态：1 正常，0 停用',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_sys_dept_parent (parent_id),
    KEY idx_sys_dept_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统部门表';

CREATE TABLE IF NOT EXISTS sys_post (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '岗位 ID',
    post_code VARCHAR(64) NOT NULL COMMENT '岗位编码',
    post_name VARCHAR(100) NOT NULL COMMENT '岗位名称',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '岗位状态：1 正常，0 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_post_code (post_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统岗位表';

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    username VARCHAR(64) NOT NULL COMMENT '登录账号',
    password VARCHAR(128) NOT NULL COMMENT '登录密码密文',
    nickname VARCHAR(64) NOT NULL COMMENT '用户昵称',
    phone VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    dept_id BIGINT DEFAULT NULL COMMENT '所属部门 ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '用户状态：1 正常，0 停用',
    last_login_time DATETIME DEFAULT NULL COMMENT '最后登录时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_dept (dept_id),
    KEY idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色 ID',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT '数据权限范围',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '角色状态：1 正常，0 停用',
    builtin TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置角色：1 是，0 否',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标记：1 删除，0 正常',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT NOT NULL COMMENT '菜单 ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父级菜单 ID',
    menu_name VARCHAR(100) NOT NULL COMMENT '菜单名称',
    menu_type VARCHAR(16) NOT NULL COMMENT '菜单类型：dir 目录，menu 菜单，btn 按钮',
    icon VARCHAR(64) DEFAULT NULL COMMENT '菜单图标',
    path VARCHAR(200) DEFAULT NULL COMMENT '前端路由路径',
    component VARCHAR(200) DEFAULT NULL COMMENT '前端组件路径',
    permission VARCHAR(128) DEFAULT NULL COMMENT '权限标识',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
    visible TINYINT NOT NULL DEFAULT 1 COMMENT '是否显示：1 显示，0 隐藏',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '菜单状态：1 正常，0 停用',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_sys_menu_parent (parent_id),
    KEY idx_sys_menu_permission (permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单和按钮权限表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_user_post (
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    post_id BIGINT NOT NULL COMMENT '岗位 ID',
    PRIMARY KEY (user_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户岗位关联表';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    menu_id BIGINT NOT NULL COMMENT '菜单 ID',
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表';

CREATE TABLE IF NOT EXISTS sys_role_dept (
    role_id BIGINT NOT NULL COMMENT '角色 ID',
    dept_id BIGINT NOT NULL COMMENT '部门 ID',
    PRIMARY KEY (role_id, dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色自定义数据权限部门关联表';

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典类型 ID',
    dict_name VARCHAR(100) NOT NULL COMMENT '字典类型名称',
    dict_type VARCHAR(100) NOT NULL COMMENT '字典类型编码',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '字典状态：1 正常，0 停用',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_dict_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS sys_dict_data (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典数据 ID',
    dict_type VARCHAR(100) NOT NULL COMMENT '字典类型编码',
    dict_label VARCHAR(100) NOT NULL COMMENT '字典项显示文本',
    dict_value VARCHAR(100) NOT NULL COMMENT '字典项实际值',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '字典项状态：1 正常，0 停用',
    tone VARCHAR(32) DEFAULT NULL COMMENT '前端标签色调',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认项：1 是，0 否',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_sys_dict_data_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '参数 ID',
    config_name VARCHAR(100) NOT NULL COMMENT '参数名称',
    config_key VARCHAR(100) NOT NULL COMMENT '参数键名',
    config_value VARCHAR(500) NOT NULL COMMENT '参数键值',
    config_type CHAR(1) NOT NULL DEFAULT 'N' COMMENT '是否系统内置：Y 是，N 否',
    builtin TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置参数：1 是，0 否',
    remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='参数配置表';

CREATE TABLE IF NOT EXISTS sys_oper_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '操作日志 ID',
    module VARCHAR(100) NOT NULL COMMENT '操作模块',
    action VARCHAR(64) NOT NULL COMMENT '操作类型',
    request_method VARCHAR(16) DEFAULT NULL COMMENT '请求方法',
    request_url VARCHAR(500) DEFAULT NULL COMMENT '请求地址',
    request_params TEXT COMMENT '请求参数',
    oper_name VARCHAR(64) DEFAULT NULL COMMENT '操作人',
    dept_name VARCHAR(100) DEFAULT NULL COMMENT '部门名称',
    ip VARCHAR(64) DEFAULT NULL COMMENT 'IP 地址',
    location VARCHAR(128) DEFAULT NULL COMMENT '操作地点',
    cost BIGINT DEFAULT NULL COMMENT '耗时毫秒',
    result TINYINT NOT NULL DEFAULT 1 COMMENT '操作结果：1 成功，0 失败',
    error_msg TEXT COMMENT '错误信息',
    oper_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_sys_oper_log_time (oper_time),
    KEY idx_sys_oper_log_oper_name (oper_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '登录日志 ID',
    username VARCHAR(64) DEFAULT NULL COMMENT '登录账号',
    ip VARCHAR(64) DEFAULT NULL COMMENT 'IP 地址',
    location VARCHAR(128) DEFAULT NULL COMMENT '登录地点',
    browser VARCHAR(128) DEFAULT NULL COMMENT '浏览器',
    os VARCHAR(128) DEFAULT NULL COMMENT '操作系统',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '登录状态：1 成功，0 失败',
    msg VARCHAR(500) DEFAULT NULL COMMENT '提示消息',
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (id),
    KEY idx_sys_login_log_time (login_time),
    KEY idx_sys_login_log_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

CREATE TABLE IF NOT EXISTS sys_file (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件 ID',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    storage_name VARCHAR(255) NOT NULL COMMENT '存储文件名',
    url VARCHAR(500) NOT NULL COMMENT '访问地址',
    content_type VARCHAR(128) DEFAULT NULL COMMENT '文件类型',
    size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小字节数',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件信息表';

CREATE TABLE IF NOT EXISTS gen_table (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '代码生成表 ID',
    table_name VARCHAR(100) NOT NULL COMMENT '数据库表名',
    table_comment VARCHAR(200) DEFAULT NULL COMMENT '表说明',
    class_name VARCHAR(100) NOT NULL COMMENT '后端实体类名',
    module_name VARCHAR(64) NOT NULL COMMENT '模块名称',
    business_name VARCHAR(64) NOT NULL COMMENT '业务名称',
    synced TINYINT NOT NULL DEFAULT 0 COMMENT '是否同步数据库字段：1 是，0 否',
    create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_table_name (table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成表配置';

CREATE TABLE IF NOT EXISTS gen_table_column (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '代码生成字段 ID',
    table_id BIGINT NOT NULL COMMENT '代码生成表 ID',
    column_name VARCHAR(100) NOT NULL COMMENT '数据库字段名',
    java_field VARCHAR(100) NOT NULL COMMENT 'Java 字段名',
    java_type VARCHAR(64) NOT NULL COMMENT 'Java 类型',
    jdbc_type VARCHAR(64) NOT NULL COMMENT 'JDBC 类型',
    column_comment VARCHAR(200) DEFAULT NULL COMMENT '字段说明',
    is_insert TINYINT NOT NULL DEFAULT 1 COMMENT '是否新增字段',
    is_edit TINYINT NOT NULL DEFAULT 1 COMMENT '是否编辑字段',
    is_list TINYINT NOT NULL DEFAULT 1 COMMENT '是否列表展示',
    is_query TINYINT NOT NULL DEFAULT 0 COMMENT '是否查询字段',
    query_type VARCHAR(32) NOT NULL DEFAULT '=' COMMENT '查询方式',
    form_type VARCHAR(64) NOT NULL DEFAULT '文本框' COMMENT '表单控件类型',
    required TINYINT NOT NULL DEFAULT 0 COMMENT '是否必填',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序号',
    PRIMARY KEY (id),
    KEY idx_gen_table_column_table (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成字段配置';


-- =====================================================================
-- 三、系统种子数据（V2__seed_data.sql）
-- =====================================================================


-- 本地开发示例数据；组织与用户仅供演示，电话留空，邮箱使用 example.com。
INSERT IGNORE INTO sys_dept (id, parent_id, ancestors, dept_name, leader, phone, email, sort, status, create_by, update_by)
VALUES
    (1, 0, '0', '云原科技集团', '周明远', '', 'hq@example.com', 1, 1, 'system', 'system'),
    (2, 1, '0,1', '研发中心', '陈思远', '', 'rd@example.com', 1, 1, 'system', 'system'),
    (3, 1, '0,1', '产品中心', '赵晴', '', 'pm@example.com', 2, 1, 'system', 'system'),
    (4, 1, '0,1', '职能中心', '钱伟', '', 'hr@example.com', 3, 1, 'system', 'system'),
    (5, 2, '0,1,2', '平台架构部', '李维', '', 'arch@example.com', 1, 1, 'system', 'system'),
    (6, 2, '0,1,2', '应用开发部', '吴敏', '', 'app@example.com', 2, 1, 'system', 'system'),
    (7, 2, '0,1,2', '测试质量部', '韩雪', '', 'qa@example.com', 3, 1, 'system', 'system'),
    (8, 3, '0,1,3', '产品设计部', '孙浩', '', 'design@example.com', 1, 1, 'system', 'system'),
    (9, 3, '0,1,3', '用户研究部', '马玲', '', 'ux@example.com', 2, 0, 'system', 'system'),
    (10, 4, '0,1,4', '人力资源部', '冯婷', '', 'people@example.com', 1, 1, 'system', 'system'),
    (11, 4, '0,1,4', '财务部', '蒋文', '', 'finance@example.com', 2, 1, 'system', 'system');

INSERT IGNORE INTO sys_post (id, post_code, post_name, sort, status, remark, create_by, update_by)
VALUES
    (1, 'ceo', '董事长', 1, 1, '集团最高管理岗位', 'system', 'system'),
    (2, 'gm', '总经理', 2, 1, '分管业务线总经理', 'system', 'system'),
    (3, 'tech_dir', '技术总监', 3, 1, '研发中心负责人', 'system', 'system'),
    (4, 'pm', '产品经理', 4, 1, '负责产品规划与设计', 'system', 'system'),
    (5, 'arch', '架构师', 5, 1, '平台架构设计岗位', 'system', 'system'),
    (6, 'dev', '研发工程师', 6, 1, '前后端研发岗位', 'system', 'system'),
    (7, 'qa', '测试工程师', 7, 1, '质量保障岗位', 'system', 'system'),
    (8, 'hr', '人事专员', 8, 1, '人力资源岗位', 'system', 'system'),
    (9, 'finance', '财务专员', 9, 0, '财务核算岗位', 'system', 'system');

INSERT IGNORE INTO sys_role (id, role_name, role_code, data_scope, sort, status, builtin, remark, create_by, update_by)
VALUES
    (1, '超级管理员', 'super_admin', 'ALL', 1, 1, 1, '系统最高权限，不可删除', 'system', 'system'),
    (2, '系统管理员', 'sys_admin', 'DEPT_AND_CHILD', 2, 1, 0, '负责系统日常配置与维护', 'system', 'system'),
    (3, '业务管理员', 'biz_admin', 'DEPT', 3, 1, 0, '维护本部门业务数据', 'system', 'system'),
    (4, '研发工程师', 'developer', 'SELF', 4, 1, 0, '使用代码生成与开发模块', 'system', 'system'),
    (5, '审计专员', 'auditor', 'CUSTOM', 5, 1, 0, '查看操作日志与登录日志', 'system', 'system'),
    (6, '只读访客', 'guest', 'SELF', 6, 0, 0, '仅可查看授权报表', 'system', 'system');

INSERT IGNORE INTO sys_user (id, username, password, nickname, phone, email, dept_id, status, create_by, update_by)
VALUES
    (1, 'admin', '$2b$10$L7q28dpKkcfrrUzfvZebqe7pvxcKpaSgh7EUenf9Q5z2LiYKxDpIu', '周明远', '', 'zhoumy@example.com', 5, 1, 'system', 'system'),
    (2, 'chensy', '$2b$10$L7q28dpKkcfrrUzfvZebqe7pvxcKpaSgh7EUenf9Q5z2LiYKxDpIu', '陈思远', '', 'chensy@example.com', 5, 1, 'system', 'system'),
    (3, 'wumin', '$2b$10$L7q28dpKkcfrrUzfvZebqe7pvxcKpaSgh7EUenf9Q5z2LiYKxDpIu', '吴敏', '', 'wumin@example.com', 6, 1, 'system', 'system');

INSERT IGNORE INTO sys_user_role (user_id, role_id) VALUES (1, 1), (2, 2), (3, 3);
INSERT IGNORE INTO sys_user_post (user_id, post_id) VALUES (1, 3), (2, 5), (3, 6);

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (1, 0, '工作台', 'menu', 'dashboard', '/dashboard', 'dashboard/index', 'system:dashboard:list', 1, 1, 1, 'system', 'system'),
    (100, 0, '系统管理', 'dir', 'gear', '/system', '', '', 2, 1, 1, 'system', 'system'),
    (101, 100, '用户管理', 'menu', 'users', '/system/user', 'system/user/index', 'system:user:list', 1, 1, 1, 'system', 'system'),
    (1011, 101, '新增用户', 'btn', '', '', '', 'system:user:add', 1, 1, 1, 'system', 'system'),
    (1012, 101, '编辑用户', 'btn', '', '', '', 'system:user:edit', 2, 1, 1, 'system', 'system'),
    (1013, 101, '删除用户', 'btn', '', '', '', 'system:user:remove', 3, 1, 1, 'system', 'system'),
    (1014, 101, '重置密码', 'btn', '', '', '', 'system:user:resetPwd', 4, 1, 1, 'system', 'system'),
    (102, 100, '角色管理', 'menu', 'role', '/system/role', 'system/role/index', 'system:role:list', 2, 1, 1, 'system', 'system'),
    (1021, 102, '新增角色', 'btn', '', '', '', 'system:role:add', 1, 1, 1, 'system', 'system'),
    (1022, 102, '编辑角色', 'btn', '', '', '', 'system:role:edit', 2, 1, 1, 'system', 'system'),
    (1023, 102, '删除角色', 'btn', '', '', '', 'system:role:remove', 3, 1, 1, 'system', 'system'),
    (103, 100, '菜单管理', 'menu', 'menu', '/system/menu', 'system/menu/index', 'system:menu:list', 3, 1, 1, 'system', 'system'),
    (1031, 103, '新增菜单', 'btn', '', '', '', 'system:menu:add', 1, 1, 1, 'system', 'system'),
    (1032, 103, '编辑菜单', 'btn', '', '', '', 'system:menu:edit', 2, 1, 1, 'system', 'system'),
    (1033, 103, '删除菜单', 'btn', '', '', '', 'system:menu:remove', 3, 1, 1, 'system', 'system'),
    (104, 100, '部门管理', 'menu', 'dept', '/system/dept', 'system/dept/index', 'system:dept:list', 4, 1, 1, 'system', 'system'),
    (1041, 104, '新增部门', 'btn', '', '', '', 'system:dept:add', 1, 1, 1, 'system', 'system'),
    (1042, 104, '编辑部门', 'btn', '', '', '', 'system:dept:edit', 2, 1, 1, 'system', 'system'),
    (1043, 104, '删除部门', 'btn', '', '', '', 'system:dept:remove', 3, 1, 1, 'system', 'system'),
    (105, 100, '岗位管理', 'menu', 'post', '/system/post', 'system/post/index', 'system:post:list', 5, 1, 1, 'system', 'system'),
    (1051, 105, '新增岗位', 'btn', '', '', '', 'system:post:add', 1, 1, 1, 'system', 'system'),
    (1052, 105, '编辑岗位', 'btn', '', '', '', 'system:post:edit', 2, 1, 1, 'system', 'system'),
    (1053, 105, '删除岗位', 'btn', '', '', '', 'system:post:remove', 3, 1, 1, 'system', 'system'),
    (106, 100, '字典管理', 'menu', 'dict', '/system/dict', 'system/dict/index', 'system:dict:list', 6, 1, 1, 'system', 'system'),
    (1061, 106, '新增字典', 'btn', '', '', '', 'system:dict:add', 1, 1, 1, 'system', 'system'),
    (1062, 106, '编辑字典', 'btn', '', '', '', 'system:dict:edit', 2, 1, 1, 'system', 'system'),
    (1063, 106, '删除字典', 'btn', '', '', '', 'system:dict:remove', 3, 1, 1, 'system', 'system'),
    (107, 100, '参数配置', 'menu', 'gear', '/system/config', 'system/config/index', 'system:config:list', 7, 1, 1, 'system', 'system'),
    (1071, 107, '新增参数', 'btn', '', '', '', 'system:config:add', 1, 1, 1, 'system', 'system'),
    (1072, 107, '编辑参数', 'btn', '', '', '', 'system:config:edit', 2, 1, 1, 'system', 'system'),
    (1073, 107, '删除参数', 'btn', '', '', '', 'system:config:remove', 3, 1, 1, 'system', 'system'),
    (200, 0, '系统监控', 'dir', 'log', '/monitor', '', '', 3, 1, 1, 'system', 'system'),
    (201, 200, '操作日志', 'menu', 'file', '/monitor/oper-log', 'monitor/operlog/index', 'monitor:operlog:list', 1, 1, 1, 'system', 'system'),
    (2011, 201, '清理操作日志', 'btn', '', '', '', 'monitor:operlog:remove', 1, 1, 1, 'system', 'system'),
    (202, 200, '登录日志', 'menu', 'lock', '/monitor/login-log', 'monitor/loginlog/index', 'monitor:loginlog:list', 2, 1, 1, 'system', 'system'),
    (2021, 202, '清理登录日志', 'btn', '', '', '', 'monitor:loginlog:remove', 1, 1, 1, 'system', 'system'),
    (300, 0, '系统工具', 'dir', 'tool', '/tool', '', '', 4, 1, 1, 'system', 'system'),
    (301, 300, '代码生成', 'menu', 'command', '/tool/generator', 'tool/gen/index', 'tool:gen:list', 1, 1, 1, 'system', 'system'),
    (3011, 301, '导入表', 'btn', '', '', '', 'tool:gen:edit', 1, 1, 1, 'system', 'system'),
    (3012, 301, '修改配置', 'btn', '', '', '', 'tool:gen:edit', 2, 1, 1, 'system', 'system'),
    (3013, 301, '生成代码', 'btn', '', '', '', 'tool:gen:code', 3, 1, 1, 'system', 'system'),
    (3014, 301, '下载代码', 'btn', '', '', '', 'tool:gen:code', 4, 1, 1, 'system', 'system');

-- 超级管理员拥有全部菜单；其余角色由管理员在「角色管理」中按需授权
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

-- 研发工程师（role_id=4）默认拥有代码生成器的读写权限：
-- 该角色职责本就是「使用代码生成与开发模块」，生成代码会写文件，
-- 故 edit 与 code 两个写权限一并授予；其余角色仍由管理员按需授权。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 4, id FROM sys_menu
WHERE id IN (301, 3011, 3012, 3013, 3014);

-- 工作台菜单补权限码：DashboardController 已加 @RequiresPermission("system:dashboard:list")，
-- 存量库中该行 permission 为空，需幂等补齐，否则非超管角色将无法访问工作台。
UPDATE sys_menu SET permission = 'system:dashboard:list' WHERE id = 1 AND (permission IS NULL OR permission = '');

INSERT IGNORE INTO sys_dict_type (id, dict_name, dict_type, status, remark, create_by, update_by)
VALUES
    (1, '用户性别', 'sys_user_sex', 1, '用户性别列表', 'system', 'system'),
    (2, '系统开关', 'sys_normal_disable', 1, '系统正常 / 停用状态', 'system', 'system'),
    (3, '是否', 'sys_yes_no', 1, '通用是 / 否选项', 'system', 'system');

INSERT IGNORE INTO sys_dict_data (id, dict_type, dict_label, dict_value, sort, status, tone, is_default, create_by, update_by)
VALUES
    (1, 'sys_user_sex', '男', '0', 1, 1, 'info', 1, 'system', 'system'),
    (2, 'sys_user_sex', '女', '1', 2, 1, 'purple', 0, 'system', 'system'),
    (3, 'sys_user_sex', '未知', '2', 3, 1, 'neutral', 0, 'system', 'system'),
    (4, 'sys_normal_disable', '正常', '1', 1, 1, 'ok', 1, 'system', 'system'),
    (5, 'sys_normal_disable', '停用', '0', 2, 1, 'danger', 0, 'system', 'system'),
    (6, 'sys_yes_no', '是', 'Y', 1, 1, 'ok', 1, 'system', 'system'),
    (7, 'sys_yes_no', '否', 'N', 2, 1, 'neutral', 0, 'system', 'system');

INSERT IGNORE INTO sys_config (id, config_name, config_key, config_value, config_type, builtin, remark, create_by, update_by)
VALUES
    (1, '用户管理-账号初始密码', 'sys.user.initPassword', 'Yy@123456', 'Y', 1, '初始化密码', 'system', 'system'),
    (2, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', 1, '是否开启登录验证码', 'system', 'system'),
    (3, '主框架-默认外观', 'sys.index.appearance', 'light', 'N', 0, '可选 light / dark / auto', 'system', 'system');

-- 代码生成配置初始为空，按需导入当前数据库的真实表。


-- =====================================================================
-- 四、咖啡运营模块菜单与权限（V3__coffee_menus.sql）
-- =====================================================================


-- V3: 咖啡业务菜单与按钮权限（咖啡运营模块）
-- 前提：V1__init_schema.sql、V2__seed_data.sql 已执行。
-- 页面组件路径对应 HiddenWhite-admin-front/src/views/coffee/ 下的页面；
-- 权限码前缀 coffee:，对应后端 com.hiddenwhite.admin.controller.CoffeeController。

-- 目录：咖啡运营（沿用种子数据排序号段，排在系统工具之后）
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (400, 0, '咖啡运营', 'dir', 'grid', '/coffee', '', '', 5, 1, 1, 'system', 'system'),
    (401, 400, '商品管理', 'menu', 'layers', '/coffee/product', 'coffee/product/index', 'coffee:product:list', 1, 1, 1, 'system', 'system'),
    (4011, 401, '新增商品', 'btn', '', '', '', 'coffee:product:add', 1, 1, 1, 'system', 'system'),
    (4012, 401, '编辑商品', 'btn', '', '', '', 'coffee:product:edit', 2, 1, 1, 'system', 'system'),
    (4013, 401, '删除商品', 'btn', '', '', '', 'coffee:product:remove', 3, 1, 1, 'system', 'system'),
    (402, 400, '规格选项', 'menu', 'sliders', '/coffee/option', 'coffee/option/index', 'coffee:option:list', 2, 1, 1, 'system', 'system'),
    (4021, 402, '新增选项', 'btn', '', '', '', 'coffee:option:add', 1, 1, 1, 'system', 'system'),
    (4022, 402, '编辑选项', 'btn', '', '', '', 'coffee:option:edit', 2, 1, 1, 'system', 'system'),
    (4023, 402, '删除选项', 'btn', '', '', '', 'coffee:option:remove', 3, 1, 1, 'system', 'system'),
    (403, 400, '订单管理', 'menu', 'clock', '/coffee/order', 'coffee/order/index', 'coffee:order:list', 3, 1, 1, 'system', 'system'),
    (4031, 403, '完成订单', 'btn', '', '', '', 'coffee:order:edit', 1, 1, 1, 'system', 'system'),
    (4032, 403, '删除订单', 'btn', '', '', '', 'coffee:order:remove', 2, 1, 1, 'system', 'system'),
    (404, 400, '会员管理', 'menu', 'users', '/coffee/member', 'coffee/member/index', 'coffee:member:list', 4, 1, 1, 'system', 'system'),
    (4041, 404, '编辑会员', 'btn', '', '', '', 'coffee:member:edit', 1, 1, 1, 'system', 'system'),
    (4042, 404, '删除会员', 'btn', '', '', '', 'coffee:member:remove', 2, 1, 1, 'system', 'system'),
    (405, 400, '会员权益', 'menu', 'shield', '/coffee/benefit', 'coffee/benefit/index', 'coffee:benefit:list', 5, 1, 1, 'system', 'system'),
    (4051, 405, '新增权益', 'btn', '', '', '', 'coffee:benefit:add', 1, 1, 1, 'system', 'system'),
    (4052, 405, '编辑权益', 'btn', '', '', '', 'coffee:benefit:edit', 2, 1, 1, 'system', 'system'),
    (4053, 405, '删除权益', 'btn', '', '', '', 'coffee:benefit:remove', 3, 1, 1, 'system', 'system'),
    (406, 400, '门店设置', 'menu', 'home', '/coffee/store', 'coffee/store/index', 'coffee:store:list', 6, 1, 1, 'system', 'system'),
    (4061, 406, '修改门店', 'btn', '', '', '', 'coffee:store:edit', 1, 1, 1, 'system', 'system');

-- 超级管理员（role_id = 1）授予全部咖啡菜单与按钮
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 400 OR (id >= 401 AND id <= 4061);


-- =====================================================================
-- 五、门店运营表结构（V4__shop_operation.sql）
-- =====================================================================


-- =====================================================================
-- V4: 店铺运营模块（营业状态 / 每日运营时段 / 日流量统计）
-- 落库：hidden_white 库（与点单端共用，单库方案）
-- 前提：hidden_white_mysql.sql 已执行；本文件幂等，可重复运行。
-- 执行方式：mysql -uroot -p < V4__shop_operation.sql
-- =====================================================================
CREATE DATABASE IF NOT EXISTS hidden_white
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
USE hidden_white;

-- ---------------------------------------------------------------------
-- 1. stores 表追加营业状态字段
--    营业与否是随时计算的派生值，不落库；库中只存“人工意图”。
--    MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，按 information_schema 判断。
-- ---------------------------------------------------------------------

-- manual_closed：人工打烊开关（最高优先级）
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stores' AND COLUMN_NAME = 'manual_closed');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE stores ADD COLUMN manual_closed BOOLEAN NOT NULL DEFAULT FALSE COMMENT ''人工打烊开关：TRUE 已打烊（最高优先级），FALSE 按时段自动判断'' AFTER closing_time',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- status_note：打烊提示语，展示在点单端
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stores' AND COLUMN_NAME = 'status_note');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE stores ADD COLUMN status_note VARCHAR(100) NOT NULL DEFAULT '''' COMMENT ''打烊提示语，空则用默认文案'' AFTER manual_closed',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- closed_at：最近一次人工打烊时间（恢复营业时置 NULL）
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stores' AND COLUMN_NAME = 'closed_at');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE stores ADD COLUMN closed_at DATETIME NULL COMMENT ''最近一次人工打烊时间'' AFTER status_note',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- manual_open：人工开启营业（与 manual_closed 对称，强制覆盖例休与时间窗口自动判定）
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stores' AND COLUMN_NAME = 'manual_open');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE stores ADD COLUMN manual_open TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''人工开启营业：TRUE 强制营业，覆盖例休与时间窗口自动判定'' AFTER manual_closed',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 1.5 orders：接入微信支付（JSAPI），状态由 IN_PROGRESS 拆为五态
--     拆分的意义在于把「有没有收到钱」与「做到哪一步」分开：
--     JSAPI 下单只是发起预支付，用户可能关掉页面，此时订单既未付款也
--     未制作。若无中间态，只能误判为已付（漏收钱）或直接取消（用户丢单）。
--
--     历史订单的 IN_PROGRESS 一律映射为 PAID：改造前系统只接受过
--     「已确认取餐」这一动作，隐含已完成收款，不应误判为未收款而影响对账。
-- ---------------------------------------------------------------------

SET @has_status := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'status');
SET @sql := IF(@has_status > 0,
    'ALTER TABLE orders MODIFY COLUMN status
       ENUM(''CREATED'',''PENDING_PAYMENT'',''PAID'',''COMPLETED'',''CANCELLED'')
       NOT NULL DEFAULT ''PENDING_PAYMENT''
       COMMENT ''CREATED=已创建 / PENDING_PAYMENT=待支付 / PAID=已支付制作中 / COMPLETED=已完成 / CANCELLED=已取消''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史数据迁移：须紧跟改枚举之后，否则旧值不在新枚举内会被置为空值
UPDATE orders SET status = 'PAID' WHERE status = 'IN_PROGRESS';

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'pay_channel');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE orders ADD COLUMN pay_channel VARCHAR(32) NULL COMMENT ''支付渠道：WECHAT_JSAPI'' AFTER completed_at',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'transaction_id');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE orders ADD COLUMN transaction_id VARCHAR(64) NULL COMMENT ''微信支付交易号，对账与退款必需'' AFTER pay_channel',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'prepay_id');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE orders ADD COLUMN prepay_id VARCHAR(64) NULL COMMENT ''统一下单返回的 prepay_id，唤起支付用'' AFTER transaction_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'prepay_expire_at');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE orders ADD COLUMN prepay_expire_at DATETIME(6) NULL COMMENT ''预支付会话过期时间'' AFTER prepay_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'paid_at');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE orders ADD COLUMN paid_at DATETIME(6) NULL COMMENT ''支付确认时间'' AFTER prepay_expire_at',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'refund_id');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE orders ADD COLUMN refund_id VARCHAR(64) NULL COMMENT ''微信退款单号'' AFTER paid_at',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'refunded_at');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE orders ADD COLUMN refunded_at DATETIME(6) NULL COMMENT ''退款完成时间'' AFTER refund_id',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'cancelled_at');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE orders ADD COLUMN cancelled_at DATETIME(6) NULL COMMENT ''取消时间'' AFTER refunded_at',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'cancel_reason');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE orders ADD COLUMN cancel_reason VARCHAR(32) NULL COMMENT ''取消原因：TIMEOUT_UNPAID / TIMEOUT_UNPICKED / MANUAL / PAY_FAILED'' AFTER cancelled_at',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 取餐号位数规范化：3 位。历史上曾扩到 4 位后又收回，
-- 此处按列宽双向兼容，存量库重跑本脚本即可自动对齐。
SET @pickup_len := (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'pickup');
-- 先把超长数据截到 3 位，避免 ALTER 时因截断报错中断
SET @sql := IF(@pickup_len > 3,
    'UPDATE orders SET pickup = RIGHT(LPAD(CAST(CAST(pickup AS UNSIGNED) AS CHAR), 4, ''0''), 3) WHERE pickup REGEXP ''^[0-9]+$'' AND CHAR_LENGTH(pickup) > 3',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@pickup_len <> 3,
    'ALTER TABLE orders MODIFY COLUMN pickup VARCHAR(3) NOT NULL COMMENT ''取餐号：当日 1~999 循环，固定 3 位''',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 超时扫描与支付回调都要按状态定位订单，需联合索引
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'idx_orders_status_created');
SET @sql := IF(@has_idx = 0,
    'ALTER TABLE orders ADD KEY idx_orders_status_created (status, created_at)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- transaction_id 唯一索引：回调幂等的基础，防止同一笔支付被重复入账
SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders' AND INDEX_NAME = 'uk_orders_transaction');
SET @sql := IF(@has_idx = 0,
    'ALTER TABLE orders ADD UNIQUE KEY uk_orders_transaction (transaction_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------
-- 2. store_hours：按星期运营时段（进阶配置，stores 默认时段作兜底）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS store_hours (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    store_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '门店 ID，当前单店固定 1',
    day_of_week TINYINT UNSIGNED NOT NULL COMMENT '星期：1=周一 … 7=周日',
    open_time TIME NOT NULL COMMENT '当日开门时间',
    close_time TIME NOT NULL COMMENT '当日打烊时间',
    closed BOOLEAN NOT NULL DEFAULT FALSE COMMENT '当日是否休息（如周一例休）',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_hours_day (store_id, day_of_week)
) ENGINE=InnoDB COMMENT='门店按星期运营时段';

-- 初始化周一至周日 7 行：默认值取 stores 第一行的时段（仅当表为空时执行）
INSERT INTO store_hours (store_id, day_of_week, open_time, close_time, closed)
SELECT s.id, d.day, s.opening_time, s.closing_time, FALSE
FROM (SELECT 1 AS day UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
      UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7) d
CROSS JOIN (SELECT id, opening_time, closing_time FROM stores ORDER BY id LIMIT 1) s
WHERE NOT EXISTS (SELECT 1 FROM store_hours);

-- ---------------------------------------------------------------------
-- 3. store_daily_traffic：日流量统计（主键即日期，一天一行）
--    自动汇总 orders 表生成；打烊/跨日结算冻结（settled=TRUE）后可人工调整，
--    origin_* 三列只在首次调整时快照原始值，永远可追溯。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS store_daily_traffic (
    stat_date DATE NOT NULL COMMENT '统计日期',
    order_count INT NOT NULL DEFAULT 0 COMMENT '订单数（自动汇总）',
    revenue DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '营业额（自动汇总）',
    visitor_count INT NOT NULL DEFAULT 0 COMMENT '客流数（下单会员去重估算，可人工修正）',
    closed_minutes INT NOT NULL DEFAULT 0 COMMENT '当日累计人工打烊时长（分钟）',
    origin_order_count INT NULL COMMENT '调整前的原始订单数',
    origin_revenue DECIMAL(10,2) NULL COMMENT '调整前的原始营业额',
    origin_visitor_count INT NULL COMMENT '调整前的原始客流',
    adjusted BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否被人工调整过',
    adjust_reason VARCHAR(200) NOT NULL DEFAULT '' COMMENT '最近一次调整原因',
    settled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已结算冻结（打烊后置 TRUE）',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (stat_date)
) ENGINE=InnoDB COMMENT='门店日流量统计';

-- ---------------------------------------------------------------------
-- 4. 只读检查
-- ---------------------------------------------------------------------
SELECT COLUMN_NAME FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stores' AND COLUMN_NAME IN ('manual_closed', 'manual_open', 'status_note', 'closed_at');
SELECT COUNT(*) AS store_hours_rows FROM store_hours;


-- =====================================================================
-- 六、门店运营模块菜单与权限（V4__shop_operation_menus.sql）
-- =====================================================================


-- V4: 店铺运营模块菜单与按钮权限（管理端 admin 库）
-- 前提：V1__init_schema.sql、V2__seed_data.sql、V3__coffee_menus.sql 已执行。
-- 页面组件路径对应 HiddenWhite-admin-front/src/views/operation/ 下的页面；
-- 权限码前缀 operation:，对应后端 com.hiddenwhite.admin.operation.controller.OperationController。
-- 幂等（INSERT IGNORE），可重复运行。

-- 目录：店铺运营（500 段，排在咖啡运营 400 段之后）
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (500, 0, '店铺运营', 'dir', 'grid', '/operation', '', '', 6, 1, 1, 'system', 'system'),
    (501, 500, '营业状态', 'menu', 'sliders', '/operation/status', 'operation/status/index', 'operation:status:list', 1, 1, 1, 'system', 'system'),
    (5011, 501, '切换状态', 'btn', '', '', '', 'operation:status:edit', 1, 1, 1, 'system', 'system'),
    (502, 500, '运营时间', 'menu', 'clock', '/operation/hours', 'operation/hours/index', 'operation:hours:list', 2, 1, 1, 'system', 'system'),
    (5021, 502, '编辑时段', 'btn', '', '', '', 'operation:hours:edit', 1, 1, 1, 'system', 'system'),
    (503, 500, '日流统计', 'menu', 'dashboard', '/operation/traffic', 'operation/traffic/index', 'operation:traffic:list', 3, 1, 1, 'system', 'system'),
    (5031, 503, '调整数据', 'btn', '', '', '', 'operation:traffic:edit', 1, 1, 1, 'system', 'system');

-- 超级管理员（role_id = 1）授予全部店铺运营菜单与按钮
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 500 OR (id >= 501 AND id <= 5031);


-- =====================================================================
-- 七、积分运营（积分流水 / 兑换品 / 兑换单）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) member_points_log：积分流水（开发清单 P0-2）
--    每一笔积分变动都必须有且只有一条流水，余额以「条件更新」改写后
--    同事务回读写入 balance_after —— 任何时点的流水序列求和应等于当时余额。
--    biz_type 取值由 com.hiddenwhite.common.points.PointsBizType 枚举锁定：
--      ORDER_AWARD / REDEEM / REDEEM_CANCEL / REDEEM_EXPIRE_REFUND /
--      POINTS_DEDUCT / POINTS_REFUND / ADMIN_ADJUST
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS member_points_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    member_id VARCHAR(64) NOT NULL COMMENT '会员 id',
    change_amount INT NOT NULL COMMENT '变动量：正为发放/退回，负为消耗',
    balance_after INT NOT NULL COMMENT '变动后余额（同事务回读）',
    biz_type VARCHAR(32) NOT NULL COMMENT '业务类型，见 PointsBizType',
    ref_type VARCHAR(32) NOT NULL DEFAULT '' COMMENT '关联对象类型：ORDER / REDEMPTION / ADMIN',
    ref_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '关联对象 id（订单/兑换单/管理操作）',
    remark VARCHAR(200) NOT NULL DEFAULT '' COMMENT '备注文案',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_points_log_member (member_id, id),
    KEY idx_points_log_ref (ref_type, ref_id)
) ENGINE=InnoDB COMMENT='会员积分流水';

-- ---------------------------------------------------------------------
-- 2) redeem_products：兑换品目录
--    独立于 products —— 后者绑定奶咖假设（has_milk / SIZE·MILK·TEMPERATURE），
--    无法表达「0 元 / 纯积分价 / 非饮品」的兑换品，且兑换品不需要规格矩阵。
--    stock 为 NULL 表示不限量；非空时用条件更新原子递减。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS redeem_products (
    id VARCHAR(40) NOT NULL COMMENT '业务主键，如 rw-latte',
    name VARCHAR(80) NOT NULL COMMENT '兑换品名称',
    subtitle VARCHAR(120) NOT NULL DEFAULT '' COMMENT '副标题',
    description VARCHAR(300) NOT NULL DEFAULT '' COMMENT '描述',
    image VARCHAR(200) NOT NULL DEFAULT '' COMMENT '图片，复用 /uploads 链路，存相对路径',
    points_cost INT NOT NULL COMMENT '兑换所需积分',
    stock INT NULL DEFAULT NULL COMMENT '库存：NULL 不限，非空须 >0 可兑',
    per_member_limit INT NOT NULL DEFAULT 0 COMMENT '每人限兑次数（待核销+已核销合计），0 不限',
    sort_order INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='积分兑换品目录';

-- ---------------------------------------------------------------------
-- 3) redemptions：兑换单（到店核销凭证）
--    商品名称与图片做快照：兑换品后续改名/下架/删图，已发出的凭证仍要能核对。
--    expires_at 由下单时写入（默认 30 天），逾期由惰性结算改为 EXPIRED 并退分。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS redemptions (
    id CHAR(36) NOT NULL,
    redeem_no VARCHAR(32) NOT NULL COMMENT '兑换单号：RW + yyyyMMdd + 4 位日序号',
    member_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(40) NOT NULL,
    product_name VARCHAR(80) NOT NULL COMMENT '快照：兑换时的商品名',
    product_image VARCHAR(200) NOT NULL DEFAULT '' COMMENT '快照：兑换时的商品图',
    points_cost INT NOT NULL COMMENT '快照：实际扣除积分',
    code VARCHAR(8) NOT NULL COMMENT '8 位核销码，全局唯一',
    status ENUM('PENDING','REDEEMED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING=待核销 / REDEEMED=已核销 / CANCELLED=已取消 / EXPIRED=已过期',
    expires_at DATETIME(6) NOT NULL COMMENT '核销截止时间',
    verified_at DATETIME(6) NULL COMMENT '核销时间',
    verified_by VARCHAR(64) NULL COMMENT '核销管理员账号（sys_user.username，非数字 id，便于日志直接读）',
    cancelled_at DATETIME(6) NULL COMMENT '取消时间',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_redemptions_no (redeem_no),
    UNIQUE KEY uk_redemptions_code (code),
    KEY idx_redemptions_member (member_id, created_at),
    KEY idx_redemptions_status_expire (status, expires_at),
    KEY idx_redemptions_product_limit (member_id, product_id, status),
    CONSTRAINT fk_redemptions_member FOREIGN KEY (member_id) REFERENCES members(id),
    CONSTRAINT fk_redemptions_product FOREIGN KEY (product_id) REFERENCES redeem_products(id)
) ENGINE=InnoDB COMMENT='积分兑换单（核销凭证）';

-- 演示兑换品：对齐 demo 会员的 1280 积分，开箱即有可走通的兑换路径。
-- 用 INSERT IGNORE：运营在管理端改过的价格/库存/上下架状态不会被重跑脚本覆盖。
INSERT IGNORE INTO redeem_products
    (id, name, subtitle, description, points_cost, per_member_limit, sort_order, enabled)
VALUES
    ('rw-americano', '经典美式', '到店核销 · 兑一杯', '双份浓缩与纯净水，干净回甘。兑换后 30 天内到店出示核销码即可。', 880, 0, 1, TRUE),
    ('rw-latte', '经典拿铁', '到店核销 · 兑一杯', '双份浓缩与丝绒奶泡，坚果香气绵长。每位会员限兑 1 次。', 1280, 1, 2, TRUE),
    ('rw-oat-latte', '燕麦拿铁', '到店核销 · 兑一杯', '燕麦奶与浓缩的清爽组合，乳糖不耐也能安心喝。', 1580, 0, 3, TRUE),
    ('rw-badge', '隐白周边徽章', '限量 · 到店自提', '门店限定金属徽章，数量有限，兑完即止。', 2880, 1, 4, TRUE);

-- ---------------------------------------------------------------------
-- 4) 管理端菜单：积分运营（600 段，排在店铺运营 500 段之后）
--    页面组件路径对应 HiddenWhite-admin/src/views/redeem/ 下的页面；
--    权限码前缀 redeem:，对应后端 com.hiddenwhite.admin.redeem.controller.RedeemAdminController。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
VALUES
    (600, 0, '积分运营', 'dir', 'activity', '/redeem', '', '', 7, 1, 1, 'system', 'system'),
    (601, 600, '兑换品管理', 'menu', 'layers', '/redeem/product', 'redeem/product/index', 'redeem:product:list', 1, 1, 1, 'system', 'system'),
    (6011, 601, '兑换品新增', 'btn', '', '', '', 'redeem:product:add', 1, 1, 1, 'system', 'system'),
    (6012, 601, '兑换品编辑', 'btn', '', '', '', 'redeem:product:edit', 2, 1, 1, 'system', 'system'),
    (6013, 601, '兑换品删除', 'btn', '', '', '', 'redeem:product:remove', 3, 1, 1, 'system', 'system'),
    (602, 600, '兑换记录', 'menu', 'table', '/redeem/order', 'redeem/order/index', 'redeem:order:list', 2, 1, 1, 'system', 'system'),
    (6021, 602, '兑换核销', 'btn', '', '', '', 'redeem:order:verify', 1, 1, 1, 'system', 'system');

-- 超级管理员（role_id = 1）授予全部积分运营菜单与按钮。
-- 用「父节点 ∈ 600/601/602」而非 id 区间：id 区间会误伤 1011、4011 这类
-- 数字上落在 601~6029 之内的其它模块按钮，授权范围随菜单新增也不会走偏。
INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 600 OR parent_id IN (600, 601, 602);

-- ---------------------------------------------------------------------
-- 5) 只读检查
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS redeem_products_rows FROM redeem_products;
SELECT COUNT(*) AS redeem_menus_rows FROM sys_menu WHERE id = 600 OR parent_id IN (600, 601, 602);

