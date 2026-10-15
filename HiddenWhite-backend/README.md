# HiddenWhite 统一后端

隐白咖啡唯一后端：一个 Spring Boot 应用同时提供 **小程序 / H5 接口** 与 **管理端接口**。
（2026-10-13 由原 `HiddenWhite-backend` 与 `HiddenWhite-admin-backend` 合并而成，合并前代码备份于 `_archive/merge-backup-2026-10-13/`。）

## 快速开始

```powershell
# 1. 初始化数据库（咖啡业务表 + 管理端系统表，本地开发默认 root/123456@localhost/hidden_white）
mvn -q compile exec:java -Dexec.mainClass=com.hiddenwhite.tools.DbInit

# 2. 启动（默认端口 8080，需环境变量或默认 JWT 密钥）
mvnw spring-boot:run

# 3. 打包后启动
mvnw -DskipTests package
java -jar target\hiddenwhite-server-0.2.0-SNAPSHOT.jar
```

环境变量：`DB_HOST` / `DB_PORT` / `DB_NAME`（默认 hidden_white）/ `DB_USERNAME` / `DB_PASSWORD` / `SERVER_PORT` / `JWT_SECRET`（生产必配，至少 32 字节）。

- 种子管理员：`admin` / `admin123`（仅本地开发）
- API 文档（knife4j）：http://localhost:8080/doc.html

## 接口分布

| 端 | 前缀 | 鉴权 | 代码位置 |
|---|---|---|---|
| 小程序 / H5 | `/api/**`（auth、products、store、cart、orders、members） | 无需登录 | `com.hiddenwhite.shop` |
| 管理端 | `/api/admin/**`（auth、coffee、system、dashboard、monitor、tool/generator） | JWT 登录 | `com.hiddenwhite.admin` |
| 健康检查 | `/api/health` | 放行 | `com.hiddenwhite.admin.controller` |

注意：管理端登录为 `/api/admin/auth/login`（原独立管理端后端为 `/api/auth/login`），管理端前端 `HiddenWhite-admin-front` 的 baseURL 已同步改为 `/api/admin`。

## 包结构

```
com.hiddenwhite
├── HiddenWhiteApplication   # 唯一启动类
├── common/                  # R / 分页 / 异常与统一异常处理
├── framework/               # 安全(JWT+验证码)、操作日志、数据权限、TraceId
├── config/                  # Web MVC（CORS）
├── shop/                    # 小程序端：controller / service / repository / domain / dto
├── admin/                   # 管理端：system(权限/角色/菜单) coffee(咖啡业务) generator
└── tools/                   # DbInit 数据库初始化工具
```

## 技术栈

Spring Boot 3.5.14 · Java 17 · MyBatis-Plus 3.5.16（管理端）+ Spring JDBC（小程序端）· Spring Security + JJWT · knife4j 4.5 · MySQL（单库 `hidden_white`，两端共用）

设计要点：
- **无 Redis 依赖**：登录 token 与图形验证码存内存，应用重启后需重新登录（token 有效期 2 小时）。
- **仅 MySQL 模式**：原内存演示数据（MemoryDataStore）已移除，启动必须连接数据库。
- **统一异常处理**：小程序端返回 `ApiError` 结构，管理端返回 `R` 结构，按路径 `/api/admin` 自动区分。

## 数据库脚本

- `database/hidden_white_mysql.sql`：咖啡业务表（商品、订单、会员、门店等）
- `database/admin/V1__init_schema.sql` + `V2__seed_data.sql` + `V3__coffee_menus.sql`：管理端系统表与种子数据
