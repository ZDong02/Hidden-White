# 隐白咖啡（Hidden White）统一代码规范

本规范适用于工作区内全部子项目。编辑器层面的缩进、换行、编码已由根目录 `.editorconfig` 统一约束；前端格式化由各前端项目的 `.prettierrc` 承担。

## 1. 通用规则

- 编码统一 **UTF-8**，换行统一 **LF**（`.cmd`/`.bat` 除外，必须 CRLF）。
- 文件末尾保留一个空行，行尾不得有多余空白（Markdown 除外）。
- 命名语言：代码内标识符用英文，注释、文档、UI 文案用中文。
- 提交信息（若启用版本控制）：`类型: 简述`，类型取 `feat / fix / refactor / docs / style / chore`。
- 禁止提交：`node_modules/`、`target/`、`dist/`、`unpackage/`、`.idea/`、日志、密钥与环境变量文件。

## 2. 前端（HiddenWhite-front · uni-app，JavaScript）

- 缩进 2 空格；字符串统一单引号；语句末尾不加分号；行宽 100。
- 使用 ESLint flat config（`eslint.config.mjs`）检查，规则集 `js/recommended` + `eslint-plugin-vue`。
  - 旧版 `.eslintrc.js` 已删除（ESLint 10 仅支持 flat config，旧文件已失效），历史实现见 Git 记录。
- uni-app / 微信全局变量（`uni`、`wx`、`getCurrentPages` 等）已在 ESLint 配置中声明，页面文件（`pages/**/*.vue`）关闭 `multi-word-component-names`。
- 组件命名：`pages/` 下页面按 `pages.json` 约定可用单词命名；`components/` 下共享组件必须多单词大驼峰（如 `MainTabBar.vue`）。
- 状态管理放 `store/`，请求统一走 `config/backend.js` 的 `requestApi`，禁止在组件里直接裸调 `uni.request`。
- 生产构建中 `console` / `debugger` 视为警告。

## 3. 管理端前端（HiddenWhite-admin · Vue 3 + TypeScript）

- 缩进 2 空格；字符串单引号；语句末尾不加分号（与点单端保持一致）。
- **页面组件模板必须单根节点**（根元素统一 `<div class="page">`）：`AppShell` 用 `<Transition out-in>` 驱动页面切换，多根/fragment 根会导致过渡卡死、内容区空白（2026-10-13 会员权益页事故）。
- TypeScript：接口返回统一在 `src/api` 声明类型；页面组件放 `src/views`，复用组件放 `src/components`；组合式函数放 `src/composables`。
- 列表页复用 `ListPage` + `useListPage` / `useTreeListPage`；表单复用 `FormDialog` + `Field`；权限操作复用 `PermissionButton`。
- 新页面必须在 `src/composables/auth.ts` 的 `COMPONENT_MAP` 注册。
- 构建前必须通过 `vue-tsc --noEmit` 类型检查（`npm run build` 已内置）。

## 4. 后端（Spring Boot · Java 17）

- 缩进 4 空格；类名大驼峰、方法与变量小驼峰、常量全大写下划线。
- 分层：Controller 只做参数校验与转发；业务在 Service；数据访问走 Repository / Mapper。
- 统一返回结构与异常处理复用 `exception/` 与 `common/`（管理端）已有设施，不得在 Controller 内自行 try-catch 吞异常。
- 配置一律通过环境变量注入（`DB_*`、`JWT_SECRET` 等），不把密码写进源码或提交进仓库。
- 点单端价格、取餐号、首单减免等由后端计算，禁止信任客户端传入的价格字段。

## 5. 数据库

- 建表脚本幂等（`IF NOT EXISTS` / 按主键更新），可重复执行。
- 业务表默认包含 `create_by`、`update_by`、`create_time`、`update_time` 四个审计字段。
- 结构变更写入迁移脚本并同步整合版 SQL，禁止只改库不改脚本。

## 6. 文档同步

新增页面、接口或模块时，同步更新对应 README 与 `docs/PROJECT_MAP.md`，保持文档与代码一致。
