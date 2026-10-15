<template>
  <view class="screen order-screen">
    <PhoneHeader title="取餐订单" />

    <view class="order-body">
      <view v-if="order" class="current-heading">
        <view>
          <text class="section-title">正在进行</text>
          <text class="muted current-caption">最新订单 · 到店后请出示取餐号</text>
        </view>
        <text class="current-badge">制作中</text>
      </view>

      <!-- 制作进度卡片 -->
      <view v-if="order" class="progress-card">
        <text class="progress-caption">咖啡师正在为你制作</text>
        <text class="pickup-number">{{ order.pickup }}</text>
        <text class="ready-caption">预计 {{ order.ready }} 可取 · 约需 {{ order.preparationTime || 5 }} 分钟</text>
        <view class="progress-track">
          <view />
          <view />
        </view>
        <view class="progress-labels">
          <text>已接单</text>
          <text class="gold">制作中</text>
          <text>待取餐</text>
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
        <view>
          <text class="muted">实付金额</text>
          <text class="gold">¥{{ order.total }} · 微信支付</text>
        </view>
        <view>
          <text class="muted">下单时间</text>
          <text>{{ order.created }}</text>
        </view>
        <view>
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
            <text>{{ historyOrder.summary }}</text>
            <text class="muted">共 {{ historyOrder.count }} 杯</text>
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

    <!-- 底部操作栏 -->
    <view class="bottom-fixed order-footer">
      <template v-if="order">
        <text class="muted">到店后向咖啡师出示取餐号即可</text>
        <view class="order-footer-actions">
          <button class="outline-button" @tap="completeCurrentOrder">确认已取餐</button>
          <button class="black-button" @tap="voucherOpen = true">查看取餐凭证</button>
        </view>
      </template>
      <MainTabBar active="orders" @select="handleTabSelect" />
      <HomeIndicator />
    </view>

    <!-- 取餐凭证弹窗 -->
    <view v-if="voucherOpen" class="voucher-mask" @tap="voucherOpen = false">
      <view class="voucher" @tap.stop>
        <view class="voucher-heading">
          <text>取餐凭证</text>
          <text @tap="voucherOpen = false">×</text>
        </view>
        <text class="voucher-brand">隐白</text>
        <text class="muted">{{ storeName }}</text>
        <text class="voucher-number gold">{{ order.pickup }}</text>
        <text>共 {{ order.count }} 杯 · 到店自提</text>
        <text class="muted voucher-hint">请向咖啡师出示此取餐号</text>
        <button class="black-button" @tap="voucherOpen = false">收好凭证</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PhoneHeader from '../../components/PhoneHeader.vue'
import HomeIndicator from '../../components/HomeIndicator.vue'
import StoreLine from '../../components/StoreLine.vue'
import OrderItem from '../../components/OrderItem.vue'
import MainTabBar from '../../components/MainTabBar.vue'
import { shop, completeOrder, refreshOrders } from '../../store/index'
import { STORE } from '../../config/constants'
import { navigateToStore, showStoreInfo, resetToPage } from '../../utils/navigation'

const voucherOpen = ref(false)

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
const storeName = STORE.name

onShow(async () => {
  // refreshOrders 内部已做并发去重，切页往返不会重复请求
  try {
    await refreshOrders()
  } catch (error) {
    uni.showToast({ title: error.message || '无法读取订单', icon: 'none' })
  }
})

function handleNavigateStore() {
  navigateToStore()
}

function handleShowStore() {
  showStoreInfo()
}

function handleTabSelect(tab) {
  if (tab === 'home') {
    resetToPage('index')
  } else if (tab === 'orders') {
    return
  } else if (tab === 'member') {
    resetToPage('member')
  } else if (tab === 'profile') {
    resetToPage('profile')
  }
}

function completeCurrentOrder() {
  uni.showModal({
    title: '确认完成取餐',
    content: '确认后订单会移入历史记录。',
      success: async (result) => {
      if (result.confirm) {
          try {
            voucherOpen.value = false
            await completeOrder()
          } catch (error) {
            uni.showToast({ title: error.message || '完成取餐失败', icon: 'none' })
          }
      }
    }
  })
}
</script>

<style scoped>
.order-screen {
  padding-bottom: 520rpx;
}

.order-body {
  padding: 46rpx 44rpx 0;
}

.progress-card {
  min-height: 513rpx;
  background: #030303;
  border: 2rpx solid #b99a32;
  border-radius: 23rpx;
  color: #eee;
  text-align: center;
  padding: 51rpx 40rpx;
  box-shadow: 0 16rpx 24rpx rgba(0, 0, 0, 0.06);
}

.progress-caption {
  display: block;
  font-size: 22rpx;
}

.pickup-number {
  display: block;
  font-size: 115rpx;
  font-weight: 300;
  line-height: 1.2;
  margin-top: 48rpx;
  letter-spacing: -5rpx;
}

.ready-caption {
  display: block;
  font-size: 24rpx;
  margin-top: 43rpx;
}

.progress-track {
  display: flex;
  gap: 15rpx;
  height: 15rpx;
  margin-top: 34rpx;
}

.progress-track view {
  border-radius: 20rpx;
  background: #565656;
  flex: 1;
}

.progress-track view:first-child {
  background: #d4ad2f;
}

.progress-labels {
  display: flex;
  justify-content: space-between;
  color: #888;
  font-size: 20rpx;
  margin-top: 29rpx;
}

.progress-labels text:first-child {
  color: #ccc;
}

.store-block {
  padding: 57rpx 0 45rpx;
  border-bottom: 1rpx solid #f0f0f0;
  margin-bottom: 53rpx;
}

.address {
  display: block;
  font-size: 22rpx;
  margin-top: 28rpx;
}

.store-actions {
  display: flex;
  gap: 30rpx;
  margin-top: 42rpx;
}

.store-actions button {
  flex: 1;
}

.your-coffee {
  margin-bottom: 37rpx;
}

.order-metadata {
  margin-top: 55rpx;
}

.order-metadata > view {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 24rpx;
  margin-bottom: 42rpx;
}

.order-metadata .gold {
  font-size: 22rpx;
}

.order-footer {
  padding: 18rpx 0 0;
}

.order-footer > text {
  display: block;
  text-align: center;
  font-size: 20rpx;
  margin: 0 44rpx 18rpx;
}

.order-footer .black-button {
  border: 2rpx solid #c3a438;
}

.order-footer-actions {
  display: flex;
  gap: 16rpx;
  margin: 0 44rpx 18rpx;
}

.order-footer-actions button {
  flex: 1;
}

.no-order {
  padding: 180rpx 0;
  text-align: center;
}

.current-heading,
.history-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.current-heading {
  margin-bottom: 24rpx;
}

.current-heading .section-title,
.history-heading .section-title {
  margin-bottom: 0;
}

.current-caption,
.history-caption {
  display: block;
  margin-top: 8rpx;
  font-size: 21rpx;
}

.current-badge,
.history-status {
  flex-shrink: 0;
  padding: 9rpx 16rpx;
  border-radius: 24rpx;
  color: #a17e14;
  background: #f8f1d9;
  font-size: 20rpx;
}

.history-heading {
  margin: 64rpx 0 25rpx;
}

.history-count {
  color: #999;
  font-size: 22rpx;
}

.history-card {
  margin-bottom: 20rpx;
  padding: 27rpx;
  border: 1rpx solid #eee9dc;
  border-radius: 19rpx;
  background: #fff;
  box-shadow: 0 8rpx 20rpx rgba(25, 21, 11, 0.04);
}

.history-card-top,
.history-items,
.history-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.history-pickup-label {
  display: block;
  color: #999;
  font-size: 19rpx;
}

.history-pickup {
  display: block;
  margin-top: 5rpx;
  font-size: 45rpx;
  line-height: 1.1;
}

.history-total {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 9rpx;
  font-size: 22rpx;
}

.history-status {
  padding: 5rpx 12rpx;
  font-size: 18rpx;
}

.history-divider {
  height: 1rpx;
  margin: 20rpx 0;
  background: #f0eee8;
}

.history-items {
  align-items: flex-start;
  color: #292720;
  font-size: 22rpx;
}

.history-items > text:first-child {
  flex: 1;
  min-width: 0;
  line-height: 1.5;
}

.history-items > text:last-child {
  flex-shrink: 0;
}

.history-meta {
  flex-wrap: wrap;
  margin-top: 17rpx;
  font-size: 18rpx;
}

.history-empty {
  display: flex;
  min-height: 210rpx;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  border: 1rpx dashed #e6e0d1;
  border-radius: 19rpx;
  color: #999;
  font-size: 22rpx;
  text-align: center;
}

.empty-mark {
  color: #c9ad5e;
  font-size: 48rpx;
}

.voucher-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 30;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 50rpx;
}

.voucher {
  padding: 40rpx;
  width: 100%;
  background: #fff;
  border-radius: 25rpx;
  text-align: center;
}

.voucher-heading {
  display: flex;
  justify-content: space-between;
  font-size: 30rpx;
  margin-bottom: 35rpx;
}

.voucher-brand {
  display: block;
  font-family: SimSun, serif;
  font-size: 60rpx;
  margin-bottom: 25rpx;
}

.voucher-number {
  display: block;
  font-size: 150rpx;
  margin: 45rpx 0;
}

.voucher-hint {
  display: block;
  font-size: 20rpx;
  margin: 30rpx 0;
}

.voucher .black-button {
  width: 100%;
  margin-top: 20rpx;
}
</style>
