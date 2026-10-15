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
