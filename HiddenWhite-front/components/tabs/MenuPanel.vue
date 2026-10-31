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
      <view class="brand-row reveal">
        <view class="brand-block">
          <text class="brand">隐白</text>
          <view class="brand-caption-line">
            <text class="brand-caption">精品咖啡 · 日常之上</text>
          </view>
        </view>
        <view
          class="member-chip"
          hover-class="is-press"
          :hover-stay-time="60"
          @tap="$emit('switch-tab', 'member')"
        >
          <Icon name="sparkle" tone="gold" :size="26" />
          <text class="member-chip-label">会员礼遇</text>
        </view>
      </view>

      <!-- 门店信息 -->
      <view class="store-block reveal reveal-1">
        <StoreLine />
        <view class="store-meta-row">
          <text class="store-meta muted">距你 {{ storeDistance }}</text>
          <view class="meta-dot" :class="storeOpen ? 'meta-dot--open' : 'meta-dot--closed'" />
          <text class="store-meta" :class="storeOpen ? 'store-open' : 'muted'">
            {{ storeOpen ? '营业中' : '已打烊' }}
          </text>
          <text class="store-meta muted store-hours">{{ storeHours }}</text>
        </view>
      </view>

      <!--
        首页推荐位：轮播池来自管理端「推荐商品」，端上随机抽最多 3 屏。
        原来的静态 Banner（写死「一杯，恰好。」）已落成推荐位数据，
        文案改在管理端，这里只负责展示。
        paused：本面板不在前台时停掉自动轮播（切到别的 Tab 仍在渲染，没必要继续转）。
      -->
      <view class="hero-block reveal reveal-2">
        <RecommendCarousel :slides="heroSlides" :paused="!panelActive" @select="openDetail" />
      </view>

      <!-- 分类：可横向滚动的紧凑标签列，选中项金色下划线跟随 -->
      <scroll-view class="categories reveal reveal-3" scroll-x :show-scrollbar="false">
        <view class="categories-track">
          <view
            v-for="category in categoryTabs"
            :key="category.id"
            :class="['category', { active: selectedCategory === category.id }]"
            hover-class="category--press"
            :hover-stay-time="60"
            @tap="selectedCategory = category.id"
          >
            <text>{{ category.name }}</text>
            <view v-if="selectedCategory === category.id" class="category-underline" />
          </view>
        </view>
      </scroll-view>

      <!-- 加载中：骨架屏，避免「空屏 + 一行提示」造成的页面跳动 -->
      <view v-if="loading && !products.length" class="products">
        <view v-for="n in 4" :key="`skeleton-${n}`" class="product-card product-card--skeleton">
          <view class="skeleton skeleton-media" />
          <view class="skeleton skeleton-line" />
          <view class="skeleton skeleton-line skeleton-line--short" />
        </view>
      </view>

      <!-- 产品列表：两列卡片，无图商品显示品牌色占位 -->
      <view v-else class="products">
        <view
          v-for="product in visibleProducts"
          :key="product.id"
          class="product-card"
          hover-class="product-card--press"
          :hover-stay-time="60"
          @tap="openDetail(product.id)"
        >
          <view class="product-media">
            <view class="media-fallback">
              <Icon name="cup" tone="decor" :size="56" />
            </view>
            <image class="product-image" :src="product.image" mode="aspectFill" lazy-load />
          </view>
          <text class="product-name">{{ product.name }}</text>
          <text class="product-subtitle muted">{{ product.subtitle }}</text>
          <view class="product-bottom">
            <text class="gold product-price num">¥{{ product.price }}</text>
            <view
              class="add-button"
              aria-label="查看并加入"
              hover-class="add-button--press"
              :hover-stay-time="60"
              @tap.stop="openDetail(product.id)"
            >
              <Icon name="plus" tone="gold-on-dark" :size="30" />
            </view>
          </view>
        </view>
      </view>

      <!-- 空状态：区分「加载失败」与「确实没有商品」，避免误导 -->
      <view v-if="loadError" class="state-block">
        <Icon name="cup" tone="decor" :size="52" />
        <text class="state-text muted">商品加载失败，请下拉刷新重试</text>
      </view>
      <view v-else-if="!loading && !visibleProducts.length" class="state-block">
        <Icon name="cup" tone="decor" :size="52" />
        <text class="state-text muted">该分类下暂无商品。</text>
      </view>
    </view>
  </scroll-view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import Icon from '../ui/Icon.vue'
import StoreLine from '../StoreLine.vue'
import RecommendCarousel from '../RecommendCarousel.vue'
import {
  products,
  heroSlides,
  loadProducts,
  loadRecommendations,
  refreshOrders,
  loadStoreInfo,
  storeInfo
} from '../../store/index'
import { STORE, CATEGORY_NAMES } from '../../config/constants'
import { openPage } from '../../utils/navigation'

defineEmits(['switch-tab'])

const selectedCategory = ref('all')
const refreshing = ref(false)
const loading = ref(true)
const loadError = ref(false)

/**
 * 本面板是否在前台。
 * 壳页面用横向 swiper 同时挂着四个面板，切走后面板仍在渲染树里 ——
 * 轮播的自动播放没有观众时应该停掉，所以这里如实记录可见性，透传给轮播组件。
 */
const panelActive = ref(false)

// 门店信息来自 /api/store（管理端可改），未加载到时回落本地常量
const storeDistance = computed(() => storeInfo.distance || STORE.distance)
const storeHours = computed(() => storeInfo.hours || STORE.hours)
const storeOpen = computed(() => (storeInfo.loaded ? storeInfo.open : true))

// 分类由实际商品动态派生：只给「商品里真实出现过的分类」出标签，
// 名称从 CATEGORY_NAMES 取，未登记的数字分类显示为「分类 N」。
// 这样管理端新增商品分类后筛选栏自动跟上，不需要前端再改一份分类表。
const categoryTabs = computed(() => {
  const used = []
  products.forEach((product) => {
    const id = Number(product.category) || 0
    if (!used.includes(id)) used.push(id)
  })
  used.sort((a, b) => a - b)
  return [
    { id: 'all', name: '全部' },
    ...used.map((id) => ({ id, name: CATEGORY_NAMES[id] || `分类 ${id}` }))
  ]
})

// 四个请求相互独立：用 allSettled 隔离失败，避免门店信息报错时
// 把商品列表一起拖下水（原来 Promise.all 会整体reject，商品随之加载失败）。
async function refreshAll(force = false) {
  const results = await Promise.allSettled([
    loadProducts({ force }),
    refreshOrders(),
    // 门店营业状态直接决定能否下单，每次 activate（onShow / 切 tab）都强制刷新，
    // 不吃缓存；单条门店查询开销极小，实时性优先
    loadStoreInfo({ force: true }),
    // 推荐位同样按 force 走：下拉刷新要立刻看到管理端刚改的文案 / 商品
    loadRecommendations({ force })
  ])
  const failed = results.find((item) => item.status === 'rejected')
  // 只有商品列表失败才算致命：订单 / 门店 / 推荐位失败仍可展示缓存或隐藏该区块
  loadError.value = results[0].status === 'rejected'
  loading.value = false
  if (failed) {
    uni.showToast({ title: failed.reason?.message || '无法读取服务器数据', icon: 'none' })
  }
}

// 壳页面在切到本面板 / 页面 onShow 时调用，这里也自己兜一层：
// 面板挂载即发起首次加载，不再依赖壳页面的 onShow 时机（子组件 ref 到位与否不该决定有没有数据）
let inFlight = null

function activate() {
  panelActive.value = true
  if (inFlight) return inFlight
  inFlight = refreshAll()
    .catch(() => {
      // refreshAll 自己抛错（同步或异步）也必须把加载态收掉：
      // 骨架屏永远转下去比给一条错误提示糟糕得多
      loadError.value = true
      loading.value = false
    })
    .finally(() => {
      inFlight = null
    })
  return inFlight
}

/** 切走时由壳页面调用：只影响「是否还在前台」这一件事，不做数据清理（回来要秒显） */
function deactivate() {
  panelActive.value = false
}

onMounted(activate)

function onPullRefresh() {
  if (refreshing.value) return
  refreshing.value = true
  // 下拉刷新强制绕过缓存，保证管理端改动立刻可见
  refreshAll(true)
    .catch(() => {
      // 同上：下拉刷新失败也要收掉 loading，否则下拉指示器会一直转
      loadError.value = true
      loading.value = false
    })
    .finally(() => {
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

defineExpose({ activate, deactivate })
</script>

<style lang="scss" scoped>
.menu-panel {
  box-sizing: border-box;
  padding-bottom: $pad-panel-cart;
}

.menu-body {
  padding: $pad-panel-top $gutter 0;
}

/* 小程序端：右上角有胶囊按钮，首屏内容整体下移避让 */
/* #ifdef MP-WEIXIN */
.menu-body {
  padding-top: $pad-capsule;
}
/* #endif */

/* ---- 品牌区 ---- */
.brand-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: $sp-4;
  margin-bottom: $sp-5;
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
  @include caps;
  color: $c-ink-subtle;
}

/* 会员入口：细描边胶囊，触控目标 88rpx（44px） */
.member-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $sp-1;
  flex-shrink: 0;
  min-height: $tap-min;
  padding: 0 $sp-4;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-pill;
  background: $c-gold-soft;
  transition: transform $dur-instant $ease-out, background-color $dur-fast ease,
    box-shadow $dur-fast ease;
}

.member-chip-label {
  color: $c-gold-text;
  font-size: $fs-micro;
  letter-spacing: 1rpx;
}

/* ---- 门店信息 ---- */
.store-block {
  border-bottom: 1rpx solid $c-divider;
  padding-bottom: $sp-3;
  margin-bottom: $sp-5;
}

.store-meta-row {
  display: flex;
  align-items: center;
  gap: $sp-2;
  margin-top: $sp-1;
}

.store-meta {
  font-size: $fs-caption;
  letter-spacing: 0.5rpx;
}

.store-open {
  color: $c-gold-text;
}

/* 营业状态点：8rpx 极小的实心点，只做状态暗示，不抢文字 */
.meta-dot {
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.meta-dot--open {
  background: $c-gold;
  box-shadow: 0 0 0 4rpx $c-focus-ring;
}

.meta-dot--closed {
  background: $c-ink-faint;
}

.store-hours {
  margin-left: auto;
}

/* 推荐轮播：整块沿用原 Banner 的纵向节奏，间距由 .categories 的 margin-top 提供 */

/* ---- 分类标签：横向滚动 + 金色下划线 ---- */
.categories {
  margin: $sp-6 (-$gutter) $sp-5;
  white-space: nowrap;
}

.categories-track {
  display: inline-flex;
  align-items: stretch;
  padding: 0 $gutter;
}

/* 触控目标 88rpx；文字垂直居中后再由下划线指示选中 */
.category {
  position: relative;
  display: inline-flex;
  align-items: center;
  min-height: $tap-min;
  margin-right: $sp-7;
  font-size: $fs-body-sm;
  color: $c-ink-muted;
  transition: color $dur-fast ease;
}

.category.active {
  color: $c-gold-text;
  font-weight: 600;
}

/* 下划线从中间向两侧展开：比整条淡入更像「选中」而不是「新增」 */
.category-underline {
  position: absolute;
  bottom: 12rpx;
  left: 50%;
  width: 40rpx;
  height: 6rpx;
  margin-left: -20rpx;
  background: $c-gold;
  border-radius: $r-pill;
  transform-origin: center;
  animation: underline-in 240ms $ease-out both;
}

@keyframes underline-in {
  from {
    transform: scaleX(0.2);
    opacity: 0.3;
  }

  to {
    transform: scaleX(1);
    opacity: 1;
  }
}

.category--press {
  opacity: 0.6;
}

/* ---- 商品卡 ---- */
.products {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $sp-4;
}

.product-card {
  padding: $sp-4;
  border: 1rpx solid $c-border;
  border-radius: $r-lg;
  background: $c-surface;
  box-shadow: $sh-1;
  transition: transform $dur-instant $ease-out, box-shadow $dur-base $ease-out,
    border-color $dur-fast ease;
}

.product-card--press {
  transform: scale(0.977);
}

/* 图片容器：自带品牌色渐变底与占位图形，无图商品不再是生硬灰块 */
.product-media {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 230rpx;
  border-radius: $r-md;
  overflow: hidden;
  background: linear-gradient(150deg, $c-surface-2, $c-bg-sunken);
  margin-bottom: $sp-4;
}

.media-fallback {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.55;
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
  letter-spacing: $ls-normal;
}

.product-subtitle {
  display: block;
  font-size: $fs-caption;
  color: $c-ink-muted;
  margin-top: $sp-1;
  @include ellipsis;
  /* 副标题为空时仍保留一行高度，使同排卡片的名称/价格基线对齐。
     空文本元素会塌陷成 0 高，导致价格行整体上移。
     取值 = 字号 × 全局行高($lh-body)，与有副标题时实际占位一致。 */
  min-height: $fs-caption * $lh-body;
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
}

/* 加购按钮：88rpx（44px）触控目标 */
.add-button {
  width: $tap-min;
  height: $tap-min;
  border-radius: 50%;
  background: $c-dark;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $sh-2;
  transition: transform $dur-instant $ease-out, box-shadow $dur-base $ease-out;
}

.add-button--press {
  transform: scale(0.9);
}

/* ---- 骨架屏 ---- */
.product-card--skeleton {
  animation: none;
  box-shadow: none;
  border-color: $c-border;
}

.skeleton-media {
  height: 230rpx;
  margin-bottom: $sp-4;
}

.skeleton-line {
  height: 26rpx;
  margin-top: $sp-2;
}

.skeleton-line--short {
  width: 56%;
}

/* ---- 空态 ---- */
.state-block {
  display: flex;
  padding: $sp-8 0;
  flex-direction: column;
  align-items: center;
  gap: $sp-3;
  animation: reveal-up 380ms $ease-out both;
}

.state-text {
  font-size: $fs-caption;
  text-align: center;
}

/* ---- H5 悬停 ---- */
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

.add-button:hover {
  transform: scale(1.06);
}
/* #endif */
</style>
