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
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'stores' AND COLUMN_NAME IN ('manual_closed', 'status_note', 'closed_at');
SELECT COUNT(*) AS store_hours_rows FROM store_hours;
