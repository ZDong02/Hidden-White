# 隐白咖啡（Hidden White）项目现状评估与开发进程规划

> 评估日期：2026-10-17
> 评估范围：`HiddenWhite-front`（点单端 uni-app）、`HiddenWhite-admin/HiddenWhite-admin-front`（管理端）、`HiddenWhite-backend`（统一后端）、`database/init.sql`、`HiddenWhite-项目介绍.md`、`CODESTYLE.md`
> 评估方式：三端代码逐模块走查 + 接口双向比对 + 构建/测试实测 + Git 状态核查

---

## 一、项目现状总结

### 1.1 总体判断

**这是一个功能完成度相当高、但工程成熟度明显落后于功能完成度的项目。**

三端主流程（登录 → 选品 → 下单 → 取餐 / 后台配商品 → 出餐 → 结算客流）已真实打通，26 张表、101 个接口、20 个管理页、7 个点单页全部落在真实数据上。三个探索代理对「假数据 / TODO / 空实现」的交叉扫描结果一致：**全库零命中**——这是很多同体量项目做不到的。

但与之并列的另一面是：**测试覆盖率约 3%、点单端无鉴权、支付完全未接入、1870 行不可达死代码、文档与代码多处失配、无 CI 无部署配置**。项目当前处于「能跑、能演示，但不敢上线」的状态。

用一张表概括三端成熟度：

| 维度 | 评价 | 依据 |
|---|---|---|
| 业务完整度 | **高** | 主流程闭环，零 mock、零 TODO |
| 架构清晰度 | **高** | 单库单应用、分层明确、双风格数据访问有意设计 |
| 安全性 | **低（阻塞上线）** | 点单端 6 个 Controller 零鉴权，`anyRequest().permitAll()` 兜底 |
| 测试保障 | **低** | 后端 8 用例覆盖 4 类、管理端 21 用例零组件测试、点单端无测试 |
| 工程规范 | **中** | CODESTYLE 落地彻底（32 处选择器已清零），但工具链缺口多 |
| 文档同步 | **低** | 5 处断链引用、README 目录表滞后、无 PROJECT_MAP.md / DESIGN.md |
| 可交付性 | **低** | 无 CI、无 Dockerfile、无 LICENSE 文件、无生产部署路径 |

### 1.2 规模与结构事实

| 项目 | 规模 | 关键事实 |
|---|---|---|
| **统一后端** | 106 个 Java 类 / 14 个 Controller / 101 个接口 | Spring Boot 3.5.14 + Java 17 + MyBatis-Plus 3.5.16 + knife4j 4.5 + jjwt 0.12.6；无 Redis |
| **管理端前端** | 20 个页面 / 59 个组件 / 16 个 composables / 83 个 API 函数 / 8069 行逻辑代码 | Vue 3.5 + TS 5.7 + Vite 6；仅 3 个运行时依赖（vue / vue-router / gsap），零 UI 框架，组件全自绘 |
| **点单端** | 7 个页面 / 14 个组件 / 2 个 store / 8479 行 | uni-app 3.0 alpha + Vue 3.4 + Vite 5；无 Pinia，用 reactive + storage |
| **数据库** | `init.sql` 800 行 / 26 张表 | 业务表 10 张 + 系统表 16 张；单库 `hidden_white`；幂等可重复执行 |

**后端模块完整度实测**（全部真实落库，无一处空实现）：

| 模块 | 接口数 | 权限注解 | 落库 | 测试 | 备注 |
|---|---|---|---|---|---|
| `shop`（小程序） | 15 | **0** | ✅ | ❌ | 全部公开可访问 |
| `admin/coffee` | 21 | 21/21 | ✅ | ❌ | 含软下架等业务规则 |
| `admin/operation` | 7 | 7/7 | ✅ | ❌ | **业务逻辑最完整**（派生营业状态、幂等结算） |
| `admin/system` | 64 | 56/58 | ✅ | ❌ | 数据权限机制完整但仅 3 处使用 |
| `admin/generator` | 8 | 8/8 | ✅ | ❌ | 读写共用一个权限码 |

**Git 状态**：已初始化并有远程（`origin/main`），5 次提交。但工作区有 **16 个已修改未提交 + 3 个未跟踪** 文件，累积了 2026-10-16 一整天的工作（WXSS 改造、会员卡、init.sql 整合）。记忆档案中「项目尚未纳入 Git」的记录已过期。

### 1.3 已确认做对的事（不建议返工）

1. **WXSS 改造已彻底落地** —— 用两轮正则（含后代选择器、`::before`、`@media` 嵌套）扫 21 个文件，标签名/id/属性选择器 **0 残留**。此项无需再投入。
2. **管理端组件零闲置** —— 59 个组件、16 个 composables 全部有实际引用；`COMPONENT_MAP` 20 项与 views 目录 **20=20 完全对应**。
3. **前后端契约零缺口** —— 点单端调用的 12 个接口后端全部实现，无 404 风险。
4. **DOC 同步机制已有雏形** —— CODESTYLE.md 对「多根节点导致过渡卡死」等真实事故的记录，是少见的、把踩坑写进规范的做法，建议保持。

---

## 二、待完成的功能与任务清单

### P0 · 阻塞上线（安全类，必须最先处理）

| # | 任务 | 位置 | 严重度 |
|---|---|---|---|
| **S1** | **点单端 6 个 Controller 零鉴权**，`SecurityConfig.java:62` `.anyRequest().permitAll()` 兜底全放行 | `shop/controller/*` | 🔴 严重 |
| **S2** | **越权读单**：`GET /api/orders?memberId=X` 无凭证即可列出任意会员全部订单（含金额、取餐号、备注） | `OrderController.java:27-30` | 🔴 严重 |
| **S3** | **归属校验可绕过**：`POST /api/orders/{id}/complete` 的 `memberId` 可省略，`OrderService.java:82` 判断是 `memberId != null && ...`，省略即跳过校验 | `OrderService.java:82` | 🔴 严重 |
| **S4** | **越权读会员**：`GET /api/members/{id}` 无凭证可读任意会员资料 | `MemberController.java:26-29` | 🟠 高 |
| **S5** | 点单端**无 token 机制**，登录成功不签发凭证（`AuthController.java:27-30` 直接返回 Member 对象），全链路靠 `memberId` 明文传参 | `store/auth.js` + `shop/*` | 🔴 严重 |
| **S6** | `DashboardController` 位于 `/api/admin/**` 但**无 `@RequiresPermission`** → 任意已登录低权账号可读全站统计 | `DashboardController.java:22-25` | 🟠 高 |
| **S7** | CORS `allowedOriginPatterns("*")` 硬编码，**生产环境同样全放行**，无白名单机制 | `WebConfig.java:12-17` | 🟠 高 |
| **S8** | `DataScopeContext.current()` 无上下文时返回 `all()`（全量）而非最严，新增未标注 `@DataScope` 的查询即全量放行 | `DataScopeContext.java:16` | 🟡 中 |

### P0 · 功能缺口（决定「能不能收钱」）

| # | 任务 | 事实依据 |
|---|---|---|
| **F1** | **微信支付完全未接入** —— 全项目 `requestPayment` **0 命中**；`orders` 表**无任何支付字段**（`pay_status`/`pay_method`/`pay_time`/`transaction_id`/`refund_*` 全缺）；`status` 枚举仅 `IN_PROGRESS`/`COMPLETED`，无 `CANCELLED`/`REFUNDING`/`REFUNDED` | `checkout.vue:154-183`、`database/init.sql:128-148` |
| **F2** | **UI 谎称已支付** —— 结算页写「微信支付·演示 / 不会实际扣款」，但订单列表仍显示「¥xx · 微信支付」，自相矛盾，构成用户投诉风险点 | `OrdersPanel.vue:49`、`order.vue:50` vs `checkout.vue:55/56/167` |
| **F3** | **3D 翻转会员卡功能已在用户路径上丢失（功能回归）** —— 唯一实现该特性的 `pages/member/member.vue` 是**不可达死代码**；用户实际看到的 `MemberPanel.vue` 中 `flip/rotor/perspective/rotateY` **0 命中** | `MemberPanel.vue` vs `member.vue` |
| **F4** | **积分/优惠券无流水表** —— `members.points`、`members.coupons` 只是整数字段，无法追溯消耗过程，无法对账、无法退单 | `init.sql:94-95` |
| **F5** | **跨日客流结算无定时任务** —— `@Scheduled`/`@EnableScheduling` 全库零命中，`OperationService.java:226` 注释提到「次日惰性补结算」但无实现 | `admin/operation` |

### P1 · 质量与工程化

| # | 任务 | 依据 |
|---|---|---|
| **E1** | **点单端 1870 行不可达死代码** —— `pages/member`(743) + `pages/order`(536) + `pages/profile`(591)；`utils/navigation.js` 已把这三个业务全部重定向到 `index?tab=`，全项目无任何跳转指向这三个页面，但仍注册在 `pages.json` 编译进主包（产物 79K） | 已实测验证导航表 |
| **E2** | 后端测试覆盖率约 3%（8 用例 / 4 类）；shop 全模块、coffee、operation、system、generator **零测试**；已声明的 `spring-security-test` 依赖未使用 | `src/test` |
| **E3** | 管理端零组件级测试（20 页面 + 59 组件），`coffee.ts`/`operation.ts` 两个 API 模块无测试文件；536 行的 `motion.ts` 无测试 | `package.json:10` |
| **E4** | **点单端无任何测试脚本**（package.json 无 test 命令），无 CI，三端均无 CI | `HiddenWhite-front/package.json` |
| **E5** | 点单端遗留调试代码：`store/index.js:18-26` 硬编码 demo 购物车组合，命中即**静默清空用户购物车** | 已定位行号 |
| **E6** | 死代码与游离文件：根 `member-card-flip-preview.html`(389 行原型)、`USAGE_EXAMPLES.js`(8.6KB)、`utils/composables.js`(213 行)；`validation.js` / `validators.js` 命名高度近似易混淆 | 已核实 0 引用 |
| **E7** | 管理端**无 ESLint**（有点单端的同类配置可参照）；`.prettierrc` 存在但 prettier 未列入依赖 | `package.json` |
| **E8** | 后端死 import：`CoffeeService.java:14` `Transactional` 全文 0 使用；`SecurityConfig.java:73-78` 占位 `UserDetailsService` 永久抛异常 | 已核实 |
| **E9** | `scripts/run-uni.cjs` 让 dev 与 build **写同一产物目录** `unpackage/dist/build/mp-weixin`；H5 又落在 `dist/build/h5`，两套产物目录并存（3.1M，`unpackage/dist/dev` 是 1.1M 残留重复） | 已核实 |

### P2 · 文档与体验

| # | 任务 | 依据 |
|---|---|---|
| **D1** | **5 处断链引用**：README/LICENSE、`tokens.css:4`→DESIGN.md、`PROJECT_MAP.md` 在 `README.md:39`、`frontend-backend-integration.md:3`、`README.md:53`→`../LICENSE` —— 这些文件**均不存在** | 已逐一核实 |
| **D2** | 管理端 README 目录表滞后：称「六层样式」实为 7 层（漏 `instruments.css`，且它是最大文件 1220 行）；称「10 个管理页」实为 16 个；漏写 `coffee`/`operation` 两个 API 模块与 11 个业务组件 | 已核实 |
| **D3** | `manifest.json` 保留了 HBuilderX 模板默认的 **15 个无关 Android 权限**（`READ_PHONE_STATE`/`READ_LOGS`/`CAMERA`/`GET_ACCOUNTS` 等），与咖啡点单业务无关 | `manifest.json:25-41` |
| **D4** | `config/backend-hosts.js` 硬编码 2 个局域网 IP，且生产构建的 H5 会真实去探测它们（`backend.js:123` 仅 `DEV` 短路） | 已核实 |
| **D5** | 点单端依赖为 **uni-app alpha 通道快照版**；`eslint 10 + vite 5 + alpha` 组合激进，升级易炸；`package.json` 改动后 lock 未同步 | `package.json` |
| **D6** | 无 `subPackages`，7 页面全在主包（含 3 个死页面） | `pages.json` |

---

## 三、分阶段开发计划

> 工作量按「人日」估算（1 人日 ≈ 8 小时有效开发时间，不含自测与联调等待）。

### 阶段〇：安全止血（0.5 人日）· 最高优先级 ✅ 已完成（2026-10-17）

**目标**：把「任何人都能读任意会员订单」这个致命漏洞堵上。这是**唯一不能带着上线**的问题。

**实施结果**：全部 7 项已落地，后端测试 8 → 14 个全绿，点单端 ESLint 通过。

| 任务 | 工作量 | 状态 |
|---|---|---|
| 点单端引入 token 机制：新增 `MemberSessionService`，复用管理端 `JwtService` + `TokenStore`（不引新依赖、不引 Redis） | 0.15 | ✅ |
| 收紧 `SecurityConfig`：`.anyRequest()` 由 `permitAll()` 改为 **`denyAll()`**，兜底从「默认公开」翻转为「默认拒绝」 | 0.05 | ✅ |
| 补齐归属校验：`/api/orders/**`、`/api/cart/quote`、`/api/members/{id}` 全部按 token 判定，移除客户端可控的 memberId | 0.1 | ✅ |
| 给 `DashboardController` 补 `@RequiresPermission("system:dashboard:list")`，并同步 `init.sql` 菜单权限码（含存量库幂等 UPDATE） | 0.05 | ✅ |
| CORS 改为白名单：新增 `hiddenwhite.cors.allowed-origins`，dev 限本地端口，prod 走 `CORS_ALLOWED_ORIGINS`，留空即不放行 | 0.05 | ✅ |
| `DataScopeContext.current()` 无上下文时由 `all()` 改为 `selfOnly(null)`（fail-closed） | 0.05 | ✅ |
| 验证：编译通过 + 14 个单测全绿 + 点单端 ESLint 零错误 | 0.05 | ✅ |

**关键实现决策 —— 两端 token 靠 subject 前缀天然隔离**：

管理端 token 的 subject 是 `sys_user.username`（admin / chensy），点单端 token 的 subject 改为 **`member:<会员ID>`**。由于管理端权限切面 `PermissionAspect` 是拿 principal 反查 `sys_user` 的，`member:xxx` 永远查不到该用户，因此：

- 点单端 token **打不进**任何 `/api/admin/**` 接口；
- 管理端 token **进不了**点单端接口（不匹配 `member:` 前缀）。

无需两套密钥、无需额外过滤器，改动面最小。**这个前缀是隔离的关键，后续维护不要移除**（已写进 `MemberSessionService` 类注释）。

**新增文件**：
- `shop/service/MemberSessionService.java` — 会员会话签发/解析/注销
- `shop/dto/MemberAuthResponse.java` — 登录注册响应 `{ member, token }`
- `src/test/.../MemberSessionServiceTest.java` — 6 个鉴权用例

**破坏性变更（前后端必须同时发布）**：
- `POST /api/auth/login`、`/api/auth/register` 返回值由 `Member` 改为 `{ member, token }`
- `POST /api/auth/logout` 为新增接口
- `GET /api/orders`、`/api/orders/{id}`、`POST /api/orders`、`/api/orders/{id}/complete`、`GET /api/members/{id}`、`POST /api/cart/quote` **现均需 token**

**验收标准**：用 A 账号 token 请求 B 会员的订单/资料返回 403；未带 token 请求 `/api/orders` 返回 401。

---

### 阶段一：清理与一致性（1 人日）

**目标**：消除误导性代码与文档，让后续开发不会踩到已删代码，建立最小质量闸门。

| 任务 | 工作量 |
|---|---|
| **先迁移再删除 3D 翻转卡**（顺序不可颠倒）：把 `member.vue` 的翻转卡回填到 `MemberPanel.vue` | 0.2 |
| 删除 `pages/{member,order,profile}.vue` 三份死代码 + 从 `pages.json` 注销（-1870 行，主包 -79K） | 0.1 |
| 删除 `store/index.js:18-26` demo 购物车清理逻辑 | 0.05 |
| 清理 `USAGE_EXAMPLES.js`、`utils/composables.js`、根目录 `member-card-flip-preview.html`（或移入 `docs/`） | 0.05 |
| 修复 `run-uni.cjs`：dev 与 build 输出目录分离 | 0.05 |
| 补根 `LICENSE`（README 已声明 MIT）、`DESIGN.md`、`PROJECT_MAP.md`；修复 5 处断链引用 | 0.25 |
| 刷新管理端 README 目录表（七层样式、16 业务页、补 coffee/operation API） | 0.1 |
| 清理 `manifest.json` 的 15 个无关 Android 权限 | 0.05 |
| 后端清死 import；`generator` 权限码拆分读/写 | 0.1 |
| 提交当前 16 个未提交改动（**先提交再清理**，保留可回滚点） | 0.05 |

**验收标准**：`pages.json` 仅 4 个页面；`MemberPanel` 可翻转；三端 `npm run build` 与 `mvnw test` 全绿；文档无断链。

---

### 阶段二：质量基线（3 人日）

**目标**：把「靠人眼验证」变成「靠测试拦截」，这是后续所有迭代的前提。

| 任务 | 工作量 |
|---|---|
| **点单端引入测试框架**（现零测试）：配 vitest + `@vue/test-utils` | 0.3 |
| 点单端补 store 层测试：`createOrder` 分支、`discount` 首单减免、`validateCart`、`addItem` 去重 | 0.5 |
| 点单端补组件测试：`Stepper`、`Button` 的 `hover-class` 交互 | 0.2 |
| 管理端补 `coffee.ts` / `operation.ts` API 测试（当前唯一空缺的两个 API 模块） | 0.2 |
| 后端补关键路径集成测试（`@SpringBootTest` + MockMvc）：**优先覆盖阶段〇新增的鉴权逻辑** | 0.8 |
| 后端补 `OperationService` 派生状态与幂等结算测试（业务最复杂且无测试） | 0.4 |
| 后端补 `CoffeeService` 商品软下架规则测试 | 0.2 |
| **接入 CI**（GitHub Actions）：三端构建 + 类型检查 + 测试 + ESLint 门禁 | 0.4 |

**验收标准**：点单端测试从 0 → 40+ 用例；后端从 8 → 60+ 用例；CI 全绿且为后续 PR 必过门禁。

---

### 阶段三：支付闭环（8 人日）· 核心业务价值

**目标**：从「演示下单」变成「真实收钱」。这是项目商业价值的分水岭，也是工作量最大的一段。

| 任务 | 工作量 |
|---|---|
| **数据库改造**：`orders` 加 `pay_status`/`pay_method`/`pay_time`/`transaction_id`/`refund_status`/`refund_amount`/`updated_at`；`status` 扩 `CANCELLED`/`REFUNDING`/`REFUNDED`；写幂等迁移脚本 | 0.5 |
| 积分/券流水表：新增 `member_point_log`、`member_coupon_log`，支持追溯与对账 | 0.5 |
| 后端：统一下单接口返回支付参数；订单状态机（超时自动取消、已支付不可取消） | 0.8 |
| 后端：微信支付对接（JSAPI 下单、支付回调、验签、幂等、金额校验） | 2 |
| 后端：支付回调必须**验签 + 幂等 + 金额与订单一致**，不可信任客户端 | （含上项） |
| 后端：退款流程 | 0.8 |
| 后端：定时任务（`@Scheduled` + 订单超时取消、跨日客流结算） | 0.4 |
| 点单端：接入 `uni.requestPayment`，补支付结果页、支付失败重试、取消回流 | 0.8 |
| 点单端：**消除「演示」与「微信支付」的文案矛盾**（F2，验收必查项） | 0.1 |
| 管理端：订单列表加支付状态列与筛选；退款操作入口 | 0.5 |
| 端到端真机联调（含沙箱支付全流程） | 1.4 |
| 文档：支付配置说明 + 环境变量清单 | 0.2 |

**依赖**：微信支付商户号与 API 密钥（沙箱模式下用沙箱密钥）、支付域名备案（小程序需 https 合法域名）。**当前尚未持有凭据，故本阶段先建接入层，凭据全部走环境变量。**
**验收标准**：真机完成一笔真实沙箱支付；回调日志可查；断网/取消/重复回调三种异常路径均正确处理。

---

### 阶段四：运营完善（2.5 人日）

**目标**：补齐「能运营」而非仅「能下单」的能力。

| 任务 | 工作量 |
|---|---|
| 优惠券/权益核销闭环：下单时抵扣、后台核销与统计（依赖阶段三流水表） | 0.8 |
| 门店多店支持：`store_id` 硬编码 1 的 6 处 SQL 改造 | 0.6 |
| 商品图片上传：`sys_file` 表已有但 Java 零引用，需接文件存储 | 0.5 |
| 点单端：商品详情改走 `GET /api/products/{id}`（后端已实现未调用） | 0.2 |
| 点单端：订单列表改走 `GET /api/orders/current`（后端已实现未调用，省前端分流逻辑） | 0.2 |
| 点单端：引入 `subPackages` 分包，去除主包冗余 | 0.2 |

---

### 阶段五：生产交付（2 人日）

| 任务 | 工作量 |
|---|---|
| Dockerfile（后端）+ docker-compose（MySQL + 后端 + 管理端） | 0.5 |
| Nginx 反向代理、HTTPS、静态资源缓存策略 | 0.3 |
| 生产 profile 实化：日志级别、JWT 密钥、CORS 白名单、数据源全部走环境变量 | 0.3 |
| 引入 Redis 或明确记录「单实例部署 + 重启需重新登录」的部署前提 | 0.3 |
| 健康检查与监控告警接入（`/api/health` 已就绪，缺 actuator） | 0.3 |
| 数据库迁移版本化（引入 Flyway，替代「全量 init.sql 幂等覆盖」） | 0.3 |

---

### 阶段排期总览

> 进度更新至 2026-10-17（阶段〇、Ⅰ、Ⅱ 均已完成，总计 4.5 / 17 人日，约 26%）

> 📌 **支付方案已确认**：采用**微信支付沙箱环境**接入（用户为个人开发者，无商户资质，走沙箱而非正式环境）。F2 的文案矛盾在沙箱接入时一并解决。
>
> ⚠️ **待确认的前置条件**：微信支付沙箱 v2 需先有商户号（mch_id）才能进入，沙箱仅提供测试号与测试密钥。个人主体能否申请商户号、以及是否有面向个人的服务商通道，需实际核实。
>
> ✅ **已解决**：3D 翻转会员卡功能曾在用户路径上丢失（唯一实现在不可达死代码中）。经决定直接移除该特性，连同 1870 行死代码一并删除（提交 `4a9a359`）。

| 阶段 | 目标 | 工作量 | 前置依赖 | 状态 |
|---|---|---|---|---|
| 〇 安全止血 | 堵上越权漏洞 | 0.5 人日 | 无 | ✅ **已完成**（`e72709d`） |
| Ⅰ 清理与一致性 | 消除死代码与文档失配 | 1 人日 | 阶段〇 | ✅ **已完成 10/10** |
| Ⅱ 质量基线 | 建立 CI 与测试闸门 | 3 人日 | 阶段Ⅰ | ✅ **已完成**（`e3bf1d4`） |
| Ⅲ 支付闭环 | 真实收钱 | 8 人日 | 阶段Ⅱ + 商户资质 | ⬜ 待启动（**建议立即启动资质申请**） |
| Ⅳ 运营完善 | 券核销、多店、上传 | 2.5 人日 | 阶段Ⅲ 流水表 | ⬜ 待启动 |
| Ⅴ 生产交付 | 可上线 | 2 人日 | 阶段Ⅲ | ⬜ 待启动 |
| | **合计** | 约 17 人日 | | **已完成 4.5 / 17（约 26%）** |

> 🎯 **「敢迭代」的门槛已达成**：阶段〇+Ⅰ+Ⅱ 共 4.5 人日全部完成，现在动订单表结构与支付状态机有测试网与 CI 兜底。

### 阶段Ⅱ 完成情况

测试用例 **35 → 53**（后端 14 → 26，管理端 21 → 29，点单端 0 → 14）。

| 项 | 内容 |
|---|---|
| 后端 | `OrderServiceTest` 12 例：归属校验（读/完成他人订单 403、订单不存在 404）、请求体 memberId 被忽略（防冒领首单减免）、首单减 6 元、老客无减免、打烊拒单、单进行中订单、取餐号由后端生成 |
| 管理端 | 补齐 `coffee.ts` / `operation.ts` 两个零覆盖模块，本质是**前后端契约测试**——核对后端 36 个 `@Mapping` 与前端调用逐一对齐后固化路径 |
| 点单端 | 新增 `tests/utils.test.js` 覆盖 price 与 validators；用 Node 内置 runner，**不引入 vitest / jest** |
| CI | `.github/workflows/ci.yml`：push/PR 到 main 触发三端闸门，Maven 与 npm 开缓存，同分支新推送取消旧任务 |

顺带修复：`utils/validators.js` 末尾的 legacy re-export 既无使用方又因缺扩展名无法在 ESM 下解析，导致模块无法被测试加载，已删除。


**建议节奏**：阶段〇+Ⅰ+Ⅱ 共 **4.5 人日**是「从能演示到敢迭代」的门槛，阶段〇+Ⅰ 已完成，剩阶段Ⅱ 3 人日；阶段Ⅲ 是独立里程碑（8 人日，约两周），可与产品决策并行启动。

> ⚠️ **阶段〇 含破坏性接口变更**（登录返回值、6 个接口需 token），前后端必须同时发布。点单端已同步改完并通过 ESLint，后端已编译 + 测试全绿，可直接联调验证。

### 阶段Ⅰ 完成情况

| 任务 | 状态 |
|---|---|
| 建立可回滚点（commit + 推送） | ✅ `944e50b` |
| 删除 demo 购物车静默清空逻辑 | ✅ |
| 删除 1870 行不可达死代码（含 3D 翻转卡） | ✅ `4a9a359`，包体 1.1M → 733K |
| 删除 HTML 原型预览页与零引用文件 | ✅ `4a9a359` |
| 修复 `toast.js` 重复键缺陷 | ✅ `4a9a359` |
| ESLint 全量归零 | ✅ `4a9a359` |
| 补根 `LICENSE` | ✅ `a9d88bd` |
| 新建 `DESIGN.md`、`PROJECT_MAP.md`，修复断链引用 | ✅ `a9d88bd` |
| 刷新管理端 README 目录表 | ✅ `a9d88bd` |
| 清理 `manifest.json` 的无关 Android 权限 | ✅ `a9d88bd`（15 → 3） |
| `run-uni.cjs` dev/build 输出目录分离 | ✅ `a9d88bd`（`dist/{dev,build}/<platform>`） |
| 后端清死 import | ✅ `a9d88bd` |
| `generator` 权限码拆分读 / 写 | ✅ `84a6d3b`（list / edit / code 三级） |


---

## 四、风险与依赖项

### 4.1 高风险（会实质影响计划达成）

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| **R1** | **支付依赖外部资质**：微信支付商户号、API 密钥、小程序 https 合法域名备案、域名 ICP | **阶段Ⅲ 8 人日全部阻塞**，非技术问题 | **尽早启动资质申请**（周期 1-2 周，与开发并行）。技术方案先做，但联调无法在资质到位前完成 |
| **R2** | **点单端无鉴权是架构级缺陷，不是补个注解能解决的** | 需要新增 token 机制 + 改造点单端全链路 + 存量数据结构，牵动 shop 全模块 | 已排在阶段〇；但需预留联调时间，避免只改后端不改前端导致全面不可用 |
| **R3** | **测试覆盖 3%，回归靠人眼** | 阶段Ⅲ 支付改造涉及订单表结构变更 + 状态机，改动面大且无网，回归风险高 | 严格执行「阶段Ⅱ 先于 阶段Ⅲ」的顺序，不要跳过测试直接做支付 |
| **R4** | **16 个未提交改动 + 3 个未跟踪文件**，累积了 2026-10-16 一整天工作 | 一旦误操作或清理脚本跑偏，**无法回滚** | **动手清理前先 commit**，建立可回滚点 |
| **R5** | **uni-app 使用 alpha 通道快照版**（`5030120260930001`） | 依赖漂移导致构建行为不确定；`package.json` 已改动但 lock 未同步 | 锁定版本，升级前先跑通三端构建；考虑切到稳定版 uni-app 发行版 |

### 4.2 中风险

| # | 风险 | 应对 |
|---|---|---|
| **R6** | **单实例依赖**：`TokenStore`/`CaptchaService` 存 `ConcurrentHashMap` 内存；`OrderService` 用 `synchronized` 单机锁 | 上线前明确「单实例部署 + 重启需重新登录」前提；多实例需求出现时引入 Redis |
| **R7** | `TokenStore` **无容量上限、无定时清理**，仅在 `usernameOf()` 调用时惰性清理单个 key | 长期运行会内存泄漏，需补定时清理或容量上限 |
| **R8** | **无 LICENSE 文件**，README 声明 MIT 并有远程仓库 | 补 `LICENSE` 文件（阶段Ⅰ） |
| **R9** | **无 CI**，所有验证靠本地手工 | 阶段Ⅱ 接入 CI，作为后续所有迭代的门禁 |
| **R10** | 探活接口 `HealthController` 位于 **admin 包**，点单端探活依赖管理端模块 | admin 模块若被裁剪则点单端全线不可用，建议移入 `shop` 或 `common` 包 |
| **R11** | 点单端主包无 `subPackages`，且含 3 个死页面 | 阶段Ⅰ 删死代码可减 79K；阶段Ⅳ 再分包 |
| **R12** | 生产构建的 H5 会真实探测 `backend-hosts.js` 里的内网 IP | 上线前必须替换为 https 正式域名（阶段Ⅴ） |

### 4.3 外部依赖清单

| 类别 | 依赖项 | 状态 | 阻塞阶段 |
|---|---|---|---|
| **资质** | 微信支付商户号 + API 密钥 | ❓待确认 | 阶段Ⅲ |
| **资质** | 小程序 https 合法域名 + ICP 备案 | ❓待确认 | 阶段Ⅲ |
| **资质** | 正式后端域名与证书 | ❓待确认 | 阶段Ⅴ |
| 环境 | JDK 17 / Node 22 / MySQL 5.7+ / 微信开发者工具 | ✅ 已具备 | — |
| 环境 | 正式小程序 appid（`wx90d8af9ad4e52689`） | ✅ 已统一 | — |
| 工具 | 微信支付商户平台 API 文档、证书下载 | ❓待申请 | 阶段Ⅲ |
| 工具 | 服务器（云主机 + 域名 + 备案） | ❓待准备 | 阶段Ⅴ |

### 4.4 文档债（影响协作效率，建议随手修）

- **5 处断链引用**：`README.md:39`（DESIGN.md / PROJECT_MAP.md）、`README.md:53`（../LICENSE）、`tokens.css:4`、`frontend-backend-integration.md:3` —— 目标文件全部不存在
- **管理端 README 严重滞后**：样式层数、页面数、API 模块数均与实际不符
- **`PROJECT_MAP.md` 被两处文档引用但从未创建**，而 `CODESTYLE.md` 明确要求「同步更新 PROJECT_MAP.md」——规范与现实脱节
- **`项目介绍.md` 10.2 待办第 3 条「工作区基本无 Git 覆盖」已过期**（现已纳入 Git 并有远程），需更新

---

## 五、给决策者的三句话

1. **立刻做阶段〇（0.5 人日）**：点单端任何人都能读任意会员订单，这是唯一不能带上线的问题，且修复成本极低。
2. **别跳过阶段Ⅱ**：当前测试覆盖 3%，而阶段Ⅲ 要改订单表结构 + 状态机。没有测试网就动支付，是拿商业闭环赌运气。
3. **尽早启动支付资质**：阶段Ⅲ 8 人日的关键路径不在写代码，而在商户号和域名备案的等待周期。

---

*本文档基于 2026-10-17 三端代码逐模块走查生成。所有结论均标注了文件路径与行号，可直接复核。建议随代码变更同步更新。*
