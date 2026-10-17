<template>
  <!-- 订单面板：进度卡片、门店信息、本单商品与历史订单 -->
  <scroll-view class="panel-scroll orders-panel" scroll-y>
    <view class="order-body">
      <view v-if="order" class="current-heading">
        <view>
          <text class="section-title">正在进行</text>
          <text class="muted current-caption">最新订单 · 到店后请出示取餐号</text>
        </view>
        <text class="current-badge">制作中</text>
      </view>

      <!-- 制作进度卡片：点击展开取餐凭证 -->
      <view
        v-if="order"
        class="progress-card"
        hover-class="progress-card--press"
        @tap="emit('show-voucher')"
      >
        <text class="progress-caption">咖啡师正在为你制作</text>
        <text class="pickup-number">{{ order.pickup }}</text>
        <text class="ready-caption">预计 {{ order.ready }} 可取 · 约需 {{ order.preparationTime || 5 }} 分钟</text>
        <view class="progress-track">
          <view class="track-segment track-segment--done" />
          <view class="track-segment" />
        </view>
        <view class="progress-labels">
          <text class="progress-label-done">已接单</text>
          <text class="gold">制作中</text>
          <text>待取餐</text>
        </view>
        <!-- 取餐凭证入口：由底部操作区的按钮迁到这里，点击展开完整凭证 -->
        <view class="voucher-entry">
          <text class="voucher-entry-text">出示取餐凭证</text>
          <view class="voucher-entry-arrow" />
        </view>
      </view>

      <!-- 门店信息 -->
      <view v-if="order" class="store-block">
        <StoreLine :arrow="false" />
        <text class="address muted">{{ storeAddress }}</text>
        <view class="store-actions">
          <button class="outline-button" @tap="handleNavigateStore">导航到店</button>
          <button class="outline-button" @tap="handleShowStore">联系门店</button>
        </view>
      </view>

      <!-- 订单商品 -->
      <text v-if="order" class="section-title">本单商品</text>
      <view v-for="item in currentItems" :key="item.id + item.size + item.temperature + item.milk" class="your-coffee">
        <OrderItem :item="item" />
      </view>

      <!-- 订单信息 -->
      <view v-if="order" class="order-metadata">
        <view class="metadata-row">
          <text class="muted">实付金额</text>
          <text class="gold">¥{{ order.total }} · 微信支付</text>
        </view>
        <view class="metadata-row">
          <text class="muted">下单时间</text>
          <text>{{ order.created }}</text>
        </view>
        <view class="metadata-row">
          <text class="muted">订单编号</text>
          <text>{{ order.number }}</text>
        </view>
      </view>

      <view class="history-heading">
        <view>
          <text class="section-title">历史订单</text>
          <text class="muted history-caption">已完成的到店自提记录</text>
        </view>
        <text class="history-count">{{ shop.orderHistory.length }} 笔</text>
      </view>

      <view v-if="shop.orderHistory.length" class="history-list">
        <view
          v-for="historyOrder in historyItems"
          :key="historyOrder.number"
          class="history-card"
        >
          <view class="history-card-top">
            <view>
              <text class="history-pickup-label">取餐号</text>
              <text class="history-pickup gold">{{ historyOrder.pickup }}</text>
            </view>
            <view class="history-total">
              <text class="history-status">已完成</text>
              <text>实付 ¥{{ historyOrder.total }}</text>
            </view>
          </view>
          <view class="history-divider" />
          <view class="history-items">
            <text class="history-summary">{{ historyOrder.summary }}</text>
            <text class="muted history-qty">共 {{ historyOrder.count }} 杯</text>
          </view>
          <view class="history-meta">
            <text class="muted">{{ historyOrder.created }}</text>
            <text class="muted">订单号 {{ historyOrder.number }}</text>
          </view>
        </view>
      </view>

      <view v-else class="history-empty">
        <text class="empty-mark">☕</text>
        <text>完成取餐后，订单记录会保存在这里</text>
      </view>
    </view>
  </scroll-view>
</template>

<script setup>
import { computed } from 'vue'
import StoreLine from '../StoreLine.vue'
import OrderItem from '../OrderItem.vue'
import { shop, refreshOrders } from '../../store/index'
import { STORE } from '../../config/constants'
import { navigateToStore, showStoreInfo } from '../../utils/navigation'

const order = computed(() => shop.order)
const currentItems = computed(() => shop.order?.items || [])
const historyItems = computed(() => shop.orderHistory.map((historyOrder) => ({
  // 只挑选模板真正用到的字段：原先 {...historyOrder} 会连同 items 数组一起浅拷贝，
  // 历史订单达上百条时每次重渲染都要额外分配大量对象
  number: historyOrder.number,
  pickup: historyOrder.pickup,
  total: historyOrder.total,
  count: historyOrder.count,
  created: historyOrder.created,
  summary: (historyOrder.items || []).map((item) => `${item.name} ×${item.quantity}`).join('、')
})))
const storeAddress = STORE.address

/**
 * 取餐凭证入口迁入本面板，但弹窗本体留在壳页面（index.vue）渲染。
 * 原因：swiper-item 会带 transform，弹窗作为 scroll-view 的子节点时
 * position: fixed 在微信端会相对 swiper-item 定位而错位；放在页面层才稳定。
 */
const emit = defineEmits(['show-voucher'])

// 壳页面在切到本面板 / 页面 onShow 时调用；
// refreshOrders 内部已做并发去重，频繁切 Tab 不会重复请求
async function activate() {
  try {
    await refreshOrders()
  } catch (error) {
    uni.showToast({ title: error.message || '无法读取订单', icon: 'none' })
  }
}

function handleNavigateStore() {
  navigateToStore()
}

function handleShowStore() {
  showStoreInfo()
}

defineExpose({ activate })
</script>

<style lang="scss" scoped>
.orders-panel {
  box-sizing: border-box;
  /* 本 Tab 底栏最高：提示语 + 操作条 + 分段控件 + 指示条 ≈ 364rpx */
  padding-bottom: 372rpx;
}

.order-body {
  padding: $sp-6 $gutter 0;
}

.progress-card {
  position: relative;
  overflow: hidden;
  min-height: 500rpx;
  background: #050504;
  border: 2rpx solid $c-gold-line;
  border-radius: $r-lg;
  color: $c-on-dark;
  text-align: center;
  padding: $sp-7 $sp-6;
  box-shadow: $sh-dark;
  transition: transform $dur-fast ease-out, opacity $dur-fast ease-out;
}

/* 按压反馈用 hover-class：小程序 :active 在真机不可靠 */
.progress-card--press {
  transform: scale(0.985);
  opacity: 0.9;
}

/* 与首页 Banner 同源的暗金细环，强化「同一套设计语言」 */
.progress-card::after {
  content: '';
  position: absolute;
  right: -50rpx;
  top: -80rpx;
  width: 240rpx;
  height: 240rpx;
  border: 1rpx solid rgba(212, 173, 47, 0.22);
  border-radius: 50%;
  pointer-events: none;
}

.progress-caption {
  display: block;
  font-size: 24rpx;
  color: $c-on-dark-muted;
}

/* 取餐号是这一屏的绝对主角：衬线展示字 + 等宽数字，保证数字不跳动 */
.pickup-number {
  display: block;
  font-family: $font-display;
  font-size: $fs-ticket;
  font-weight: 300;
  line-height: 1.15;
  margin-top: $sp-5;
  letter-spacing: -4rpx;
  font-variant-numeric: tabular-nums;
  color: $c-gold-on-dark;
}

.ready-caption {
  display: block;
  font-size: 26rpx;
  margin-top: $sp-6;
  color: $c-on-dark-muted;
}

.progress-track {
  display: flex;
  gap: $sp-3;
  height: 14rpx;
  margin-top: $sp-5;
}

/* 进度条分段：原为 .progress-track view / :first-child，组件 wxss 不允许标签名选择器 */
.track-segment {
  border-radius: $r-pill;
  background: #4a4a4a;
  flex: 1;
}

.track-segment--done {
  background: $c-gold;
}

.progress-labels {
  display: flex;
  justify-content: space-between;
  color: #9b9b9b;
  font-size: 22rpx;
  margin-top: $sp-4;
}

/* 已完成的那一段（原 .progress-labels text:first-child） */
.progress-label-done {
  color: #d5d5d5;
}

/* ---- 取餐凭证入口（原在底部操作区，现并入进度卡片） ---- */

.voucher-entry {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $sp-3;
  margin-top: $sp-6;
  padding-top: $sp-5;
  border-top: 1rpx solid rgba(212, 173, 47, 0.18);
}

.voucher-entry-text {
  font-size: 22rpx;
  color: $c-gold-on-dark;
  letter-spacing: $ls-wide;
}

/* 右向雪佛龙：与 SlideConfirm 的箭头同一套画法 */
.voucher-entry-arrow {
  width: 12rpx;
  height: 12rpx;
  border-top: 2rpx solid $c-gold-on-dark;
  border-right: 2rpx solid $c-gold-on-dark;
  transform: rotate(45deg);
}

.store-block {
  padding: $sp-7 0 $sp-6;
  border-bottom: 1rpx solid $c-divider;
  margin-bottom: $sp-7;
}

.address {
  display: block;
  font-size: 24rpx;
  margin-top: $sp-4;
}

.store-actions {
  display: flex;
  gap: $sp-5;
  margin-top: $sp-6;
}

/* 两个按钮等宽（原 .store-actions button，组件 wxss 不允许标签名选择器） */
.outline-button {
  flex: 1;
}

.your-coffee {
  margin-bottom: $sp-5;
}

.order-metadata {
  margin-top: $sp-7;
}

/* 每行「标签 + 值」左右分栏（原 .order-metadata > view） */
.metadata-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $sp-4;
  font-size: 24rpx;
  margin-bottom: $sp-6;
}

.order-metadata .gold {
  font-size: 24rpx;
}

.current-heading,
.history-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
}

.current-heading {
  margin-bottom: $sp-4;
}

.current-heading .section-title,
.history-heading .section-title {
  margin-bottom: 0;
}

.current-caption,
.history-caption {
  display: block;
  margin-top: $sp-1;
  font-size: 24rpx;
}

/* 状态标签：文字色用 $c-gold-text，底用极浅金，对比度 4.7:1 */
.current-badge,
.history-status {
  flex-shrink: 0;
  padding: $sp-1 $sp-3;
  border-radius: $r-pill;
  color: $c-gold-text;
  background: $c-gold-soft;
  border: 1rpx solid $c-gold-line;
  font-size: 22rpx;
}

.history-heading {
  margin: $sp-8 0 $sp-4;
}

.history-count {
  color: $c-ink-muted;
  font-size: 24rpx;
}

.history-card {
  margin-bottom: $sp-3;
  padding: $sp-5;
  border: 1rpx solid $c-border;
  border-radius: $r-md;
  background: $c-surface;
  box-shadow: $sh-1;
}

.history-card-top,
.history-items,
.history-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
}

.history-pickup-label {
  display: block;
  color: $c-ink-subtle;
  font-size: 22rpx;
}

.history-pickup {
  display: block;
  margin-top: 4rpx;
  font-family: $font-display;
  font-size: 46rpx;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.history-total {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: $sp-2;
  font-size: 24rpx;
}

.history-status {
  padding: 4rpx $sp-2;
  font-size: 20rpx;
}

.history-divider {
  height: 1rpx;
  margin: $sp-4 0;
  background: $c-divider;
}

.history-items {
  align-items: flex-start;
  color: $c-ink;
  font-size: 24rpx;
}

/* 左侧商品摘要：占满剩余宽度并允许收缩换行（原 .history-items > text:first-child） */
.history-summary {
  flex: 1;
  min-width: 0;
  line-height: 1.6;
}

/* 右侧杯数不参与收缩（原 .history-items > text:last-child） */
.history-qty {
  flex-shrink: 0;
}

.history-meta {
  flex-wrap: wrap;
  margin-top: $sp-3;
  font-size: 22rpx;
}

.history-empty {
  display: flex;
  min-height: 220rpx;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-3;
  border: 1rpx dashed $c-border-strong;
  border-radius: $r-md;
  color: $c-ink-muted;
  font-size: 24rpx;
  text-align: center;
}

.empty-mark {
  color: $c-gold-line;
  font-size: 52rpx;
}
</style>
