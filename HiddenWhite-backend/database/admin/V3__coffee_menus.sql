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
