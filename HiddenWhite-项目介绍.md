# 隐白咖啡（Hidden White）全栈点单应用 · 项目详细介绍

> 生成日期：2026-10-13 ｜ 工作区路径：`D:\Hidden White` ｜ 文档定位：面向新团队成员与维护者的项目全景说明

---

## 一、项目简介

**隐白咖啡（Hidden White）** 是一套完整的精品咖啡点单应用，采用"一个小程序/H5 点单端 + 一个 Web 管理端 + 一个统一后端 + 一个 MySQL 单库"的全栈架构：

- **点单端**：基于 uni-app（Vue 3）的微信小程序 / H5 应用，面向顾客，提供菜单浏览、规格选择、购物袋、下单支付（演示）、取餐订单、会员权益等完整点单流程。
- **管理端**：基于 Vue 3 + TypeScript 的后台管理系统，面向店员与运营，提供商品/规格/订单/会员/权益/门店的咖啡运营管理，以及用户、角色、菜单、部门、岗位、字典、参数、日志、代码生成器等通用后台能力。
- **统一后端**：2026-10-13 由点单后端与管理端后端合并而成的单一 Spring Boot 应用，同时提供小程序端 `/api/**` 与管理端 `/api/admin/**` 两类接口。
- **数据库**：两端共用 `hidden_white` 单库，业务表与系统表同库，通过迁移脚本初始化。

项目遵循统一的代码规范（根目录 `CODESTYLE.md`），前后端均配置了自动化测试与构建流水线。

---

## 二、总体架构

```
┌──────────────────────┐        ┌──────────────────────┐
│  点单端（顾客）        │        │  管理端（运营）        │
│  HiddenWhite-front   │        │  HiddenWhite-admin   │
│  uni-app + Vue 3     │        │  Vue 3 + TS + Vite   │
│  微信小程序 / H5      │        │  Web 管理后台         │
└──────────┬───────────┘        └──────────┬───────────┘
           │  /api/**（免登录）              │  /api/admin/**（JWT）
           ▼                                 ▼
┌──────────────────────────────────────────────────────────┐
│           HiddenWhite-backend（统一后端，端口 8080）        │
│  Spring Boot 3.5.14 · Java 17 · Spring Security + JJWT   │
│  shop（小程序端）+ admin（管理端）+ framework + common     │
└───────────────────────────┬──────────────────────────────┘
                            │  MyBatis-Plus（管理端）
                            │  Spring JDBC（小程序端）
                            ▼
┌──────────────────────────────────────────────────────────┐
│              MySQL 单库 hidden_white（端口 3306）           │
│   8 张业务表（stores/products/orders/members/...）          │
│   + 16 张系统表（sys_user/sys_role/sys_menu/gen_* ...）     │
└──────────────────────────────────────────────────────────┘
```

**关键设计决策**：

| 决策 | 说明 |
| --- | --- |
| 单库策略 | 管理端系统表与点单业务表共用 `hidden_white` 库，管理端修改商品/规格/门店后，点单端切换页面即可看到（`onShow` 自动重新拉取） |
| 无 Redis | 登录 token 与图形验证码存内存（`ConcurrentHashMap`），应用重启后需重新登录，token 有效期 2 小时 |
| 仅 MySQL 模式 | 已移除内存演示数据模式，启动必须连接数据库 |
| 双异常结构 | 小程序端返回 `ApiError` 结构，管理端返回 `R` 结构，后端按请求路径前缀 `/api/admin` 自动区分 |
| 价格后端计算 | 点单端价格、取餐号、首单减免等一律由后端计算，不信任客户端传入的价格字段 |

**端口规划**：后端 `8080`；管理端前端开发服务器 `5173`（`/api` 代理到 8080）；点单端 H5 开发服务器默认 `127.0.0.1`（`scripts/run-uni.cjs` 指定）；MySQL `3306`。

---

## 三、工作区目录结构

```
D:/Hidden White/
├── HiddenWhite-front/              # 点单端：uni-app（Vue 3）小程序 / H5
├── HiddenWhite-backend/            # 统一后端：Spring Boot 单应用（唯一后端）
├── HiddenWhite-admin/              # 管理端
│   ├── HiddenWhite-admin-front/    #   管理端前端：Vue 3 + TypeScript + Vite
│   ├── docs/                       #   架构文档（后端架构、前后端接口约定、前端重构架构）
│   ├── PROJECT_MAP.md              #   项目地图：按功能定位页面、接口、服务、SQL 和权限
│   ├── DESIGN.md                   #   珍珠玻璃设计规范：主题、组件复用及交互
│   ├── AGENTS.md                   #   开发约定（复用优先、统一底座）
│   ├── changelog.md                #   更新日志（按日期记录每次变更与验证结果）
│   └── LICENSE                     #   MIT（GSAP 依赖另遵循其标准免费许可）
├── _archive/                       # 历史归档：合并前代码备份、旧版配置文件
├── CODESTYLE.md                    # 全工作区统一代码规范
├── .editorconfig                   # 编辑器层统一约束（UTF-8 / LF / 缩进）
├── .gitignore                      # 全工作区忽略规则（依赖、构建产物、IDE、密钥）
├── project.config.json             # 微信开发者工具项目配置（appid wx90d8af9ad4e52689）
└── project.private.config.json     # 微信开发者工具私有配置
```

**版本控制现状**：整个工作区基本未纳入 Git 管理，仅 `HiddenWhite-admin/` 目录是一个 Git 仓库（其中保留了一次"开源快照"提交，当前管理端源码未被跟踪）。日常协作依赖 `changelog.md` 与 `CODESTYLE.md` 维持一致性。

---

## 四、点单端 HiddenWhite-front（uni-app）

### 4.1 定位与功能页面

面向顾客的点单应用，支持**微信小程序与 H5 双端**。共 7 个页面：

| 页面 | 文件 | 功能 |
| --- | --- | --- |
| 首页/菜单 | `pages/index/index.vue` | 门店信息、营业状态、商品分类与列表、购物袋入口 |
| 商品详情 | `pages/detail/detail.vue` | 规格选择（杯型/奶类/温度等）、数量、加入购物袋 |
| 结算 | `pages/checkout/checkout.vue` | 订单确认、取餐方式、微信支付（演示，不实际扣款） |
| 订单 | `pages/order/order.vue` | 当前订单与历史订单、取餐号、预计完成时间 |
| 会员 | `pages/member/member.vue` | 会员权益、积分、优惠信息 |
| 我的 | `pages/profile/profile.vue` | 账号信息、入口导航（体验版本提示） |
| 登录 | `pages/auth/auth.vue` | 会员登录/注册 |

自定义底部标签栏（`components/MainTabBar.vue`）：首页、订单、会员、我的。

### 4.2 技术栈

- **框架**：uni-app 3.0.0-alpha（Vue 3.4.21，Composition API）
- **构建**：Vite 5.2.8（`@dcloudio/vite-plugin-uni`），npm scripts 经 `scripts/run-uni.cjs` 统一转发
- **样式**：SCSS + `uni.scss` 全局变量 + `styles/common.scss`
- **语言**：JavaScript（无 TypeScript）
- **代码检查**：ESLint 10 flat config（`eslint.config.mjs`，`js/recommended` + `eslint-plugin-vue`）

### 4.3 目录职责

| 目录 | 职责 |
| --- | --- |
| `pages/` | 7 个业务页面，按 `pages.json` 注册 |
| `components/` | 5 个共享组件：PhoneHeader（模拟手机头部）、HomeIndicator（底部指示条）、MainTabBar（标签栏）、StoreLine（门店信息行）、OrderItem（订单项）；`components/ui/` 子目录含 5 个统一 UI 组件：Card（卡片）、Button（按钮）、Input（输入框）、Stepper（数字步进器）、Modal（底部弹窗） |
| `store/` | 状态管理：`index.js`（购物车/订单/商品，基于 Vue `reactive` 的轻量实现，本地存储持久化）、`auth.js`（登录态） |
| `config/` | `backend.js`（统一请求封装 `requestApi` + 后端地址自动解析）、`backend-hosts.js`（候选后端地址列表，换网络只需改此文件）、`constants.js`（门店、业务规则、选项、图片路径等常量） |
| `utils/` | 10 个工具模块：`format.js`（日期/取餐号/预计完成时间）、`navigation.js`（页面导航封装）、`validation.js`（购物车校验）、`price.js`（价格计算）、`storage.js`（防抖本地存储）、`toast.js`（统一提示）、`datetime.js`（日期时间增强）、`validators.js`（表单验证）、`image.js`（图片资源管理）、`composables.js`（Vue 组合式函数） |
| `data/products.js` | 静态商品数据：API 加载前的初始渲染数据、后端不可用时的兜底、商品分类的唯一来源 |
| `static/` | 图片资源（咖啡产品图、图标；图标由 SVG 经脚本生成 PNG） |
| `scripts/` | `run-uni.cjs`（开发/构建统一入口）、`generate-icons.cjs`（SVG → PNG 图标生产链） |
| `preview/` | UI 设计稿截图（设计参考，不参与构建） |

### 4.4 关键设计与机制

- **统一请求与后端地址自动解析**：全项目唯一 `uni.request` 调用点在 `config/backend.js` 的 `requestApi`，组件禁止裸调（CODESTYLE 强制）。后端地址不再硬编码 IP，而是按以下优先级解析：① 运行时手动覆盖（"我的"页可配置）→ ② 构建期环境变量 `VITE_API_BASE_URL` → ③ H5 开发态相对路径 `/api`（vite 代理）→ ④ 按 `config/backend-hosts.js` 候选地址列表做 `/api/health` 健康检查，第一个连通的即生效并缓存 → ⑤ 兜底 `localhost:8080`。更换网络只需修改 `backend-hosts.js` 中的候选 IP，无需改动业务代码。
- **数据实时性**：首页、订单、会员页在 `onShow`（切换/回到页面）时自动重新拉取商品、订单、权益与门店信息——管理端改动后点单端切页即见。
- **营业状态管控**：门店打烊时禁止结算（前端拦截 + 后端规则）。
- **本地兜底**：后端不可用时使用 `data/products.js` 静态数据渲染，购物车/订单持久化在本地存储。
- **图标生产链**：`static/icons/*.svg` 为源文件，`npm run assets:icons` 用 `@resvg/resvg-js` 渲染为运行时引用的 PNG（小程序 `image` 组件不支持 SVG）。
- **多端构建**：`npm run dev:mp-weixin` / `build:mp-weixin` 产出 `unpackage/dist/build/mp-weixin`（微信开发者工具导入目录）；`dev:h5` / `build:h5` 产出 `dist/build/h5`。

---

## 五、管理端 HiddenWhite-admin

### 5.1 管理端前端 HiddenWhite-admin-front（Vue 3 + TypeScript）

**定位**：从通用后台管理脚手架演进而来的咖啡运营管理后台，"珍珠玻璃"设计语言 + 自有组件库，不依赖 Element Plus / three.js 等第三方 UI 框架。

**技术栈**：Vue 3.5.13 · TypeScript 5.7 · Vite 6.0.7 · Vue Router 4.5 · GSAP 3.15（动效）· vue-tsc 2.2（构建前类型检查）。

**架构分层**：

| 层 | 内容 |
| --- | --- |
| `src/views/` | 20 个页面（见下方清单） |
| `src/components/ui/` | 26 个基础控件：Button、TextInput、Select、DataTable、Modal、Drawer、ToastHost、Pagination、StatusTag 等 |
| `src/components/business/` | 20 个业务组件：ListPage、SearchForm、FormDialog、PermissionButton、DictSelect、DeptTree、UserSelect、PermissionTree、PermissionMatrix、MenuStarMap、OrgCanvas 等 |
| `src/components/charts/` | 6 个自绘图表：TrendChart、BarChart、HeatGrid、RelationGraph、Swimlane、TickRing |
| `src/components/layout/` | 6 个布局组件：AppShell（页面切换外壳）、Sidebar、Topbar、CommandPalette（命令面板）、AppearanceDialog、PasswordDialog |
| `src/composables/` | 16 个组合式函数：auth（权限/动态菜单）、list（列表页）、dict、dept、toast、confirm、appearance、motion、poll、viewport 等 |
| `src/api/` | 统一 HTTP 客户端（`http.ts`）+ auth / session / system / portal / coffee 五个业务接口模块 |
| `src/styles/` | 七层样式体系：tokens → base → materials → components → instruments → layout → pages |
| `src/utils/` | askMatch（本地问答引擎）、clipboard、configValue、download、heatmap、permissionGraph、validate |

**页面清单（20 个）**：

- 入口 3 个：登录（全幅深色画布 + 神经网络动效背景）、工作台（Dashboard）、无权限 / 未匹配（NoAccess / NotFound）
- 系统管理 7 个：用户（列表/看板拖拽）、角色（权限矩阵/溯源图谱）、菜单（导航星系星图）、部门（组织架构画布）、岗位（职级阶梯）、字典（样本册）、参数（设置面板）
- 监控 2 个：操作日志（概览/趋势/泳道/明细）、登录日志（趋势/热力图/异常）
- 工具 1 个：代码生成器（三步流水线抽屉）
- 咖啡运营 6 个：商品、规格选项、订单、会员、权益、门店

**权限模型**：菜单与按钮权限由后端下发；新页面必须在 `src/composables/auth.ts` 的 `COMPONENT_MAP` 注册组件路径；权限动作用 `PermissionButton` 控制；数据权限由后端 `@DataScope` 注解实现。

**测试体系**（零测试框架依赖，用 Node 内置运行器）：

- `npm run test:unit`：`node --test --experimental-strip-types` 运行 9 个测试文件（api 5 个 + utils 4 个）
- `npm run test:api`：仅 API 层测试
- `npm run build`：`vue-tsc --noEmit` 类型检查 + `vite build`
- `scripts/session-regression.py`：Python Playwright 浏览器 E2E 回归（登录竞态、菜单与按钮权限、旧账号请求晚到），自建自删测试夹具，不清空数据表

**开发约定**（`AGENTS.md`）：复用优先——已有通用组件能满足时直接复用；稍不足时优先扩展通用组件；多页面会用的新能力先抽成组件；单场景独有逻辑才写页面内部。所有列表页默认具备查询、重置、分页、导出、状态展示、行操作能力。

### 5.2 管理端后端（已合并）

原为独立的 Maven 多模块后端（common / framework / system / generator / server），2026-10-13 其全部代码已合并进 `HiddenWhite-backend` 统一后端（接口前缀 `/api/**` → `/api/admin/**`）。合并前的完整备份保存在 `_archive/merge-backup-2026-10-13/`。

### 5.3 文档体系

| 文档 | 内容 |
| --- | --- |
| `PROJECT_MAP.md` | 项目地图：业务术语词典、模块文件地图（后端 Controller/Service/Mapper ↔ 前端页面/API ↔ 表） |
| `DESIGN.md` | 珍珠玻璃设计规范：七层样式、设计令牌、版式与交互 |
| `AGENTS.md` | 开发约定与复用优先级 |
| `changelog.md` | 按日期的变更记录（含每次验证结果） |
| `docs/backend-architecture.md` | 后端架构说明 |
| `docs/frontend-backend-integration.md` | 前后端接口约定 |
| `docs/admin-redesign-architecture.md` | 前端重构架构 |

---

## 六、统一后端 HiddenWhite-backend（Spring Boot）

### 6.1 定位与包结构

隐白咖啡**唯一后端**：一个 Spring Boot 应用同时提供小程序/H5 接口与管理端接口（2026-10-13 由两个独立后端合并而成，合并前代码备份于 `_archive/merge-backup-2026-10-13/`）。

```
com.hiddenwhite
├── HiddenWhiteApplication      # 唯一启动类
├── common/                     # R 统一返回、分页模型、异常与统一异常处理
├── framework/                  # 安全（JWT + 验证码）、操作日志、数据权限、TraceId 过滤器
├── config/                     # Web MVC（CORS）
├── shop/                       # 小程序端：controller / service / repository / domain / dto
├── admin/                      # 管理端：system（权限/角色/菜单）、coffee（咖啡业务）、generator
└── tools/DbInit.java           # 数据库初始化工具（main 方法，IDE 或 mvn exec:java 运行）
```

### 6.2 接口分布

| 端 | 前缀 | 鉴权 | 代码位置 |
| --- | --- | --- | --- |
| 小程序 / H5 | `/api/**`（auth、products、store、cart、orders、members） | 无需登录 | `com.hiddenwhite.shop` |
| 管理端 | `/api/admin/**`（auth、coffee、system、dashboard、monitor、tool/generator） | JWT 登录 | `com.hiddenwhite.admin` |
| 健康检查 | `/api/health` | 放行 | `com.hiddenwhite.admin.controller` |

管理端登录地址为 `/api/admin/auth/login`（合并前为 `/api/auth/login`），管理端前端 `defaultBaseUrl` 已同步为 `/api/admin`。

### 6.3 技术栈与依赖

Spring Boot 3.5.14 · Java 17 · Spring MVC + Validation + AOP · Spring Security · JJWT 0.12.6 · MyBatis-Plus 3.5.16（管理端）· Spring JDBC / `JdbcTemplate`（小程序端）· MySQL Connector/J · knife4j 4.5（API 文档）· HikariCP 连接池（默认 10）。

双数据访问风格并存：小程序端 shop 包用 `JdbcTemplate`（`ShopRepository`），管理端 admin 包用 MyBatis-Plus Mapper，共用一个数据源。

### 6.4 安全设计

- **JWT**：登录签发 token，有效期 2 小时（`JWT_EXPIRES_SECONDS`），密钥经 `JWT_SECRET` 环境变量注入（生产必配，至少 32 字节，启动校验）；`TokenStore` 为内存实现
- **验证码**：图形验证码（`CaptchaService`，内存存储），登录需按页面显示字符输入
- **Spring Security 过滤器链**：`JwtAuthenticationFilter` 解析 token，`/api/admin/**` 需认证，`/api/**` 放行，knife4j 文档路径放行
- **权限体系**：`@RequiresPermission` 注解 + `PermissionAspect` 拦截；菜单/按钮权限码（如 `coffee:product:list`）由角色授予
- **数据权限**：`@DataScope` 注解 + `DataScopeAspect`，按部门数据范围过滤
- **操作审计**：`@OperLog` 注解 + `OperationLogAspect` 记录操作日志；`TraceIdFilter` 全链路请求 ID
- **本地开发密钥**：`start-dev.ps1` 首次运行随机生成 `.jwt-secret.local`（已被 `.gitignore` 的 `*.local` 规则忽略，固定密钥可使重启前的 token 继续有效）

### 6.5 配置与环境变量

配置文件：`src/main/resources/application.yml`（全部支持环境变量覆盖，不读取 `.env` 文件）。

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` | MySQL 地址 / 端口 | `localhost` / `3306` |
| `DB_NAME` | 数据库名 | `hidden_white` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 / 密码 | `root` / `123456`（仅本地开发） |
| `DB_TIMEZONE` | 连接时区 | `+08:00` |
| `DB_POOL_SIZE` | 连接池大小 | `10` |
| `SERVER_PORT` | 服务端口 | `8080` |
| `JWT_SECRET` | JWT 签名密钥（生产必配） | 开发用默认值 |
| `JWT_EXPIRES_SECONDS` | token 有效期（秒） | `7200` |
| `COFFEE_DB_NAME` | 咖啡业务表所在库名 | `hidden_white` |

### 6.6 数据库初始化

```powershell
# 1. 初始化数据库（咖啡业务表 + 管理端系统表）
mvn -q compile exec:java -Dexec.mainClass=com.hiddenwhite.tools.DbInit

# 2. 启动（默认 8080）
mvnw spring-boot:run
# 或：.\start-dev.ps1（自动生成/复用本地 JWT 密钥）

# 3. 打包启动
mvnw -DskipTests package
java -jar target\hiddenwhite-server-0.2.0-SNAPSHOT.jar
```

种子管理员：`admin` / `admin123`（仅本地开发）。API 文档（knife4j）：<http://localhost:8080/doc.html>。

### 6.7 测试

`src/test` 下 4 个单元测试类（`PageQueryTest`、`PageResultTest`、`RTest`、`JwtServiceTest`，共 8 个用例），覆盖分页模型、统一返回结构与 JWT 服务；`./mvnw -o test` 可离线运行（本地 Maven 仓库已缓存依赖）。2026-10-13 合并后真实启动冒烟通过：health / products / store / benefits / captcha / admin 登录 → me / coffee / system.users / dashboard / monitor 全部 200，未带 token 401。

---

## 七、数据库设计

**策略**：单库 `hidden_white`，系统表（`sys_*` / `gen_*`）与点单业务表同库；建表脚本幂等（`IF NOT EXISTS` / `INSERT IGNORE` / 按主键更新），可重复执行；会话时区 `+08:00`。

| 脚本 | 内容 |
| --- | --- |
| `HiddenWhite-backend/database/init.sql` | **唯一的完整初始化脚本**，按执行顺序整合：8 张点单业务表（stores/products/product_options/members/users/member_benefits/orders/order_items）+ 演示数据；16 张系统表（用户/角色/菜单/部门/岗位/字典/参数/日志/代码生成）；系统种子数据（管理员账号/内置角色/菜单权限码/字典/参数）；咖啡运营模块菜单与权限；门店运营表结构（store_hours/store_daily_traffic/stores 营业状态字段）与菜单权限 |

**审计字段**：业务表默认包含 `create_by`、`update_by`、`create_time`、`update_time` 四个审计字段（日志表、字典表、关联中间表等可例外）。

**结构变更原则**（CODESTYLE）：结构变更直接更新 `init.sql`（保持幂等），禁止只改库不改脚本。

---

## 八、工程规范（CODESTYLE.md 摘要）

| 章节 | 要点 |
| --- | --- |
| 通用 | UTF-8 / LF；标识符英文，注释/文档/UI 文案中文；提交信息 `类型: 简述`（feat/fix/refactor/docs/style/chore） |
| 禁止提交 | `node_modules/`、`target/`、`dist/`、`unpackage/`、`.idea/`、日志、密钥与环境变量文件 |
| 点单端 | 2 空格缩进、单引号、无分号、行宽 100；ESLint flat config；请求统一走 `requestApi`；状态管理放 `store/` |
| 管理端前端 | 页面模板必须单根节点（`<div class="page">`）；接口类型在 `src/api` 声明；列表复用 `ListPage`、表单复用 `FormDialog`；新页面注册 `COMPONENT_MAP`；构建前 `vue-tsc --noEmit` |
| 后端 | 4 空格缩进；Controller 只做参数校验与转发，业务在 Service；配置一律环境变量注入；点单端价格/取餐号/首单减免后端计算 |
| 数据库 | 建表幂等；业务表含四个审计字段；结构变更写迁移脚本 |
| 文档同步 | 新增页面/接口/模块时同步更新 README 与 `PROJECT_MAP.md` |

---

## 九、开发指南

### 9.1 环境要求

- **JDK 17+**（已验证 Java 17.0.14）
- **Node.js 22+** 与 npm
- **MySQL 5.7+**（本地库 `hidden_white`，utf8mb4，时区 +08:00）
- **微信开发者工具**（小程序调试，导入 `HiddenWhite-front/unpackage/dist/build/mp-weixin`）
- 管理端浏览器 E2E 回归另需 Python + Playwright + Chromium（可选）

### 9.2 启动步骤

```bash
# ① 后端（在 HiddenWhite-backend/）
mvn -q compile exec:java -Dexec.mainClass=com.hiddenwhite.tools.DbInit   # 首次初始化数据库
mvnw spring-boot:run                                                      # 或 .\start-dev.ps1

# ② 管理端前端（在 HiddenWhite-admin/HiddenWhite-admin-front/）
npm ci
npm run dev          # http://localhost:5173，/api 代理到 8080

# ③ 点单端（在 HiddenWhite-front/）
npm install
npm run dev:mp-weixin   # 产出小程序包，用微信开发者工具导入
npm run dev:h5          # http://127.0.0.1:xxxx（H5 调试）
```

### 9.3 构建与测试

| 项目 | 命令 | 说明 |
| --- | --- | --- |
| 点单端 | `npm run build:h5` / `build:mp-weixin` | 产物在 `dist/`、`unpackage/` |
| 点单端 | `npx eslint .` | ESLint 检查（package.json 暂无 lint script） |
| 管理端前端 | `npm run test:unit` / `test:api` | Node 内置测试运行器 |
| 管理端前端 | `npm run build` | vue-tsc 类型检查 + vite build |
| 管理端前端 | `python scripts/session-regression.py` | Playwright 浏览器回归（需前后端运行中） |
| 统一后端 | `./mvnw test` | 8 个单元测试 |

### 9.4 常用信息

- 种子管理员：`admin` / `admin123`（仅本地开发，验证码按页面显示输入）
- API 文档：<http://localhost:8080/doc.html>（knife4j）
- 点单端后端地址：`HiddenWhite-front/config/backend.js` 的 `API_BASE_URL`（当前为开发机局域网 IP，换网络需改）
- 小程序 appid：`project.config.json` 中为 `wx90d8af9ad4e52689`

---

## 十、版本历史与项目现状

### 10.1 大事记

| 日期 | 事件 |
| --- | --- |
| 2026-10-13 | **后端合并**：管理端后端并入点单后端成为单一 Spring Boot 应用；管理端接口前缀改 `/api/admin/**`；去除 Redis 依赖与内存演示模式；点单端清理旧 admin 接口。验证：8 个单测通过 + 真实启动冒烟通过 |
| 2026-10-12 | 整库改名：`scaffold-master` → `HiddenWhite-admin`，包名/坐标/配置前缀统一为 `hiddenwhite`；三端工作区结构成型 |
| 2026-10-12 | 新增咖啡运营模块：管理端 6 个业务页面 + 后端 coffee 包，直连 `hidden_white` 库实现三端数据互通 |
| 2026-10-11 | 神经网络可视化升级：权限溯源图谱、菜单星图、岗位阶梯、登录热力图等差异化视图；全站中文化 |
| 2026-10-11 | UI 精致化：仪器化设计语汇、六种自绘图表、横带分层日志页、组织画布、用户看板、权限矩阵 |
| 2026-10-11 | MIT 开源发布：独立公开版本快照；JWT 密钥改为必填环境变量 |

### 10.2 已知事项与待办

1. ~~**小程序 appid 不一致**~~ ✅ 已修复（2026-10-16）：`manifest.json` 的 `mp-weixin.appid` 已统一为正式 appid `wx90d8af9ad4e52689`，与 `project.config.json` 一致。
2. ~~**点单端局域网地址硬编码**~~ ✅ 已重构（2026-10-15）：后端地址已改为 `config/backend-hosts.js` 候选列表 + 启动时 `/api/health` 自动探测机制，换网络只需修改候选 IP，无需改动业务代码。
3. **版本管理薄弱**：工作区基本无 Git 覆盖（仅 HiddenWhite-admin 有开源快照提交），依赖 `_archive/` 手工备份与 changelog 维系历史。
4. **工程清理计划（部分执行）**：`cross-env` 冗余依赖已移除；`_archive/` 合并备份待评估是否删除；点单端未引用资源与管理端孤立组件/死代码清理待执行；清理后需重新执行三端构建与测试验证。

---

## 十一、关键文件速查

| 想了解什么 | 看哪里 |
| --- | --- |
| 全工作区代码规范 | `CODESTYLE.md` |
| 点单端页面与交互 | `HiddenWhite-front/pages/`、`pages.json` |
| 点单端请求与后端地址 | `HiddenWhite-front/config/backend.js`、`HiddenWhite-front/config/backend-hosts.js` |
| 点单端购物车/订单状态 | `HiddenWhite-front/store/index.js` |
| 管理端权限与动态菜单 | `HiddenWhite-admin-front/src/composables/auth.ts` |
| 管理端页面清单与接口映射 | `HiddenWhite-admin/PROJECT_MAP.md` |
| 管理端设计语言 | `HiddenWhite-admin/DESIGN.md` |
| 后端接口分布与启动 | `HiddenWhite-backend/README.md` |
| 后端安全配置 | `HiddenWhite-backend/src/main/java/com/hiddenwhite/framework/config/SecurityConfig.java` |
| 数据库结构 | `HiddenWhite-backend/database/` |
| 变更历史与验证记录 | `HiddenWhite-admin/changelog.md` |
| 合并前代码存档 | `_archive/merge-backup-2026-10-13/` |

---

*本文档基于 2026-10-13 工作区实际代码与文档梳理生成，2026-10-16 更新点单端组件化重构与后端地址自动解析机制；如代码发生重大变更，请同步更新。*
