# 隐白咖啡 Hidden White

> 一套完整的精品咖啡点单全栈应用：微信小程序 / H5 点单端 + Web 管理端 + 统一后端 + MySQL 单库。

![Tech Stack](https://img.shields.io/badge/Spring%20Boot-3.5.14-green) ![Vue](https://img.shields.io/badge/Vue-3.5-blue) ![uni-app](https://img.shields.io/badge/uni--app-3.0-orange) ![TypeScript](https://img.shields.io/badge/TypeScript-5.7-blue) ![Java](https://img.shields.io/badge/Java-17-red) ![License](https://img.shields.io/badge/License-MIT-yellow)

---

## 功能特性

### 点单端（顾客）
- 菜单浏览：商品分类、规格选择（杯型 / 奶类 / 温度等）
- 购物袋：数量调整、价格实时计算、本地持久化
- 下单结算：取餐方式、订单备注、微信支付（演示模式）
- 取餐订单：取餐号、制作进度、预计完成时间
- 会员体系：会员等级、积分、权益卡
- 后端地址自动探测：换网络无需改代码，候选地址 + 健康检查自动匹配
- 视觉与动效：设计令牌驱动的黑金纸感界面、统一图标集、入场序列与状态微动效（详见[点单端视觉规范](#点单端视觉规范)）

### 管理端（运营）
- 咖啡运营：商品、规格选项、订单、会员、权益、门店管理
- 门店运营：营业状态、营业时间、客流调节
- 系统管理：用户、角色、菜单、部门、岗位、字典、参数
- 权限体系：菜单 / 按钮权限码、数据权限、角色权限矩阵
- 监控审计：操作日志、登录日志（含热力图与异常检测）
- 工具：代码生成器（三步流水线）
- 可视化：权限溯源图谱、菜单星图、组织架构画布、岗位阶梯、登录热力图

---

## 技术栈

| 层 | 技术 |
|----|------|
| 点单端 | uni-app 3.0 + Vue 3.4 + Vite 5 + SCSS（设计令牌 + 自有组件/图标体系） |
| 管理端前端 | Vue 3.5 + TypeScript 5.7 + Vite 6 + Vue Router 4 + GSAP |
| 后端 | Spring Boot 3.5.14 + Java 17 + Spring Security + JJWT |
| 数据访问 | MyBatis-Plus 3.5.16（管理端）+ Spring JDBC / JdbcTemplate（点单端） |
| 数据库 | MySQL 8.0+（单库 `hidden_white`，业务表与系统表同库） |
| API 文档 | knife4j 4.5（OpenAPI 3） |
| 构建 | Maven（后端）+ npm（前端） |

---

## 项目结构

```
Hidden White/
├── HiddenWhite-front/                  # 点单端：uni-app（微信小程序 / H5）
│   ├── pages/                          # 6 个业务页面（订单/会员/我的为壳页内 Tab 面板；另有积分兑换、积分明细）
│   ├── components/                     # 共享组件 + components/ui/ 统一组件库
│   │                                   #   Button / Card / Icon / Input / Modal / SlideConfirm / Stepper
│   ├── styles/                         # 设计令牌 tokens.scss + 通用元件与动效 common.scss
│   ├── config/                         # backend.js（统一请求）+ backend-hosts.js（候选地址）
│   │                                   #   + constants.js + icon-set.json（图标清单）
│   ├── scripts/                        # 构建辅助：icons/*.svg 母版 + generate-icons.cjs
│   ├── store/                          # 状态管理（购物车 / 订单 / 登录态）
│   ├── utils/                          # 工具模块（价格 / 校验 / 时间 / 图片 / 导航 / 存储 / 提示）
│   ├── tests/                          # 单元测试 + 设计一致性守卫
│   └── static/                         # 图片资源；icons/*.png 由 npm run assets:icons 生成
│
├── HiddenWhite-admin/                  # 管理端：Vue 3 + TS + Vite（端即项目根，无额外嵌套）
│   ├── src/views/                      # 25 个页面（含 redeem/ 积分运营两页）
│   ├── src/components/                 # ui(27) + business(19) + charts(6) + layout(6)
│   ├── src/composables/                # 15 个组合式函数
│   ├── src/api/                        # 统一 HTTP 客户端 + 业务接口模块
│   └── src/styles/                     # 七层样式体系（珍珠玻璃设计语言）
│
├── HiddenWhite-backend/                # 统一后端：Spring Boot 单应用（端口 8080）
│   ├── src/main/java/com/hiddenwhite/
│   │   ├── shop/                       #   小程序端接口 /api/**（浏览类免登录，订单需登录）
│   │   ├── admin/                      #   管理端接口 /api/admin/**（JWT）
│   │   │   ├── system/                 #     用户、角色、菜单、部门、岗位、字典、参数
│   │   │   ├── coffee/                 #     咖啡运营业务
│   │   │   ├── operation/              #     门店运营（营业状态/时间/客流）
│   │   │   └── generator/              #     代码生成器
│   │   ├── framework/                  #   安全、日志、数据权限、TraceId
│   │   ├── common/                     #   统一返回、分页、异常、图片处理
│   │   └── tools/                      #   DbInit：本地库初始化命令行工具
│   ├── database/init.sql               # 单库全量脚本（业务表 + 系统表 + 种子数据，幂等）
│   └── src/test/                       # 单元测试
│
├── docs/                               # 全部文档（入口见下表）
├── .github/workflows/ci.yml            # 三端质量闸门
├── CODESTYLE.md                        # 全工作区统一代码规范
├── .editorconfig                       # 编辑器层统一约束
└── .gitignore
```

### 文档索引

| 文档 | 内容 |
|---|---|
| [`docs/开发清单.md`](docs/开发清单.md) | **常态台账**：质量闸门、周/月/季节奏、UI 更新流程、功能候选池、调试手册 |
| [`docs/项目介绍.md`](docs/项目介绍.md) | 系统全貌：业务、架构、接口、数据表 |
| [`docs/开发进程规划.md`](docs/开发进程规划.md) | 阶段性评估快照（2026-10-18）；常态推进以 `开发清单.md` 为准 |
| [`docs/PROJECT_MAP.md`](docs/PROJECT_MAP.md) | 项目地图：页面 ↔ 接口 ↔ 表 ↔ 权限码 |
| [`docs/DESIGN.md`](docs/DESIGN.md) | 管理端设计规范（七层样式体系） |
| [`docs/清理与修复报告-2026-10-23.md`](docs/清理与修复报告-2026-10-23.md) | 最近一次清理/重构/修复的清单与验证方式 |
| [`docs/优化记录-2026-10-24.md`](docs/优化记录-2026-10-24.md) | 验证码服务端渲染、认证限流、依赖治理等优化与遗留事项 |
| [`docs/项目健康检查报告-2026-10-22.md`](docs/项目健康检查报告-2026-10-22.md) | 历史体检报告（阶段性快照，不代表当前状态） |

---

## 快速开始

### 环境要求

- **JDK 17+**
- **Node.js 22+** 与 npm
- **MySQL 5.7+**（本地库 `hidden_white`，utf8mb4，时区 +08:00）
- 微信开发者工具（小程序调试，可选）

### 1. 初始化数据库

```bash
cd HiddenWhite-backend
mvn -q compile exec:java -Dexec.mainClass=com.hiddenwhite.tools.DbInit
```

这会创建 `hidden_white` 库并执行 `database/init.sql`（全部业务表 + 系统表 + 种子数据 + 各模块菜单），脚本幂等可重复执行。也可直接手动执行 `mysql -uroot -p < database/init.sql`。

### 2. 启动后端

```bash
# 方式一：Maven 直接运行
mvnw spring-boot:run

# 方式二：打包后运行
mvnw -DskipTests package
java -jar target/hiddenwhite-server-0.2.0-SNAPSHOT.jar
```

- 默认端口：`8080`
- API 文档：http://localhost:8080/doc.html
- 种子管理员：`admin` / `admin123`（仅本地开发，登录需输入图形验证码）

### 3. 启动管理端前端

```bash
cd HiddenWhite-admin
npm ci
npm run dev
```

- 开发服务器：http://localhost:5173
- `/api` 代理到 `http://localhost:8080`

### 4. 启动点单端

```bash
cd HiddenWhite-front
npm install

# H5 调试
npm run dev:h5

# 微信小程序（产出后用微信开发者工具导入 dist/dev/mp-weixin）
npm run dev:mp-weixin
```

- H5 开发服务器默认 `127.0.0.1`
- 后端地址自动探测：按 `config/backend-hosts.js` 候选列表做健康检查，换网络只需修改该文件

> **不要用微信开发者工具的「生成骨架屏」**（工具 → 生成骨架屏）。它会把
> `pages/xxx/xxx.skeleton.wxml` / `.wxss` 写进**编译产物目录**（`dist/dev|build/mp-weixin`
> 或 HBuilderX 的 `unpackage/dist/dev/mp-weixin`），而下一次编译会清空该目录，
> 文件消失后工具仍按旧文件表编译，于是报
> `ENOENT ... index.skeleton.wxml` / `index.skeleton.wxss`。
> 这不是代码问题，处理办法：开发者工具「工具 → 清缓存 → 全部清除」（或关闭项目重开）后重新编译；
> 顽固时删掉产物目录再让 HBuilderX / `npm run dev:mp-weixin` 完整编一次。
> 需要骨架屏请写在源码里——首页商品列表、积分明细、兑换页都已有 `skeleton-*` 加载态。

---

## 配置说明

后端全部配置支持环境变量覆盖（不读取 `.env` 文件）：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_HOST` / `DB_PORT` | MySQL 地址 / 端口 | `localhost` / `3306` |
| `DB_NAME` | 数据库名 | `hidden_white` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 / 密码 | `root` / dev 下默认 `123456`；**prod 无默认值，未注入即启动失败** |
| `DB_POOL_SIZE` | HikariCP 连接池大小 | `10` |
| `SERVER_PORT` | 服务端口 | `8080` |
| `JWT_SECRET` | JWT 签名密钥（生产必配，至少 32 字节） | 开发默认值 |
| `JWT_EXPIRES_SECONDS` | Token 有效期（秒） | `7200` |
| `COFFEE_DB_NAME` | 咖啡业务表所在库 | `hidden_white` |
| `CORS_ALLOWED_ORIGINS` | 跨域白名单（逗号分隔），**仅 prod 生效**；留空 = 不放行任何跨域来源 | 空 |

点单端后端地址配置：编辑 `HiddenWhite-front/config/backend-hosts.js`，添加你的后端 IP 即可，应用启动时自动探测连通的地址。

---

## 接口分布

| 端 | 前缀 | 鉴权 | 说明 |
|----|------|------|------|
| 小程序 / H5 | `/api/auth/login`、`/api/auth/register` | 无需登录 | 登录 / 注册，**返回 `{ member, token }`** |
| 小程序 / H5 | `/api/products`、`/api/products/options`、`/api/store`、`/api/members/benefits` | 无需登录 | 浏览类公开接口 |
| 小程序 / H5 | `/api/orders/**`、`/api/members/{id}`、`/api/members/points-log`、`/api/cart/quote`、`/api/redeem/**`、`/api/auth/logout` | **需登录** | 会员归属以 token 判定，不接受客户端传入 memberId；报价要按本人历史订单判首单，故也需登录；积分兑换与明细拒绝 `guest` 账号 |
| 管理端 | `/api/admin/**` | JWT 登录 + 权限码 | auth、coffee、operation、redeem（积分运营）、system、dashboard、monitor、tool/generator |
| 健康检查 | `/api/health` | 放行 | 点单端用于后端地址自动探测 |

> 管理端登录地址：`/api/admin/auth/login`；点单端注销地址：`/api/auth/logout`

### 点单端鉴权约定（2026-10-17 安全加固）

- 登录 / 注册成功后，后端签发 JWT，客户端存入 `hidden-white-token`，此后所有请求自动带 `Authorization: Bearer <token>`。
- 会员 token 的 subject 为 `member:<会员ID>`，管理端 token 的 subject 是 `sys_user.username`。两者靠前缀天然隔离：点单端 token 打不进管理端接口，管理端 token 也进不了点单端接口。
- **订单归属一律以 token 判定**：`GET /api/orders`、`GET /api/orders/{id}`、`POST /api/orders`、`POST /api/orders/{id}/complete`、`GET /api/members/{id}` 均不接受客户端传入 memberId，传入也会被忽略。
- `SecurityConfig` 兜底为 `denyAll()`：新增路径若忘记配置，默认拒绝而非默认放行。
- token 与验证码存内存（`ExpiringStore`：带 TTL + 定时清理 + 容量上界），**应用重启后需重新登录**；单实例部署，不支持多副本。

---

## 设计要点

- **单库策略**：管理端系统表与点单业务表共用 `hidden_white` 库，管理端修改商品后点单端切页即见（`onShow` 自动重新拉取）
- **无 Redis**：登录 token 与图形验证码存内存（`ExpiringStore`，带过期清理与容量上界），应用重启后需重新登录
- **价格后端计算**：点单端价格、取餐号、首单减免等一律由后端计算，不信任客户端传入的价格字段
- **双异常结构**：小程序端返回 `ApiError`，管理端返回 `R`，按请求路径前缀自动区分
- **双数据访问风格**：小程序端用 `JdbcTemplate`，管理端用 MyBatis-Plus Mapper，共用一个数据源
- **两套设计语言，两种场景**：管理端是「珍珠玻璃」（信息密度优先），点单端是「暖白纸 + 黑金」（品牌体验优先），各自独立演进
- **点单端零第三方 UI 库**：视觉全部由设计令牌 + 自有组件库承载，不引入 Vant / uView 等框架
- **JWT 双主体**：管理端与点单端共用一套密钥，靠 subject 前缀（`member:`）隔离权限域
- **认证入口有频率约束**：两端登录、注册、验证码四个免登录入口按 IP 滑动窗口限流（默认 30 次/分钟，超限 429）；管理端图形验证码由服务端渲染为 SVG 图片下发，明文不出服务端

---

## 点单端视觉规范

品类型产品的界面靠一致性而不是装饰取胜。点单端的规则收敛在三个文件里（`styles/tokens.scss`、`styles/common.scss`、`config/icon-set.json`），**改样式先改这三个，不要在页面里就地写值**。

### 定调

编辑式奢感：暖白纸画布 + 暖调近黑 + 单一金强调色，宋体做大标题、无衬线做正文。
金只在两处出现——深色面上的 `$c-gold-on-dark`、浅色面上的 `$c-gold-text`；同屏不允许第二个强调色。

### 令牌（`styles/tokens.scss`）

| 类别 | 关键值 |
|---|---|
| 画布 / 表面 | `#fbfaf7` 暖白纸 · `#f2efe7` 下沉底 · `#ffffff` 卡片 |
| 文字 | `#191710` 标题 · `#26231b` 正文 · `#5b564a` 次要 · `#8a8477` 仅装饰 |
| 深色面 | `#171713` 主按钮 / 会员卡 · `#0b0a07` Banner / 取餐卡 · `#f5f0e3` 深底正文 |
| 金 | `#d4ad2f` 深底亮金 · `#8a6a14` 浅底金字（≥4.5:1） · `#b99737` 描边 · `#d9cba6` 纯装饰 |
| 字阶 | 22 / 24 / 26 / 28 / 30 / 32 / 36 / 44 / 56 / 62 / 64 / 132 rpx（750 设计宽） |
| 字体 | 展示字 Songti SC / Noto Serif SC；正文 PingFang SC 优先栈 |
| 间距 | 8 级梯度 8–80rpx，页面横向留白 `$gutter: 44rpx` |
| 圆角 | 内容卡 `22rpx` · 内部块/输入 `18rpx` · 小标签 `14rpx` · 胶囊 `999rpx` |
| 阴影 | 3 级（Y 偏移 2 / 10 / 24rpx）+ 深色卡专用 |
| 动效 | 按压 120ms · 状态色 180ms · 位移 300ms · 场景 600ms；缓动 `cubic-bezier(.16,1,.3,1)` |

通用元件（`.card` / `.card-dark` / `.tag` / `.stat-grid` / `.row-item` / `.skeleton` / `.is-press`）与入场序列（`reveal-1..8`）在 `styles/common.scss`，四个 Tab 面板与四个页面共用同一套。

### 图标（`components/ui/Icon.vue`）

不用 emoji，也不用 `<text>` 里的字形符号（U+2615 咖啡杯、U+2726 星标、U+203A 单右角引号、U+2197 右上箭头这类）——它们在 iOS / 安卓 / H5 上是三种粗细与比例，且无法统一着色。

图标走「SVG 母版 → 按色版预生成 PNG」管线：微信 `<image>` 不支持 `currentColor`，`filter` 在部分安卓 webview 无效，预生成是最稳的做法。

```bash
cd HiddenWhite-front
npm run assets:icons          # scripts/icons/*.svg + config/icon-set.json → static/icons/*.png
```

- 20 个 24 网格 / 1.6 线宽母版，6 种色版（`ink` `muted` `faint` `gold` `gold-on-dark` `decor`），共 37 张产物
- 新增图标：放母版 → 在 `icon-set.json` 登记色版 → 跑上面的命令（脚本会自动清理已下线的孤儿产物）
- `tests/design-system.test.js` 会拦住未登记的图标名、缺失的色版产物与残留的 emoji

### 动效红线

- 只动 `opacity` 与 `transform`；不要给含 `position: fixed` 后代的祖先加 `transform`（会成为包含块，底部固定栏跟着漂）
- 按压反馈统一用 `hover-class="is-press"`（小程序 `:active` 真机不可靠）；悬停与 `prefers-reduced-motion` 用 `#ifdef H5` 隔离
- 组件 `scoped` 样式里禁用标签名选择器与属性选择器（`[disabled]`）；需要给自定义组件做定位/动效时，包一层 `<view>` 而不要把 class 挂在组件标签上

---

## 构建与测试

| 项目 | 命令 | 说明 |
|------|------|------|
| 点单端 | `npm run build:h5` / `build:mp-weixin` | 正式产物在 `dist/build/`，调试产物在 `dist/dev/` |
| 点单端 | `npm test` | Node 内置测试运行器：工具函数 + 设计一致性守卫 |
| 点单端 | `npm run lint` | ESLint 检查 |
| 点单端 | `npm run assets:icons` | 由 SVG 母版重生成全部图标色版（改图标后必跑） |
| 管理端前端 | `npm run test:unit` | Node 内置测试运行器（API + utils） |
| 管理端前端 | `npm run build` | vue-tsc 类型检查 + vite build |
| 统一后端 | `./mvnw test` | 单元测试 |

---

## 许可证

[MIT](LICENSE) © 隐白咖啡 Hidden White

第三方依赖遵循各自许可协议（GSAP 遵循其标准免费许可）。
