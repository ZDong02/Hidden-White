<template>
  <!-- 订单面板：进度卡片、门店信息、本单商品与历史订单 -->
  <scroll-view class="panel-scroll orders-panel" scroll-y>
    <view class="order-body">
      <view v-if="order" class="current-heading reveal">
        <view>
          <text class="section-title heading-title">正在进行</text>
          <text class="card-note">最新订单 · 到店后请出示取餐号</text>
        </view>
        <text class="tag">制作中</text>
      </view>

      <!-- 制作进度卡片：点击展开取餐凭证 -->
      <view
        v-if="order"
        class="progress-card card-dark reveal reveal-1"
        hover-class="progress-card--press"
        :hover-stay-time="60"
        @tap="emit('show-voucher')"
      >
        <text class="progress-caption">咖啡师正在为你制作</text>
        <text class="pickup-number">{{ order.pickup }}</text>
        <text class="ready-caption">
          预计 {{ order.ready }} 可取 · 约需 {{ order.preparationTime || 5 }} 分钟
        </text>
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
          <Icon name="chevron-right" tone="gold-on-dark" :size="24" />
        </view>
      </view>

      <!-- 门店信息 -->
      <view v-if="order" class="store-block reveal reveal-2">
        <StoreLine :arrow="false" />
        <text class="address muted">{{ storeAddress }}</text>
        <view class="store-actions">
          <button class="outline-button" hover-class="is-press" :hover-stay-time="60" @tap="handleNavigateStore">
            导航到店
          </button>
          <button class="outline-button" hover-class="is-press" :hover-stay-time="60" @tap="handleShowStore">
            联系门店
          </button>
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
          <text class="num">{{ order.number }}</text>
        </view>
      </view>

      <view class="history-heading">
        <view>
          <text class="section-title heading-title">历史订单</text>
          <text class="card-note">已完成的到店自提记录</text>
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
              <text class="tag tag-muted">已完成</text>
              <text class="num">实付 ¥{{ historyOrder.total }}</text>
            </view>
          </view>
          <view class="divider history-divider" />
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
        <Icon name="cup" tone="decor" :size="52" />
        <text>完成取餐后，订单记录会保存在这里</text>
      </view>
    </view>
  </scroll-view>
</template>

<script setup>
import { computed } from 'vue'
import Icon from '../ui/Icon.vue'
import StoreLine from '../StoreLine.vue'
import OrderItem from '../OrderItem.vue'
import { shop, storeInfo, refreshOrders } from '../../store/index'
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
  // 与点店铺行同一套逻辑：接口坐标优先，缺失回落常量，非法则降级为地址弹窗
  navigateToStore({
    name: storeInfo.name,
    address: storeInfo.address,
    latitude: storeInfo.latitude,
    longitude: storeInfo.longitude
  })
}

function handleShowStore() {
  showStoreInfo()
}

defineExpose({ activate })
</script>

<style lang="scss" scoped>
.orders-panel {
  box-sizing: border-box;
  padding-bottom: $pad-panel-order;
}

.order-body {
  padding: $pad-panel-top $gutter 0;
}

/* ---- 进度卡片 ---- */
.progress-card {
  min-height: 500rpx;
  padding: $sp-7 $sp-6;
  text-align: center;
  transition: transform $dur-instant $ease-out, box-shadow $dur-base $ease-out;
}

/* 按压反馈用 hover-class：小程序 :active 在真机不可靠 */
.progress-card--press {
  transform: scale(0.985);
}

.progress-caption {
  display: block;
  font-size: $fs-caption;
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
  color: $c-gold-on-dark;
  @include numeric;
}

.ready-caption {
  display: block;
  font-size: $fs-body-sm;
  margin-top: $sp-6;
  color: $c-on-dark-muted;
}

.progress-track {
  display: flex;
  gap: $sp-3;
  height: 14rpx;
  margin-top: $sp-5;
}

.track-segment {
  flex: 1;
  border-radius: $r-pill;
  background: $c-dark-track;
  overflow: hidden;
}

/* 已完成段：进入时从左侧展开，把「进度」这件事演出来，而不是直接满格 */
.track-segment--done {
  position: relative;
  background: $c-dark-track;
}

.track-segment--done::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: $c-gold;
  transform-origin: left center;
  animation: track-grow 720ms $ease-out 160ms both;
}

@keyframes track-grow {
  from {
    transform: scaleX(0);
  }

  to {
    transform: scaleX(1);
  }
}

.progress-labels {
  display: flex;
  justify-content: space-between;
  margin-top: $sp-4;
  font-size: $fs-micro;
  color: $c-on-dark-faint;
}

/* 已完成的那一段 */
.progress-label-done {
  color: $c-on-dark-muted;
}

/* ---- 取餐凭证入口 ---- */
.voucher-entry {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $sp-2;
  margin-top: $sp-6;
  padding-top: $sp-5;
  border-top: 1rpx solid $c-hairline-gold;
}

.voucher-entry-text {
  font-size: $fs-micro;
  color: $c-gold-on-dark;
  letter-spacing: $ls-wide;
}

/* ---- 门店信息 ---- */
.store-block {
  padding: $sp-7 0 $sp-6;
  border-bottom: 1rpx solid $c-divider;
  margin-bottom: $sp-7;
}

.address {
  display: block;
  font-size: $fs-caption;
  margin-top: $sp-2;
}

.store-actions {
  display: flex;
  gap: $sp-5;
  margin-top: $sp-5;
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

.metadata-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $sp-4;
  font-size: $fs-caption;
  margin-bottom: $sp-5;
}

.order-metadata .gold {
  font-size: $fs-caption;
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

/* 标题行里的 section-title 不再自带下边距，改由卡片间距承担 */
.heading-title {
  margin-bottom: $sp-1;
}

.history-heading {
  margin: $sp-8 0 $sp-4;
}

.history-count {
  color: $c-ink-muted;
  font-size: $fs-caption;
}

/* 历史卡片：与首页商品卡同圆角（$r-lg），只是内边距更大 */
.history-card {
  margin-bottom: $sp-3;
  padding: $sp-5;
  border: 1rpx solid $c-border;
  border-radius: $r-lg;
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
  font-size: $fs-micro;
}

.history-pickup {
  display: block;
  margin-top: $sp-0;
  font-family: $font-display;
  font-size: $fs-h2;
  line-height: 1.1;
  @include numeric;
}

.history-total {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: $sp-1;
  font-size: $fs-caption;
}

.history-divider {
  margin: $sp-4 0;
}

.history-items {
  align-items: flex-start;
  color: $c-ink;
  font-size: $fs-caption;
}

.history-summary {
  flex: 1;
  min-width: 0;
  line-height: 1.6;
}

.history-qty {
  flex-shrink: 0;
}

.history-meta {
  flex-wrap: wrap;
  margin-top: $sp-3;
  font-size: $fs-micro;
}

.history-empty {
  display: flex;
  min-height: 240rpx;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-3;
  border: 1rpx dashed $c-border-strong;
  border-radius: $r-lg;
  color: $c-ink-muted;
  font-size: $fs-caption;
  text-align: center;
}

/* #ifdef H5 */
.progress-card:hover {
  box-shadow: $sh-3;
}

.history-card:hover {
  border-color: $c-border-strong;
}
/* #endif */
</style>
