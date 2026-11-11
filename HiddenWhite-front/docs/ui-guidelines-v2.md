# 点单端 UI 使用规范 v2.0

> 本文档记录 2026 年 UI 优化后的设计规范，包含字体、间距、动效等统一标准。

---

## 设计理念

**编辑式奢感** —— 暖白纸画布 + 暖调近黑 + 唯一强调色金

- 金色在所有深色面上只用 `$c-gold-on-dark`，在所有浅色面上只用 `$c-gold-text`
- 不允许第二个强调色出现
- 宋体做大标题展示字，无衬线做正文

---

## 字体使用规范

### 字号层级

| 场景 | 变量名 | 字号 | 字重 | 字距 | 字体 |
|------|--------|------|------|------|------|
| 品牌标题（首页"隐白"） | `$fs-h1` | 58rpx | 600 | 4rpx | 宋体 |
| 页面主标题 | `$fs-h2` | 46rpx | 600 | 0.5rpx | 宋体 |
| 轮播标题 | `$fs-display` | 68rpx | 500 | -0.5rpx | 宋体 |
| 区块标题 | `$fs-title` | 34rpx | 600 | 0.4rpx | 无衬线 |
| 卡片标题 | `$fs-title-lg` | 38rpx | 600 | 0.4rpx | 无衬线 |
| 小标题 | `$fs-title-sm` | 32rpx | 600 | 0.4rpx | 无衬线 |
| 正文 | `$fs-body` | 30rpx | 400 | 0.2rpx | 无衬线 |
| 次要正文 | `$fs-body-sm` | 28rpx | 400 | 0.2rpx | 无衬线 |
| 说明文字 | `$fs-caption` | 26rpx | 400 | 0.5rpx | 无衬线 |
| 微标签 | `$fs-micro` | 24rpx | 600 | 2-4rpx | 无衬线 |
| 价格（强调） | `$fs-title-lg` / `$fs-h2` | 38/46rpx | **700** | -0.5rpx | 无衬线 |
| 取餐号 | `$fs-ticket` | 136rpx | 600 | -0.5rpx | 宋体 |

### 字重梯度

```scss
$fw-light: 300;     // 仅用于超大展示字（≥64rpx）的装饰性文字
$fw-normal: 400;    // 正文默认
$fw-medium: 500;    // 小标题、强调文字
$fw-semibold: 600;  // 卡片标题、区块标题、按钮
$fw-bold: 700;      // 页面主标题、数字强调（价格、取餐号）
```

### 使用原则

1. **大标题用宋体**：≥44rpx 的标题使用 `$font-display`（宋体系）
2. **正文用无衬线**：≤38rpx 的文字使用 `$font-body`（无衬线）
3. **数字用等宽**：所有会变化的数字必须加 `@include numeric`
4. **拉丁大写配字距**：微标签用 `@include caps`（字距 3rpx + 大写）

---

## 间距系统

### 基础梯度

```scss
$sp-0: 4rpx;   // 微调（图标与文字、光学对齐）
$sp-1: 8rpx;
$sp-2: 12rpx;
$sp-3: 16rpx;
$sp-4: 24rpx;
$sp-5: 32rpx;
$sp-6: 40rpx;
$sp-7: 56rpx;
$sp-8: 80rpx;
```

### 语义化变量（v2 新增）

| 场景 | 变量名 | 值 | 用途 |
|------|--------|-----|------|
| 区块之间 | `$sp-section` | 56rpx | 轮播与分类、分类与商品列表 |
| 内容块之间 | `$sp-block` | 32rpx | 品牌行与门店、门店与轮播 |
| 相关元素组 | `$sp-group` | 24rpx | 图片与标题、标题与价格 |
| 行内元素 | `$sp-inline` | 16rpx | 图标与文字、标签间隙 |
| 紧凑场景 | `$sp-tight` | 12rpx | 指示点间隙 |
| 光学对齐 | `$sp-optical` | 4rpx | 微调对齐 |

### 横向留白

```scss
$gutter: 44rpx;      // 页面标准留白
$gutter-sm: 32rpx;   // 紧凑场景留白（v2 新增）
```

### 卡片内边距

```scss
.card: 40rpx;           // 浅色卡片
.card-dark: 56rpx 40rpx; // 深色卡片（纵向更大）
.product-card: 32rpx;    // 商品卡片
```

---

## 色彩使用

### 深色面增强（v2 轮播优化）

```scss
// 增强渐变（轮播卡片）
background: linear-gradient(135deg, #29261d 0%, #171713 40%, #241f15 75%, #0b0a07 100%);

// 金色光晕
background: radial-gradient(circle at 80% 20%, rgba(212, 173, 47, 0.15) 0%, transparent 60%);

// 图片边缘渐隐
background: radial-gradient(ellipse at center, transparent 30%, rgba(11, 10, 7, 0.3) 100%);
```

### 阴影增强

```scss
// 图片容器阴影
box-shadow: 
  inset 0 0 0 1rpx rgba(212, 173, 47, 0.08),  // 内描边
  0 8rpx 24rpx rgba(11, 10, 7, 0.4);          // 外阴影

// 品牌环双环效果
box-shadow:
  inset 0 0 0 1rpx rgba(212, 173, 47, 0.1),
  0 0 40rpx rgba(212, 173, 47, 0.1);

// 指示点激活发光
box-shadow:
  0 0 8rpx rgba(212, 173, 47, 0.6),
  0 0 16rpx rgba(212, 173, 47, 0.3);
```

---

## 动效规范

### 时长层级

| 场景 | 变量 | 时长 | 缓动曲线 |
|------|------|------|----------|
| 按压反馈 | `$dur-instant` | 120ms | `cubic-bezier(0.34, 1.4, 0.64, 1)` 弹性 |
| 颜色过渡 | `$dur-fast` | 180ms | `ease` |
| 位移动画 | `$dur-base` | 300ms | `ease-out` |
| 场景切换 | `$dur-slow` | 600ms | `ease-out` |

### 轮播优化（v2）

```scss
// 切换时长：从 380ms 减少到 340ms
:duration="340"

// 指示点过渡
transition: width 300ms cubic-bezier(0.34, 1.4, 0.64, 1),
            background-color 180ms ease,
            transform 180ms ease;
```

### 微交互动效

#### 按钮按压
```scss
.ui-button--press {
  transform: scale(0.96);  // 从 0.975 加大压缩感
  opacity: 0.88;
}
```

#### 数字跳动
```scss
@keyframes number-pop {
  0% {
    opacity: 0.3;
    transform: translateY(-8rpx) scale(0.9);
  }
  60% {
    transform: translateY(0) scale(1.06);  // 过冲
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
```

#### 骨架屏掠光
```scss
// 从 1.4s 放慢到 1.6s，亮度从 0.7 提升到 0.8
animation: sweep 1.6s ease-in-out infinite;
background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.8), transparent);
```

---

## 轮播图专项规范

### 视觉层次

1. **背景渐变**：使用 `$c-dark-gradient-enhanced`（四色渐变）
2. **左上光晕**：`radial-gradient` 金色光晕，opacity 0.6
3. **图片容器**：内外阴影增强边框感
4. **图片边缘**：径向渐隐遮罩融入背景
5. **品牌环**：双环效果（内描边 + 外发光）

### 文字层级

```scss
眉标：24rpx / 600 / 大写 / 字距 4rpx / 发光
标题：68rpx / 500 / 宋体 / 最多两行
副标题：28rpx / 400 / opacity 0.95
价格：30rpx / 700 / 金色
```

### 指示点

- **位置**：底部居中（从左下改为居中）
- **尺寸**：非激活 10rpx，激活 32rpx
- **背景**：毛玻璃效果 `backdrop-filter: blur(12rpx)`
- **发光**：激活点双层阴影

### 交互反馈

```scss
按压：scale(0.985) translateY(2rpx)  // 下沉感
悬停：图片放大 scale(1.04)
      链接右移 translateX(4rpx)
```

---

## 商品卡片规范

### 内间距
```scss
padding: 32rpx;  // 从 24rpx 增加
margin-bottom: 32rpx;  // 图片与标题
```

### 字号优化
```scss
.product-name: 38rpx / 600     // 从 30rpx 提升
.product-price: 38rpx / 700    // 字重从 600 提升
```

---

## 品牌区装饰

### 光晕效果
```scss
.brand-row::before {
  background: radial-gradient(
    circle,
    rgba(212, 173, 47, 0.06) 0%,
    transparent 70%
  );
}
```

---

## 弹窗与提示规范

点单端**不再使用系统弹窗**（`uni.showModal` / `wx.showModal` / `alert` / `confirm` / `prompt`）：
原生弹窗的字体、按钮、圆角由系统决定，引用不到设计令牌，三端表现不一致。
按「信息量」分成两层，不要混用：

| 层 | 组件 | 用于 | 形态 |
|---|---|---|---|
| 抽屉 | `components/ui/Modal.vue` | 需要看/选的内容（选券、升杯说明、生日礼遇） | 底部上滑，`$r-xl` 顶圆角 |
| 对话框 | `components/ui/Dialog.vue` | 一句话 + 表态（提示、二次确认、单行输入） | 居中卡片，`$r-xl` 四周圆角 |

**调用方式**（命令行式，不用在页面里维护 `visible`）：

```js
import { showNotice, showConfirm, showPrompt } from '../utils/toast'
import { dialogNotice, dialogConfirm, dialogPrompt } from '../store/dialog'

showNotice('会员中心', '该服务正在筹备中')                  // 单个「知道了」
const ok = await showConfirm({ title: '取消订单', content: '…', danger: true })
const note = await showPrompt({ title: '订单备注', value: shop.note })
```

- 渲染方是 `components/ui/DialogHost.vue`，**必须挂在页面层**——
  `swiper-item` 带 `transform`，`position: fixed` 在微信端会相对它定位而错位。
- 页面接入了弹窗 API 就必须挂 `<DialogHost />`，否则会退回系统弹窗
  （`store/dialog.js` 的降级分支，只为兜底，不是正常路径）。
- `danger: true` 用于破坏性主操作（取消订单、退出登录）：确认键改用 `$c-danger`，
  普通主操作仍是深底金字。
- 遮罩默认**不可点关闭**，与原生 modal 一致，避免误触关掉需要明确表态的确认。
- 轻提示（`showSuccess` / `showError` / `showInfo`）仍是系统 toast：1.5 秒即走、
  不需要表态，自绘反会盖住操作区。

这条规则靠人工守：改动弹窗相关代码后全仓搜一遍系统弹窗调用（`uni.showModal` /
`uni.showActionSheet`），出现即改自绘 Dialog；页面用了弹窗 API 就必须挂一次宿主，
否则点击没有任何反应。

---

## 兼容性说明

- **小程序**：所有动效使用 `transform` + `opacity`，不使用 `filter`
- **H5**：悬停态用 `#ifdef H5` 隔离
- **无障碍**：`prefers-reduced-motion` 自动关闭动画

---

## 迁移清单

从 v1 迁移到 v2 需要注意：

1. **字号变量**：部分字号提升（22→24, 26→28, 30→32, 44→46...）
2. **间距变量**：新增语义化变量（`$sp-section`, `$sp-block` 等）
3. **字重提升**：价格、品牌标题字重从 600 提升到 700
4. **动效曲线**：按钮使用弹性曲线 `cubic-bezier(0.34, 1.4, 0.64, 1)`
5. **轮播时长**：从 380ms 减少到 340ms
6. **指示点位置**：从左下改为底部居中

---

**文档版本**：v2.0  
**更新日期**：2026-10  
**维护者**：前端团队
