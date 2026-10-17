# 隐白咖啡管理端 · 设计规范

> 管理端前端的视觉与交互唯一真源。本文件说明设计语言与七层样式体系的职责边界；
> **具体数值一律以 `src/styles/tokens.css` 为准**，本文不重复罗列色值与尺寸，避免两处失同步。

---

## 一、设计方向

**仪器化 + 珍珠玻璃**。

管理端不是普通的表格后台。它的信息密度高（用户、角色、菜单、部门、岗位、字典、参数、审计日志并存），
所以走的是**工业仪表盘**的路子：等宽数字、刻度尺、规格铭牌、读数板、悬停名片。
同时用玻璃材质承载层次——四种玻璃配方只靠「背景不透明度 + 模糊 + 饱和度 + 白边强度」拉开差距，
**不靠换颜色**，避免界面变成五彩斑斓的调色盘。

**记忆点**：把 B 端做成了仪表盘。用户看惯了同心圆式的圆角卡片，这里给出的是刻度、读数与泳道。

---

## 二、七层样式体系

`src/main.ts` 按以下顺序导入，**顺序不可调换**——后层依赖前层的基础设定。

| # | 文件 | 行数 | 职责 |
|---|---|---|---|
| 1 | `tokens.css` | 220 | **设计令牌**：全站颜色、圆角、阴影、字色、材质、分类色的唯一来源 |
| 2 | `base.css` | 107 | 重置、排版、焦点环、颗粒层、无障碍降级 |
| 3 | `materials.css` | 82 | 四种玻璃配方 + 槽 + 阅读面 |
| 4 | `components.css` | 704 | 控件语汇。交互控件的 transition 只留 color / background / border-color / box-shadow |
| 5 | `instruments.css` | 1220 | **仪器化语汇**：眉标、显示级读数、规格铭牌、环比胶囊、刻度尺，以及图表、泳道、画布、看板、矩阵等版式级模块 |
| 6 | `layout.css` | 357 | 外壳：舞台 → 深色外框 → 导航轨 + 工作区 |
| 7 | `pages.css` | 505 | 页面级独有排布。**通用控件不在这里写** |

### 分层铁律

- 页面与组件**只引用令牌**，不手写取值。需要新颜色先加进 `tokens.css`。
- 通用控件的样式写进 `components.css` / `instruments.css`，**不要写进 `pages.css`**。
- `transform` 与 `opacity` 归 GSAP 处理，CSS transition 不碰这两个属性（会与动效编排打架）。

---

## 三、色彩纪律

- **主色 60% / 辅助 30% / 强调 10%**。
- 强调色单屏最多一个，只留给关键动作。
- **分类色只区分类别**（看板列、图例），**不承载状态含义**；状态由语义色（蓝 = 选择 / 主操作，青柠 = 能力与状态，红 = 警示）表达。
- **禁止仅用颜色表达状态**：状态必须同时有文字或形态差异，否则色觉障碍用户无法辨识。

---

## 四、排版

- 字体栈：`"SF Pro Display", "SF Pro Text", "PingFang SC", -apple-system, "Noto Sans SC", "Microsoft YaHei"`。
- 数据密集处使用等宽数字 `font-variant-numeric: tabular-nums`，避免数字跳动。
- 中文正文行高 ≥ 1.7。

---

## 五、动效

| 类型 | 时长 | 缓动 |
|---|---|---|
| 微交互（悬停、按压） | 150–200ms | `ease-out` |
| 常规过渡（菜单、面板） | 300–400ms | `cubic-bezier(0.16, 1, 0.3, 1)` |
| 场景过渡（页面、弹层） | 600–800ms | `cubic-bezier(0.16, 1, 0.3, 1)` |

- GSAP 动效编排集中在 `src/composables/motion.ts`，按压、涟漪、辉光跟随都在这里。
- **降级**：`appearance.ts` 管理 `data-theme` / `data-density`，在 `main.ts` 中于 `mount()` 之前应用，
  避免首屏闪色。用户开启「减少动效」时统一降级。

---

## 六、可访问性

- 正文对比度 ≥ 4.5:1，大字（≥ 24px）≥ 3:1，控件边框 ≥ 3:1。
- 点击目标 ≥ 44×44px（`$tap-min` 在点单端为 88rpx，管理端对应 44px）。
- 全部功能键盘可达，`:focus-visible` 有可见描边。
- 浮层（Modal / Drawer / Select / CommandPalette）统一走 `composables/focus.ts` 的焦点陷阱。

---

## 七、组件复用约定

| 场景 | 复用 |
|---|---|
| 列表页 | `ListPage` + `useListPage` / `useTreeListPage` |
| 表单弹窗 | `FormDialog` + `Field` |
| 权限按钮 | `PermissionButton` |
| 视图模式记忆 | `useView`（列表 / 矩阵 / 星图等切换） |

**新增页面必须**在 `src/composables/auth.ts` 的 `COMPONENT_MAP` 注册，
否则动态路由拿不到组件，`buildNav()` 会把它当作无效菜单丢弃。

---

## 八、构建前自检

```bash
npm run build     # 内含 vue-tsc --noEmit 类型检查
npm run test:unit # Node 内置测试运行器
```

- 页面组件模板**必须单根节点**（根元素统一 `<div class="page">`）。`AppShell` 用 `<Transition out-in>`
  驱动页面切换，多根 / fragment 根会导致过渡卡死、内容区空白（2026-10-13 会员权益页事故）。
