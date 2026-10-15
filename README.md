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
| 点单端 | uni-app 3.0 + Vue 3.4 + Vite 5 + SCSS |
| 管理端前端 | Vue 3.5 + TypeScript 5.7 + Vite 6 + Vue Router 4 + GSAP |
| 后端 | Spring Boot 3.5.14 + Java 17 + Spring Security + JJWT |
| 数据访问 | MyBatis-Plus 3.5.16（管理端）+ Spring JDBC / JdbcTemplate（点单端） |
| 数据库 | MySQL 5.7+（单库 `hidden_white`，业务表与系统表同库） |
| API 文档 | knife4j 4.5（OpenAPI 3） |
| 构建 | Maven（后端）+ npm（前端） |

---

## 项目结构

```
Hidden White/
├── HiddenWhite-front/                  # 点单端：uni-app（微信小程序 / H5）
│   ├── pages/                          # 7 个业务页面
│   ├── components/                     # 共享组件 + components/ui/ 统一 UI 组件库
│   ├── store/                          # 状态管理（购物车 / 订单 / 登录态）
│   ├── config/                         # backend.js（统一请求）+ backend-hosts.js（候选地址）
│   ├── utils/                          # 10 个工具模块
│   └── static/                         # 图片资源
│
├── HiddenWhite-admin/                  # 管理端
│   └── HiddenWhite-admin-front/        #   管理端前端：Vue 3 + TS
│       ├── src/views/                  #   23 个页面
│       ├── src/components/             #   ui(26) + business(20) + charts(6) + layout(6)
│       ├── src/composables/            #   16 个组合式函数
│       ├── src/api/                    #   统一 HTTP 客户端 + 业务接口模块
│       └── src/styles/                 #   七层样式体系（珍珠玻璃设计语言）
│
├── HiddenWhite-backend/                # 统一后端：Spring Boot 单应用（端口 8080）
│   ├── src/main/java/com/hiddenwhite/
│   │   ├── shop/                       #   小程序端接口 /api/**（免登录）
│   │   ├── admin/                      #   管理端接口 /api/admin/**（JWT）
│   │   │   ├── system/                 #     用户、角色、菜单、部门、岗位、字典、参数
│   │   │   ├── coffee/                 #     咖啡运营业务
│   │   │   ├── operation/              #     门店运营（营业状态/时间/客流）
│   │   │   └── generator/              #     代码生成器
│   │   ├── framework/                  #   安全、日志、数据权限、TraceId
│   │   └── common/                     #   统一返回、分页、异常
│   └── database/                       # SQL 迁移脚本（业务表 + 系统表 + 种子数据）
│
├── CODESTYLE.md                        # 全工作区统一代码规范
├── .editorconfig                       # 编辑器层统一约束
└── .gitignore
```

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
cd HiddenWhite-admin/HiddenWhite-admin-front
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

# 微信小程序（产出后用微信开发者工具导入 unpackage/dist/build/mp-weixin）
npm run dev:mp-weixin
```

- H5 开发服务器默认 `127.0.0.1`
- 后端地址自动探测：按 `config/backend-hosts.js` 候选列表做健康检查，换网络只需修改该文件

---

## 配置说明

后端全部配置支持环境变量覆盖（不读取 `.env` 文件）：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_HOST` / `DB_PORT` | MySQL 地址 / 端口 | `localhost` / `3306` |
| `DB_NAME` | 数据库名 | `hidden_white` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 / 密码 | `root` / `123456` |
| `DB_POOL_SIZE` | HikariCP 连接池大小 | `10` |
| `SERVER_PORT` | 服务端口 | `8080` |
| `JWT_SECRET` | JWT 签名密钥（生产必配，至少 32 字节） | 开发默认值 |
| `JWT_EXPIRES_SECONDS` | Token 有效期（秒） | `7200` |
| `COFFEE_DB_NAME` | 咖啡业务表所在库 | `hidden_white` |

点单端后端地址配置：编辑 `HiddenWhite-front/config/backend-hosts.js`，添加你的后端 IP 即可，应用启动时自动探测连通的地址。

---

## 接口分布

| 端 | 前缀 | 鉴权 | 说明 |
|----|------|------|------|
| 小程序 / H5 | `/api/**` | 无需登录 | auth、products、store、cart、orders、members |
| 管理端 | `/api/admin/**` | JWT 登录 | auth、coffee、operation、system、dashboard、monitor、tool/generator |
| 健康检查 | `/api/health` | 放行 | 点单端用于后端地址自动探测 |

> 管理端登录地址：`/api/admin/auth/login`

---

## 设计要点

- **单库策略**：管理端系统表与点单业务表共用 `hidden_white` 库，管理端修改商品后点单端切页即见（`onShow` 自动重新拉取）
- **无 Redis**：登录 token 与图形验证码存内存（`ConcurrentHashMap`），应用重启后需重新登录
- **价格后端计算**：点单端价格、取餐号、首单减免等一律由后端计算，不信任客户端传入的价格字段
- **双异常结构**：小程序端返回 `ApiError`，管理端返回 `R`，按请求路径前缀自动区分
- **双数据访问风格**：小程序端用 `JdbcTemplate`，管理端用 MyBatis-Plus Mapper，共用一个数据源
- **珍珠玻璃设计语言**：管理端自有组件库，不依赖 Element Plus 等第三方 UI 框架

---

## 构建与测试

| 项目 | 命令 | 说明 |
|------|------|------|
| 点单端 | `npm run build:h5` / `build:mp-weixin` | 产物在 `dist/`、`unpackage/` |
| 点单端 | `npx eslint .` | ESLint 检查 |
| 管理端前端 | `npm run test:unit` | Node 内置测试运行器（API + utils） |
| 管理端前端 | `npm run build` | vue-tsc 类型检查 + vite build |
| 统一后端 | `./mvnw test` | 单元测试 |

---

## 许可证

[MIT](LICENSE) © 隐白咖啡 Hidden White

第三方依赖遵循各自许可协议（GSAP 遵循其标准免费许可）。
