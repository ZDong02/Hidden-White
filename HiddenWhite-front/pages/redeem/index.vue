<template>
  <view class="screen redeem-screen">
    <PhoneHeader title="积分兑换" />

    <view class="redeem-body">
      <!-- 积分余额：全站最深的一档表面，与会员卡同一套黑金语汇 -->
      <view class="balance-card card-dark reveal">
        <view class="balance-copy">
          <text class="balance-label">当前积分</text>
          <text class="balance-value num">{{ points }}</text>
          <text class="balance-hint">兑换后 30 天内到店核销，逾期自动退回</text>
        </view>
        <view class="balance-mark">
          <Icon name="sparkle" tone="decor" :size="104" />
        </view>
      </view>

      <!-- 未登录：只给一个入口，不渲染空列表 -->
      <view v-if="!loggedIn" class="empty">
        <Icon name="user" tone="muted" :size="56" />
        <text class="muted empty-text">登录后即可查看积分与兑换好礼</text>
        <UiButton variant="primary" class="empty-action" @tap="goLogin">去登录</UiButton>
      </view>

      <template v-else>
        <view class="section-head reveal reveal-1">
          <text class="section-title head-title">兑换好礼</text>
          <text class="head-note">到店核销 · 积分当钱花</text>
        </view>

        <!-- 骨架屏：首屏加载时不闪空白 -->
        <view v-if="loading && !products.length" class="skeleton-list">
          <view class="skeleton skeleton-card" />
          <view class="skeleton skeleton-card" />
        </view>

        <view v-else-if="!products.length" class="empty">
          <Icon name="cup" tone="decor" :size="56" />
          <text class="muted empty-text">兑换好礼正在准备中，敬请期待</text>
        </view>

        <view
          v-for="product in products"
          :key="product.id"
          :class="['redeem-card', { 'redeem-card--off': !product.exchangeable }]"
          hover-class="redeem-card--press"
          :hover-stay-time="60"
          @tap="openExchange(product)"
        >
          <view class="redeem-media">
            <image
              v-if="product.image"
              class="redeem-image"
              :src="resolveImageUrl(product.image)"
              mode="aspectFill"
            />
            <Icon v-else name="cup" tone="decor" :size="48" />
          </view>

          <view class="redeem-copy">
            <text class="redeem-name">{{ product.name }}</text>
            <text class="redeem-subtitle">{{ product.subtitle }}</text>
            <view class="redeem-meta">
              <text class="redeem-cost num">{{ product.pointsCost }}<text class="redeem-unit"> 积分</text></text>
              <text class="redeem-stock">{{ stockText(product) }}</text>
            </view>
          </view>

          <view class="redeem-action">
            <view :class="['redeem-button', { 'redeem-button--off': !product.exchangeable }]">
              {{ product.exchangeable ? '兑换' : '不可兑' }}
            </view>
            <text v-if="!product.exchangeable" class="redeem-reason">{{ product.reason }}</text>
          </view>
        </view>

        <!-- 我的兑换 -->
        <view class="section-head orders-head">
          <text class="section-title head-title">我的兑换</text>
          <text class="head-note">{{ orderTotal ? `${orderTotal} 笔` : '到店出示核销码' }}</text>
        </view>

        <view v-if="!orders.length && !ordersLoading" class="empty">
          <Icon name="receipt" tone="muted" :size="56" />
          <text class="muted empty-text">还没有兑换记录，先兑一份好礼吧</text>
        </view>

        <view v-for="order in orders" :key="order.id" class="order-card">
          <view class="order-top">
            <text class="order-name">{{ order.productName }}</text>
            <text :class="['tag', { 'tag-muted': order.status !== 'PENDING' }]">{{ statusLabel(order.status) }}</text>
          </view>

          <view class="order-line">
            <text class="muted order-no num">{{ order.redeemNo }}</text>
            <text class="gold num">−{{ order.pointsCost }} 积分</text>
          </view>

          <view class="divider order-divider" />

          <view class="order-bottom">
            <text class="muted order-validity">{{ validityText(order) }}</text>
            <view v-if="order.status === 'PENDING'" class="order-actions">
              <text class="order-link" hover-class="is-press" :hover-stay-time="60" @tap.stop="showCode(order)">核销码</text>
              <text class="order-link order-link--muted" hover-class="is-press" :hover-stay-time="60" @tap.stop="cancelOrder(order)">取消</text>
            </view>
          </view>
        </view>

        <view v-if="orders.length && orders.length < orderTotal" class="load-more">
          <text class="muted">{{ loadingMore ? '正在加载…' : '上滑加载更多' }}</text>
        </view>

        <view class="footnote">积分兑换以门店实际库存为准 · 兑换后请到店出示核销码</view>
      </template>
    </view>

    <!-- 兑换确认弹层：扣分不可逆，滑动确认比二次弹窗更契合现有交互语言 -->
    <view v-if="active" class="sheet-mask" @tap="closeSheet">
      <view class="sheet" @tap.stop>
        <view class="sheet-head">
          <text class="sheet-title">确认兑换</text>
          <view class="sheet-close" aria-label="关闭" hover-class="is-press" :hover-stay-time="60" @tap="closeSheet">
            <Icon name="close" tone="muted" :size="30" />
          </view>
        </view>

        <view class="sheet-product">
          <text class="sheet-name">{{ active.name }}</text>
          <text class="muted sheet-subtitle">{{ active.subtitle }}</text>
        </view>

        <view class="sheet-rows">
          <view class="sheet-row">
            <text class="muted">消耗积分</text>
            <text class="gold num">−{{ active.pointsCost }}</text>
          </view>
          <view class="sheet-row">
            <text class="muted">兑换后余额</text>
            <text class="num">{{ Math.max(points - active.pointsCost, 0) }}</text>
          </view>
          <view class="sheet-row">
            <text class="muted">核销有效期</text>
            <text>{{ VALID_DAYS }} 天（到店出示核销码）</text>
          </view>
        </view>

        <SlideConfirm
          ref="confirmRef"
          class="sheet-confirm"
          hint-text="滑动确认兑换"
          armed-text="松开即可兑换"
          done-text="兑换中…"
          :disabled="exchanging"
          @confirm="submitExchange"
        />
        <text class="sheet-hint">兑换后积分立即扣除；未核销可在有效期内取消并全额退回</text>
      </view>
    </view>

    <!-- 核销码弹层：兑换成功后自动弹出，也可从「我的兑换」再次唤出 -->
    <view v-if="codeOrder" class="sheet-mask" @tap="codeOrder = null">
      <view class="sheet code-sheet" @tap.stop>
        <view class="sheet-head">
          <text class="sheet-title">到店核销</text>
          <view class="sheet-close" aria-label="关闭" hover-class="is-press" :hover-stay-time="60" @tap="codeOrder = null">
            <Icon name="close" tone="muted" :size="30" />
          </view>
        </view>

        <text class="code-brand">隐白</text>
        <text class="code-product">{{ codeOrder.productName }}</text>
        <text class="code-value num">{{ codeText }}</text>
        <text class="muted code-hint">{{ codeHint }}</text>
        <view class="code-actions">
          <UiButton variant="primary" @tap="codeOrder = null" style="width: 100%;">收好核销码</UiButton>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onReachBottom, onShow } from '@dcloudio/uni-app'
import PhoneHeader from '../../components/PhoneHeader.vue'
import Icon from '../../components/ui/Icon.vue'
import UiButton from '../../components/ui/Button.vue'
import SlideConfirm from '../../components/ui/SlideConfirm.vue'
import { requestApi } from '../../config/backend'
import { resolveImageUrl } from '../../utils/image'
import { openPage } from '../../utils/navigation'
import { isLoggedIn } from '../../store/auth'
import { showError, showConfirm } from '../../utils/toast'
import { formatDateTime } from '../../utils/datetime'

/** 与服务端 RedeemService.VALID_DAYS 保持一致；仅用于文案，实际有效期以兑换单为准 */
const VALID_DAYS = 30
const PAGE_SIZE = 10

const STATUS_TEXT = {
  PENDING: '待核销',
  REDEEMED: '已核销',
  CANCELLED: '已取消',
  EXPIRED: '已过期'
}

const loggedIn = computed(() => isLoggedIn.value)
const points = ref(0)
const products = ref([])
const loading = ref(false)

const orders = ref([])
const orderTotal = ref(0)
const ordersLoading = ref(false)
const loadingMore = ref(false)
let orderPage = 1

const active = ref(null)
const exchanging = ref(false)
const confirmRef = ref(null)
const codeOrder = ref(null)

const codeText = computed(() => {
  const code = codeOrder.value?.code || ''
  return code.length === 8 ? `${code.slice(0, 4)} ${code.slice(4)}` : code
})
const codeHint = computed(() => {
  const order = codeOrder.value
  if (!order?.expiresAt) return '请向咖啡师出示此核销码'
  return `有效期至 ${formatDateTime(order.expiresAt).date} · 请向咖啡师出示`
})

// ---- 数据加载 ----

async function loadCatalog() {
  if (!isLoggedIn.value) return
  loading.value = true
  try {
    const data = await requestApi('/redeem/products')
    points.value = Number(data?.points) || 0
    products.value = Array.isArray(data?.products) ? data.products : []
  } catch (error) {
    showError(error.message || '兑换列表加载失败')
  } finally {
    loading.value = false
  }
}

async function loadOrders(reset = false) {
  if (!isLoggedIn.value) return
  if (reset) {
    orderPage = 1
    orders.value = []
  }
  if (reset) ordersLoading.value = true
  else loadingMore.value = true
  try {
    const data = await requestApi(`/redeem/orders?pageNum=${orderPage}&pageSize=${PAGE_SIZE}`)
    const rows = Array.isArray(data?.rows) ? data.rows : []
    orders.value = reset ? rows : [...orders.value, ...rows]
    orderTotal.value = Number(data?.total) || 0
  } catch (error) {
    // 增量加载失败只提示，不把已经拿到的列表清空
    showError(error.message || '兑换记录加载失败')
  } finally {
    ordersLoading.value = false
    loadingMore.value = false
  }
}

function refresh() {
  started = true
  loadCatalog()
  loadOrders(true)
}

// 是否已经发起过首次加载。onLoad 与 onShow 在首次进入时都会触发，
// 用它把「首屏」与「从登录页回来补数据」区分开，避免一次进页发两组请求。
let started = false

onLoad(() => {
  if (!isLoggedIn.value) return
  refresh()
})

onShow(() => {
  // 未登录进页 → 去登录 → 返回：此时才需要补一次加载
  if (isLoggedIn.value && !started) refresh()
})

onReachBottom(() => {
  if (loadingMore.value || ordersLoading.value) return
  if (orders.value.length >= orderTotal.value) return
  orderPage += 1
  loadOrders(false)
})

// ---- 展示辅助 ----

function stockText(product) {
  if (product.stock === null || product.stock === undefined) {
    return product.perMemberLimit > 0 ? `每人限兑 ${product.perMemberLimit} 次` : '不限量'
  }
  return product.stock > 0 ? `剩余 ${product.stock} 份` : '已兑完'
}

function statusLabel(status) {
  return STATUS_TEXT[status] || status
}

function validityText(order) {
  if (order.status === 'REDEEMED' && order.verifiedAt) {
    return `已于 ${formatDateTime(order.verifiedAt).datetime} 核销`
  }
  if (order.status === 'CANCELLED' && order.cancelledAt) {
    return `已于 ${formatDateTime(order.cancelledAt).datetime} 取消，积分已退回`
  }
  if (order.status === 'EXPIRED') return '已过期，积分已退回'
  return `有效期至 ${formatDateTime(order.expiresAt).date}`
}

// ---- 交互 ----

function goLogin() {
  openPage('auth', { redirect: 'redeem' })
}

function openExchange(product) {
  if (!product.exchangeable) {
    // 不可兑的原因来自服务端：积分差多少、是否超限，用户需要看到确切原因
    showConfirm({ title: '暂不可兑换', content: product.reason || '该兑换品当前不可兑换', showCancel: false })
    return
  }
  active.value = product
}

function closeSheet() {
  if (exchanging.value) return
  active.value = null
}

async function submitExchange() {
  if (!active.value || exchanging.value) return
  exchanging.value = true
  try {
    const order = await requestApi(`/redeem/products/${encodeURIComponent(active.value.id)}/exchange`, {
      method: 'POST'
    })
    active.value = null
    // 余额与记录以接口返回为准：服务端扣分后才是真实余额
    loadCatalog()
    loadOrders(true)
    codeOrder.value = order
  } catch (error) {
    // 失败要解锁滑动条，否则用户以为兑换还在进行
    confirmRef.value?.settle(true)
    showError(error.message || '兑换失败，请稍后重试')
    // 库存/积分可能已被别人改变，重新拉一次让卡片状态与事实一致
    loadCatalog()
  } finally {
    exchanging.value = false
  }
}

function showCode(order) {
  codeOrder.value = order
}

async function cancelOrder(order) {
  const ok = await showConfirm({
    title: '取消兑换',
    content: `取消后将退回 ${order.pointsCost} 积分，核销码同时失效。`,
    confirmText: '取消兑换',
    cancelText: '再想想'
  })
  if (!ok) return
  try {
    await requestApi(`/redeem/orders/${encodeURIComponent(order.id)}/cancel`, { method: 'POST' })
    loadCatalog()
    loadOrders(true)
  } catch (error) {
    showError(error.message || '取消失败，请稍后重试')
  }
}
</script>

<style lang="scss" scoped>
.redeem-screen {
  min-height: 100vh;
}

.redeem-body {
  padding: $sp-6 $gutter $sp-8;
}

/* ---- 余额卡 ---- */
.balance-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 220rpx;
  padding: $sp-6;
}

.balance-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
  max-width: calc(100% - 120rpx);
}

.balance-label {
  display: block;
  @include caps;
  color: $c-on-dark-muted;
}

.balance-value {
  display: block;
  margin-top: $sp-1;
  color: $c-gold-on-dark;
  font-size: $fs-display;
  font-weight: 600;
  line-height: $lh-tight;
}

.balance-hint {
  display: block;
  margin-top: $sp-2;
  color: $c-on-dark-muted;
  font-size: $fs-micro;
}

.balance-mark {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  opacity: 0.32;
  pointer-events: none;
}

/* ---- 区块标题 ---- */
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $sp-3;
  margin: $sp-6 2rpx $sp-4;
}

.head-title {
  margin-bottom: 0;
}

.head-note {
  color: $c-ink-muted;
  font-size: $fs-micro;
}

/* ---- 兑换品卡片 ---- */
.redeem-card {
  display: flex;
  align-items: center;
  gap: $sp-4;
  min-height: 190rpx;
  padding: $sp-4;
  margin-bottom: $sp-3;
  border: 1rpx solid $c-border;
  border-radius: $r-lg;
  background: $c-surface;
  box-shadow: $sh-1;
  transition: transform $dur-instant $ease-out, box-shadow $dur-base $ease-out;
}

.redeem-card--press {
  transform: scale(0.98);
}

/* 不可兑：整体降饱和而不是变灰到底，名称与原因仍需清楚可读 */
.redeem-card--off {
  background: $c-surface-2;
  box-shadow: none;
}

.redeem-media {
  display: flex;
  width: 152rpx;
  height: 152rpx;
  flex: 0 0 152rpx;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: $r-md;
  background: $c-surface-3;
}

.redeem-image {
  width: 100%;
  height: 100%;
}

.redeem-copy {
  min-width: 0;
  flex: 1;
}

.redeem-name {
  display: block;
  color: $c-ink-strong;
  font-size: $fs-title-sm;
  font-weight: 600;
  @include ellipsis;
}

.redeem-subtitle {
  display: block;
  margin-top: $sp-0;
  color: $c-ink-muted;
  font-size: $fs-micro;
  @include ellipsis;
}

.redeem-meta {
  display: flex;
  align-items: baseline;
  gap: $sp-2;
  margin-top: $sp-2;
}

.redeem-cost {
  color: $c-gold-text;
  font-size: $fs-body;
  font-weight: 600;
}

.redeem-unit {
  color: $c-gold-text;
  font-size: $fs-micro;
  font-weight: 400;
}

.redeem-stock {
  color: $c-ink-muted;
  font-size: $fs-micro;
  @include ellipsis;
}

.redeem-action {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: $sp-1;
  max-width: 160rpx;
}

.redeem-button {
  display: flex;
  min-width: 128rpx;
  min-height: 64rpx;
  align-items: center;
  justify-content: center;
  padding: 0 $sp-3;
  border-radius: $r-pill;
  background: $c-dark;
  color: $c-gold-on-dark;
  font-size: $fs-caption;
  font-weight: 500;
}

.redeem-button--off {
  background: $c-surface-3;
  color: $c-ink-faint;
}

.redeem-reason {
  max-width: 100%;
  color: $c-ink-muted;
  font-size: $fs-micro;
  text-align: right;
  line-height: $lh-tight;
}

/* ---- 兑换记录 ---- */
.orders-head {
  margin-top: $sp-7;
}

.order-card {
  padding: $sp-4 $sp-5;
  margin-bottom: $sp-3;
  border: 1rpx solid $c-border;
  border-radius: $r-lg;
  background: $c-surface;
  box-shadow: $sh-1;
}

.order-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
}

.order-name {
  min-width: 0;
  color: $c-ink-strong;
  font-size: $fs-body-sm;
  font-weight: 600;
  @include ellipsis;
}

.order-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
  margin-top: $sp-2;
  font-size: $fs-caption;
}

.order-no {
  min-width: 0;
  @include ellipsis;
}

.order-divider {
  margin: $sp-3 0;
}

.order-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
  min-height: 48rpx;
}

.order-validity {
  min-width: 0;
  font-size: $fs-micro;
  @include ellipsis;
}

.order-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: $sp-4;
}

.order-link {
  color: $c-gold-text;
  font-size: $fs-micro;
}

.order-link--muted {
  color: $c-ink-muted;
}

.load-more {
  padding: $sp-4 0 $sp-1;
  text-align: center;
  font-size: $fs-micro;
}

/* ---- 空态 ---- */
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-2;
  padding: $sp-7 $sp-4;
  border: 1rpx dashed $c-border-strong;
  border-radius: $r-lg;
  background: $c-surface-2;
}

.empty-text {
  font-size: $fs-caption;
  text-align: center;
}

.empty-action {
  width: 100%;
  margin-top: $sp-2;
}

.skeleton-list {
  display: block;
}

.skeleton-card {
  height: 190rpx;
  margin-bottom: $sp-3;
  border-radius: $r-lg;
}

.footnote {
  padding: $sp-5 0 $sp-1;
  color: $c-ink-muted;
  font-size: $fs-micro;
  text-align: center;
}

/* ---- 弹层 ---- */
.sheet-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: $z-mask;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: $c-scrim;
  animation: modal-enter 200ms ease both;
}

.sheet {
  width: 100%;
  max-width: $app-max;
  padding: $sp-5 $gutter calc(#{$sp-5} + env(safe-area-inset-bottom));
  border-radius: $r-xl $r-xl 0 0;
  background: $c-surface;
  animation: sheet-enter 260ms $ease-out both;
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
  min-height: $tap-min;
}

.sheet-title {
  color: $c-ink-strong;
  font-size: $fs-title-sm;
  font-weight: 600;
}

.sheet-close {
  display: flex;
  width: 56rpx;
  height: 56rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
}

.sheet-product {
  margin-top: $sp-2;
}

.sheet-name {
  display: block;
  color: $c-ink-strong;
  font-size: $fs-title;
  font-weight: 600;
}

.sheet-subtitle {
  display: block;
  margin-top: $sp-0;
  font-size: $fs-caption;
}

.sheet-rows {
  margin: $sp-4 0;
  padding: $sp-1 $sp-4;
  border-radius: $r-md;
  background: $c-surface-2;
}

.sheet-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
  min-height: 72rpx;
  font-size: $fs-caption;
}

.sheet-row + .sheet-row {
  border-top: 1rpx solid $c-divider;
}

.sheet-confirm {
  margin-top: $sp-2;
}

.sheet-hint {
  display: block;
  margin-top: $sp-3;
  color: $c-ink-muted;
  font-size: $fs-micro;
  text-align: center;
  line-height: $lh-body;
}

/* ---- 核销码 ---- */
.code-sheet {
  text-align: center;
}

.code-brand {
  display: block;
  margin-top: $sp-3;
  color: $c-gold-text;
  font-family: $font-display;
  font-size: $fs-h2;
  letter-spacing: $ls-wider;
}

.code-product {
  display: block;
  margin-top: $sp-1;
  color: $c-ink;
  font-size: $fs-body-sm;
}

.code-value {
  display: block;
  margin: $sp-4 0 $sp-2;
  color: $c-ink-strong;
  font-size: $fs-ticket;
  font-weight: 600;
  letter-spacing: $ls-wider;
  line-height: $lh-tight;
}

.code-hint {
  display: block;
  font-size: $fs-micro;
}

.code-actions {
  margin-top: $sp-5;
}

/* #ifdef H5 */
.redeem-card:hover {
  transform: translateY(-4rpx);
  box-shadow: $sh-2;
  border-color: $c-border-strong;
}
/* #endif */

@media (max-width: 340px) {
  .redeem-body {
    padding-left: $sp-4;
    padding-right: $sp-4;
  }

  .redeem-media {
    width: 128rpx;
    height: 128rpx;
    flex-basis: 128rpx;
  }

  .code-value {
    font-size: $fs-h1;
  }
}
</style>
