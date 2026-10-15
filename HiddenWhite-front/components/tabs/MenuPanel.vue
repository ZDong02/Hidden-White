<template>
  <!-- 首页面板：独立滚动区域，下拉刷新走 scroll-view refresher（页面级 pull-down 在 swiper 内不可用） -->
  <scroll-view
    class="panel-scroll menu-panel"
    scroll-y
    refresher-enabled
    :refresher-triggered="refreshing"
    refresher-default-style="black"
    @refresherrefresh="onPullRefresh"
  >
    <view class="menu-body">
      <!-- 品牌标题：宋体展示字 + 金色会员入口；小程序端整体下移避让右上角胶囊按钮 -->
      <view class="brand-row">
        <view class="brand-block">
          <text class="brand">隐白</text>
          <view class="brand-caption-line">
            <text class="brand-caption">精品咖啡 · 日常之上</text>
          </view>
        </view>
        <view class="member-chip" @tap="$emit('switch-tab', 'member')">
          <text class="sparkle">✦</text>
          <text class="member-chip-label">会员礼遇</text>
        </view>
      </view>

      <!-- 门店信息 -->
      <StoreLine />
      <view class="store-meta-row">
        <text class="store-meta muted">距你 {{ storeDistance }} · {{ storeOpen ? '营业中' : '已打烊' }} · {{ storeHours }}</text>
      </view>

      <!-- Banner：黑金主视觉，编辑式排版层级 -->
      <view class="banner" @tap="openDetail('latte')">
        <view class="banner-eyebrow-row">
          <view class="eyebrow-rule" />
          <text class="banner-eyebrow">本周推荐 · 匠心双份浓缩</text>
        </view>
        <text class="banner-title">一杯，恰好。</text>
        <text class="banner-caption">细腻奶感，纯粹咖啡香。</text>
        <view class="banner-link-row">
          <text class="banner-link">品味招牌馥白</text>
          <text class="banner-link-arrow gold">↗</text>
        </view>
        <image :src="bannerImage" mode="aspectFill" />
      </view>

      <!-- 分类：可横向滚动的紧凑标签列，选中项金色下划线跟随 -->
      <scroll-view class="categories" scroll-x :show-scrollbar="false">
        <view class="categories-track">
          <view
            v-for="category in categoryTabs"
            :key="category.id"
            :class="['category', { active: selectedCategory === category.id }]"
            @tap="selectedCategory = category.id"
          >
            <text>{{ category.name }}</text>
            <view v-if="selectedCategory === category.id" class="category-underline" />
          </view>
        </view>
      </scroll-view>

      <!-- 产品列表：两列卡片，无图商品显示品牌色占位 -->
      <view class="products">
        <view
          v-for="product in visibleProducts"
          :key="product.id"
          class="product-card"
          @tap="openDetail(product.id)"
        >
          <view class="product-media">
            <text class="media-fallback">☕</text>
            <image class="product-image" :src="product.image" mode="aspectFill" lazy-load />
          </view>
          <text class="product-name">{{ product.name }}</text>
          <text class="product-subtitle muted">{{ product.subtitle }}</text>
          <view class="product-bottom">
            <text class="gold product-price">¥{{ product.price }}</text>
            <button class="add-button" @tap.stop="openDetail(product.id)">＋</button>
          </view>
        </view>
      </view>

      <!-- 空状态：区分"加载失败"与"确实没有商品"，避免误导 -->
      <view v-if="loadError" class="empty-category muted">
        商品加载失败，请下拉刷新重试
      </view>
      <view v-else-if="!visibleProducts.length" class="empty-category muted">
        <text v-if="!products.length" class="loading-mark">☕</text>
        {{ products.length ? '该分类下暂无商品。' : '正在加载商品…' }}
      </view>
    </view>
  </scroll-view>
</template>

<script setup>
import { ref, computed } from 'vue'
import StoreLine from '../StoreLine.vue'
import { products, loadProducts, refreshOrders, loadStoreInfo, storeInfo } from '../../store/index'
import { categories as baseCategories } from '../../data/products'
import { STORE, IMAGES } from '../../config/constants'
import { openPage } from '../../utils/navigation'

defineEmits(['switch-tab'])

const selectedCategory = ref('all')
const refreshing = ref(false)
const loadError = ref(false)

// 门店信息来自 /api/store（管理端可改），未加载到时回落本地常量
const storeDistance = computed(() => storeInfo.distance || STORE.distance)
const storeHours = computed(() => storeInfo.hours || STORE.hours)
const storeOpen = computed(() => (storeInfo.loaded ? storeInfo.open : true))
const bannerImage = IMAGES.coffee.banner

// 分类由实际商品动态派生：管理端新增的分类能立即出现在筛选栏，
// 不会被本地硬编码的分类表挡住（原实现只认 data/products.js 里的 0/1）。
const categoryTabs = computed(() => {
  const known = new Map(baseCategories.filter((item) => item.id !== 'all').map((item) => [Number(item.id), item.name]))
  const used = []
  products.forEach((product) => {
    const id = Number(product.category) || 0
    if (!used.includes(id)) used.push(id)
  })
  const extra = used
    .filter((id) => !known.has(id))
    .map((id) => ({ id, name: `分类 ${id}` }))
  return [{ id: 'all', name: '全部' }, ...[...known.entries()]
    .filter(([id]) => used.includes(id))
    .map(([id, name]) => ({ id, name })), ...extra]
})

// 三个请求相互独立：用 allSettled 隔离失败，避免门店信息报错时
// 把商品列表一起拖下水（原来 Promise.all 会整体reject，商品随之加载失败）。
async function refreshAll(force = false) {
  const results = await Promise.allSettled([
    loadProducts({ force }),
    refreshOrders(),
    loadStoreInfo()
  ])
  const failed = results.find((item) => item.status === 'rejected')
  // 只有商品列表失败才算致命：订单 / 门店失败仍可展示缓存内容
  loadError.value = results[0].status === 'rejected'
  if (failed) {
    uni.showToast({ title: failed.reason?.message || '无法读取服务器数据', icon: 'none' })
  }
}

// 壳页面在切到本面板 / 页面 onShow 时调用
function activate() {
  refreshAll()
}

function onPullRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  // 下拉刷新强制绕过缓存，保证管理端改动立刻可见
  refreshAll(true).finally(() => {
    refreshing.value = false
  })
}

const visibleProducts = computed(() => {
  if (selectedCategory.value === 'all') {
    return products
  }
  return products.filter((p) => Number(p.category) === Number(selectedCategory.value))
})

function openDetail(id) {
  openPage('detail', { id })
}

defineExpose({ activate })
</script>

<style lang="scss" scoped>
.menu-panel {
  box-sizing: border-box;
  /* 底部预留刚好盖住固定底栏（购物车条 + 分段控件 + 指示条约 310rpx） */
  padding-bottom: 350rpx;
}

.menu-body {
  padding: $sp-6 $gutter 0;
}

/* 小程序端：右上角有胶囊按钮，首屏内容整体下移避让 */
/* #ifdef MP-WEIXIN */
.menu-body {
  padding-top: 116rpx;
}
/* #endif */

/* ---- 品牌区 ---- */
.brand-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: $sp-4;
  margin-bottom: $sp-6;
}

.brand {
  display: block;
  font-family: $font-display;
  font-size: $fs-h1;
  line-height: $lh-display;
  letter-spacing: $ls-wider;
  color: $c-ink-strong;
}

.brand-caption-line {
  display: flex;
  align-items: center;
  gap: $sp-2;
  margin-top: $sp-2;
}

.brand-caption-line::before {
  content: '';
  width: 28rpx;
  height: 2rpx;
  background: $c-gold-line;
}

.brand-caption {
  font-size: $fs-micro;
  color: $c-ink-subtle;
  letter-spacing: $ls-wider;
  text-transform: uppercase;
}

/* 会员入口：细描边胶囊。触控目标提升到 88rpx（44px） */
.member-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $sp-2;
  flex-shrink: 0;
  min-height: $tap-min;
  padding: 0 $sp-4;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-pill;
  background: $c-gold-soft;
  transition: transform $dur-fast ease, background-color 200ms ease, box-shadow 200ms ease;
}

.sparkle {
  color: $c-gold-text;
  font-size: 26rpx;
  line-height: 1;
}

.member-chip-label {
  color: $c-gold-text;
  font-size: $fs-micro;
  letter-spacing: 1rpx;
}

/* ---- 门店信息 ---- */
.store-meta-row {
  display: flex;
  align-items: center;
  margin-top: $sp-3;
  padding-bottom: $sp-5;
  border-bottom: 1rpx solid $c-divider;
  margin-bottom: $sp-5;
}

.store-meta {
  font-size: $fs-caption;
  letter-spacing: 0.5rpx;
}

/* ---- Banner ---- */
.banner {
  position: relative;
  height: 360rpx;
  border: 2rpx solid $c-gold-line;
  border-radius: $r-lg;
  background: #050504;
  overflow: hidden;
  padding: $sp-6 $sp-5;
  color: #d9d5cb;
  box-shadow: $sh-dark;
}

/* 氛围层：右下角一圈金色细环，呼应会员卡的暗金质感 */
.banner::after {
  content: '';
  position: absolute;
  right: -60rpx;
  bottom: -110rpx;
  width: 260rpx;
  height: 260rpx;
  border: 1rpx solid rgba(212, 173, 47, 0.28);
  border-radius: 50%;
  pointer-events: none;
}

.banner-eyebrow-row {
  display: flex;
  align-items: center;
  gap: $sp-2;
  position: relative;
  z-index: 1;
}

.eyebrow-rule {
  width: 26rpx;
  height: 2rpx;
  background: $c-gold;
}

.banner-eyebrow {
  font-size: 22rpx;
  letter-spacing: $ls-wide;
  color: #cdaa53;
}

.banner-title {
  display: block;
  position: relative;
  z-index: 1;
  font-family: $font-display;
  font-size: 50rpx;
  line-height: $lh-display;
  margin-top: $sp-5;
  color: #f5ecd5;
}

.banner-caption {
  display: block;
  position: relative;
  z-index: 1;
  font-size: 22rpx;
  margin-top: $sp-4;
  color: #a8a49a;
}

.banner-link-row {
  display: flex;
  align-items: center;
  gap: $sp-2;
  position: relative;
  z-index: 1;
  margin-top: $sp-6;
}

.banner-link {
  font-size: 22rpx;
  letter-spacing: 1rpx;
  color: #ecdcb2;
}

.banner-link-arrow {
  font-size: 26rpx;
}

/* 深色底上的金色统一走 $c-gold-on-dark，避免复用浅底的 .gold 导致对比度不足 */
.banner .gold {
  color: $c-gold-on-dark;
}

.banner image {
  position: absolute;
  right: 28rpx;
  top: 32rpx;
  width: 280rpx;
  height: 280rpx;
  border-radius: $r-md;
  border: 1rpx solid rgba(212, 173, 47, 0.35);
}

/* ---- 分类标签：横向滚动 + 金色下划线 ---- */
.categories {
  margin: $sp-6 (-$gutter) $sp-6;
  white-space: nowrap;
}

.categories-track {
  display: inline-flex;
  align-items: stretch;
  padding: 0 $gutter;
}

/* 触控目标提升到 88rpx；文字垂直居中后再由下划线指示选中 */
.category {
  position: relative;
  display: inline-flex;
  align-items: center;
  min-height: $tap-min;
  margin-right: $sp-7;
  font-size: 26rpx;
  color: $c-ink-muted;
  transition: color 200ms ease;
}

.category.active {
  color: $c-gold-text;
  font-weight: 600;
}

.category-underline {
  position: absolute;
  bottom: 12rpx;
  left: 50%;
  transform: translateX(-50%);
  width: 40rpx;
  height: 6rpx;
  background: $c-gold;
  border-radius: $r-pill;
  animation: category-indicator 240ms $ease-out both;
}

/* ---- 商品卡 ---- */
.products {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $sp-4;
}

.product-card {
  padding: $sp-4 $sp-4 $sp-4;
  border: 1rpx solid $c-border;
  border-radius: $r-lg;
  background: $c-surface;
  box-shadow: $sh-1;
  transition: transform 200ms ease, box-shadow 200ms ease, border-color 200ms ease;
}

/* 图片容器：自带品牌色渐变底与占位符号，无图商品不再是生硬灰块 */
.product-media {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 230rpx;
  border-radius: $r-md;
  overflow: hidden;
  background: linear-gradient(150deg, #f5f1e6, $c-bg-sunken);
  margin-bottom: $sp-4;
}

.media-fallback {
  position: absolute;
  color: #c2ab72;
  font-size: 60rpx;
  opacity: 0.8;
}

.product-image {
  position: relative;
  z-index: 1;
  display: block;
  width: 100%;
  height: 100%;
}

.product-name {
  display: block;
  font-size: $fs-title-sm;
  font-weight: 600;
  color: $c-ink-strong;
  letter-spacing: 0.4rpx;
}

.product-subtitle {
  display: block;
  font-size: $fs-caption;
  color: $c-ink-muted;
  margin-top: $sp-1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.product-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: $sp-4;
}

.product-price {
  font-size: $fs-title-lg;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

/* 加购按钮：76rpx → 88rpx（44px），达标可点 */
.add-button {
  width: $tap-min;
  height: $tap-min;
  border-radius: 50%;
  background: $c-dark;
  color: $c-gold-on-dark;
  font-size: 44rpx;
  font-weight: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $sh-2;
}

.empty-category {
  display: block;
  padding: $sp-8 0;
  text-align: center;
  font-size: $fs-caption;
}

/* 加载态用轻微呼吸的金色咖啡杯，替代原来一行干巴巴的提示文字 */
.loading-mark {
  display: block;
  margin-bottom: $sp-3;
  color: $c-gold-line;
  font-size: 52rpx;
  animation: shimmer 1.2s ease-in-out infinite alternate;
}

/* ---- 状态与动效 ---- */
@keyframes category-indicator {
  from {
    transform: translateX(-50%) scaleX(0.4);
    opacity: 0.4;
  }

  to {
    transform: translateX(-50%) scaleX(1);
    opacity: 1;
  }
}

.member-chip:active {
  opacity: 0.85;
}

.product-card:active {
  transform: scale(0.98);
}

/* #ifdef H5 */
.product-card:hover {
  transform: translateY(-4rpx);
  box-shadow: $sh-2;
  border-color: $c-border-strong;
}

.member-chip:hover {
  box-shadow: $sh-1;
}

.category:hover {
  color: $c-gold-text;
}
/* #endif */

.add-button:active {
  transform: scale(0.92);
}
</style>
