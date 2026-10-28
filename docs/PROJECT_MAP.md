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
| 兑换品（redeem product） | 积分商城里用积分兑换的商品（饮品券 / 周边），**独立于 `products`**，不与「权益」混用 |
| 积分流水（points log） | 每一笔积分变动（发放 / 兑换 / 退回 / 管理调整），`balance_after` 可反推任意时点余额 |
| 核销码（code） | 兑换成功后生成的 8 位数字，到店由管理端核销 |

---

## 二、鉴权模型

两端共用一套 JWT 密钥，靠 **token subject 前缀**天然隔离：

| 端 | 登录接口 | subject 格式 | 可访问 |
|---|---|---|---|
| 管理端 | `/api/admin/auth/login` | `sys_user.username`（如 `admin`） | `/api/admin/**` |
| 点单端 | `/api/auth/login` | `member:<会员ID>` | `/api/orders/**`、`/api/members/**`、`/api/cart/**`、`/api/redeem/**` |

管理端权限切面 `PermissionAspect` 用 principal 反查 `sys_user`，`member:` 前缀查不到该用户，
因此**点单端 token 打不进任何管理端接口**，反之亦然。**该前缀是隔离关键，不要移除。**

- 归属判定一律以 token 为准，客户端传入的 `memberId` 一律忽略。
- `SecurityConfig` 兜底为 `denyAll()`：新增路径忘记配置时默认拒绝而非默认放行。
- token 与验证码存内存（`ConcurrentHashMap`），**应用重启后需重新登录**，仅支持单实例部署。

---

## 三、点单端（HiddenWhite-front）

6 个页面；订单 / 会员 / 我的为壳页面 `pages/index/index.vue` 内的 Tab 面板
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
| `pages/redeem/index.vue` | `GET /api/redeem/products`、`POST /api/redeem/products/{id}/exchange`、`GET /api/redeem/orders`、`POST /api/redeem/orders/{id}/cancel` | `redeem_products`、`redemptions`、`members` | 需登录（拒绝 `guest`） |
| `pages/points-log/index.vue` | `GET /api/members/points-log` | `member_points_log` | 需登录（拒绝 `guest`） |

**价格、取餐号、首单减免一律由后端计算**，不信任客户端传入的价格字段。

**积分兑换链路**：会员页「去兑换 / 积分明细 / 积分兑换礼遇卡」三个入口 → 兑换页
（余额条 + 兑换品卡片 + 我的兑换）→ 滑动确认扣分 → 展示 8 位核销码 → 到店由管理端核销。
「能否兑换」由服务端判定（积分不足 / 已兑完 / 已达限兑次数），前端只展示结论与原因。

---

## 四、管理端（HiddenWhite-admin）

`COMPONENT_MAP` 共 22 项，与 `views/` 下 22 个菜单页面一一对应
（`Login` / `NoAccess` / `NotFound` 三个非菜单页不在映射内）。

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

**商品图片上传**（列表图 / 详情图各自独立配置目标宽高）：

| 接口 | 方法 | 说明 | 权限码 |
|---|---|---|---|
| `/api/admin/coffee/images/upload` | POST | multipart 上传，`slot` 取 `list` / `detail` | `coffee:product:add` |
| `/api/admin/coffee/images/spec` | GET | 各用途目标宽高与缩放方式 | `coffee:product:list` |
| `/uploads/**` | GET | 上传后的图片静态资源（免登录，供点单端加载） | 匿名 |

- 缩放由服务端完成，**解码一次 + 编码一次**，不产生二次压缩失真；大图缩小时走渐进式降采样保留细节。
- 列表图与详情图目标宽高在 `hiddenwhite.image.list` / `hiddenwhite.image.detail` 分别配置。
- 图片路径存相对路径（如 `/uploads/2026/10/21/xxx.jpg`），点单端在 `utils/image.js` 统一补全域名。

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

### 4.4 积分运营

| 页面 | 组件标识 | 接口前缀 | 权限码 |
|---|---|---|---|
| `redeem/Products.vue` | `redeem/product/index` | `/api/admin/redeem/products` | `redeem:product:*` |
| `redeem/Orders.vue` | `redeem/order/index` | `/api/admin/redeem/orders` | `redeem:order:*` |

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/admin/redeem/products` | GET / POST | 分页 + 关键字 / 上下架筛选；新增校验积分价 ≥ 1、库存 ≥ 0 或 NULL |
| `/api/admin/redeem/products/{id}` | PUT / DELETE | 整行更新；**已有兑换记录 → 409 并建议改为下架**（凭证外键不能断） |
| `/api/admin/redeem/products/{id}/enabled` | PUT | 上下架，独立端点（同商品管理约定） |
| `/api/admin/redeem/orders` | GET | 分页 + 状态 / 关键字筛选，**查询前先做过期结算** |
| `/api/admin/redeem/orders/verify` | POST | body `{code}`：8 位核销码 → `PENDING` 且未过期才翻转为 `REDEEMED` |
| `/api/admin/redeem/images/upload|spec` | POST / GET | 复用商品图片处理链路，权限码换成 `redeem:product:*` |

**过期结算不靠定时任务**：`RedeemService.settleExpired()` 在「会员查兑换单」「运营查兑换记录」
「核销前」三个入口触发，条件翻转（`WHERE status='PENDING'`）保证退分只发生一次。

### 4.5 监控与工具

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
| `member_benefits` | 会员权益说明（**规则文案，不是商品**） |
| `orders` | 订单。含支付字段（`pay_channel`/`transaction_id`/`prepay_id`/`paid_at`/`refund_*`），状态五态 `CREATED`/`PENDING_PAYMENT`/`PAID`/`COMPLETED`/`CANCELLED` |
| `order_items` | 订单明细 |
| `member_points_log` | **积分流水**。`change_amount` 正负记方向，`balance_after` 为同事务回读余额；`biz_type` 取值由 `PointsBizType` 锁定 |
| `redeem_products` | **兑换品目录**（独立于 `products`，纯积分为价；`stock=NULL` 不限量） |
| `redemptions` | **兑换单（核销凭证）**。8 位唯一核销码、30 天有效期、商品名/图/积分价快照 |
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
| 无支付 | `orders` 已有完整支付字段，微信支付通道未接入（`pay.enabled=false`），演示模式直接置 `PAID` | Ⅲ |
| 券无流水 | `members.coupons` 仍是数量计数，优惠券实体化另立方案（积分已于 2026-10-28 接入流水） | Ⅲ |
| 积分抵现未接入 | 结算尚未接入积分抵扣（方案阶段 C），当前只有「兑换」这一条消耗出口 | Ⅲ |
| 跨日结算无定时任务 | `@Scheduled` 零命中，跨日客流不会自动结算 | Ⅲ |
| 单店硬编码 | `OperationMapper` 6 处 `store_id = 1` | Ⅳ |
| 死代码接口 | 后端已实现但前端未调用：`GET /api/orders/current`、`GET /api/orders/{id}`、`GET /api/products/{id}` | Ⅳ |
| `sys_file` 表空置 | Java 代码零引用，文件上传未实现 | Ⅳ |
