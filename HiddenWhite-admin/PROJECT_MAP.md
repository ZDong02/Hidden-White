# 隐白咖啡 · 项目地图

> 按功能定位「前端页面 → 后端接口 → 数据表 → 权限码」。
> 新增页面、接口或模块时**同步更新本文件**，保持文档与代码一致。

---

## 一、业务术语

| 术语 | 含义 |
|---|---|
| 会员（member） | 点单端顾客。`members` 表，与管理端账号 `sys_user` 是**两套体系** |
| 管理端账号（user） | 后台使用者，`sys_user` 表，登录后按角色分配菜单与权限 |
| 取餐号（pickup） | 订单号后 3 位，由**后端计算**（`OrderService`），顾客到店出示 |
| 首单减免 | 会员无历史订单时减 6 元，由后端判定 |
| 门店（store） | 当前**单店**模型，`store_id` 硬编码为 1，多店支持见阶段Ⅳ |
| 权益（benefit） | 会员卡背面的规则说明，如积分兑换、生日礼遇 |

---

## 二、鉴权模型

两端共用一套 JWT 密钥，靠 **token subject 前缀**天然隔离：

| 端 | 登录接口 | subject 格式 | 可访问 |
|---|---|---|---|
| 管理端 | `/api/admin/auth/login` | `sys_user.username`（如 `admin`） | `/api/admin/**` |
| 点单端 | `/api/auth/login` | `member:<会员ID>` | `/api/orders/**`、`/api/members/**`、`/api/cart/**` |

管理端权限切面 `PermissionAspect` 用 principal 反查 `sys_user`，`member:` 前缀查不到该用户，
因此**点单端 token 打不进任何管理端接口**，反之亦然。**该前缀是隔离关键，不要移除。**

- 归属判定一律以 token 为准，客户端传入的 `memberId` 一律忽略。
- `SecurityConfig` 兜底为 `denyAll()`：新增路径忘记配置时默认拒绝而非默认放行。
- token 与验证码存内存（`ConcurrentHashMap`），**应用重启后需重新登录**，仅支持单实例部署。

---

## 三、点单端（HiddenWhite-front）

4 个页面；订单 / 会员 / 我的为壳页面 `pages/index/index.vue` 内的 Tab 面板
（`components/tabs/`，通过 `?tab=` 直达，不单独注册）。

| 页面 / 面板 | 后端接口 | 表 | 权限 |
|---|---|---|---|
| `pages/index/index.vue` | 壳页面，承载四个面板 | — | — |
| `components/tabs/MenuPanel.vue` | `GET /api/products`、`GET /api/store` | `products`、`stores` | 公开 |
| `components/tabs/OrdersPanel.vue` | `GET /api/orders`、`POST /api/orders/{id}/complete` | `orders`、`order_items` | 需登录 |
| `components/tabs/MemberPanel.vue` | `GET /api/members/{id}`、`GET /api/members/benefits` | `members`、`member_benefits` | 需登录 / 公开 |
| `components/tabs/ProfilePanel.vue` | 无（本地会话 + 后端地址诊断） | — | — |
| `pages/detail/detail.vue` | `GET /api/products/options` | `product_options` | 公开 |
| `pages/checkout/checkout.vue` | `POST /api/cart/quote`、`POST /api/orders` | `orders`、`order_items` | 需登录 |
| `pages/auth/auth.vue` | `POST /api/auth/login`、`/api/auth/register`、`/api/auth/logout` | `members`、`users` | 公开 |

**价格、取餐号、首单减免一律由后端计算**，不信任客户端传入的价格字段。

---

## 四、管理端（HiddenWhite-admin）

20 个页面，`COMPONENT_MAP` 与 `views/` 目录 **20 = 20 完全对应**。

### 4.1 咖啡运营

| 页面 | 组件标识 | 接口前缀 | 权限码 |
|---|---|---|---|
| `coffee/Products.vue` | `coffee/product/index` | `/api/admin/coffee/products` | `coffee:product:*` |
| `coffee/Options.vue` | `coffee/option/index` | `/api/admin/coffee/options` | `coffee:option:*` |
| `coffee/Orders.vue` | `coffee/order/index` | `/api/admin/coffee/orders` | `coffee:order:*` |
| `coffee/Members.vue` | `coffee/member/index` | `/api/admin/coffee/members` | `coffee:member:*` |
| `coffee/Benefits.vue` | `coffee/benefit/index` | `/api/admin/coffee/benefits` | `coffee:benefit:*` |
| `coffee/Store.vue` | `coffee/store/index` | `/api/admin/coffee/store` | `coffee:store:*` |

**业务规则**：删除商品时若已产生订单，改为软下架（`enabled=false`）并返回提示文案，不做物理删除。
`guest` 会员受保护，不可删除。

### 4.2 门店运营

| 页面 | 组件标识 | 接口前缀 | 权限码 |
|---|---|---|---|
| `operation/Status.vue` | `operation/status/index` | `/api/admin/operation/status` | `operation:status:*` |
| `operation/Hours.vue` | `operation/hours/index` | `/api/admin/operation/hours` | `operation:hours:*` |
| `operation/Traffic.vue` | `operation/traffic/index` | `/api/admin/operation/traffic` | `operation:traffic:*` |

**营业状态是派生值**，优先级由高到低：
人工打烊 > 人工开启 > 当日例休 > 未到营业时间 > 已过打烊 > 营业中。

- `manual_closed` / `manual_open` 构成三态：打烊 / 自动判断 / 人工开启。
- 日流量在打烊时结算，已被人工调整过的列不被覆盖（`IF(adjusted, order_count, VALUES(...))`）。

### 4.3 系统管理

| 页面 | 组件标识 | 子模块 |
|---|---|---|
| `system/Users.vue` | `system/user/index` | 用户 CRUD + 看板视图，**受数据权限约束** |
| `system/Roles.vue` | `system/role/index` | 角色 + 权限矩阵 + 权限溯源 |
| `system/Menus.vue` | `system/menu/index` | 菜单树 + 星图 |
| `system/Depts.vue` | `system/dept/index` | 部门 + 组织画布 |
| `system/Posts.vue` | `system/post/index` | 岗位 + 阶梯 |
| `system/Dict.vue` | `system/dict/index` | 字典类型与字典项 |
| `system/Configs.vue` | `system/config/index` | 参数配置 |

**数据权限**：`@DataScope` 标注在 `SystemManagementService` 的 `users()` / `exportUsers()` /
`permissionGraph()` 三处。无标注时 `DataScopeContext.current()` 返回 `selfOnly(null)`（fail-closed），
即**查不到数据**而非返回全表。新增查询若需数据权限，必须加注解。

### 4.4 监控与工具

| 页面 | 组件标识 | 说明 |
|---|---|---|
| `monitor/OperLog.vue` | `monitor/operlog/index` | 操作日志 + 统计 + 泳道 |
| `monitor/LoginLog.vue` | `monitor/loginlog/index` | 登录日志 + 统计 + 7×24 热力图 + 异常聚合 |
| `tool/Generator.vue` | `tool/gen/index` | 代码生成器（三步流水线） |
| `Dashboard.vue` | `dashboard/index` | 工作台概览，权限码 `system:dashboard:list` |

---

## 五、数据表（单库 `hidden_white`）

### 5.1 业务表（点单端）

| 表 | 说明 |
|---|---|
| `stores` | 门店。含 `manual_closed` / `manual_open` 三态开关 |
| `products` | 商品。`enabled=false` 为软下架 |
| `product_options` | 规格选项（SIZE / MILK / TEMPERATURE） |
| `members` | 会员。`guest` 为受保护的游客账号 |
| `users` | 会员登录凭据（`phone` + `password_hash`），与管理端 `sys_user` 无关 |
| `member_benefits` | 会员权益说明 |
| `orders` | 订单。**当前无支付字段**，状态仅 `IN_PROGRESS` / `COMPLETED` |
| `order_items` | 订单明细 |
| `store_hours` | 按星期运营时段，恰好 7 行 |
| `store_daily_traffic` | 日客流量，打烊时结算 |

### 5.2 系统表（管理端）

`sys_dept`、`sys_post`、`sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_user_post`、
`sys_role_menu`、`sys_role_dept`、`sys_dict_type`、`sys_dict_data`、`sys_config`、
`sys_oper_log`、`sys_login_log`、`sys_file`、`gen_table`、`gen_table_column`

`sys_menu.permission` 存权限码，`sys_menu.component` 存前端组件标识（对应 `COMPONENT_MAP` 的键）。

### 5.3 数据库脚本

唯一脚本 `HiddenWhite-backend/database/init.sql`（幂等，可重复执行），由 `tools/DbInit` 执行。
**结构变更必须写入该脚本，禁止只改库不改脚本。**

---

## 六、已知技术债

| 项 | 说明 | 阶段 |
|---|---|---|
| 无支付 | `orders` 表无支付字段，微信支付未接入 | Ⅲ |
| 积分 / 券无流水 | 仅整数字段，无法追溯消耗过程 | Ⅲ |
| 跨日结算无定时任务 | `@Scheduled` 零命中，跨日客流不会自动结算 | Ⅲ |
| 单店硬编码 | `OperationMapper` 6 处 `store_id = 1` | Ⅳ |
| 死代码接口 | 后端已实现但前端未调用：`GET /api/orders/current`、`GET /api/orders/{id}`、`GET /api/products/{id}` | Ⅳ |
| `sys_file` 表空置 | Java 代码零引用，文件上传未实现 | Ⅳ |
