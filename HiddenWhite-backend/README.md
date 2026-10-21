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
| 上传图片 | `/uploads/**` | 放行 | 静态资源（供点单端加载商品图） |

注意：管理端登录为 `/api/admin/auth/login`（原独立管理端后端为 `/api/auth/login`），管理端前端 `HiddenWhite-admin-front` 的 baseURL 已同步改为 `/api/admin`。

## 商品图片上传

管理端商品表单的「列表图 / 详情图」支持从本地选图上传，图片按各自目标比例由服务端处理。

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/admin/coffee/images/upload` | POST | multipart 上传，`slot` 取 `list` / `detail` |
| `/api/admin/coffee/images/spec` | GET | 各用途目标宽高与缩放方式 |
| `/uploads/**` | GET | 处理后的图片（免登录） |

- **一次编码**：解码 → 缩放 → 编码只走一遍，不做「先压缩再压缩」，避免代际画质损失；
  大图缩小时采用渐进式降采样（二分逼近）保留细节。
- **目标宽高分别配置**（`application.yml`）：

  ```yaml
  hiddenwhite:
    image:
      upload-dir: ./data/uploads   # 上传目录（生产建议指向独立数据盘）
      max-bytes: 10485760          # 单张上限 10MB
      list:                        # 列表图
        width: 600
        height: 400
        mode: cover                # cover 居中裁剪 / contain 等比缩放
      detail:                      # 详情图
        width: 1200
        height: 800
        mode: cover
  ```

- 支持 JPG / PNG / WebP，输出：无透明通道转 JPEG（含透明则保留 PNG）。
- 落盘路径按 `yyyy/MM/dd` 分目录，文件名为内容哈希前 16 位，同图重复上传不产生冗余文件。
- 路径存相对路径（如 `/uploads/2026/10/21/xxx.jpg`），点单端在 `utils/image.js` 统一补全域名。

## 包结构

```
com.hiddenwhite
├── HiddenWhiteApplication   # 唯一启动类
├── common/                  # R / 分页 / 异常与统一异常处理 / image(图片处理)
├── framework/               # 安全(JWT+验证码)、操作日志、数据权限、TraceId
├── config/                  # Web MVC（CORS + 上传资源映射）
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

- `database/init.sql`：**唯一的完整初始化脚本**，包含全部业务表 + 系统表 + 种子数据 + 各模块菜单，幂等可重复运行
- 执行方式：`mysql -uroot -p < database/init.sql`，或使用 `DbInit` 工具自动执行
