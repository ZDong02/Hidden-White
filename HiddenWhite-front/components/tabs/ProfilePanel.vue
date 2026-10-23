<template>
  <!-- 我的面板：账号信息、订单概览与常用服务 -->
  <scroll-view class="panel-scroll profile-panel" scroll-y>
    <view class="profile-hero">
      <view class="avatar"><text>白</text></view>
      <view class="profile-identity">
        <text class="profile-name">{{ isLoggedIn ? currentUser.displayName : '隐白客人' }}</text>
        <text class="profile-caption">{{ isLoggedIn ? currentUser.phone : '登录后享受更多会员礼遇' }}</text>
      </view>
      <text class="guest-badge" @tap="handleAuth">{{ isLoggedIn ? '退出登录' : '登录 / 注册' }}</text>
    </view>

    <view class="member-card" @tap="$emit('switch-tab', 'member')">
      <view class="member-card-copy">
        <text class="member-kicker">HIDDEN WHITE · MEMBER</text>
        <text class="member-title">让每一杯都有礼遇</text>
        <text class="member-link">查看会员权益 ›</text>
      </view>
      <text class="member-mark">✦</text>
    </view>

    <view class="orders-card">
      <view class="section-heading">
        <text>我的订单</text>
        <view class="all-orders" @tap="openOrders">全部订单 <text class="all-orders-arrow">›</text></view>
      </view>
      <view class="order-summary">
        <view class="order-stat" @tap="openOrders">
          <text class="order-stat-value">{{ activeOrderCount }}</text>
          <text class="order-stat-label">进行中</text>
        </view>
        <view class="order-stat-divider" />
        <view class="order-stat" @tap="openOrders">
          <text class="order-stat-value">{{ shop.orderHistory.length }}</text>
          <text class="order-stat-label">历史订单</text>
        </view>
        <view class="order-stat-divider" />
        <view class="order-stat" @tap="openOrders">
          <text class="order-stat-icon">☕</text>
          <text class="order-stat-label">到店自提</text>
        </view>
      </view>
      <view v-if="shop.order" class="latest-order" @tap="openOrders">
        <view>
          <text class="latest-order-label">最新取餐号</text>
          <text class="latest-order-number gold">{{ shop.order.pickup }}</text>
        </view>
        <text class="latest-order-status">制作中 ›</text>
      </view>
      <view v-else class="order-empty" @tap="$emit('switch-tab', 'home')">
        <text>还没有进行中的订单</text>
        <text class="gold">去点一杯 ›</text>
      </view>
    </view>

    <view class="service-card">
      <text class="section-heading service-heading">常用服务</text>
      <view class="service-row" @tap="$emit('switch-tab', 'member')">
        <view class="service-icon gold">✦</view>
        <text class="service-title">会员中心</text>
        <text class="service-description">积分与专属礼遇</text>
        <text class="service-arrow">›</text>
      </view>
      <view class="service-row" @tap="showStoreInfo">
        <view class="service-icon">⌖</view>
        <text class="service-title">门店信息</text>
        <text class="service-description">地址与营业时间</text>
        <text class="service-arrow">›</text>
      </view>
      <view class="service-row" @tap="showFeature('帮助与反馈')">
        <view class="service-icon">?</view>
        <text class="service-title">帮助与反馈</text>
        <text class="service-description">我们随时为你提供帮助</text>
        <text class="service-arrow">›</text>
      </view>
    </view>

    <view class="profile-note">{{ isLoggedIn ? '订单与会员信息已绑定当前账号' : '登录后即可点单，订单信息会安全保存' }}</view>
    <view class="dev-tools" @tap="openBackendSetting" @longpress="onBackendLongPress">
      <view class="dev-row">
        <text class="dev-label">后端服务</text>
        <text class="dev-value" :class="backendOk ? 'ok' : 'bad'">
          {{ backendOk ? '已连接' : '未连接' }}
        </text>
      </view>
      <text class="dev-address">{{ backendAddress || '正在检测…' }} · 点击修改，长按自动探测</text>
    </view>
  </scroll-view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { shop, refreshOrders, invalidateCatalog, clearOrderState } from '../../store/index'
import { currentUser, isLoggedIn, logout } from '../../store/auth'
import { openPage, showStoreInfo } from '../../utils/navigation'
import { showNotice } from '../../utils/toast'
import { checkApiHealth, resetApiBaseUrl, setApiBaseUrl } from '../../config/backend'

const emit = defineEmits(['switch-tab'])

const activeOrderCount = computed(() => shop.order ? 1 : 0)

// 后端连接状态：换网络后能直接看出「连不上」并当场改地址，不用再去翻代码
const backendOk = ref(false)
const backendAddress = ref('')

async function checkBackend() {
  const health = await checkApiHealth()
  backendOk.value = health.ok
  backendAddress.value = health.baseUrl
}

// 壳页面在切到本面板 / 页面 onShow 时调用
async function activate() {
  checkBackend()

  try {
    await refreshOrders()
  } catch (error) {
    uni.showToast({ title: error.message || '订单信息加载失败', icon: 'none' })
  }
}

// 打开后端地址设置：可手动填写、自动探测或恢复默认
function openBackendSetting() {
  uni.showModal({
    title: '后端服务地址',
    content: `当前：${backendAddress.value || '未检测'}\n状态：${backendOk.value ? '已连接' : '未连接'}\n\n可直接填写新地址，如 http://10.30.120.150:8080`,
    editable: true,
    placeholderText: 'http://IP:8080',
    confirmText: '保存',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) return
      const value = (res.content || '').trim()
      if (value) {
        setApiBaseUrl(value)
        // 地址变了，商品与门店缓存需失效，否则会继续展示旧数据
        invalidateCatalog()
      }
      const health = await checkApiHealth()
      backendOk.value = health.ok
      backendAddress.value = health.baseUrl
      if (value && !health.ok) {
        uni.showModal({
          title: '仍然无法连接',
          content: `地址已保存为 ${health.baseUrl}，但连接失败。\n\n请确认：\n1. Spring Boot 已启动\n2. 手机与电脑在同一局域网\n3. 电脑防火墙已放行 8080 端口\n\n点「重置」可恢复自动探测。`,
          confirmText: '知道了'
        })
        return
      }
      if (health.ok) {
        invalidateCatalog()
        uni.showToast({ title: '连接成功', icon: 'success' })
      }
    }
  })
}

// 长按可恢复自动探测（清除手动配置）
function onBackendLongPress() {
  resetApiBaseUrl()
  invalidateCatalog()
  checkBackend()
  uni.showToast({ title: '已恢复自动探测', icon: 'none' })
}

function showFeature(title) {
  showNotice(title, '当前为体验版本，该服务暂未开放。你仍可浏览菜单、创建演示订单并查看取餐记录。')
}

function openOrders() {
  emit('switch-tab', 'orders')
}

function handleAuth() {
  if (!isLoggedIn.value) {
    openPage('auth')
    return
  }

  uni.showModal({
    title: '退出登录',
    content: '退出后需要重新登录才能下单。',
    success: async (res) => {
      if (!res.confirm) return
      await logout()
      // 只清内存不够：上一账号的订单还留在 storage 里，
      // 下次冷启动会被读回来，连带把新会员的首单减免判掉
      clearOrderState()
      uni.showToast({ title: '已退出登录', icon: 'none' })
    }
  })
}

defineExpose({ activate })
</script>

<style lang="scss" scoped>
/* 与其余三个面板统一横向留白与底部预留 */
.profile-panel {
  /* 顶部留白与首页面板一致（$sp-6） */
  padding: $sp-6 $gutter 218rpx;
  box-sizing: border-box;
}

/* 小程序端：与首页一致，整体下移避让右上角胶囊按钮 */
/* #ifdef MP-WEIXIN */
.profile-panel {
  padding-top: 116rpx;
}
/* #endif */

.profile-hero {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: $sp-4;
  /* 顶部归零：面板容器已统一 $sp-6 顶部留白，避免双重叠加 */
  padding: 0 0 $sp-4;
}

.avatar {
  display: flex;
  width: 100rpx;
  height: 100rpx;
  flex: 0 0 100rpx;
  align-items: center;
  justify-content: center;
  border: 2rpx solid $c-gold-line;
  border-radius: 50%;
  color: $c-gold-on-dark;
  background: linear-gradient(145deg, #29261d, #11110f);
  font-family: $font-display;
  font-size: 46rpx;
}

.profile-identity {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: $sp-1;
}

.profile-name {
  color: $c-ink-strong;
  font-size: $fs-title-sm;
  font-weight: 600;
}

.profile-caption {
  overflow: hidden;
  color: $c-ink-muted;
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 登录/退出入口：触控高度提到 88rpx（原 ~60rpx，太小） */
.guest-badge {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  min-height: $tap-min;
  padding: 0 $sp-4;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-pill;
  color: $c-gold-text;
  background: $c-gold-soft;
  font-size: 22rpx;
  transition: transform $dur-fast ease, box-shadow 200ms ease;
}

.guest-badge:active {
  transform: scale(0.96);
}

.member-card {
  position: relative;
  display: flex;
  min-height: 190rpx;
  align-items: center;
  overflow: hidden;
  padding: $sp-5 $sp-6;
  border: 1rpx solid $c-gold-line;
  border-radius: $r-md;
  color: #f0e6c8;
  background: radial-gradient(circle at 90% 10%, #3c3421 0, transparent 38%),
    linear-gradient(120deg, #11110f, #252218);
}

.member-card-copy {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: $sp-2;
}

.member-kicker {
  color: #cdaa53;
  font-size: 20rpx;
  letter-spacing: $ls-wide;
}

.member-title {
  font-family: $font-display;
  font-size: $fs-title-sm;
  color: $c-on-dark;
}

.member-link {
  color: $c-gold-on-dark;
  font-size: 22rpx;
}

.member-mark {
  position: absolute;
  right: 25rpx;
  z-index: 1;
  color: rgba(212, 173, 47, 0.2);
  font-size: 125rpx;
}

.orders-card,
.service-card {
  margin-top: $sp-4;
  padding: $sp-5;
  border: 1rpx solid $c-border;
  border-radius: $r-md;
  background: $c-surface;
  box-shadow: $sh-1;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
  color: $c-ink-strong;
  font-size: 28rpx;
  font-weight: 600;
}

/* 「全部订单 ›」是入口，给足可点高度 */
.all-orders {
  display: flex;
  align-items: center;
  min-height: $tap-min;
  padding-left: $sp-3;
  color: $c-ink-muted;
  font-size: 22rpx;
  font-weight: 400;
}

/* 箭头「›」字号比文字大一号（原 .all-orders text，组件 wxss 不允许标签名选择器） */
.all-orders-arrow {
  margin-left: 4rpx;
  font-size: 30rpx;
  line-height: 1;
}

.order-summary {
  display: flex;
  align-items: stretch;
  margin-top: $sp-3;
}

.order-stat {
  display: flex;
  min-width: 0;
  min-height: $tap-min;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $sp-1;
  border-radius: $r-sm;
  transition: background-color $dur-fast ease;
}

.order-stat-value {
  color: $c-ink-strong;
  font-size: $fs-title;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.order-stat-icon {
  display: flex;
  height: 45rpx;
  align-items: center;
  color: $c-gold-display;
  font-size: 34rpx;
  line-height: 1;
}

.order-stat-label {
  color: $c-ink-muted;
  font-size: 22rpx;
}

.order-stat-divider {
  width: 1rpx;
  height: 55rpx;
  align-self: center;
  background: $c-divider;
}

.latest-order,
.order-empty {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
  min-height: $tap-min;
  margin-top: $sp-4;
  padding: $sp-2 $sp-4;
  border-radius: $r-sm;
  background: $c-surface-2;
}

.latest-order-label {
  display: block;
  color: $c-ink-subtle;
  font-size: 22rpx;
}

.latest-order-number {
  display: block;
  margin-top: 2rpx;
  font-family: $font-display;
  font-size: 34rpx;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.latest-order-status {
  color: $c-ink-muted;
  font-size: 22rpx;
}

.order-empty {
  color: $c-ink-muted;
  font-size: 22rpx;
}

.service-heading {
  margin-bottom: $sp-1;
}

.service-row {
  display: flex;
  min-height: $tap-min;
  align-items: center;
  gap: $sp-3;
  border-top: 1rpx solid $c-divider;
  transition: background-color $dur-fast ease;
}

.service-icon {
  display: flex;
  width: 44rpx;
  height: 44rpx;
  flex: 0 0 44rpx;
  align-items: center;
  justify-content: center;
  border-radius: $r-xs;
  color: $c-ink-muted;
  background: $c-surface-2;
  font-size: 28rpx;
}

.service-icon.gold {
  color: $c-gold-text;
  background: $c-gold-soft;
}

.service-title {
  flex: 0 0 auto;
  color: $c-ink;
  font-size: 26rpx;
}

.service-description {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  color: $c-ink-muted;
  font-size: 22rpx;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-arrow {
  flex: 0 0 auto;
  color: $c-ink-faint;
  font-size: 30rpx;
}

.profile-note {
  padding: $sp-5 0 $sp-1;
  color: $c-ink-muted;
  font-size: 22rpx;
  text-align: center;
}

.dev-tools {
  display: flex;
  min-height: $tap-min;
  flex-direction: column;
  justify-content: center;
  margin-top: $sp-2;
  padding: $sp-4 $sp-5;
  border: 1rpx dashed $c-border-strong;
  border-radius: $r-md;
  background: $c-surface-2;
}

.dev-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $sp-3;
}

.dev-label {
  color: $c-ink-muted;
  font-size: 24rpx;
}

.dev-value {
  font-size: 22rpx;
}

.dev-value.ok {
  color: $c-success;
}

.dev-value.bad {
  color: $c-danger;
}

/* 原先 #a8a49a 对浅底仅约 2.6:1，几乎看不清 */
.dev-address {
  display: block;
  margin-top: $sp-1;
  color: $c-ink-subtle;
  font-size: 22rpx;
  word-break: break-all;
}

/* ---- 交互态 ---- */
/* #ifdef H5 */
.order-stat:hover,
.service-row:hover,
.all-orders:hover,
.latest-order:hover,
.order-empty:hover {
  background: $c-surface-2;
}

.member-card:hover {
  border-color: $c-gold;
}
/* #endif */

.service-row:active,
.latest-order:active,
.order-empty:active {
  background: $c-surface-3;
}

@media (max-width: 340px) {
  .profile-panel {
    padding-right: $sp-4;
    padding-left: $sp-4;
  }

  .profile-hero {
    gap: $sp-3;
  }

  .avatar {
    width: 84rpx;
    height: 84rpx;
    flex-basis: 84rpx;
  }

  .guest-badge {
    padding-right: $sp-3;
    padding-left: $sp-3;
    font-size: 20rpx;
  }

  .service-row {
    gap: $sp-2;
  }
}
</style>
